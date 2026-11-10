package app.treelune.core.github

import android.content.Context
import android.util.Base64
import app.treelune.core.ai.providers.awaitReply
import app.treelune.core.apis.ApiCall
import app.treelune.core.config.AppSettings
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.secrets.SecretBox
import app.treelune.core.secrets.SecretSettings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The user's GitHub repository (Settings › GitHub, docs/design/github-worker.md), through GitHub's
 * REST API and the token that opens it: the files the app writes and reads there, the branches of
 * its calls, the workflows it dispatches and their runs. Knows nothing of a recipe nor of a Task.
 *
 * A failure is an [ApiCall.Failure], a key of the strings and its arguments, as an API call's.
 */
class GitHubRepo(val repository: String, private val token: String) {

    /** A file of the repository: its git mode (100644, or 100755 for one that runs) and its bytes. */
    class RepoFile(val mode: String, val bytes: ByteArray)

    /** What one request gave back: its HTTP status and its body, whole. */
    private data class Reply(val code: Int, val body: String)

    /** The repository's default branch: where the workflows and the recipes live. */
    suspend fun defaultBranch(): String =
        json(send(get("repos/$repository"))).getString("default_branch")

    /** The text of the file at [path] on [ref] (the default branch when null), with its blob's sha; null when there is none. */
    suspend fun file(path: String, ref: String? = null): Pair<String, String>? {
        val reply = send(get(contents(path, ref)), allow404 = true) ?: return null
        val found = JSONObject(reply.body)
        val text = String(Base64.decode(found.getString("content"), Base64.DEFAULT), Charsets.UTF_8)
        return text to found.getString("sha")
    }

    /** [text] written at [path] on the default branch, one commit, replacing the blob [sha] when one is there. */
    suspend fun putFile(path: String, text: String, sha: String?, message: String) {
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        sha?.let { body.put("sha", it) }
        send(request("repos/$repository/contents/$path").put(body.toString().toRequestBody(JSON)).build())
    }

    /**
     * The branch [name] made with [files] alone (path to text), one commit without a parent: a
     * call's branch shares nothing with the default branch, its files are the call's.
     */
    suspend fun newBranch(name: String, files: Map<String, String>, message: String) {
        val tree = JSONArray()
        files.forEach { (path, text) -> tree.put(JSONObject().put("path", path).put("mode", "100644").put("type", "blob").put("content", text)) }
        val treeSha = json(send(post("repos/$repository/git/trees", JSONObject().put("tree", tree)))).getString("sha")
        val commit = json(send(post("repos/$repository/git/commits", JSONObject().put("message", message).put("tree", treeSha).put("parents", JSONArray()))))
        send(post("repos/$repository/git/refs", JSONObject().put("ref", "refs/heads/$name").put("sha", commit.getString("sha"))))
    }

    /** The branches whose name starts with [prefix]. */
    suspend fun branches(prefix: String): List<String> =
        JSONArray(send(get("repos/$repository/git/matching-refs/heads/$prefix")).body).let { refs ->
            (0 until refs.length()).map { refs.getJSONObject(it).getString("ref").removePrefix("refs/heads/") }
        }

    /** The branch [name] deleted; one already gone is no failure. */
    suspend fun deleteBranch(name: String) {
        send(request("repos/$repository/git/refs/heads/$name").delete().build(), allow404 = true)
    }

    /**
     * The workflow [workflow] (its file's name) dispatched on [ref] with [inputs]: the id of the
     * run it starts, which GitHub gives back when asked (`return_run_details`).
     */
    suspend fun dispatch(workflow: String, ref: String, inputs: Map<String, String>): Long {
        val body = JSONObject().put("ref", ref).put("inputs", JSONObject(inputs)).put("return_run_details", true)
        return json(send(post("repos/$repository/actions/workflows/$workflow/dispatches", body))).getLong("workflow_run_id")
    }

    /** Where the run [id] stands: its `status` (queued, in_progress, completed…) and, completed, its `conclusion`. */
    suspend fun run(id: Long): Pair<String, String?> {
        val run = json(send(get("repos/$repository/actions/runs/$id")))
        return run.getString("status") to run.optString("conclusion").takeIf { it.isNotEmpty() && it != "null" }
    }

    /** The names of the repository's secrets, never their values, which GitHub never gives. */
    suspend fun secretNames(): Set<String> =
        json(send(get("repos/$repository/actions/secrets?per_page=100"))).getJSONArray("secrets").let { list ->
            (0 until list.length()).map { list.getJSONObject(it).getString("name") }.toSet()
        }

    /**
     * Every file under the folder [folder] of the default branch, by its path inside it, its bytes
     * whole: a recipe's folder, which a part of the app carries. A repository still empty, without
     * a commit, holds none: GitHub answers its trees with a 409.
     */
    suspend fun folder(folder: String): Map<String, RepoFile> {
        val branch = defaultBranch()
        val treeReply = send(get("repos/$repository/git/trees/${java.net.URLEncoder.encode(branch, "UTF-8")}?recursive=1"), allowed = EMPTY_REPOSITORY)
            ?: return emptyMap()
        val tree = json(treeReply)
        if (tree.optBoolean("truncated")) throw ApiCall.Failure("github_error_tree_truncated", folder)
        val entries = tree.getJSONArray("tree")
        return (0 until entries.length()).map { entries.getJSONObject(it) }
            .filter { it.getString("type") == "blob" && it.getString("path").startsWith("$folder/") }
            .associate { entry ->
                val blob = json(send(get("repos/$repository/git/blobs/${entry.getString("sha")}")))
                entry.getString("path").removePrefix("$folder/") to RepoFile(entry.getString("mode"), Base64.decode(blob.getString("content"), Base64.DEFAULT))
            }
    }

    /**
     * The folder [folder] of the default branch made to hold [files] alone (path inside it to
     * file), one commit: a file it held and [files] lacks is removed.
     */
    suspend fun replaceFolder(folder: String, files: Map<String, RepoFile>, message: String) {
        val branch = defaultBranch()
        val head = json(send(get("repos/$repository/git/ref/heads/${branch}"))).getJSONObject("object").getString("sha")
        val baseTree = json(send(get("repos/$repository/git/commits/$head"))).getJSONObject("tree").getString("sha")
        val held = json(send(get("repos/$repository/git/trees/$baseTree?recursive=1"))).getJSONArray("tree").let { entries ->
            (0 until entries.length()).map { entries.getJSONObject(it) }
                .filter { it.getString("type") == "blob" && it.getString("path").startsWith("$folder/") }.map { it.getString("path") }
        }
        val tree = JSONArray()
        files.forEach { (path, file) ->
            val blob = json(send(post("repos/$repository/git/blobs", JSONObject()
                .put("content", Base64.encodeToString(file.bytes, Base64.NO_WRAP)).put("encoding", "base64")))).getString("sha")
            tree.put(JSONObject().put("path", "$folder/$path").put("mode", file.mode).put("type", "blob").put("sha", blob))
        }
        held.filter { it.removePrefix("$folder/") !in files }.forEach { tree.put(JSONObject().put("path", it).put("mode", "100644").put("type", "blob").put("sha", JSONObject.NULL)) }
        val newTree = json(send(post("repos/$repository/git/trees", JSONObject().put("base_tree", baseTree).put("tree", tree)))).getString("sha")
        val commit = json(send(post("repos/$repository/git/commits", JSONObject().put("message", message).put("tree", newTree).put("parents", JSONArray().put(head))))).getString("sha")
        send(request("repos/$repository/git/refs/heads/$branch").patch(JSONObject().put("sha", commit).toString().toRequestBody(JSON)).build())
    }

    /** The address of the file at [path] on [ref], as the contents API serves it. */
    fun fileAddress(path: String, ref: String): String = API + contents(path, ref)

    /**
     * The request that downloads [address], a file of this repository the contents API serves:
     * with the token and in raw form; null when [address] is not one, to be asked as any other.
     */
    fun fileRequest(address: String): Request? {
        val url = address.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.host != API_HOST || !url.encodedPath.startsWith("/repos/$repository/contents/")) return null
        return Request.Builder().url(url).withHeaders().header("Accept", "application/vnd.github.raw+json").get().build()
    }

    // ========================================================================================

    private fun contents(path: String, ref: String?): String =
        "repos/$repository/contents/$path" + (ref?.let { "?ref=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")

    private fun request(path: String): Request.Builder = Request.Builder().url(API + path).withHeaders()

    /** The headers every request to GitHub bears: the token, the API's version, a name for the app. */
    private fun Request.Builder.withHeaders(): Request.Builder =
        header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Treelune")

    private fun get(path: String): Request = request(path).get().build()

    private fun post(path: String, body: JSONObject): Request = request(path).post(body.toString().toRequestBody(JSON)).build()

    private suspend fun send(request: Request): Reply = send(request, allowed = null)!!

    private suspend fun send(request: Request, allow404: Boolean): Reply? = send(request, allowed = if (allow404) 404 else null)

    /** [request] sent; a refusal of GitHub said with its status and its message, the status [allowed] null. */
    private suspend fun send(request: Request, allowed: Int?): Reply? {
        val reply = try {
            CLIENT.awaitReply(request)
        } catch (e: java.io.IOException) {
            throw ApiCall.Failure("github_error_network", e.message ?: e.javaClass.simpleName)
        }
        if (allowed != null && reply.code == allowed) return null
        if (reply.code !in 200..299) {
            val message = runCatching { JSONObject(reply.body).optString("message") }.getOrNull().orEmpty().ifEmpty { "-" }
            throw ApiCall.Failure("github_error_status", reply.code, message)
        }
        return Reply(reply.code, reply.body)
    }

    private fun json(reply: Reply): JSONObject = try {
        JSONObject(reply.body)
    } catch (e: org.json.JSONException) {
        throw ApiCall.Failure("api_error_not_json")
    }

    companion object {
        private const val API_HOST = "api.github.com"
        private const val API = "https://$API_HOST/"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** What GitHub's git database answers about a repository without a commit. */
        private const val EMPTY_REPOSITORY = 409

        /** A repository as Settings › GitHub takes it: its owner and its name. */
        private val REPOSITORY = Regex("[A-Za-z0-9-]+/[A-Za-z0-9._-]+")

        /** A file of a few tens of MB read from GitHub, on a phone's connection. */
        private val CLIENT = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(1, TimeUnit.MINUTES)
            .build()

        /**
         * The repository Settings › GitHub holds, its token opened. A repository or a token not
         * set, or a token sealed on another phone (a restored backup), is a failure said in words.
         */
        suspend fun of(context: Context): GitHubRepo {
            val stored = AppDatabase.getDatabase(context).appSettingsCategoryDao()
                .getSettingsJsonForCategory(AppSettingCategories.GITHUB)?.let { JSONObject(it) } ?: JSONObject()
            val opened = SecretSettings.open(SecretSettings.names(AppSettings.nodes(AppSettingCategories.GITHUB, context)), stored, SecretBox.of())
            if (AppSettings.GITHUB_TOKEN in opened.unreadable) throw ApiCall.Failure("github_error_token_unreadable")
            val repository = opened.settings.optString(AppSettings.GITHUB_REPOSITORY).trim()
            val token = opened.settings.optString(AppSettings.GITHUB_TOKEN).trim()
            if (repository.isEmpty() || token.isEmpty()) throw ApiCall.Failure("github_error_unset")
            if (!REPOSITORY.matches(repository)) throw ApiCall.Failure("github_error_repository", repository)
            return GitHubRepo(repository, token)
        }
    }
}
