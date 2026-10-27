package app.treelune.core.fields

import app.treelune.core.ai.prompts.ModelValues
import app.treelune.core.conditions.ConditionJudge
import app.treelune.core.fields.migration.FieldChange
import app.treelune.core.fields.migration.FieldConfigComparator
import app.treelune.core.fields.migration.FieldDataMigrator
import app.treelune.core.fields.migration.MigrationPolicy
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.selection.TimeResolver
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.time.ZoneId

/**
 * A multiple REFERENCE field: a list of references, each once, taken as a multiple choice is
 * wherever that one is taken -- its schema, its change of shape, its filter, the model's reading
 * and writing of it.
 */
class MultipleReferenceTest {

    private val anna = Reference(ReferenceKind.ENTRY, "anna")
    private val ben = Reference(ReferenceKind.ENTRY, "ben")
    private val annaValue = mapOf("kind" to "ENTRY", "id" to "anna")
    private val benValue = mapOf("kind" to "ENTRY", "id" to "ben")

    private fun config(multiple: Boolean, tools: List<String> = emptyList()): Map<String, Any> = mapOf(
        "target" to mapOf("kinds" to listOf("ENTRY"), "tool_instances" to tools.map { mapOf("kind" to "TOOL_INSTANCE", "id" to it) }),
        "multiple" to multiple
    )

    private fun field(multiple: Boolean, tools: List<String> = emptyList()) =
        FieldDefinition(name = "people", displayName = "People", description = null, type = FieldType.REFERENCE, alwaysVisible = false, config = config(multiple, tools))

    @Test
    fun `a stored value reads as its references, one or a list`() {
        assertEquals(listOf(anna), ReferenceTarget.referencesOf(annaValue))
        assertEquals(listOf(anna, ben), ReferenceTarget.referencesOf(listOf(annaValue, benValue)))
        assertEquals(listOf(anna, ben), ReferenceTarget.referencesOf(JSONArray("""[{"kind":"ENTRY","id":"anna"},{"kind":"ENTRY","id":"ben"}]""")))
        assertEquals(emptyList<Reference>(), ReferenceTarget.referencesOf(null))
    }

    @Test
    fun `the value schema is a list of distinct references, each marked as one`() {
        val schema = FieldValueSchema.of(field(multiple = true))
        assertEquals("array", schema.getString("type"))
        assertTrue(schema.getBoolean("uniqueItems"))
        assertEquals(FieldValueSchema.REFERENCE, schema.getJSONObject("items").getString("format"))
        assertEquals("object", FieldValueSchema.of(field(multiple = false)).getString("type"))
    }

    // The change of shape, as a choice's

    private fun migrate(values: Map<String, Any?>, old: FieldDefinition, new: FieldDefinition, instances: Map<String, String> = emptyMap()): Map<String, Any?> {
        val changes = FieldConfigComparator.compare(listOf(old), listOf(new))
        return FieldDataMigrator.applyMigrationStrategies(values, changes, MigrationPolicy.getStrategies(changes)) { instances[it] }
    }

    @Test
    fun `turning multiple is a change of shape, not a narrowed target`() {
        assertEquals(listOf(FieldChange.ReferenceShapeChanged("people", true)), FieldConfigComparator.compare(listOf(field(false)), listOf(field(true))))
        val both = FieldConfigComparator.compare(listOf(field(false)), listOf(field(true, listOf("contacts"))))
        assertTrue(both.any { it is FieldChange.ReferenceShapeChanged } && both.any { it is FieldChange.ReferenceTargetNarrowed })
    }

    @Test
    fun `one reference becomes a list of one`() {
        assertEquals(listOf(annaValue), migrate(mapOf("people" to annaValue), field(false), field(true))["people"])
    }

    @Test
    fun `back to single, a list of one becomes its reference and a longer one goes`() {
        assertEquals(annaValue, migrate(mapOf("people" to listOf(annaValue)), field(true), field(false))["people"])
        assertFalse(migrate(mapOf("people" to listOf(annaValue, benValue)), field(true), field(false)).containsKey("people"))
    }

    @Test
    fun `a narrowed target removes a list holding one reference it no longer takes`() {
        val instances = mapOf("anna" to "contacts", "ben" to "colleagues")
        assertFalse(migrate(mapOf("people" to listOf(annaValue, benValue)), field(true), field(true, listOf("contacts")), instances).containsKey("people"))
        assertEquals(listOf(annaValue), migrate(mapOf("people" to listOf(annaValue)), field(true), field(true, listOf("contacts")), instances)["people"])
    }

    // The filter: « contains one of », by the ids the list holds

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().execute(
            "CREATE TABLE tool_data (id TEXT, tool_instance_id TEXT, tooltype TEXT, timestamp INTEGER, name TEXT, " +
                "data TEXT, created_at INTEGER, updated_at INTEGER, extra TEXT, state TEXT)"
        )
        listOf(
            Triple("j1", 1L, """{"people": [{"kind": "ENTRY", "id": "anna"}, {"kind": "ENTRY", "id": "ben"}]}"""),
            Triple("j2", 2L, """{"people": [{"kind": "ENTRY", "id": "ben"}]}"""),
            Triple("j3", 3L, """{"people": []}"""),
            Triple("j4", 4L, null)
        ).forEach { (id, timestamp, extra) ->
            db.prepareStatement("INSERT INTO tool_data VALUES (?, 'journal', 'journal', ?, NULL, '{}', 0, 0, ?, NULL)").apply {
                setString(1, id); setLong(2, timestamp); setString(3, extra); executeUpdate()
            }
        }
    }

    @After
    fun tearDown() = db.close()

    private val fields = mapOf("extra.people" to field(multiple = true))

    private fun ids(json: String): List<String> {
        val filters = (EntryFilters.parse(JSONArray(json), fields) { it } as EntryFilters.Parsed.Ready).filters
        val query = EntryFilters.select("journal", filters, fields, null, 0)
        val statement = db.prepareStatement(query.clause)
        query.args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
        val rows = statement.executeQuery()
        return buildList { while (rows.next()) add(rows.getString(1)) }.sorted()
    }

    @Test
    fun `a multiple reference filters by holding one of the references, an empty list being no answer`() {
        assertEquals(listOf("j1"), ids("""[{"left": {"field": "extra.people"}, "op": "in", "right": {"constant": [{"kind": "ENTRY", "id": "anna"}]}}]"""))
        assertEquals(listOf("j1", "j2"), ids("""[{"left": {"field": "extra.people"}, "op": "in", "right": {"constant": [{"kind": "ENTRY", "id": "anna"}, {"kind": "ENTRY", "id": "ben"}]}}]"""))
        assertEquals(listOf("j3", "j4"), ids("""[{"left": {"field": "extra.people"}, "op": "absent"}]"""))
    }

    @Test
    fun `a multiple reference is never equal to one thing`() {
        val equal = """[{"left": {"field": "extra.people"}, "op": "=", "right": {"constant": {"kind": "ENTRY", "id": "anna"}}}]"""
        assertTrue(EntryFilters.parse(JSONArray(equal), fields) { it } is EntryFilters.Parsed.Refused)
    }

    @Test
    fun `a condition holds when the list holds one of the references`() {
        val resolver = TimeResolver(0L, ZoneId.of("Europe/Paris"), dayStartHour = 4, weekStartDay = "MONDAY")
        fun holds(left: Any?, right: List<Any?>) = ConditionJudge.holds(field(true), FilterOperator.IN, left, listOf(right), resolver) { it }
        assertEquals(true, holds(listOf(annaValue, benValue), listOf(JSONObject(benValue))))
        assertEquals(false, holds(listOf(annaValue), listOf(benValue)))
    }

    // The model: each reference of the list read with its name, written back without it

    private val entrySchema = JSONObject().put("type", "object").put("properties", JSONObject()
        .put("extra", JSONObject().put("type", "object").put("properties", JSONObject().put("people", FieldValueSchema.of(field(multiple = true))))))

    @Test
    fun `the model reads each reference of the list with its name, and sends them back without`() {
        val entry = mapOf("extra" to mapOf("people" to listOf(annaValue, benValue)))
        assertEquals(listOf(anna, ben), ModelValues.references(entry, entrySchema))
        val named = ModelValues.withReferenceNames(entry, entrySchema, mapOf(anna to "Anna", ben to "Ben")) as Map<*, *>
        val people = (named["extra"] as Map<*, *>)["people"] as List<*>
        assertEquals(listOf("Anna", "Ben"), people.map { (it as Map<*, *>)["name"] })
        val stored = ModelValues.fromModel(named, entrySchema, ZoneId.of("Europe/Paris")) as Map<*, *>
        assertEquals(listOf(annaValue, benValue), (stored["extra"] as Map<*, *>)["people"])
    }
}
