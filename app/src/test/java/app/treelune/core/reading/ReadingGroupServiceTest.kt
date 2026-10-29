package app.treelune.core.reading

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.selection.EntryPeriod
import app.treelune.core.selection.TimePoint
import app.treelune.core.strings.Strings
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermReader
import app.treelune.core.utils.AppConfigManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * A grouped reading as the app reads it (readings.read, TermReader): grouped by day, it needs
 * the start of its period, which one without a period of its own takes from its context (a
 * goal's attempt); the right side of its keep, a variable, is read and compared with each group.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingGroupServiceTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val coordinator get() = Coordinator(context)

    /** Noon of [day] September 2026, in the app's time zone: clear of any hour a day may start at. */
    private fun noon(day: Int) = LocalDateTime.of(2026, 9, day, 12, 0).atZone(AppConfigManager.getDateTimeConfig().getZoneId()).toInstant().toEpochMilli()

    /** A tracking of cups: 3 on the 14th, 1 on the 16th, none on the 15th. */
    private suspend fun cups(): Pair<String, String> {
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Coffee")).data!!["zone_id"] as String
        val tool = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone, "tooltype" to "tracking", "config" to mapOf(
            "name" to "Cups", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to emptyList<String>()
        ))).data!!["tool_instance_id"] as String
        for ((day, value) in listOf(14 to 2, 14 to 1, 16 to 1)) {
            val written = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to tool, "name" to "Cups", "timestamp" to noon(day), "data" to mapOf("value" to value)))
            assertTrue(written.error, written.isSuccess)
        }
        return zone to tool
    }

    /** The cups summed per day, the days kept by [keep], counted. */
    private fun perDay(tool: String, keep: JSONObject?) = Term.fromJson(JSONObject().put("reading", JSONObject()
        .put("selection", JSONObject().put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", tool)))
        .put("field", "data.value").put("reduction", "SUM")
        .put("group", JSONObject().put("field", "timestamp").put("unit", "DAY").put("reduction", "COUNT").apply { keep?.let { put("keep", it) } })), "t") { it }

    private suspend fun read(term: Term, period: EntryPeriod?) = TermReader(context).read(term, noon(16) + 3_600_000, period)

    @Test
    fun `grouped by day, a reading without the start of its period is refused, saying so`() = runBlocking {
        val (_, tool) = cups()
        // A reading that cannot be made as written is thrown, as its other config errors are
        val refused = runCatching { read(perDay(tool, null), null) }.exceptionOrNull()
        assertTrue(refused is IllegalStateException)
        assertEquals(Strings.`for`(context = context).shared("reading_error_group_start"), refused!!.message)
    }

    @Test
    fun `a reading without a period of its own reads its context's, a goal's attempt, grouped by day`() = runBlocking {
        val (_, tool) = cups()
        // From the 14th at noon to the 16th: three days, the 15th empty
        val attempt = EntryPeriod(TimePoint.Fixed(noon(14)), TimePoint.Fixed(noon(16) + 3_600_000))
        assertEquals(3, ((read(perDay(tool, null), attempt) as TermReader.Read.Value).value as Number).toInt())
        assertEquals(2, ((read(perDay(tool, JSONObject().put("op", ">=").put("right", JSONObject().put("constant", 1))), attempt) as TermReader.Read.Value).value as Number).toInt())
    }

    @Test
    fun `the right side of the keep, a variable, is read and compared with each group`() = runBlocking {
        val (zone, tool) = cups()
        val variable = coordinator.processUserAction("variables.create", mapOf(
            "zone_id" to zone, "name" to "max_cups", "label" to "Max cups",
            "definition" to mapOf("kind" to "CONSTANT", "value" to 2, "field" to mapOf("type" to "NUMERIC"))
        ))
        assertTrue(variable.error, variable.isSuccess)
        val attempt = EntryPeriod(TimePoint.Fixed(noon(14)), TimePoint.Fixed(noon(16) + 3_600_000))
        // The days at most 2 cups: the 15th (none) and the 16th (one), not the 14th (three)
        val keep = JSONObject().put("op", "<=").put("right", JSONObject().put("variable", variable.data!!["variable_id"]))
        assertEquals(2, ((read(perDay(tool, keep), attempt) as TermReader.Read.Value).value as Number).toInt())
    }
}
