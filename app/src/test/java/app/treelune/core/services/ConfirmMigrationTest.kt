package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A config change of the AI that touches recorded values (docs/DATA.md, « Config change »): with
 * the past-data protection off, its own word is not enough — it is refused until it sends
 * confirm_migration; agreed, the values now out of bounds go.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfirmMigrationTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var toolId: String
    private val database get() = AppDatabase.getDatabase(context)

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Health"))
        assertTrue(zone.error, zone.isSuccess)
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"],
            "tooltype" to "tracking",
            "config" to mapOf("name" to "Weight", "type" to "numeric", "value" to mapOf("min" to 0, "max" to 10, "decimals" to 0), "units" to listOf("kg"))
        ))
        assertTrue(tool.error, tool.isSuccess)
        toolId = tool.data!!["tool_instance_id"] as String
        val entry = coordinator.processUserAction("tool_data.create", mapOf(
            "tool_instance_id" to toolId, "name" to "Weighing", "data" to mapOf("value" to 8, "unit" to "kg")
        ))
        assertTrue(entry.error, entry.isSuccess)
        val off = coordinator.processUserAction("app_config.set", mapOf("category" to "validation_config", "settings" to mapOf("validate_app" to false, "validate_past_data" to false)))
        assertTrue(off.error, off.isSuccess)
    }

    /** The scale tightened: 8 is out of the new bounds. */
    private fun tightened() = mapOf("name" to "Weight", "type" to "numeric", "value" to mapOf("min" to 0, "max" to 5, "decimals" to 0), "units" to listOf("kg"))

    @Test
    fun `the AI tightening the bounds is refused until confirm_migration, agreed the value goes`() = runBlocking {
        val refused = coordinator.processAICommand("tools.update", mapOf("tool_instance_id" to toolId, "config" to tightened()))
        assertFalse(refused.isSuccess)

        val agreed = coordinator.processAICommand("tools.update", mapOf("tool_instance_id" to toolId, "config" to tightened(), "confirm_migration" to true))
        assertTrue(agreed.error, agreed.isSuccess)

        assertTrue("the entry whose value fell out of the new bounds is gone", database.toolDataDao().getByToolInstance(toolId).isEmpty())
    }
}
