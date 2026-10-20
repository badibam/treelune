package app.treelune.core.history

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.strings.Strings
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the history's screen promises of a change: each field by the label of the form that edits
 * it, its values as stored, an id by its thing's name, and what no form shows counted, not shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadableChangesTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private val database get() = AppDatabase.getDatabase(context)
    private val s get() = Strings.`for`(context = context)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
    }

    private suspend fun ok(operation: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(operation, params)
        assertTrue(result.error, result.isSuccess)
        return result.data.orEmpty()
    }

    /** The rows of the last step, read for the screen. */
    private suspend fun lastRows(): List<ReadableChanges.Readable> {
        @Suppress("UNCHECKED_CAST")
        val step = (ok("history.list", mapOf("limit" to 1))["steps"] as List<Map<String, Any?>>).single()["id"]
        @Suppress("UNCHECKED_CAST")
        val rows = ok("history.rows", mapOf("id" to step))["rows"] as List<Map<String, Any?>>
        fun json(map: Any?) = (map as? Map<*, *>)?.let { JSONObject(it) }
        val all = rows.map { CoveredTable.of(it["table"] as String) to json(it["before"] ?: it["after"])!! }
        return rows.map { ReadableChanges(context, database).of(CoveredTable.of(it["table"] as String), json(it["before"]), json(it["after"]), all) }
    }

    @Test
    fun `a zone renamed shows its name by the label of the zone's form`() = runBlocking {
        val zoneId = ok("zones.create", mapOf("name" to "Garden"))["zone_id"] as String
        ok("zones.update", mapOf("zone_id" to zoneId, "name" to "Orchard"))

        val line = lastRows().single().lines.single() as ReadableChanges.Line.Value
        assertEquals(s.shared("label_zone_name"), line.field.displayName)
        assertEquals("Garden", line.old)
        assertEquals("Orchard", line.new)
    }

    @Test
    fun `a tool moved to another zone shows the zones by their names, its place in the grid counted`() = runBlocking {
        val from = ok("zones.create", mapOf("name" to "Garden"))["zone_id"] as String
        val to = ok("zones.create", mapOf("name" to "Kitchen"))["zone_id"] as String
        val toolId = ok("tools.create", mapOf("zone_id" to from, "tooltype" to "notes", "config" to mapOf("name" to "Plants")))["tool_instance_id"] as String
        ok("tools.update", mapOf("tool_instance_id" to toolId, "zone_id" to to))

        val tool = lastRows().first { row -> row.lines.any { it is ReadableChanges.Line.Value && it.field.name == "zone_id" } }
        val zone = tool.lines.filterIsInstance<ReadableChanges.Line.Value>().single { it.field.name == "zone_id" }
        assertEquals(s.shared("label_zone"), zone.field.displayName)
        assertEquals("Garden" to "Kitchen", zone.old to zone.new)
    }

    @Test
    fun `an entry changed shows its value by its field's label`() = runBlocking {
        val zoneId = ok("zones.create", mapOf("name" to "Health"))["zone_id"] as String
        val toolId = ok("tools.create", mapOf(
            "zone_id" to zoneId, "tooltype" to "tracking",
            "config" to mapOf("name" to "Weight", "type" to "numeric", "value" to mapOf("decimals" to 1), "units" to listOf("kg"))
        ))["tool_instance_id"] as String
        val entryId = ok("tool_data.create", mapOf("tool_instance_id" to toolId, "name" to "Weighing", "data" to mapOf("value" to 72, "unit" to "kg")))["id"] as String
        ok("tool_data.update", mapOf("tool_instance_id" to toolId, "id" to entryId, "data" to mapOf("value" to 71.5, "unit" to "kg")))

        val value = lastRows().single().lines.filterIsInstance<ReadableChanges.Line.Value>().single { it.field.name == "value" }
        assertEquals(72, (value.old as Number).toInt())
        assertEquals(71.5, (value.new as Number).toDouble(), 0.0)
    }
}
