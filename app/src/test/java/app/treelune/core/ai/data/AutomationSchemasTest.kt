package app.treelune.core.ai.data

import app.treelune.core.ai.prompts.ModelValues
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * What the AI reads of a routine reaches it in ISO 8601 and comes back in milliseconds, by the
 * marks of its schemas (AutomationSchemas): a pointer's fixed period bound, a run's two instants.
 * What is not an instant is left as it is, a relative bound and a text that looks like a date.
 */
class AutomationSchemasTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val text: (String) -> String = { it }

    private val start = 1_790_000_000_000L
    private val iso = app.treelune.core.utils.DateTimeConverter.timestampToISO(start, zone)
    private val relative = JSONObject().put("relative", "NOW")

    private fun message(bound: Any) = JSONArray()
        .put(JSONObject().put("type", "text").put("text", iso))
        .put(JSONObject().put("type", "pointer").put("pointer", JSONObject()
            .put("selection", JSONObject()
                .put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", "meals"))
                .put("period", JSONObject().put("start", bound).put("end", relative)))
            .put("attach", JSONObject().put("config", false).put("entries", true))))

    @Test
    fun aPointersFixedBoundIsWrittenInIsoAndStoredInMilliseconds() {
        val schema = AutomationSchemas.message(text)
        assertEquals(message(start).toString(), (ModelValues.fromModel(message(iso), schema, zone) as JSONArray).toString())
        assertEquals(message(iso).toString(), (ModelValues.toModel(message(start), schema, zone) as JSONArray).toString())
    }

    @Test
    fun aRunsInstantsAreReadInIso() {
        val run = mapOf("id" to "r1", "scheduled_execution_time" to start, "created_at" to start + 60_000, "end_reason" to "COMPLETED", "total_roundtrips" to 3)
        val read = (ModelValues.toModel(mapOf("runs" to listOf(run)), AutomationSchemas.runsContent(), zone) as Map<*, *>)["runs"] as List<*>
        val row = read.single() as Map<*, *>
        assertEquals(iso, row["scheduled_execution_time"])
        assertEquals(app.treelune.core.utils.DateTimeConverter.timestampToISO(start + 60_000, zone), row["created_at"])
        assertEquals("COMPLETED", row["end_reason"])
        assertEquals(3, row["total_roundtrips"])
    }
}
