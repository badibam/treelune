package app.treelune.core.tasks

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A Task's steps moved or removed in its editor (TaskSteps.moved, TaskSteps.removed): what a step
 * reads of another follows the step it reads; what read a removed step is emptied, a repeated
 * step's list kept on no step, never left to read the step that takes its place.
 */
class StepRenumberTest {

    private fun result(step: Int, value: String? = null) = JSONObject().put("result", JSONObject().put("step", step).apply { value?.let { put("value", it) } })

    /** An API call, an entry created from its answer, an entry updated with the one created. */
    private fun steps() = JSONArray()
        .put(JSONObject().put("kind", "call_api").put("api_id", "weather"))
        .put(JSONObject().put("kind", "create_entry").put("values", JSONObject().put("data.t", result(0, "temperature"))))
        .put(JSONObject().put("kind", "update_entry").put("entry", result(1)).put("values", JSONObject().put("data.t", result(0, "temperature"))))

    private fun JSONArray.step(i: Int) = getJSONObject(i)

    @Test
    fun `a step moved up takes along what reads it, and what it reads`() {
        // The update moved above the creation it reads: its read follows, which saving then refuses
        val moved = TaskSteps.moved(steps(), 2, 1)
        assertEquals("update_entry", moved.step(1).getString("kind"))
        assertEquals(2, moved.step(1).getJSONObject("entry").getJSONObject("result").getInt("step"))
        assertEquals(0, moved.step(1).getJSONObject("values").getJSONObject("data.t").getJSONObject("result").getInt("step"))
        assertEquals(0, moved.step(2).getJSONObject("values").getJSONObject("data.t").getJSONObject("result").getInt("step"))
    }

    @Test
    fun `a step removed empties what read it, and the reads past it move up`() {
        val removed = TaskSteps.removed(steps(), 1)
        assertEquals(2, removed.length())
        val update = removed.step(1)
        assertFalse("the read of the removed step emptied", update.has("entry"))
        assertEquals(0, update.getJSONObject("values").getJSONObject("data.t").getJSONObject("result").getInt("step"))

        val first = TaskSteps.removed(steps(), 0)
        assertEquals(0, first.step(1).getJSONObject("entry").getJSONObject("result").getInt("step"))
        assertFalse(first.step(0).getJSONObject("values").has("data.t"))
    }

    @Test
    fun `a repeated step whose list is removed keeps its for_each, on no step`() {
        val steps = JSONArray()
            .put(JSONObject().put("kind", "call_api").put("api_id", "list"))
            .put(JSONObject().put("kind", "create_entry").put("for_each", result(0, "items")))
        val removed = TaskSteps.removed(steps, 0)
        assertEquals(-1, removed.step(0).getJSONObject("for_each").getJSONObject("result").getInt("step"))
    }
}
