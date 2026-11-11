package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the v116 migration promises (SelectionSortAtV116): every selection of a tool or a series
 * says the order it was read in — the newest first, by date, or by creation for a tool without
 * dates; a Task's list input the oldest first —, a Session's media its `media_sort`; a trigger's,
 * a zone's and one entry's are left as they were; LAST, EARLIEST and LATEST become FIRST, MIN and
 * MAX.
 */
class SelectionSortAtV116Test {

    private val tools = SelectionSortAtV116.Tools(mapOf("journal" to "journal", "table" to "structured"))

    private fun target(kind: String, id: String?) = JSONObject().put("kind", kind).apply { id?.let { put("id", it) } }
    private fun selection(kind: String, id: String?) = JSONObject().put("target", target(kind, id))
    private fun sortOf(selection: JSONObject) = selection.optJSONArray("sort")?.getJSONObject(0)?.let { it.getString("field") to it.getString("direction") }

    @Test
    fun aReadingSaysItsOrder_andItsReductionIsRenamed() {
        val definition = JSONObject().put("terms", JSONObject()
            .put("a", JSONObject().put("reading", JSONObject().put("selection", selection("TOOL_INSTANCE", "journal")).put("field", "timestamp").put("reduction", "LAST")))
            .put("b", JSONObject().put("reading", JSONObject().put("selection", selection("TOOL_INSTANCE", "table")).put("field", "data.n").put("reduction", "SUM")
                .put("group", JSONObject().put("field", "timestamp").put("reduction", "LATEST"))))
            .put("c", JSONObject().put("reading", JSONObject().put("selection", selection("VARIABLE", "v")).put("field", "value").put("reduction", "EARLIEST"))))
        val terms = JSONObject(SelectionSortAtV116.json(definition.toString(), tools)!!).getJSONObject("terms")
        val a = terms.getJSONObject("a").getJSONObject("reading")
        assertEquals("timestamp" to "desc", sortOf(a.getJSONObject("selection")))
        assertEquals("FIRST", a.getString("reduction"))
        val b = terms.getJSONObject("b").getJSONObject("reading")
        // A table has no date: its entries were the newest created first
        assertEquals("created_at" to "desc", sortOf(b.getJSONObject("selection")))
        assertEquals("MAX", b.getJSONObject("group").getString("reduction"))
        val c = terms.getJSONObject("c").getJSONObject("reading")
        assertEquals("timestamp" to "desc", sortOf(c.getJSONObject("selection")))
        assertEquals("MIN", c.getString("reduction"))
    }

    @Test
    fun aTasksListInputKeepsTheOldestFirst_aTriggerNothing() {
        val steps = JSONArray().put(JSONObject().put("kind", "call_api").put("inputs", JSONObject()
            .put("cards", JSONObject().put("selection", selection("TOOL_INSTANCE", "journal").put("fields", JSONArray().put("name"))))))
        val stepsAfter = JSONArray(SelectionSortAtV116.json(steps.toString(), tools)!!)
        assertEquals("timestamp" to "asc", sortOf(stepsAfter.getJSONObject(0).getJSONObject("inputs").getJSONObject("cards").getJSONObject("selection")))

        val triggers = JSONArray().put(JSONObject().put("kind", "entry_written").put("entries", selection("TOOL_INSTANCE", "journal")))
        assertNull(SelectionSortAtV116.json(triggers.toString(), tools))
    }

    @Test
    fun aZoneAndOneEntry_areLeftAsTheyWere() {
        val message = JSONObject().put("segments", JSONArray()
            .put(JSONObject().put("type", "enrichment").put("enrichment_type", "POINTER")
                .put("config", JSONObject().put("selection", selection("ZONE", "z")).toString()))
            .put(JSONObject().put("type", "enrichment").put("enrichment_type", "POINTER")
                .put("config", JSONObject().put("selection", selection("ENTRY", "e")).toString())))
        assertNull(SelectionSortAtV116.richContent(message.toString(), tools))
    }

    @Test
    fun aMessagesPointerSaysItsOrder() {
        val message = JSONObject().put("segments", JSONArray()
            .put(JSONObject().put("type", "text").put("content", "Lis "))
            .put(JSONObject().put("type", "enrichment").put("enrichment_type", "POINTER")
                .put("config", JSONObject().put("selection", selection("TOOL_INSTANCE", "journal")).put("attach", JSONObject().put("entries", true)).toString())))
        val after = JSONObject(SelectionSortAtV116.richContent(message.toString(), tools)!!)
        val config = JSONObject(after.getJSONArray("segments").getJSONObject(1).getString("config"))
        assertEquals("timestamp" to "desc", sortOf(config.getJSONObject("selection")))
    }

    @Test
    fun aSessionsMediaTakesItsSort_whichGoes() {
        val sort = JSONArray().put(JSONObject().put("field", "data.n").put("direction", "asc"))
        val config = JSONObject().put("variant", "media").put("media_source", selection("TOOL_INSTANCE", "table")).put("media_sort", sort)
        val after = JSONObject(SelectionSortAtV116.config("sequence", config.toString(), tools)!!)
        assertFalse(after.has("media_sort"))
        assertEquals("data.n" to "asc", sortOf(after.getJSONObject("media_source")))

        val unsorted = JSONObject().put("variant", "media").put("media_source", selection("TOOL_INSTANCE", "table"))
        assertEquals("created_at" to "desc", sortOf(JSONObject(SelectionSortAtV116.config("sequence", unsorted.toString(), tools)!!).getJSONObject("media_source")))
    }

    @Test
    fun aStartedSessionsRun_hasItsStepsReadingsRenamed() {
        val reading = JSONObject().put("selection", selection("ENTRY", "e")).put("field", "extra.audio").put("reduction", "LAST")
        val run = JSONObject().put("steps", JSONArray().put(JSONObject().put("terms", JSONObject().put("media", JSONObject().put("reading", reading)))))
        val state = JSONObject().put("status", "running").put("run", run.toString())
        val after = JSONObject(SelectionSortAtV116.entryState(state.toString(), tools)!!)
        val step = JSONObject(after.getString("run")).getJSONArray("steps").getJSONObject(0)
        assertEquals("FIRST", step.getJSONObject("terms").getJSONObject("media").getJSONObject("reading").getString("reduction"))
        assertNull(SelectionSortAtV116.entryState(JSONObject().put("status", "done").toString(), tools))
    }

    @Test
    fun aSortAlreadyWritten_isKept() {
        val written = selection("TOOL_INSTANCE", "journal").put("sort", JSONArray().put(JSONObject().put("field", "name").put("direction", "asc")))
        assertNull(SelectionSortAtV116.json(JSONObject().put("selection", written).toString(), tools))
    }
}
