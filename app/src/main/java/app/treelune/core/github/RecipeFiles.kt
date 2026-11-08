package app.treelune.core.github

import android.content.Context
import android.util.Base64
import app.treelune.core.apis.ApiSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * A recipe's files carried by a part of the app (docs/design/github-worker.md, « La partager,
 * c'est l'exporter »): the export reads them in the user's repository, under `recipes/<recipe>/`,
 * as they are at that moment; the import writes them into the repository of whoever imports,
 * before the API that names them is created. They go under the part's data key [KEY], one element
 * per recipe: `recipe`, and `files`, each its `path` inside the folder, `mode` and `content` in base64.
 */
object RecipeFiles {

    const val KEY = "recipes"

    /** A recipe's folder name, as ApiSettings takes it. */
    private val RECIPE = Regex("[a-z0-9][a-z0-9-]*")

    private val MODES = setOf("100644", "100755")

    /** The recipes the APIs [apis] (rows of the `apis` table) name, read in the repository; empty when none names one. */
    suspend fun read(context: Context, apis: List<JSONObject>): JSONArray {
        val recipes = apis.map { JSONObject(it.getString("config_json")) }.filter { ApiSettings.isRecipe(it) }
            .map { it.getString(ApiSettings.RECIPE) }.distinct()
        if (recipes.isEmpty()) return JSONArray()
        val repo = GitHubRepo.of(context)
        val out = JSONArray()
        recipes.forEach { recipe ->
            val files = JSONArray()
            repo.folder("recipes/$recipe").forEach { (path, file) ->
                files.put(JSONObject().put("path", path).put("mode", file.mode).put("content", Base64.encodeToString(file.bytes, Base64.NO_WRAP)))
            }
            if (files.length() == 0) throw app.treelune.core.apis.ApiCall.Failure("api_error_recipe_absent", recipe, repo.repository)
            out.put(JSONObject().put("recipe", recipe).put("files", files))
        }
        return out
    }

    /**
     * The recipes [carried] holds that [created] or [replaced] names, written into the repository.
     * One [replaced] names has its folder replaced: the API that runs it is replaced too, as the
     * user chose. One [created] names, for a new API, never overwrites a folder the repository
     * holds that differs (another recipe of that name, a copy made by hand): the import is refused
     * before anything is written, saying so; a folder identical is left as it is.
     */
    suspend fun write(context: Context, carried: JSONArray?, created: Set<String>, replaced: Set<String>) {
        val wanted = (0 until (carried?.length() ?: 0)).map { carried!!.getJSONObject(it) }
            .filter { it.getString("recipe") in created || it.getString("recipe") in replaced }
            .associate { it.getString("recipe") to files(it) }
        if (wanted.isEmpty()) return
        val repo = GitHubRepo.of(context)
        // Every refusal before the first write: a refused import leaves the repository as it was
        val toWrite = wanted.filter { (recipe, files) ->
            if (recipe in replaced) return@filter true
            val held = repo.folder("recipes/$recipe")
            when {
                held.isEmpty() -> true
                same(held, files) -> false
                else -> throw app.treelune.core.apis.ApiCall.Failure("github_error_recipe_taken", recipe, repo.repository)
            }
        }
        if (toWrite.isEmpty()) return
        // A repository still empty has no branch to commit onto: the workflow makes its first commit
        app.treelune.core.apis.RecipeCall.ensureWorkflow(repo, app.treelune.core.apis.RecipeCall.workflow(context))
        toWrite.forEach { (recipe, files) -> repo.replaceFolder("recipes/$recipe", files, "Recipe $recipe, imported") }
    }

    /** The files of [recipe], an element of [KEY], checked: inside its folder, plain or runnable. */
    private fun files(recipe: JSONObject): Map<String, GitHubRepo.RepoFile> {
        require(RECIPE.matches(recipe.getString("recipe"))) { "Not a recipe's folder: ${recipe.getString("recipe")}" }
        val list = recipe.getJSONArray("files")
        return (0 until list.length()).map { list.getJSONObject(it) }.associate { file ->
            val path = file.getString("path")
            // A path inside the folder alone: never one that climbs out of it
            require(path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Recipe file outside its folder: $path" }
            // A plain file or one that runs: never a link nor a submodule
            require(file.getString("mode") in MODES) { "Recipe file of mode ${file.getString("mode")}: $path" }
            path to GitHubRepo.RepoFile(file.getString("mode"), Base64.decode(file.getString("content"), Base64.DEFAULT))
        }
    }

    /** Whether two folders hold the same files, each of the same mode and bytes. */
    internal fun same(a: Map<String, GitHubRepo.RepoFile>, b: Map<String, GitHubRepo.RepoFile>): Boolean =
        a.keys == b.keys && a.all { (path, file) -> b.getValue(path).let { it.mode == file.mode && it.bytes.contentEquals(file.bytes) } }
}
