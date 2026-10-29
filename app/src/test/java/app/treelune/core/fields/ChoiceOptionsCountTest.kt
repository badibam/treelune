package app.treelune.core.fields

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A closed choice offers one option at least, an open one may start with none: its options come
 * with the entries written.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChoiceOptionsCountTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val coordinator get() = Coordinator(context)

    /** A tracking with one choice of the user's, [config] its config. */
    private suspend fun create(zone: String, config: Map<String, Any?>) = coordinator.processUserAction("tools.create", mapOf(
        "zone_id" to zone, "tooltype" to "tracking", "config" to mapOf(
            "name" to "Moods", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to emptyList<String>(),
            "extra_fields" to listOf(mapOf("name" to "mood", "display_name" to "Mood", "type" to "CHOICE", "always_visible" to false, "config" to config))
        )
    ))

    @Test
    fun `a closed choice takes one option, not none, an open one none, filled by the writes`() = runBlocking {
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Feelings")).data!!["zone_id"] as String

        assertTrue(create(zone, mapOf("options" to ChoiceSettings.storedOptions(listOf("calm")))).isSuccess)
        assertFalse(create(zone, mapOf("options" to emptyList<Any>())).isSuccess)

        val open = create(zone, mapOf("options" to emptyList<Any>(), "open" to true))
        assertTrue(open.error, open.isSuccess)
        val tool = open.data!!["tool_instance_id"] as String
        val written = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to tool, "name" to "Evening", "data" to mapOf("value" to 3), "extra" to mapOf("mood" to "tired")))
        assertTrue(written.error, written.isSuccess)
        @Suppress("UNCHECKED_CAST")
        val config = (coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to tool)).data!!["tool_instance"] as Map<*, *>)["config"] as Map<String, Any>
        val mood = (config["extra_fields"] as List<*>).map { it as Map<*, *> }.single { it["name"] == "mood" }
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("tired"), ChoiceSettings.fromConfig(mood["config"] as Map<String, Any>).options)
    }
}
