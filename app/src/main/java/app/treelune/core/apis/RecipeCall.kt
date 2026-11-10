package app.treelune.core.apis

import android.content.Context
import app.treelune.core.github.GitHubRepo
import org.json.JSONObject

/**
 * One call to a recipe of the user's GitHub repository (docs/design/github-worker.md, « L'API
 * « Recette sur GitHub » »): the app writes the call's branch, `treelune/call-<call>`, holding
 * `input.json`, and dispatches the recipe workflow, which pushes `result/` to that branch; the
 * app reads `result/result.json` there until it is written. A call's job id is its call and the
 * id of its run, `<call>:<run>`.
 *
 * The workflow's text lives in the app (assets/github/treelune-recipe.yml): each call writes it
 * into the repository when it is missing there or differs, so that the repository always runs
 * the protocol of the app installed.
 */
class RecipeCall(private val repo: GitHubRepo, private val recipe: String, private val spec: RecipeSpec, private val workflowText: String) {

    /** The call started with [inputs] by name; its job id. */
    suspend fun start(inputs: Map<String, Any?>): String {
        val declared = (0 until spec.inputs.length()).map { spec.inputs.getJSONObject(it) }
            .associate { it.getString(ApiSettings.NAME) to it.getString(ApiSettings.TYPE) }
        (inputs.keys - declared.keys).firstOrNull()?.let { throw ApiCall.Failure("api_error_input_unknown", it, declared.keys.joinToString(", ").ifEmpty { "-" }) }
        val given = inputs.filterValues { it != null }.mapValues { (name, value) -> ApiCall.typed(name, value!!, declared.getValue(name)) }
        val branch = ensureWorkflow(repo, workflowText)
        cleanUp()
        val call = "${System.currentTimeMillis()}-${hex(8)}"
        repo.newBranch(BRANCH_PREFIX + call, mapOf(INPUT to JSONObject(given).toString()), "Call $call")
        val run = repo.dispatch(WORKFLOW, branch, mapOf("recipe" to recipe, "call" to call))
        return "$call:$run"
    }

    /** Where the job [job] stands: its result once written, or why there is none. */
    suspend fun status(job: String): Pair<ApiCall.Status, JSONObject?> {
        val (call, run) = parse(job)
        val written = repo.file(RESULT, BRANCH_PREFIX + call)
        if (written == null) {
            // The workflow always writes its result: a run over without one was cut (cancelled, its machine lost)
            val (state, conclusion) = repo.run(run)
            if (state == "completed") throw ApiCall.Failure("api_error_recipe_no_result", conclusion ?: "-")
            return ApiCall.Status.PENDING to null
        }
        val result = try {
            JSONObject(written.first)
        } catch (e: org.json.JSONException) {
            throw ApiCall.Failure("api_error_not_json")
        }
        return when (result.optString("status")) {
            "done" -> ApiCall.Status.READY to result
            "failed" -> throw ApiCall.Failure("api_error_recipe_failed", result.optString("message").ifEmpty { "-" })
            else -> throw ApiCall.Failure("api_error_recipe_status", result.optString("status"))
        }
    }

    /**
     * The values the recipe declares, read from [result] under `values`, each of its type: a file
     * is the address of the file of that name in `result/` of the call's branch.
     */
    fun values(job: String, result: JSONObject): Map<String, Any> {
        val (call, _) = parse(job)
        val given = result.optJSONObject("values") ?: JSONObject()
        return (0 until spec.values.length()).map { spec.values.getJSONObject(it) }.associate { value ->
            val name = value.getString(ApiSettings.NAME)
            val type = value.getString(ApiSettings.TYPE)
            val found = given.opt(name)?.takeIf { it != JSONObject.NULL } ?: throw ApiCall.Failure("api_error_value_absent", name, "values.$name")
            name to if (type == ApiSettings.FILE) {
                val file = found as? String
                if (file == null || !FILE_NAME.matches(file)) throw ApiCall.Failure("api_error_value_type", name, type)
                repo.fileAddress("result/$file", BRANCH_PREFIX + call)
            } else ApiCall.typed(name, found, type)
        }
    }

    /**
     * The branches of calls over a day old deleted: a call's files are read by the steps that
     * follow it in its Task, which never wait a day; the branch's age is in its name.
     */
    private suspend fun cleanUp() {
        val now = System.currentTimeMillis()
        repo.branches(BRANCH_PREFIX).forEach { name ->
            val started = name.removePrefix(BRANCH_PREFIX).substringBefore('-').toLongOrNull() ?: return@forEach
            if (now - started > KEPT_MS) repo.deleteBranch(name)
        }
    }

    private fun parse(job: String): Pair<String, Long> {
        val call = job.substringBefore(':')
        val run = job.substringAfter(':', "").toLongOrNull()
        if (!CALL.matches(call) || run == null) throw ApiCall.Failure("api_error_recipe_job", job)
        return call to run
    }

    private fun hex(bytes: Int): String =
        ByteArray(bytes).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    companion object {
        const val WORKFLOW = "treelune-recipe.yml"
        private const val WORKFLOW_PATH = ".github/workflows/$WORKFLOW"
        /** Where the workflow's text lives in the app. */
        const val WORKFLOW_ASSET = "github/$WORKFLOW"
        private const val BRANCH_PREFIX = "treelune/call-"
        private const val INPUT = "input.json"
        private const val RESULT = "result/result.json"
        private const val KEPT_MS = 24 * 3_600_000L

        /** A call's id: the moment it started, in milliseconds, and 16 random hex characters; the workflow checks the same form. */
        private val CALL = Regex("[0-9]{13}-[0-9a-f]{16}")

        /** A file a result names: in `result/`, never in a folder. */
        private val FILE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

        /** The recipe call of [config], a saved recipe API's, through the repository Settings › GitHub holds. */
        suspend fun of(context: Context, config: JSONObject): RecipeCall = RecipeCall(
            GitHubRepo.of(context), config.getString(ApiSettings.RECIPE), RecipeSpec.of(config), workflow(context))

        /**
         * The workflow [text] written into [repo] when it is missing there or differs; the branch
         * it is on. An empty repository gets its first commit so.
         */
        suspend fun ensureWorkflow(repo: GitHubRepo, text: String): String {
            val held = repo.file(WORKFLOW_PATH)
            if (held?.first != text) repo.putFile(WORKFLOW_PATH, text, held?.second, "Treelune recipe workflow")
            return repo.defaultBranch()
        }

        /** The workflow's text, as the app carries it. */
        fun workflow(context: Context): String = context.assets.open(WORKFLOW_ASSET).bufferedReader().use { it.readText() }

        /** The spec of the recipe [recipe], read in the repository: what a recipe API keeps of it when saved. */
        suspend fun readSpec(context: Context, recipe: String): RecipeSpec {
            val repo = GitHubRepo.of(context)
            val text = repo.headFile("recipes/$recipe/recipe.json") ?: throw ApiCall.Failure("api_error_recipe_absent", recipe, repo.repository)
            return RecipeSpec.parse(text)
        }
    }
}
