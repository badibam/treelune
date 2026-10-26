package app.treelune.core.fields

import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * The order of tool_data.get (EntrySort), run in a real SQLite against a tool_data table of the
 * app's shape: keys one after the other, the entries without a value last in either direction, a
 * text without its case, a choice in its options' order, the newest first where the keys leave
 * entries equal; and what a key that cannot be run is refused for.
 */
class EntrySortTest {

    private val text: (String) -> String = { it }
    private lateinit var db: Connection

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) =
        FieldDefinition(name, name, null, type, false, config)

    private fun options(vararg values: String) = values.map { mapOf("value" to it) }

    private val fields = mapOf(
        "timestamp" to field("timestamp", FieldType.DATETIME),
        "name" to field("name", FieldType.TEXT),
        "data.programme" to field("programme", FieldType.CHOICE, mapOf("options" to options("breathing", "body_scan"))),
        "data.number" to field("number", FieldType.NUMERIC),
        "data.tags" to field("tags", FieldType.CHOICE, mapOf("options" to options("short", "long"), "multiple" to true)),
        "data.audio" to field("audio", FieldType.FILE)
    )

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().execute(
            "CREATE TABLE tool_data (id TEXT, tool_instance_id TEXT, tooltype TEXT, timestamp INTEGER, name TEXT, " +
                "data TEXT, created_at INTEGER, updated_at INTEGER, extra TEXT, state TEXT)"
        )
        entry("scan2", 1000, "body scan 2", """{"programme": "body_scan", "number": 2}""")
        entry("breath1", 2000, "Breathing 1", """{"programme": "breathing", "number": 1}""")
        entry("scan1", 3000, "Body scan 1", """{"programme": "body_scan", "number": 1}""")
        entry("breath2", 4000, "Breathing 2", """{"programme": "breathing", "number": 2}""")
        entry("loose", 5000, null, """{}""")
    }

    @After
    fun tearDown() = db.close()

    private fun entry(id: String, timestamp: Long, name: String?, data: String) {
        db.prepareStatement("INSERT INTO tool_data VALUES (?, 't1', 'structured', ?, ?, ?, 0, 0, NULL, NULL)").apply {
            setString(1, id); setLong(2, timestamp); setString(3, name); setString(4, data)
            executeUpdate()
        }
    }

    private fun ids(sort: String): List<String> {
        val keys = (EntrySort.parse(JSONArray(sort), fields, text) as EntrySort.Parsed.Ready).keys
        val query = EntryFilters.select("t1", emptyList(), fields, null, 0, keys)
        val statement = db.prepareStatement(query.clause)
        query.args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
        val rows = statement.executeQuery()
        return buildList { while (rows.next()) add(rows.getString(1)) }
    }

    private fun refused(sort: String): String =
        (EntrySort.parse(JSONArray(sort), fields, text) as EntrySort.Parsed.Refused).error

    @Test
    fun `without a key the newest come first`() {
        assertEquals(listOf("loose", "breath2", "scan1", "breath1", "scan2"), ids("[]"))
    }

    @Test
    fun `a text is ordered without its case, the entries without one last in either direction`() {
        assertEquals(listOf("scan1", "scan2", "breath1", "breath2", "loose"), ids("""[{"field": "name", "direction": "asc"}]"""))
        assertEquals(listOf("breath2", "breath1", "scan2", "scan1", "loose"), ids("""[{"field": "name", "direction": "desc"}]"""))
    }

    @Test
    fun `a second key orders what the first leaves equal, a choice in its options' order`() {
        assertEquals(listOf("breath1", "breath2", "scan1", "scan2", "loose"),
            ids("""[{"field": "data.programme", "direction": "asc"}, {"field": "data.number", "direction": "asc"}]"""))
        assertEquals(listOf("scan1", "scan2", "breath1", "breath2", "loose"),
            ids("""[{"field": "data.programme", "direction": "desc"}, {"field": "data.number", "direction": "asc"}]"""))
    }

    @Test
    fun `the newest first where every key leaves entries equal`() {
        assertEquals(listOf("breath2", "scan2", "scan1", "breath1", "loose"), ids("""[{"field": "data.number", "direction": "desc"}]"""))
    }

    @Test
    fun `a field without an order, unknown, or named twice, and a direction unknown are refused`() {
        assertTrue(refused("""[{"field": "data.tags", "direction": "asc"}]""").startsWith("service_error_sort_field"))
        assertTrue(refused("""[{"field": "data.audio", "direction": "asc"}]""").startsWith("service_error_sort_field"))
        assertTrue(refused("""[{"field": "data.mood", "direction": "asc"}]""").startsWith("service_error_sort_field"))
        assertTrue(refused("""[{"field": "name", "direction": "up"}]""").startsWith("service_error_sort_direction"))
        assertTrue(refused("""[{"field": "name", "direction": "asc"}, {"field": "name", "direction": "desc"}]""").startsWith("service_error_sort_twice"))
        assertTrue(refused("""["name"]""").startsWith("service_error_sort_unreadable"))
    }
}
