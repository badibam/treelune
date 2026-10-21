package app.treelune.tools.list

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.tools.ManualOrder
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A list set to remove what is checked, the change agreed to (docs/TOOLS.md, « List »): turning the
 * setting on removes the items already checked, the rest keeping their order; without the
 * confirmation the change is refused. Held by the config-change machinery (ToolInstanceService,
 * entriesRemovedByConfig).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListRemoveWhenCheckedTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var toolId: String
    private val database get() = AppDatabase.getDatabase(context)

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Errands"))
        assertTrue(zone.error, zone.isSuccess)
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"],
            "tooltype" to "list",
            "config" to mapOf("name" to "Shopping")
        ))
        assertTrue(tool.error, tool.isSuccess)
        toolId = tool.data!!["tool_instance_id"] as String
    }

    private suspend fun add(name: String): String {
        val created = coordinator.processUserAction("tool_data.create", mapOf(
            "tool_instance_id" to toolId, "tooltype" to "list", "name" to name
        ))
        assertTrue(created.error, created.isSuccess)
        return created.data!!["id"] as String
    }

    private suspend fun check(id: String) {
        val result = coordinator.processUserAction("tool_data.update", mapOf(
            "id" to id, "state" to mapOf(ListToolType.CHECKED_AT to System.currentTimeMillis())
        ))
        assertTrue(result.error, result.isSuccess)
    }

    /** The list's stored config, with remove_when_checked set to [removeWhenChecked]. */
    private suspend fun configWith(removeWhenChecked: Boolean): Map<String, Any?> {
        val get = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolId))
        @Suppress("UNCHECKED_CAST")
        val config = (((get.data!!["tool_instance"] as Map<*, *>)["config"]) as Map<String, Any?>).toMutableMap()
        config[ListToolType.REMOVE_WHEN_CHECKED] = removeWhenChecked
        return config
    }

    /** The items left, in the manual order. */
    private suspend fun remainingInOrder(): List<String> = database.toolDataDao().getByToolInstance(toolId)
        .sortedBy { JSONObject(it.state ?: "{}").optInt(ManualOrder.POSITION, Int.MAX_VALUE) }
        .map { it.id }

    @Test
    fun `turning the setting on is refused, agreed it removes the checked and the rest keep their order`() = runBlocking {
        val milk = add("Milk")
        val eggs = add("Eggs")
        val bread = add("Bread")
        check(milk)

        val refused = coordinator.processUserAction("tools.update", mapOf(
            "tool_instance_id" to toolId, "config" to configWith(true)
        ))
        assertFalse(refused.isSuccess)

        val agreed = coordinator.processUserAction("tools.update", mapOf(
            "tool_instance_id" to toolId, "config" to configWith(true), "confirm_migration" to true
        ))
        assertTrue(agreed.error, agreed.isSuccess)

        assertEquals(listOf(eggs, bread), remainingInOrder())
    }
}
