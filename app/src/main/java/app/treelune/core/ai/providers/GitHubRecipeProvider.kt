package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.ai.data.PromptData
import app.treelune.core.ai.data.PromptPart
import app.treelune.core.ai.prompts.SessionReads
import app.treelune.core.apis.ApiCall
import app.treelune.core.apis.RecipeCall
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.github.GitHubRepo
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * The « Recipe on GitHub » kind (docs/design/github-worker.md): a session's turns go to a program
 * of the user's, started by a session recipe of their repository, of which the app knows nothing.
 * The provider's « model » is that recipe's name, so the config screen lists the repository's
 * session recipes as it lists a server's models. Its calls cost nothing the app can count.
 */
class GitHubRecipeProvider(private val context: Context) : AIProviderKind {

    override val kind: String = KIND

    override fun kindName(context: Context): String = Strings.`for`(context = context).shared("ai_provider_kind_github")

    override fun kindDescription(context: Context): String = Strings.`for`(context = context).shared("ai_provider_kind_github_description")

    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        return listOf(
            SettingNode.Field(FieldDefinition("model", s.shared("ai_provider_github_recipe"), s.shared("ai_provider_github_schema_recipe"),
                FieldType.TEXT, false, mapOf("length" to TextLength.SHORT.name)), required = true),
            ImageInput.node(s)
        )
    }

    override fun getConfigHelp(context: Context): String = Strings.`for`(context = context).shared("ai_provider_github_help")

    // The repository is in Settings › GitHub: nothing of this config is needed to list its recipes
    override fun modelListingSettings(): List<String> = emptyList()

    // A program of the user's: no fact can name it
    override fun factsProvider(): String? = null

    override fun ownPrices(config: JSONObject, context: Context): ManualPrices = ManualPrices(0.0, 0.0, 0.0, 0.0)

    /** The repository's session recipes, each saying whether it reads images when its `recipe.json` does. */
    override suspend fun listModels(config: JSONObject): ProviderModels = try {
        val repo = GitHubRepo.of(context)
        ProviderModels(repo.folderNames("recipes").mapNotNull { name ->
            val spec = repo.file("recipes/$name/recipe.json")?.first?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (spec?.optBoolean("session") != true) return@mapNotNull null
            ProviderModel(name, name, readsImages = if (spec.has(ImageInput.READS_IMAGES)) spec.optBoolean(ImageInput.READS_IMAGES) else null)
        })
    } catch (e: ApiCall.Failure) {
        ProviderModels(emptyList(), Strings.`for`(context = context).shared(e.key).format(*e.args))
    }

    override suspend fun query(promptData: PromptData, config: String): AIResponse =
        GitHubSessions.query(context.applicationContext, promptData, JSONObject(config).getString("model"))

    override fun sessionEnded(sessionId: String) = GitHubSessions.end(context.applicationContext, sessionId)

    companion object {
        const val KIND = "github"
    }
}

/**
 * What a job already holds of a session, for the next call to send only what is new. A session
 * not found here (the app restarted) simply gets a new job, given the whole history.
 */
internal class GitHubJob(
    val branch: String,
    val run: Long,
    val recipe: String,
    /** The fingerprint of the system prompt's parts that a running program cannot be told of. */
    val fixedPrompt: String,
    var level2: String,
    /** How many of the session's fused messages the program holds, and their fingerprint. */
    var sent: Int,
    var sentPrint: String,
    /** The number of the next file the app writes. */
    var turn: Int = 1,
    var ready: Boolean = false
)

/** The sessions' jobs on GitHub: the files of each turn on the session's branch, and the job's life. */
internal object GitHubSessions {

    private const val WORKFLOW = "treelune-session.yml"
    private const val WORKFLOW_PATH = ".github/workflows/$WORKFLOW"
    private const val WORKFLOW_ASSET = "github/$WORKFLOW"
    private const val BRANCH_PREFIX = "treelune/session-"
    private const val READY = "ready.json"
    private const val POLL_MS = 2_000L
    /** One poll in this many also asks whether the job still runs. */
    private const val RUN_CHECK_POLLS = 5
    private const val START_LIMIT_MS = 5 * 60_000L
    private const val REPLY_LIMIT_MS = 15 * 60_000L
    private const val KEPT_MS = 24 * 3_600_000L

    private val jobs = ConcurrentHashMap<String, GitHubJob>()

    /** Outlives the call that ends a job: a cancelled call still deletes its branch. */
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** A failure of this provider, said in a key of the strings. */
    private class Stop(val key: String, val failure: AIFailure) : Exception(key)

    suspend fun query(context: Context, prompt: PromptData, recipe: String): AIResponse {
        val sessionId = checkNotNull(prompt.sessionId) { "A GitHub session needs its session's id" }
        val s = Strings.`for`(context = context)
        return try {
            val repo = GitHubRepo.of(context)
            val fused = fuseConsecutiveUserMessages(transformSystemMessagesToUser(prompt.sessionMessages))
            check(fused.lastOrNull()?.role == "user") { "A prompt that does not end on a user message" }
            val fixed = SessionReads.fingerprint(prompt.level1Content + "\n" + prompt.level3Content)
            val datetime = prompt.buildDatetimeMessage(context)

            var reply: JSONObject? = null
            val held = jobs[sessionId]
            if (held != null) {
                val next = nextMessage(held, recipe, fixed, fused)
                if (next == null || repo.run(held.run).first == "completed") {
                    forget(context, sessionId)
                } else {
                    val replaced = prompt.level2Content.takeIf { it != held.level2 }?.let { s.shared("ai_read_replaces") + "\n\n" + it }
                    val turn = JSONObject().put("message", messageJson(context, next, replaced, datetime))
                    repo.putFile(name(held.turn, "app"), turn.toString(), null, "Turn ${held.turn}", held.branch)
                    held.level2 = prompt.level2Content
                    held.sent = fused.size
                    held.sentPrint = print(fused)
                    reply = await(repo, held)
                    // The job ended as the turn arrived: a new one takes the whole history below
                    if (reply == null) forget(context, sessionId)
                }
            }
            if (reply == null) {
                val job = start(context, repo, prompt, recipe, fixed, fused, datetime)
                jobs[sessionId] = job
                reply = await(repo, job) ?: throw Stop("ai_github_error_job_ended", AIFailure.LOST)
            }
            jobs[sessionId]?.let { it.turn += 2 }
            answer(reply).also { if (!it.success) forget(context, sessionId) }
        } catch (e: CancellationException) {
            // The program is still answering a turn the app no longer waits for: its history is no longer the session's
            forget(context, sessionId)
            throw e
        } catch (e: Stop) {
            forget(context, sessionId)
            LogManager.aiService("GitHub session failed: ${e.key}", "ERROR")
            AIResponse(false, "", errorMessage = s.shared(e.key), failure = e.failure)
        } catch (e: ApiCall.Failure) {
            forget(context, sessionId)
            LogManager.aiService("GitHub session failed: ${e.key}", "ERROR")
            val failure = when {
                e.key == "github_error_network" -> AIFailure.NETWORK
                e.passing -> AIFailure.REFUSED
                else -> AIFailure.CONFIG
            }
            AIResponse(false, "", errorMessage = s.shared(e.key).format(*e.args), failure = failure)
        }
    }

    /** The job of [sessionId] stopped: its branch deleted, which the job watches. */
    fun end(context: Context, sessionId: String) = forget(context, sessionId)

    private fun forget(context: Context, sessionId: String) {
        val job = jobs.remove(sessionId) ?: return
        cleanup.launch {
            try {
                GitHubRepo.of(context).deleteBranch(job.branch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Its job stops by itself after its idle delay, its branch goes with the next day's cleaning
                LogManager.aiService("GitHub session branch not deleted: ${e.message}", "WARN")
            }
        }
    }

    /**
     * The one message to send to a job holding [held], or null when it cannot simply go on: another
     * recipe or system prompt, or a history that is no longer what the program holds plus its
     * answer and one new message (a message removed, a retry).
     */
    internal fun nextMessage(held: GitHubJob, recipe: String, fixedPrompt: String, fused: List<FusedMessage>): FusedMessage? = when {
        held.recipe != recipe || held.fixedPrompt != fixedPrompt -> null
        fused.size != held.sent + 2 || fused[held.sent].role != "assistant" || fused.last().role != "user" -> null
        print(fused.take(held.sent)) != held.sentPrint -> null
        else -> fused.last()
    }

    /** A new job: the workflow in place, the session's branch holding its first file, the workflow dispatched. */
    private suspend fun start(context: Context, repo: GitHubRepo, prompt: PromptData, recipe: String, fixed: String, fused: List<FusedMessage>, datetime: String): GitHubJob {
        val text = context.assets.open(WORKFLOW_ASSET).bufferedReader().use { it.readText() }
        val branch = RecipeCall.ensureWorkflow(repo, text, WORKFLOW_PATH, "Treelune session workflow")
        cleanUp(repo)
        val id = "${System.currentTimeMillis()}-${hex()}"
        val first = JSONObject()
            .put("system", listOf(prompt.level1Content, prompt.level2Content, prompt.level3Content).joinToString("\n\n"))
            .put("message", messageJson(context, fused.last(), null, datetime))
        if (fused.size > 1) first.put("history", JSONArray().apply { fused.dropLast(1).forEach { put(messageJson(context, it, null, null)) } })
        repo.newBranch(BRANCH_PREFIX + id, mapOf(name(1, "app") to first.toString()), "Session $id")
        val run = repo.dispatch(WORKFLOW, branch, mapOf("recipe" to recipe, "session" to id))
        LogManager.aiService("GitHub session job started: run $run", "INFO")
        return GitHubJob(BRANCH_PREFIX + id, run, recipe, fixed, prompt.level2Content, fused.size, print(fused))
    }

    /** The reply to [job]'s current turn; null when the job ended without writing it. */
    private suspend fun await(repo: GitHubRepo, job: GitHubJob): JSONObject? {
        val file = name(job.turn + 1, "reply")
        val began = System.currentTimeMillis()
        var polls = 0
        while (true) {
            try {
                repo.file(file, job.branch)?.let {
                    // A job that answered has started, whatever the polls saw of its ready file
                    job.ready = true
                    return parse(it.first)
                }
                if (++polls % RUN_CHECK_POLLS == 0) {
                    if (!job.ready) job.ready = repo.file(READY, job.branch) != null
                    if (repo.run(job.run).first == "completed") {
                        // Its last push may have landed since the read above
                        return repo.file(file, job.branch)?.let { parse(it.first) }
                    }
                }
            } catch (e: ApiCall.Failure) {
                // A network blip or a busy GitHub while waiting: the limits below bound it
                if (!e.passing) throw e
            }
            val waited = System.currentTimeMillis() - began
            if (!job.ready && waited > START_LIMIT_MS) throw Stop("ai_github_error_no_start", AIFailure.REFUSED)
            if (waited > REPLY_LIMIT_MS) throw Stop("ai_github_error_no_reply", AIFailure.LOST)
            delay(POLL_MS)
        }
    }

    private fun parse(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: org.json.JSONException) {
        throw Stop("ai_github_error_bad_reply", AIFailure.CONFIG)
    }

    /** A reply file read as a provider's response: its failure in the app's categories, its text, its counts. */
    internal fun answer(reply: JSONObject): AIResponse {
        if (reply.has("failure")) {
            val failure = AIFailure.entries.find { it.name == reply.optString("failure") } ?: AIFailure.CONFIG
            return AIResponse(false, "", errorMessage = reply.optString("message").ifEmpty { reply.optString("failure") }, failure = failure)
        }
        val text = reply.optString("text")
        if (text.isBlank()) return AIResponse(false, "", errorMessage = "Empty answer", failure = AIFailure.EMPTY)
        val usage = reply.optJSONObject("usage") ?: JSONObject()
        return AIResponse(
            success = true,
            content = text,
            tokensUsed = usage.optInt("output"),
            cacheWriteTokens = usage.optInt("cache_write"),
            cacheReadTokens = usage.optInt("cache_read"),
            inputTokens = usage.optInt("input")
        )
    }

    /** A message in the API Messages' form, [before] and [after] as text blocks around its own. */
    private fun messageJson(context: Context, message: FusedMessage, before: String?, after: String?): JSONObject {
        val content = JSONArray()
        fun text(value: String) = content.put(JSONObject().put("type", "text").put("text", value))
        before?.let { text(it) }
        message.contentBlocks.forEach { block ->
            when (block) {
                is PromptPart.Text -> text(block.text)
                is PromptPart.Image -> content.put(JSONObject().put("type", "image").put("source", JSONObject()
                    .put("type", "base64")
                    .put("media_type", block.format.mediaType)
                    .put("data", app.treelune.core.files.Images.base64(context, block.imageId, block.format))))
            }
        }
        after?.let { text(it) }
        return JSONObject().put("role", message.role).put("content", content)
    }

    /** The branches of sessions over a day old deleted, but those of a job in hand. */
    private suspend fun cleanUp(repo: GitHubRepo) {
        val now = System.currentTimeMillis()
        val held = jobs.values.map { it.branch }.toSet()
        repo.branches(BRANCH_PREFIX).filter { it !in held }.forEach { name ->
            val started = name.removePrefix(BRANCH_PREFIX).substringBefore('-').toLongOrNull() ?: return@forEach
            if (now - started > KEPT_MS) repo.deleteBranch(name)
        }
    }

    private fun print(messages: List<FusedMessage>): String = SessionReads.fingerprint(messages.joinToString("\u0000") { message ->
        message.role + message.contentBlocks.joinToString("\u0001") { block ->
            when (block) {
                is PromptPart.Text -> block.text
                is PromptPart.Image -> block.imageId
            }
        }
    })

    private fun name(turn: Int, side: String) = "%04d-%s.json".format(java.util.Locale.ROOT, turn, side)

    private fun hex(): String =
        ByteArray(8).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
}
