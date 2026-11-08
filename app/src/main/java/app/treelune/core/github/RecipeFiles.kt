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

    /** The recipes [carried] holds that [recipes] names, each written into the repository, its folder replaced. */
    suspend fun write(context: Context, carried: JSONArray?, recipes: Set<String>) {
        val wanted = (0 until (carried?.length() ?: 0)).map { carried!!.getJSONObject(it) }.filter { it.getString("recipe") in recipes }
        if (wanted.isEmpty()) return
        val repo = GitHubRepo.of(context)
        // A repository still empty has no branch to commit onto: the workflow makes its first commit
        app.treelune.core.apis.RecipeCall.ensureWorkflow(repo, app.treelune.core.apis.RecipeCall.workflow(context))
        wanted.forEach { recipe ->
            require(RECIPE.matches(recipe.getString("recipe"))) { "Not a recipe's folder: ${recipe.getString("recipe")}" }
            val list = recipe.getJSONArray("files")
            val files = (0 until list.length()).map { list.getJSONObject(it) }.associate { file ->
                val path = file.getString("path")
                // A path inside the folder alone: never one that climbs out of it
                require(path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Recipe file outside its folder: $path" }
                // A plain file or one that runs: never a link nor a submodule
                require(file.getString("mode") in MODES) { "Recipe file of mode ${file.getString("mode")}: $path" }
                path to GitHubRepo.RepoFile(file.getString("mode"), Base64.decode(file.getString("content"), Base64.DEFAULT))
            }
            repo.replaceFolder("recipes/${recipe.getString("recipe")}", files, "Recipe ${recipe.getString("recipe")}, imported")
        }
    }
}
