package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** A Sequence step's end becomes how long it lasts and whether the next step comes by itself, down every block; its duration stays. */
class StepEndsAtV95Test {

    @Test
    fun `each end becomes a length and an auto_next, in blocks too`() {
        val config = """{"steps": [
            {"kind": "step", "name": "A", "end": "timed", "duration": 30000},
            {"kind": "block", "name": "B", "repeat": 2, "steps": [{"kind": "step", "name": "C", "end": "timed_then_manual", "duration": 60000}]},
            {"kind": "step", "name": "D", "end": "manual"},
            {"kind": "step", "name": "E", "end": "media"}]}"""
        val steps = JSONObject(StepEndsAtV95.toolConfig("sequence", config)!!).getJSONArray("steps")
        fun of(step: JSONObject) = step.getString("length") to step.getBoolean("auto_next")
        assertEquals("fixed" to true, of(steps.getJSONObject(0)))
        assertEquals(30000L, steps.getJSONObject(0).getLong("duration"))
        assertEquals("fixed" to false, of(steps.getJSONObject(1).getJSONArray("steps").getJSONObject(0)))
        assertEquals("none" to false, of(steps.getJSONObject(2)))
        assertEquals("media" to true, of(steps.getJSONObject(3)))
        assertFalse(steps.getJSONObject(3).has("end"))
    }

    @Test
    fun `another tool is left alone`() {
        assertNull(StepEndsAtV95.toolConfig("notes", """{"steps": [{"end": "timed"}]}"""))
    }
}
