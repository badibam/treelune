package app.treelune.core.variables

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.utils.AppConfigManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * A variable evaluated at several instants at once reads each of its terms at them all in one
 * call (VariableService's sources); what it gives is what each instant read alone gives: the
 * night's fast, its terms relative to the instant, one up to now, one falling back on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VariableBatchReadTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val coordinator get() = Coordinator(context)

    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 9, day, hour, 0).atZone(AppConfigManager.getDateTimeConfig().getZoneId()).toInstant().toEpochMilli()

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    @Test
    fun `values read at several instants at once are those read one by one`() = runBlocking<Unit> {
        val zone = ok("zones.create", mapOf("name" to "Body"))["zone_id"] as String
        val meals = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to "tracking", "config" to mapOf(
            "name" to "Meals", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to emptyList<String>()
        )))["tool_instance_id"] as String
        fun day(offset: Int, edge: String) = mapOf("relative" to mapOf("unit" to "DAY", "offset" to offset, "edge" to edge))
        fun latestOf(period: Map<String, Any?>, ifNone: Boolean) = mapOf("reading" to mapOf(
            "selection" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to meals), "period" to period),
            "field" to "timestamp", "reduction" to if (ifNone) "EARLIEST" else "LATEST"
        ) + (if (ifNone) mapOf("if_none" to "NOW") else emptyMap()))
        val variable = ok("variables.create", mapOf("zone_id" to zone, "name" to "fast_batch", "label" to "Fast", "definition" to mapOf(
            "kind" to "FORMULA", "formula" to "first_today - last_yesterday",
            "terms" to mapOf(
                "first_today" to latestOf(mapOf("start" to day(0, "START"), "end" to mapOf("relative" to "NOW")), ifNone = true),
                "last_yesterday" to latestOf(mapOf("start" to day(-1, "START"), "end" to day(-1, "END")), ifNone = false)
            ),
            "field" to mapOf("type" to "DURATION")
        )))["variable_id"] as String
        for ((d, h) in listOf(10 to 20, 11 to 9, 11 to 21, 12 to 8, 13 to 19)) {
            ok("tool_data.create", mapOf("tool_instance_id" to meals, "name" to "Meal", "timestamp" to at(d, h), "data" to mapOf("value" to 1)))
        }

        // Before a meal, after it, a day without any, a day without the day before's
        val instants = listOf(at(11, 7), at(11, 12), at(12, 12), at(13, 12), at(14, 23))
        val together = ok("variables.evaluate", mapOf("variable_id" to variable, "at" to instants))["values"] as List<*>
        val alone = instants.map { instant -> (ok("variables.evaluate", mapOf("variable_id" to variable, "at" to listOf(instant)))["values"] as List<*>).single() }
        assertEquals(alone, together)
    }
}
