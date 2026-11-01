package app.treelune.tools.agenda

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Origin
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.scheduling.SchedulersHeld
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * An event of its own made to repeat (AgendaService, « repeat »): it becomes the first occurrence
 * of a new series of its name and duration, which gives the next ones after it, never one at its
 * own time; its user's fields stay; an occurrence refuses to repeat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgendaRepeatTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val dao get() = AppDatabase.getDatabase(context).toolDataDao()
    private val day = 86_400_000L

    /** The zone of this test, deleted after it with its calendar: a later test's tick would give its series. */
    private var zoneId: String? = null

    @After
    fun tearDown() = runBlocking<Unit> {
        zoneId?.let {
            val deleted = Coordinator(context).processUserAction("zones.delete", mapOf("zone_id" to it))
            assertTrue(deleted.error, deleted.isSuccess)
        }
    }

    @Test
    fun `an event made to repeat is its series' first occurrence, the next ones given after it`() = runBlocking<Unit> {
        // The scheduler runs here alone, when the test says
        SchedulersHeld.during {
            val coordinator = Coordinator(context)
            val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Calendar"))
            assertTrue(zone.error, zone.isSuccess)
            zoneId = zone.data!!["zone_id"] as String
            val tool = coordinator.processUserAction("tools.create", mapOf(
                "zone_id" to zoneId, "tooltype" to "agenda",
                "config" to mapOf("name" to "Calendar", "extra_fields" to listOf(mapOf("display_name" to "Place", "type" to "TEXT")))
            ))
            assertTrue(tool.error, tool.isSuccess)
            val toolId = tool.data!!["tool_instance_id"] as String
            val fieldName = AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(toolId)!!.let { instance ->
                JSONObject(instance.config_json).getJSONArray("extra_fields").getJSONObject(0).getString("name")
            }

            // Two days ahead at 10:00, an hour long, with a place
            val timezone = AgendaToolType.zone()
            val start = Instant.now().atZone(timezone).toLocalDate().plusDays(2).atTime(10, 0).atZone(timezone).toInstant().toEpochMilli()
            val created = coordinator.processUserAction("tool_data.create", mapOf(
                "tool_instance_id" to toolId, "name" to "Physio", "timestamp" to start,
                "data" to mapOf(AgendaToolType.DURATION to 3_600_000L),
                "extra" to mapOf(fieldName to "Clinic"),
                "state" to mapOf(AgendaToolType.STATUS to AgendaToolType.Status.PLANNED)
            ))
            assertTrue(created.error, created.isSuccess)
            val eventId = created.data!!["id"] as String

            val schedule = mapOf("pattern" to mapOf("type" to "DailyMultiple", "times" to listOf("10:00")))
            val repeated = coordinator.processUserAction("agenda.repeat", mapOf("id" to eventId, "schedule" to schedule))
            assertTrue(repeated.error, repeated.isSuccess)
            val key = repeated.data!!["series"] as String

            // The series, of the event's name and duration
            val config = JSONObject(AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(toolId)!!.config_json)
            val series = config.getJSONArray(AgendaToolType.SERIES_LIST).getJSONObject(0)
            assertEquals(key, series.getString("key"))
            assertEquals("Physio", series.getString("name"))
            assertEquals(3_600_000L, series.getLong(AgendaToolType.DURATION))

            // The event, its first occurrence, its place kept
            val event = dao.getById(eventId)!!
            assertEquals(key, JSONObject(event.state!!).getString(AgendaToolType.SERIES))
            assertEquals(start, event.timestamp)
            assertEquals("Clinic", JSONObject(event.extra!!).getString(fieldName))

            // A pass gives the next ones after it, none at its time
            withContext(Origin(Source.SCHEDULER)) { AgendaScheduler.checkScheduled(context) }
            val occurrences = dao.getByToolInstance(toolId).filter { it.state?.let { s -> JSONObject(s).optString(AgendaToolType.SERIES) } == key }
            assertTrue("occurrences given: ${occurrences.size}", occurrences.size > 1)
            assertEquals("one occurrence at the event's time", 1, occurrences.count { it.timestamp == start })
            assertEquals("the next one, the day after", start + day, occurrences.filter { it.id != eventId }.minOf { it.timestamp!! })

            // An occurrence already repeats
            assertFalse(coordinator.processUserAction("agenda.repeat", mapOf("id" to eventId, "schedule" to schedule)).isSuccess)
        }
    }
}
