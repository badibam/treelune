package app.treelune.core.variables

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.utils.AppConfigManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * A variable's series read as a tool's entries (docs/design/variable-series.md): the night's
 * fast, the first meal of a day less the last of the day before, a value per day; the days it
 * reaches 16 hours counted by a filter, as a goal's criterion counts; a meal added afterwards
 * for a past day corrects its value, nothing being stored; a period without a start, a variable
 * without a series, a selection of one outside a reading are refused, saying so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VariableSeriesServiceTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val coordinator get() = Coordinator(context)

    /** [hour]:[minute] of [day] September 2026, in the app's time zone, days starting at the app's default hour. */
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 9, day, hour, minute).atZone(AppConfigManager.getDateTimeConfig().getZoneId()).toInstant().toEpochMilli()

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue(result.error, result.isSuccess)
        return result.data ?: emptyMap()
    }

    private val hour = 3_600_000L

    /** Names are unique in the app, whose database the tests share. */
    private var names = System.nanoTime() % 1_000_000

    /** Meals, and the fast as a variable with a value per day when [series]. */
    private suspend fun fast(series: Boolean = true): Pair<String, String> {
        val zone = ok("zones.create", mapOf("name" to "Body"))["zone_id"] as String
        val meals = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to "tracking", "config" to mapOf(
            "name" to "Meals", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to emptyList<String>()
        )))["tool_instance_id"] as String
        fun day(offset: Int, edge: String) = mapOf("relative" to mapOf("unit" to "DAY", "offset" to offset, "edge" to edge))
        fun latestOf(period: Map<String, Any?>, ifNone: Boolean) = mapOf("reading" to mapOf(
            "selection" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to meals), "period" to period),
            "field" to "timestamp", "reduction" to if (ifNone) "MIN" else "MAX"
        ) + (if (ifNone) mapOf("if_none" to "NOW") else emptyMap()))
        val variable = ok("variables.create", mapOf("zone_id" to zone, "name" to "fast_${names++}", "label" to "Fast", "definition" to mapOf(
            "kind" to "FORMULA", "formula" to "first_today - last_yesterday",
            "terms" to mapOf(
                "first_today" to latestOf(mapOf("start" to day(0, "START"), "end" to mapOf("relative" to "NOW")), ifNone = true),
                "last_yesterday" to latestOf(mapOf("start" to day(-1, "START"), "end" to day(-1, "END")), ifNone = false)
            ),
            "field" to mapOf("type" to "DURATION")
        ) + (if (series) mapOf("series" to mapOf("unit" to "DAY")) else emptyMap())))["variable_id"] as String
        return meals to variable
    }

    private suspend fun meal(meals: String, at: Long) = ok("tool_data.create", mapOf("tool_instance_id" to meals, "name" to "Meal", "timestamp" to at, "data" to mapOf("value" to 1)))

    /** The days of 14 to 16 September the fast reached 16 hours, read on the 16th in the evening. */
    private suspend fun daysOf16(variable: String) = coordinator.processUserAction("readings.read", mapOf(
        "selection" to mapOf(
            "target" to mapOf("kind" to "VARIABLE", "id" to variable), "sort" to listOf(mapOf("field" to "timestamp", "direction" to "desc")),
            "period" to mapOf("start" to at(14, 12), "end" to at(16, 23)),
            "filters" to listOf(mapOf("left" to mapOf("field" to "data.value"), "op" to ">=", "right" to mapOf("constant" to 16 * hour)))
        ),
        "reduction" to "COUNT",
        "reference" to at(16, 23)
    ))

    @Test
    fun `the days a fast reached 16 hours are counted, and a meal added for a past day corrects them`() = runBlocking {
        val (meals, variable) = fast()
        // The 13th ends at 20:00; the 14th starts at 13:00 (17 h) and ends at 19:00; the 15th starts at 9:00 (14 h), ends at 19:00; the 16th starts at 12:00 (17 h)
        listOf(at(13, 20), at(14, 13), at(14, 19), at(15, 9), at(15, 19), at(16, 12)).forEach { meal(meals, it) }
        assertEquals(2, (ok("readings.read", daysOf16Params(variable))["value"] as Number).toInt())

        // A late dinner on the 13th, noted afterwards: the 14th's fast is now 15 hours
        meal(meals, at(13, 22))
        assertEquals(1, (ok("readings.read", daysOf16Params(variable))["value"] as Number).toInt())
    }

    private suspend fun daysOf16Params(variable: String) = mapOf(
        "selection" to mapOf(
            "target" to mapOf("kind" to "VARIABLE", "id" to variable), "sort" to listOf(mapOf("field" to "timestamp", "direction" to "desc")),
            "period" to mapOf("start" to at(14, 12), "end" to at(16, 23)),
            "filters" to listOf(mapOf("left" to mapOf("field" to "data.value"), "op" to ">=", "right" to mapOf("constant" to 16 * hour)))
        ),
        "reduction" to "COUNT",
        "reference" to at(16, 23)
    )

    @Test
    fun `a day without a value has no entry, and an average leaves it out`() = runBlocking {
        val (meals, variable) = fast()
        // Nothing on the 13th: the 14th has no day before to measure from
        listOf(at(14, 19), at(15, 9)).forEach { meal(meals, it) }
        val read = ok("readings.read", mapOf(
            "selection" to mapOf("target" to mapOf("kind" to "VARIABLE", "id" to variable), "sort" to listOf(mapOf("field" to "timestamp", "direction" to "desc")), "period" to mapOf("start" to at(14, 12), "end" to at(15, 23))),
            "field" to "data.value", "reduction" to "AVERAGE", "reference" to at(15, 23)
        ))
        assertEquals(14 * hour, (read["value"] as Number).toLong())
    }

    @Test
    fun `a series read over a period without a start is refused, saying so`() = runBlocking {
        val (_, variable) = fast()
        val read = coordinator.processUserAction("readings.read", mapOf(
            "selection" to mapOf("target" to mapOf("kind" to "VARIABLE", "id" to variable), "sort" to listOf(mapOf("field" to "timestamp", "direction" to "desc"))),
            "reduction" to "COUNT", "reference" to at(16, 23)
        ))
        assertFalse(read.isSuccess)
        assertTrue(read.error, read.error!!.contains("start"))
    }

    @Test
    fun `a variable without a series is not selected`() = runBlocking {
        val (_, variable) = fast(series = false)
        assertFalse(daysOf16(variable).isSuccess)
    }

    @Test
    fun `a variable reading its own series is a loop, refused`() = runBlocking {
        val (_, variable) = fast()
        val changed = coordinator.processUserAction("variables.update", mapOf("variable_id" to variable, "definition" to mapOf(
            "kind" to "FORMULA", "formula" to "days",
            "terms" to mapOf("days" to mapOf("reading" to mapOf(
                "selection" to mapOf("target" to mapOf("kind" to "VARIABLE", "id" to variable), "period" to mapOf("start" to mapOf("relative" to mapOf("unit" to "DAY", "offset" to -7, "edge" to "START")))),
                "reduction" to "COUNT"
            ))),
            "series" to mapOf("unit" to "DAY")
        )))
        assertFalse(changed.isSuccess)
    }

    @Test
    fun `a goal's criterion counting the days of a series, filtered on its value, is saved`() = runBlocking {
        val (meals, variable) = fast()
        val zone = ok("tools.get", mapOf("tool_instance_id" to meals)).let { ((it["tool_instance"] as Map<*, *>)["zone_id"]) as String }
        val goal = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone, "tooltype" to "goal", "config" to mapOf(
            "name" to "Week",
            "criteria" to listOf(mapOf(
                "key" to "c_fast", "name" to "Fast of 16 h",
                "condition" to mapOf(
                    "left" to mapOf("reading" to mapOf(
                        "selection" to mapOf(
                            "target" to mapOf("kind" to "VARIABLE", "id" to variable),
                            "filters" to listOf(mapOf("left" to mapOf("field" to "data.value"), "op" to ">=", "right" to mapOf("constant" to 16 * hour)))
                        ),
                        "reduction" to "COUNT"
                    )),
                    "op" to ">=", "right" to mapOf("constant" to 7)
                )
            ))
        )))
        assertTrue(goal.error, goal.isSuccess)
    }

    @Test
    fun `the series is kept in the variable's definition`() = runBlocking {
        val (_, variable) = fast()
        val read = ok("variables.get", mapOf("variable_id" to variable))
        val definition = (read["variable"] as Map<*, *>)["definition"] as Map<*, *>
        assertEquals(mapOf("unit" to "DAY"), definition["series"])
    }
}
