package app.treelune.core.ai.prompts

import app.treelune.core.ai.data.CommunicationModules
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.EntrySchemaGenerator
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.StateField
import app.treelune.core.fields.settings.FieldTypeSettings
import app.treelune.core.fields.settings.ScheduleSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Covers what the model reads of a schema (SchemaNotation): every keyword the app's generators
 * write has a notation, nothing a schema says is lost, and a variant's shared settings are
 * written once.
 */
class SchemaNotationTest {

    private val text: (String) -> String = { it }

    private fun modelView(schema: JSONObject) = SchemaModelView.forModel(schema, ZoneId.of("Europe/Paris"))

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) =
        FieldDefinition(name, "label of $name", "description of $name", type, false, config)

    /** A field of every type, with the settings that give its schema its keywords. */
    private val everyType = listOf(
        field("a_text", FieldType.TEXT, mapOf("length" to "SHORT")),
        field("a_number", FieldType.NUMERIC, mapOf("min" to 0, "max" to 10, "decimals" to 1, "unit" to "kg")),
        field("a_scale", FieldType.SCALE, mapOf("min" to 1, "max" to 5, "step" to 1, "min_label" to "bad", "max_label" to "good")),
        field("a_choice", FieldType.CHOICE, mapOf("options" to listOf(mapOf("value" to "a"), mapOf("value" to "b")), "open" to true)),
        field("some_choices", FieldType.CHOICE, mapOf("options" to listOf(mapOf("value" to "a"), mapOf("value" to "b")), "multiple" to true)),
        field("a_boolean", FieldType.BOOLEAN, mapOf("true_label" to "yes")),
        field("a_range", FieldType.RANGE, mapOf("min" to 0, "max" to 10, "decimals" to 0)),
        field("a_date", FieldType.DATE),
        field("a_time", FieldType.TIME),
        field("a_datetime", FieldType.DATETIME),
        field("a_duration", FieldType.DURATION)
    )

    /** The schemas the app generates, in the form the model reads. */
    private val generated: Map<String, JSONObject> by lazy {
        val entries = EntrySchemaGenerator.generate(
            EntryFields(
                name = CoreFieldUsage.REQUIRED,
                timestamp = CoreFieldUsage.OPTIONAL,
                data = everyType.map { FixedField(it, required = it.type == FieldType.TEXT) } +
                    FixedField(field("sent_copy", FieldType.TEXT), systemWritten = true),
                state = listOf(StateField(field("read", FieldType.BOOLEAN), filterable = true))
            ),
            everyType,
            text
        )
        val settings = listOf(
            SettingNode.Field(field("api_key", FieldType.TEXT), required = true, secret = true),
            SettingNode.Field(field("window", FieldType.DURATION), default = 3_600_000L),
            SettingNode.ListOf("extra_fields", "Fields", SettingNode.Item.Of(FieldTypeSettings.definitionNodes(text)), fieldDefinitions = true,
                summary = listOf("display_name")),
            SettingNode.Group("schedule", "Schedule", ScheduleSettings.nodes(text))
        ) + FieldType.entries.flatMap { FieldTypeSettings.configNodes(it, text) }.distinctBy { (it as? SettingNode.Field)?.definition?.name ?: it.hashCode().toString() }
        mapOf(
            "entries" to JSONObject(entries),
            "field definition" to SettingsSchemaGenerator.generate(FieldTypeSettings.definitionNodes(text), text),
            "communication module" to SettingsSchemaGenerator.generate(CommunicationModules.declarationNodes(text), text),
            "settings" to SettingsSchemaGenerator.generate(settings, text)
        ).mapValues { modelView(it.value) }
    }

    @Test
    fun everyGeneratedSchemaHasANotation() {
        generated.forEach { (name, schema) ->
            val notation = SchemaNotation.render(schema)
            assertTrue("$name renders nothing", notation.isNotBlank())
        }
    }

    /**
     * Every value a schema names, and every label and description it holds, is in what the model
     * reads, except inside a list of field definitions, which the prompt spells out once.
     */
    @Test
    fun nothingIsLost() {
        generated.forEach { (name, schema) ->
            val notation = SchemaNotation.render(schema)
            val missing = said(schema).filterNot { it in notation }
            assertEquals("$name loses: $missing", emptyList<String>(), missing)
        }
    }

    @Test
    fun theVariantsSharedSettingsAreWrittenOnce() {
        val nodes = listOf(
            SettingNode.Field(field("name", FieldType.TEXT), required = true),
            SettingNode.Variant(
                selector = SettingNode.Field(field("type", FieldType.CHOICE,
                    mapOf("options" to listOf(mapOf("value" to "one"), mapOf("value" to "two"))))),
                cases = mapOf(
                    "one" to listOf(SettingNode.Field(field("only_one", FieldType.BOOLEAN))),
                    "two" to emptyList()
                )
            )
        )
        val notation = SchemaNotation.render(modelView(SettingsSchemaGenerator.generate(nodes, text)))

        assertEquals(1, Regex("description of name").findAll(notation).count())
        assertTrue(notation, notation.contains("type*: one|two"))
        assertTrue(notation, notation.contains("if type = one:"))
        assertTrue(notation, notation.contains("only_one"))
        assertTrue(notation, notation.contains("if type = two: nothing more"))
    }

    /** A variant inside an option, whose branches all hold the outer selector to that option. */
    @Test
    fun aVariantInsideAnOptionIsToldApartByItsOwnSelector() {
        fun choice(name: String, vararg options: String) = SettingNode.Field(field(name, FieldType.CHOICE,
            mapOf("options" to options.map { mapOf("value" to it) })), required = true)
        val nodes = listOf(SettingNode.Variant(
            selector = choice("kind", "step", "block"),
            cases = mapOf(
                "step" to listOf(SettingNode.Variant(
                    selector = choice("end", "timed", "manual"),
                    cases = mapOf("timed" to listOf(SettingNode.Field(field("duration", FieldType.DURATION), required = true)), "manual" to emptyList())
                )),
                "block" to listOf(SettingNode.Field(field("repeat", FieldType.NUMERIC, mapOf("decimals" to 0)), required = true))
            )
        ))
        val notation = SchemaNotation.render(modelView(SettingsSchemaGenerator.generate(nodes, text)))

        assertTrue(notation, notation.contains("kind*: always step"))
        assertTrue(notation, notation.contains("if end = timed:"))
        assertTrue(notation, notation.contains("if end = manual: nothing more"))
        assertTrue(notation, "if kind = step" !in notation)
    }

    @Test
    fun aListOfFieldDefinitionsPointsToThePrompt() {
        val notation = SchemaNotation.render(generated.getValue("settings"))
        assertTrue(notation, notation.contains("extra_fields: list of field definitions (see Fields)"))
        assertTrue("the definition is not spelled out", "display_name" !in notation.substringAfter("extra_fields").substringBefore("schedule"))
    }

    @Test
    fun whatTheModelMustNotWriteIsSaid() {
        val entries = SchemaNotation.render(generated.getValue("entries"))
        assertTrue(entries, Regex("sent_copy: .*written by the app").containsMatchIn(entries))
        val settings = SchemaNotation.render(generated.getValue("settings"))
        assertTrue(settings, Regex("api_key\\*: .*secret").containsMatchIn(settings))
        assertTrue(settings, Regex("window: ISO 8601 duration \\[default \"PT1H\"]").containsMatchIn(settings))
        // A required value's default is not what its absence means: its absence is refused
        assertTrue(settings, Regex("decimals\\*: .*suggested 0").containsMatchIn(settings))
    }

    /** A list's element named "Item" in two places, a group named "Pair" inside it. */
    private fun named(otherLabel: String = "label of inner"): List<SettingNode> {
        fun item(innerLabel: String) = SettingNode.Item.Of(listOf(
            SettingNode.Field(field("inner", FieldType.TEXT).copy(displayName = innerLabel), required = true),
            SettingNode.Group("pair", "Pair label", listOf(SettingNode.Field(field("left", FieldType.BOOLEAN))), description = "what a pair is", definition = "Pair")
        ), description = "what an item is", definition = "Item")
        return listOf(
            SettingNode.ListOf("first", "First", item("label of inner"), summary = listOf("inner"), description = "the first list"),
            SettingNode.ListOf("second", "Second", item(otherLabel), summary = listOf("inner"), description = "the second list")
        )
    }

    @Test
    fun aNamedObjectIsWrittenOnceAtTheEnd() {
        val schema = modelView(SettingsSchemaGenerator.generate(named(), text))
        val notation = SchemaNotation.render(schema)

        assertTrue(notation, notation.contains("first: list of Item (see Item) — First. the first list"))
        assertTrue(notation, notation.contains("second: list of Item (see Item) — Second. the second list"))
        assertEquals(notation, 1, Regex("description of inner").findAll(notation).count())
        // Its definition after the schema, what one is beside its name; one met inside another after it
        assertTrue(notation, notation.contains("\n\nItem: what an item is\ninner*: "))
        assertTrue(notation, notation.contains("pair: Pair (see Pair) — Pair label"))
        assertTrue(notation, notation.indexOf("\n\nPair: what a pair is\nleft:") > notation.indexOf("\n\nItem:"))
        val missing = said(schema).filterNot { it in notation }
        assertEquals("loses: $missing", emptyList<String>(), missing)
    }

    @Test(expected = IllegalStateException::class)
    fun twoObjectsUnderOneNameMustBeAlike() {
        SchemaNotation.render(modelView(SettingsSchemaGenerator.generate(named(otherLabel = "another label"), text)))
    }

    @Test(expected = SchemaNotation.UnknownKeyword::class)
    fun anUnknownKeywordFails() {
        SchemaNotation.render(JSONObject("""{"type":"object","properties":{"x":{"type":"string","dependentRequired":{}}}}"""))
    }

    /** The property names, labels and descriptions of [schema], outside lists of field definitions. */
    private fun said(schema: Any?): Set<String> = when (schema) {
        is JSONObject -> if (schema.optBoolean(SettingsSchemaGenerator.FIELD_DEFINITIONS)) emptySet() else buildSet {
            // A property may be named "description": the names are read from "properties" only
            schema.optJSONObject("properties")?.let { properties ->
                properties.keys().forEach { add(it); addAll(said(properties.get(it))) }
            }
            listOf("title", "description").forEach { key -> (schema.opt(key) as? String)?.takeIf { it.isNotBlank() }?.let { add(it) } }
            listOf("items", "oneOf").forEach { key -> schema.opt(key)?.let { addAll(said(it)) } }
        }
        is JSONArray -> (0 until schema.length()).flatMap { said(schema.get(it)) }.toSet()
        else -> emptySet()
    }
}
