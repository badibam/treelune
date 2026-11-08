package app.treelune.core.apis

import app.treelune.core.versioning.ApiKindAtV114
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A recipe of the user's GitHub repository as an API (docs/design/github-worker.md): what its
 * `recipe.json` declares becomes the API's inputs, values and wait, a declaration the workflow
 * could not run is refused when the API is saved, and every API before v114 is a web service.
 */
class RecipeApiTest {

    private val hypnogen = """
        {"name": "hypnogen", "command": "./run recipe", "install": ["claude", "ffmpeg"], "timeout_minutes": 45,
         "secrets": ["CLAUDE_CODE_OAUTH_TOKEN", "INWORLD_KEY"],
         "inputs": [{"name": "context", "type": "text"}],
         "values": [{"name": "audio", "type": "file"}, {"name": "fiche", "type": "text"}]}
    """.trimIndent()

    private fun refused(text: String) {
        try {
            RecipeSpec.parse(text)
            fail("accepted: $text")
        } catch (e: ApiCall.Failure) {
            assertEquals("api_error_recipe_spec", e.key)
        }
    }

    private fun recipeConfig(spec: String = hypnogen) = JSONObject()
        .put(ApiSettings.KIND, ApiSettings.GITHUB_RECIPE)
        .put(ApiSettings.RECIPE, "hypnogen")
        .put(ApiSettings.RECIPE_SPEC, RecipeSpec.parse(spec).toJson().toString())

    @Test
    fun aRecipesInputsAndValues_areTheApis_andItAlwaysAnswersLater() {
        val config = recipeConfig()
        assertTrue(ApiSettings.isRecipe(config))
        assertEquals(listOf("context"), ApiSettings.inputs(config).map { it.getString(ApiSettings.NAME) })
        assertEquals(listOf("audio" to "file", "fiche" to "text"), ApiSettings.values(config).map { it.getString(ApiSettings.NAME) to it.getString(ApiSettings.TYPE) })
        assertTrue(ApiSettings.deferred(config))
        assertEquals(listOf("context"), ApiSettings.inputFields(config).map { it.name })
    }

    @Test
    fun theSpecKept_holdsWhatTheAppReads_notTheCommand() {
        val kept = RecipeSpec.parse(hypnogen).toJson()
        assertEquals(setOf("inputs", "values", "timeout_minutes", "secrets"), kept.keys().asSequence().toSet())
        assertEquals(45, kept.getInt("timeout_minutes"))
        assertEquals(listOf("CLAUDE_CODE_OAUTH_TOKEN", "INWORLD_KEY"), RecipeSpec.parse(hypnogen).secrets)
    }

    @Test
    fun whatTheWorkflowCouldNotRun_isRefused() {
        refused("not json")
        refused("""{"timeout_minutes": 0}""")
        refused("""{"timeout_minutes": ${RecipeSpec.MAX_MINUTES + 1}}""")
        refused("""{"timeout_minutes": 10, "inputs": [{"name": "Context", "type": "text"}]}""")
        refused("""{"timeout_minutes": 10, "inputs": [{"name": "words", "type": "list"}]}""")
        refused("""{"timeout_minutes": 10, "values": [{"name": "fiche", "type": "object"}]}""")
        refused("""{"timeout_minutes": 10, "values": [{"name": "a", "type": "text"}, {"name": "a", "type": "file"}]}""")
        refused("""{"timeout_minutes": 10, "secrets": ["inworld key"]}""")
    }

    @Test
    fun everyApiBeforeV114_isAWebService_andOneThatSaysItsKindStaysAsItIs() {
        val before = JSONObject().put(ApiSettings.URL, "https://example.org").put(ApiSettings.METHOD, ApiSettings.GET)
        assertEquals(ApiSettings.WEB, JSONObject(ApiKindAtV114.config(before.toString())!!).getString(ApiSettings.KIND))
        assertNull(ApiKindAtV114.config(recipeConfig().toString()))
        val data = JSONObject().put("apis", JSONArray().put(JSONObject().put("id", "a").put("config_json", before.toString())))
        ApiKindAtV114.backup(data)
        assertEquals(ApiSettings.WEB, JSONObject(data.getJSONArray("apis").getJSONObject(0).getString("config_json")).getString(ApiSettings.KIND))
        assertFalse(ApiSettings.isRecipe(JSONObject(ApiKindAtV114.config(before.toString())!!)))
    }
}
