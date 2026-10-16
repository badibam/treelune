package app.treelune.core.fields.migration

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Covers a change of meaning taken as a new field: the recorded values stay under the field as it was. */
class NewFieldInsteadTest {

    private fun scale(name: String, display: String, max: Int) =
        JSONObject().put("name", name).put("display_name", display).put("type", "SCALE")
            .put("config", JSONObject().put("min", 0).put("max", max))

    @Test
    fun theEditedFieldTakesANewKeyAndTheFormerOneStays() {
        val before = JSONArray().put(scale("energy", "Energy", 10))
        val after = JSONArray().put(scale("energy", "Energy", 5))

        val result = NewFieldInstead.apply(before, after, listOf("energy")) { "$it (before)" }

        assertEquals(2, result.length())
        val edited = result.getJSONObject(0)
        val kept = result.getJSONObject(1)
        // The edited field without a key: the service gives it one no recorded value has
        assertFalse(edited.has("name"))
        assertEquals(5, edited.getJSONObject("config").getInt("max"))
        // The former one keeps its key and its settings, renamed
        assertEquals("energy", kept.getString("name"))
        assertEquals(10, kept.getJSONObject("config").getInt("max"))
        assertEquals("Energy (before)", kept.getString("display_name"))
    }

    /** A field not named is left as it is. */
    @Test
    fun otherFieldsAreLeft() {
        val before = JSONArray().put(scale("energy", "Energy", 10)).put(scale("mood", "Mood", 10))
        val after = JSONArray().put(scale("energy", "Energy", 5)).put(scale("mood", "Mood", 5))

        val result = NewFieldInstead.apply(before, after, listOf("energy")) { "$it (before)" }

        assertEquals(listOf("", "energy", "mood"), (0 until result.length()).map { result.getJSONObject(it).optString("name") })
    }
}
