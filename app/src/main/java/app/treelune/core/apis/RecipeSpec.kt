package app.treelune.core.apis

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a recipe declares of itself to the app, read from its `recipe.json` in the user's GitHub
 * repository (docs/design/github-worker.md): the inputs a call is given, the values its
 * `result.json` holds, the longest it runs, the secrets of the repository it needs. The API keeps
 * this much of it (ApiSettings.RECIPE_SPEC), read again each time it is saved; the rest of the file
 * (its command, what the machine installs) is the workflow's.
 */
class RecipeSpec private constructor(
    val inputs: JSONArray,
    val values: JSONArray,
    val timeoutMinutes: Int,
    val secrets: List<String>
) {

    /** The form the API stores. */
    fun toJson(): JSONObject = JSONObject()
        .put(INPUTS, inputs)
        .put(VALUES, values)
        .put(TIMEOUT, timeoutMinutes)
        .put(SECRETS, JSONArray(secrets))

    companion object {
        private const val INPUTS = "inputs"
        private const val VALUES = "values"
        private const val TIMEOUT = "timeout_minutes"
        private const val SECRETS = "secrets"

        /** The longest run the workflow lets a recipe have (its RECIPE_MAX_MINUTES). */
        const val MAX_MINUTES = 50

        /** Beyond its longest run, the time for the machine to start, install, and the result to arrive. */
        const val MARGIN_MINUTES = 15

        private val INPUT_TYPES = setOf(ApiSettings.TEXT, ApiSettings.NUMBER, ApiSettings.BOOLEAN)
        private val VALUE_TYPES = setOf(ApiSettings.TEXT, ApiSettings.NUMBER, ApiSettings.BOOLEAN, ApiSettings.FILE)
        private val SECRET_FORM = Regex("[A-Z_][A-Z0-9_]*")

        /** The spec a saved recipe API holds. */
        fun of(config: JSONObject): RecipeSpec = parse(config.getString(ApiSettings.RECIPE_SPEC))

        /**
         * [text], a `recipe.json`, read and checked: a failure names what is wrong, for the user to
         * correct the recipe rather than the API.
         */
        fun parse(text: String): RecipeSpec {
            val json = try {
                JSONObject(text)
            } catch (e: org.json.JSONException) {
                throw ApiCall.Failure("api_error_recipe_spec", e.message ?: "-")
            }
            val inputs = named(json, INPUTS, INPUT_TYPES)
            val values = named(json, VALUES, VALUE_TYPES)
            val timeout = json.opt(TIMEOUT) as? Int
            if (timeout == null || timeout !in 1..MAX_MINUTES) throw ApiCall.Failure("api_error_recipe_spec", "$TIMEOUT: 1..$MAX_MINUTES")
            val secrets = json.optJSONArray(SECRETS)?.let { list -> (0 until list.length()).map { list.optString(it) } }.orEmpty()
            secrets.firstOrNull { !SECRET_FORM.matches(it) }?.let { throw ApiCall.Failure("api_error_recipe_spec", "$SECRETS: $it") }
            return RecipeSpec(inputs, values, timeout, secrets)
        }

        /** The list [key] of [json], each element reduced to its `name` and `type`, both checked. */
        private fun named(json: JSONObject, key: String, types: Set<String>): JSONArray {
            val list = json.optJSONArray(key) ?: JSONArray()
            val out = JSONArray()
            val seen = mutableSetOf<String>()
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: throw ApiCall.Failure("api_error_recipe_spec", "$key[$i]")
                val name = item.optString(ApiSettings.NAME)
                val type = item.optString(ApiSettings.TYPE)
                if (!ApiSettings.NAME_FORM.matches(name) || !seen.add(name)) throw ApiCall.Failure("api_error_recipe_spec", "$key: $name")
                if (type !in types) throw ApiCall.Failure("api_error_recipe_spec", "$key: $name, $type")
                out.put(JSONObject().put(ApiSettings.NAME, name).put(ApiSettings.TYPE, type))
            }
            return out
        }
    }
}
