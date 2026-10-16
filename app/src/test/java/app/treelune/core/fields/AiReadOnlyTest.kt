package app.treelune.core.fields

import app.treelune.core.ai.data.CommunicationModules
import app.treelune.core.ai.prompts.SchemaModelView
import app.treelune.core.ai.prompts.SchemaNotation
import app.treelune.core.coordinator.Source
import app.treelune.core.fields.migration.FieldChange
import app.treelune.core.fields.migration.FieldConfigComparator
import app.treelune.core.fields.settings.FieldTypeSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Covers a field the AI only reads (FieldDefinition.aiReadOnly): who is refused it, which writes
 * hold it, which config changes unmark it, and that the setting reaches the forms, the stored
 * definition and what the AI reads of an entry.
 */
class AiReadOnlyTest {

    private val text: (String) -> String = { it }
    private val mapper = ObjectMapper()

    private fun field(name: String, aiReadOnly: Boolean = false, type: FieldType = FieldType.TEXT) =
        FieldDefinition(name, "label of $name", null, type, false, null, aiReadOnly = aiReadOnly)

    private val mood = field("mood", aiReadOnly = true)
    private val notes = field("notes")

    /** The AI in a chat or a routine, and an AI come through the MCP server; never the rest of the app. */
    @Test
    fun onlyTheAiIsRefused() {
        assertTrue(AiReadOnly.refuses(Source.AI))
        assertTrue(AiReadOnly.refuses(Source.EXTERNAL))
        assertFalse(AiReadOnly.refuses(Source.USER))
        assertFalse(AiReadOnly.refuses(Source.SYSTEM))
        assertFalse(AiReadOnly.refuses(Source.SCHEDULER))
    }

    /** A value sent, or cleared by a null, is a write of the field; a write without it is not. */
    @Test
    fun aWriteHoldsTheFieldsItSends() {
        val fields = listOf(mood, notes)

        assertEquals(listOf(mood), AiReadOnly.held(JSONObject("""{ "mood": "calm", "notes": "..." }"""), fields))
        assertEquals(listOf(mood), AiReadOnly.held(JSONObject("""{ "mood": null }"""), fields))
        assertEquals(emptyList<FieldDefinition>(), AiReadOnly.held(JSONObject("""{ "notes": "..." }"""), fields))
        assertEquals(emptyList<FieldDefinition>(), AiReadOnly.held(null, fields))
    }

    /** Unmarking a field kept is refused to the AI; marking one, or removing it, is not unmarking. */
    @Test
    fun unmarkingIsTellingApartFromMarkingAndRemoving() {
        assertEquals(listOf(mood), AiReadOnly.unmarked(listOf(mood, notes), listOf(field("mood"), notes)))
        assertEquals(emptyList<FieldDefinition>(), AiReadOnly.unmarked(listOf(notes), listOf(field("notes", aiReadOnly = true))))
        assertEquals(emptyList<FieldDefinition>(), AiReadOnly.unmarked(listOf(mood, notes), listOf(notes)))
        assertEquals(emptyList<FieldDefinition>(), AiReadOnly.unmarked(listOf(mood), listOf(mood)))
    }

    /**
     * A config written whole by the AI keeps the mark of a field it sent without the key, as it was;
     * a mark sent false is not put back, so that unmarking it is refused.
     */
    @Test
    fun aMarkLeftOutIsKept() {
        val before = AiReadOnly.objects(org.json.JSONArray(listOf(mood.toJson(), notes.toJson())))
        val after = AiReadOnly.objects(org.json.JSONArray("""[
            { "name": "mood", "display_name": "Feeling", "type": "TEXT" },
            { "name": "notes", "display_name": "Notes", "type": "TEXT" }]"""))
        AiReadOnly.keepMarks(after, AiReadOnly.markedIds(before))
        assertTrue(after[0].getBoolean(AI_READ_ONLY))
        assertFalse(after[1].has(AI_READ_ONLY))

        val sentFalse = AiReadOnly.objects(org.json.JSONArray("""[{ "name": "mood", "display_name": "Mood", "type": "TEXT", "ai_read_only": false }]"""))
        AiReadOnly.keepMarks(sentFalse, AiReadOnly.markedIds(before))
        assertFalse(sentFalse[0].getBoolean(AI_READ_ONLY))
        assertEquals(listOf(mood), AiReadOnly.unmarked(listOf(mood), sentFalse.map { it.toFieldDefinition() }))
    }

    /** The mark is stored with the definition, and a definition stored before it reads unmarked. */
    @Test
    fun theMarkIsStored() {
        assertTrue(mood.toJson().toFieldDefinition().aiReadOnly)
        assertFalse(notes.toJson().has(AI_READ_ONLY))
        assertFalse(JSONObject("""{ "name": "mood", "display_name": "Mood", "type": "TEXT" }""").toFieldDefinition().aiReadOnly)
    }

    /** Marking or unmarking changes who writes the field, never its values: nothing to migrate. */
    @Test
    fun markingIsCosmetic() {
        val changes = FieldConfigComparator.compare(listOf(notes), listOf(field("notes", aiReadOnly = true)))

        assertEquals(listOf<FieldChange>(FieldChange.CosmeticChange("notes")), changes)
    }

    /** The box is in a user's field's form and accepted by its schema; not in a question the AI asks. */
    @Test
    fun theFormsOfferTheBox() {
        fun names(nodes: List<SettingNode>) = nodes.mapNotNull { (it as? SettingNode.Field)?.definition?.name }
        assertTrue(AI_READ_ONLY in names(FieldTypeSettings.definitionNodes(text)))
        val definition = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(SettingsSchemaGenerator.generate(FieldTypeSettings.definitionNodes(text), text).toString())
        assertTrue(definition.validate(mapper.readTree("""{ "display_name": "Mood", "type": "TEXT", "ai_read_only": true }""")).isEmpty())

        val question = (CommunicationModules.declarationNodes(text).single() as SettingNode.ListOf).item as SettingNode.Item.Of
        assertFalse(AI_READ_ONLY in names(question.nodes))
    }

    /** A value declared apart, a goal's entered criterion, offers the box once its type is chosen. */
    @Test
    fun aValueDeclaredApartOffersTheBoxPerType() {
        val variant = FieldTypeSettings.valueNodes(text, listOf(FieldType.BOOLEAN, FieldType.DURATION)).single() as SettingNode.Variant
        variant.cases.forEach { (type, nodes) ->
            assertTrue(type, nodes.any { (it as? SettingNode.Field)?.definition?.name == AI_READ_ONLY })
        }
    }

    /** What the AI reads of an entry marks the field, and an entry holding it is still a valid entry. */
    @Test
    fun theAiReadsTheMark() {
        val schema = EntrySchemaGenerator.generate(EntryFields(), listOf(mood, notes), text)
        val extra = JSONObject(schema).getJSONObject("properties").getJSONObject("extra").getJSONObject("properties")
        assertTrue(extra.getJSONObject("mood").optBoolean(AI_READ_ONLY))
        assertFalse(extra.getJSONObject("notes").has(AI_READ_ONLY))

        val notation = SchemaNotation.render(SchemaModelView.forModel(JSONObject(schema), ZoneId.of("Europe/Paris")))
        val moodLine = notation.lines().single { "mood" in it && "label of mood" in it }
        assertTrue(moodLine, "read only: never sent" in moodLine)
        assertFalse("read only" in notation.lines().single { "label of notes" in it })

        val validator = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schema)
        assertTrue(validator.validate(mapper.readTree("""{ "tool_instance_id": "t", "tooltype": "x", "name": "Day", "extra": { "mood": "calm" } }""")).isEmpty())
    }
}
