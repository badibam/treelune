package app.treelune.core.fields.migration

import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.OptionOrigins
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.strings.Strings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An option of a choice renamed by its editor keeps what chose it: the entries take its new
 * value, nothing counts as removed (docs/DATA.md, « Config change »). Without the rename, the
 * same change removes the answers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OptionRenamesTest {

    private fun mood(multiple: Boolean = false, vararg options: String) = FieldDefinition("mood", "Mood", null, FieldType.CHOICE, false,
        mapOf("options" to ChoiceSettings.storedOptions(options.toList())) + (if (multiple) mapOf("multiple" to true) else emptyMap()))
    private fun fields(field: FieldDefinition) = EntryMigration.Fields(emptyList(), listOf(field))
    private fun entry(id: String, extra: String) = ToolDataEntity(id, "tool", "journal", 0L, id, "{}", 0L, 0L, extra, null)

    private val before = fields(mood(false, "calm", "happy", "tired"))
    private val after = fields(mood(false, "calm", "happy", "sleepy"))
    private val renames = OptionRenames(mapOf("extra.mood" to mapOf("tired" to "sleepy")))

    @Test
    fun aRenamedOption_keepsItsAnswers_underItsNewValue() {
        val plan = EntryMigration.plan(before, after, listOf(entry("a", """{"mood":"tired"}"""), entry("b", """{"mood":"calm"}""")), renames = renames)

        assertEquals(0, plan.removedValues)
        assertFalse(plan.touchesPast)
        assertEquals("sleepy", JSONObject(plan.updated.single().extra!!).getString("mood"))
    }

    @Test
    fun withoutTheRename_theSameChangeRemovesTheAnswers() {
        val plan = EntryMigration.plan(before, after, listOf(entry("a", """{"mood":"tired"}""")))
        assertEquals(1, plan.removedValues)
    }

    @Test
    fun aListOfAnswers_takesTheNewValue() {
        val plan = EntryMigration.plan(fields(mood(true, "calm", "tired")), fields(mood(true, "calm", "sleepy")),
            listOf(entry("a", """{"mood":["calm","tired"]}""")), renames = renames)
        assertEquals(listOf("calm", "sleepy"), JSONObject(plan.updated.single().extra!!).getJSONArray("mood").let { a -> (0 until a.length()).map { a.getString(it) } })
    }

    @Test
    fun aRename_isRefused_unlessItGoesFromAnOptionGoneToAnOptionNew() {
        val s = Strings.`for`(context = RuntimeEnvironment.getApplication())
        assertNull(renames.refusal(before, after, s))
        // A field that is no choice, an option still there, an option that did not appear
        assertNotNull(OptionRenames(mapOf("extra.other" to mapOf("tired" to "sleepy"))).refusal(before, after, s))
        assertNotNull(OptionRenames(mapOf("extra.mood" to mapOf("calm" to "sleepy"))).refusal(before, after, s))
        assertNotNull(OptionRenames(mapOf("extra.mood" to mapOf("tired" to "happy"))).refusal(before, after, s))
    }

    @Test
    fun theScreen_tellsARenameFromARemoval_byTheValueEachOptionWasReadWith() {
        val config = JSONObject().put("extra_fields", JSONArray().put(JSONObject().put("name", "mood").put("display_name", "Mood").put("type", "CHOICE")
            .put("config", JSONObject().put("options", JSONArray(listOf("calm", "tired").map { JSONObject().put("value", it) })))))
        val tagged = OptionOrigins.tagged(config)
        val options = tagged.getJSONArray("extra_fields").getJSONObject(0).getJSONObject("config").getJSONArray("options")
        // The value of « tired » edited in place, an option added after it
        options.getJSONObject(1).put("value", "sleepy")
        options.put(JSONObject().put("value", "happy"))

        fun fieldsOf(json: JSONObject) = EntryMigration.Fields(emptyList(),
            json.getJSONArray("extra_fields").toFieldDefinitions())
        val found = OptionOrigins.renames(fieldsOf(tagged), fieldsOf(config))
        assertEquals(mapOf("extra.mood" to mapOf("tired" to "sleepy")), found.byPath)
        // Sent without its marks
        assertFalse(OptionOrigins.untagged(tagged).toString().contains("former_value"))
        assertTrue(tagged.toString().contains("former_value"))
    }
}
