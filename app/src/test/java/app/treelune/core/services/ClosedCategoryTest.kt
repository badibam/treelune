package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.strings.Strings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A settings category closed to the AI refuses its reads, but not the reads the app makes itself
 * within an operation the AI asked: an export finds its folder in the data settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClosedCategoryTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the AI reading a closed category is refused`() = runBlocking {
        val read = Coordinator(context).processAICommand("app_config.get", mapOf("category" to AppSettingCategories.DATA))
        assertFalse(read.isSuccess)
    }

    @Test
    fun `an export the AI asks reads the data settings, and says what stops it`() = runBlocking {
        val zone = Coordinator(context).processUserAction("zones.create", mapOf("name" to "Kitchen")).data!!["zone_id"] as String
        val tool = Coordinator(context).processUserAction("tools.create", mapOf(
            "zone_id" to zone, "tooltype" to "notes", "config" to mapOf("name" to "Recipes")
        )).data!!["tool_instance_id"] as String

        val export = Coordinator(context).processAICommand("exports.csv", mapOf("tool_instance_id" to tool))
        assertFalse(export.isSuccess)
        // No folder is chosen in a fresh app: the export says so, past the data settings it read
        assertEquals(Strings.`for`(context = context).shared("export_error_no_folder"), export.error)
    }
}
