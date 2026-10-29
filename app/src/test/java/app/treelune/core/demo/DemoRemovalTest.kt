package app.treelune.core.demo

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * A reinstall of the demo removes all of it — its own rows, and what the user put in a demo zone
 * or tool — and touches nothing else, the demo's identity staying while a zone or a routine of the
 * user's names it, its API while a Task of the user's calls it: the selections of DemoRemoval, run on SQLite in their order over the columns
 * they read, each id they give deleted before the next one is read.
 */
class DemoRemovalTest {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        listOf(
            "CREATE TABLE zones (id TEXT, identity_id TEXT)",
            "CREATE TABLE tool_instances (id TEXT, zone_id TEXT)",
            "CREATE TABLE tool_data (id TEXT, tool_instance_id TEXT)",
            "CREATE TABLE variables (id TEXT, zone_id TEXT)",
            "CREATE TABLE routines (id TEXT, zone_id TEXT, identity_id TEXT)",
            "CREATE TABLE ai_sessions (id TEXT, routine_id TEXT)",
            "CREATE TABLE identities (id TEXT)",
            "CREATE TABLE tasks (id TEXT, zone_id TEXT, steps_json TEXT)",
            "CREATE TABLE apis (id TEXT)",
            "CREATE TABLE condition_judgements (owner_id TEXT)"
        ).forEach { db.createStatement().execute(it) }

        // The user's own, outside the demo
        insert("zones", "zone")
        insert("tool_instances", "tool", "zone")
        insert("tool_data", "entry", "tool")
        insert("variables", "variable", "zone")
        insert("routines", "routine", "zone")
        insert("ai_sessions", "session", "routine")
        insert("ai_sessions", "chat", null)

        // The demo's own
        insert("zones", "demo-zone")
        insert("tool_instances", "demo-tool", "demo-zone")
        insert("tool_data", "demo-entry", "demo-tool")
        insert("variables", "demo-variable", "demo-zone")
        insert("routines", "demo-routine", "demo-zone")
        insert("ai_sessions", "demo-session", "demo-routine")

        // What the user put in the demo
        insert("tool_data", "typed", "demo-tool")
        insert("tool_instances", "added", "demo-zone")
        insert("tool_data", "typed-in-added", "added")
        insert("variables", "added-variable", "demo-zone")
        insert("routines", "added-routine", "demo-zone")
        insert("ai_sessions", "added-session", "added-routine")

        // The demo's identities: one its zone alone names, one a zone of the user's names, one a routine of the user's names
        listOf("identity", "demo-identity", "demo-identity-zone", "demo-identity-routine").forEach { insert("identities", it) }
        db.createStatement().execute("UPDATE zones SET identity_id = 'demo-identity' WHERE id = 'demo-zone'")
        db.createStatement().execute("UPDATE zones SET identity_id = 'demo-identity-zone' WHERE id = 'zone'")
        db.createStatement().execute("UPDATE routines SET identity_id = 'demo-identity-routine' WHERE id = 'routine'")

        // The Tasks and the APIs: the user's, the demo's, one put in the demo; the demo's second API called by a Task of the user's
        listOf("api", "demo-api", "demo-api-kept").forEach { db.createStatement().execute("INSERT INTO apis VALUES ('$it')") }
        listOf(
            Triple("task", "zone", """[{"kind": "call_api", "api_id": "demo-api-kept"}]"""),
            Triple("demo-task", "demo-zone", """[{"kind": "call_api", "api_id": "demo-api"}]"""),
            Triple("added-task", "demo-zone", "[]")
        ).forEach { (id, zone, steps) ->
            db.prepareStatement("INSERT INTO tasks VALUES (?, ?, ?)").apply { setString(1, id); setString(2, zone); setString(3, steps); execute() }
        }
        listOf("task", "demo-task", "added-task", "routine", "added-routine").forEach { db.createStatement().execute("INSERT INTO condition_judgements VALUES ('$it')") }
    }

    @After
    fun tearDown() = db.close()

    private fun insert(table: String, id: String, parent: String? = null) {
        val sql = when (table) {
            "zones" -> "INSERT INTO zones (id) VALUES (?)"
            "identities" -> "INSERT INTO identities VALUES (?)"
            "routines" -> "INSERT INTO routines (id, zone_id) VALUES (?, ?)"
            else -> "INSERT INTO $table VALUES (?, ?)"
        }
        db.prepareStatement(sql).apply {
            setString(1, id)
            if (table != "zones" && table != "identities") setString(2, parent)
            execute()
        }
    }

    private fun ids(table: String): Set<String> =
        db.createStatement().executeQuery("SELECT id FROM $table").use { rows ->
            buildSet { while (rows.next()) add(rows.getString(1)) }
        }

    @Test
    fun `a removal takes the demo and what was put in it, and leaves everything else`() {
        DemoRemoval.OUTSIDE_HISTORY.forEach { db.createStatement().execute(it) }
        DemoRemoval.SELECTIONS.forEach { (table, query) ->
            val selected = db.createStatement().executeQuery(query).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
            selected.forEach { id -> db.prepareStatement("DELETE FROM $table WHERE id = ?").apply { setString(1, id); execute() } }
        }
        assertEquals(setOf("zone"), ids("zones"))
        assertEquals(setOf("tool"), ids("tool_instances"))
        assertEquals(setOf("entry"), ids("tool_data"))
        assertEquals(setOf("variable"), ids("variables"))
        assertEquals(setOf("routine"), ids("routines"))
        assertEquals(setOf("session", "chat"), ids("ai_sessions"))
        assertEquals(setOf("identity", "demo-identity-zone", "demo-identity-routine"), ids("identities"))
        assertEquals(setOf("task"), ids("tasks"))
        assertEquals(setOf("api", "demo-api-kept"), ids("apis"))
        assertEquals(setOf("task", "routine"), db.createStatement().executeQuery("SELECT owner_id FROM condition_judgements").use { rows ->
            buildSet { while (rows.next()) add(rows.getString(1)) }
        })
    }
}
