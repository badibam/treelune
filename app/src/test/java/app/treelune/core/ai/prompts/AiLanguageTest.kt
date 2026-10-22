package app.treelune.core.ai.prompts

import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the app writes for the AI is in English whatever the app's language, as the prompt; a
 * schema's labels, which the user sees on screen, come in the app's language beside.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr")
class AiLanguageTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `a schema read in a French app is in English, its labels with the screen's beside`() = runBlocking {
        val result = Coordinator(context).processUserAction("schemas.get", mapOf("tooltype" to "tracking"))
        assertTrue(result.error, result.isSuccess)
        val items = JSONObject(result.data!!["content"] as String).getJSONArray("oneOf").getJSONObject(0)
            .getJSONObject("properties").getJSONObject("items")
        assertEquals("Shortcuts (Raccourcis)", items.getString("title"))
        assertEquals("Shortcuts, each noting one thing in one touch", items.getString("description"))
    }

    @Test
    fun `a label alike in both languages is said once`() {
        val merged = AiLanguage.withScreenLabels(
            JSONObject("""{"title": "Notes", "properties": {"a": {"title": "Name", "description": "x"}}}"""),
            JSONObject("""{"title": "Notes", "properties": {"a": {"title": "Nom", "description": "y"}}}""")
        )
        assertEquals("Notes", merged.getString("title"))
        assertEquals("Name (Nom)", merged.getJSONObject("properties").getJSONObject("a").getString("title"))
        assertEquals("x", merged.getJSONObject("properties").getJSONObject("a").getString("description"))
    }
}
