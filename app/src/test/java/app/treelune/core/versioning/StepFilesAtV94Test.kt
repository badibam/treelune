package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** A Sequence step's images become the fixed value of a term, down every block; nothing else moves. */
class StepFilesAtV94Test {

    @Test
    fun `a step's images become a term's fixed value, in blocks too, an empty list gone`() {
        val config = """{"steps": [
            {"kind": "step", "name": "Tree", "end": "manual", "images": ["a", "b"]},
            {"kind": "block", "name": "Set", "repeat": 2, "steps": [{"kind": "step", "name": "Squat", "end": "manual", "images": ["c"]}]},
            {"kind": "step", "name": "Rest", "end": "manual", "images": []}]}"""
        val steps = JSONObject(StepFilesAtV94.toolConfig("sequence", config)!!).getJSONArray("steps")
        assertEquals("""{"constant":["a","b"]}""", steps.getJSONObject(0).getJSONObject("images").toString())
        assertEquals("""{"constant":["c"]}""", steps.getJSONObject(1).getJSONArray("steps").getJSONObject(0).getJSONObject("images").toString())
        assertFalse(steps.getJSONObject(2).has("images"))
    }

    @Test
    fun `another tool, or a session without images, is left alone`() {
        assertNull(StepFilesAtV94.toolConfig("notes", """{"steps": [{"images": ["a"]}]}"""))
        assertNull(StepFilesAtV94.toolConfig("sequence", """{"steps": [{"kind": "step", "name": "Tree", "end": "manual"}]}"""))
    }
}
