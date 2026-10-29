package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A `run_routine` step's one input `data` becomes one block at v108: an entry joined whole where
 * it designated an entry, which the routine received as its id alone; a value otherwise.
 */
class RunRoutineBlocksAtV108Test {

    private fun blocksOf(data: String?): JSONArray {
        val step = JSONObject().put("kind", "run_routine").put("routine_id", "r")
            .put("inputs", JSONObject().apply { data?.let { put("data", JSONObject(it)) } })
        val migrated = JSONArray(RunRoutineBlocksAtV108.steps(JSONArray().put(step).toString())!!).getJSONObject(0)
        assertFalse(migrated.has("inputs"))
        return migrated.getJSONArray("blocks")
    }

    @Test
    fun anEntryGiven_becomesAnEntryJoinedWhole() {
        for (data in listOf("""{"received": {}}""", """{"result": {"step": 0}}""", """{"constant": {"kind": "ENTRY", "id": "e1"}}""")) {
            val block = blocksOf(data).getJSONObject(0)
            assertEquals("entry", block.getString("type"))
            assertEquals(true, block.getBoolean("attach"))
            assertEquals(JSONObject(data).toString(), block.getJSONObject("entry").toString())
        }
    }

    @Test
    fun aValueGiven_becomesAValue() {
        for (data in listOf("""{"received": {"field": "data.title"}}""", """{"received": {"value": "fiche"}}""", """{"result": {"step": 0, "value": "script"}}""", """{"constant": "Hello"}""")) {
            val block = blocksOf(data).getJSONObject(0)
            assertEquals("value", block.getString("type"))
            assertEquals(JSONObject(data).toString(), block.getJSONObject("value").toString())
        }
    }

    @Test
    fun nothingGiven_noBlock() {
        assertEquals(0, blocksOf(null).length())
    }

    @Test
    fun theOtherSteps_stayAsTheyAre() {
        assertNull(RunRoutineBlocksAtV108.steps("""[{"kind": "emit_event", "event_id": "e", "inputs": {"x": {"constant": 1}}}]"""))
        assertNull(RunRoutineBlocksAtV108.steps("""[{"kind": "run_routine", "routine_id": "r", "blocks": []}]"""))
    }

    @Test
    fun aBackup_hasItsTasksMigrated() {
        val data = JSONObject().put("tasks", JSONArray().put(JSONObject().put("id", "t").put("steps_json",
            """[{"kind":"run_routine","routine_id":"r","inputs":{"data":{"received":{}}}}]""")))
        RunRoutineBlocksAtV108.backup(data)
        val step = JSONArray(data.getJSONArray("tasks").getJSONObject(0).getString("steps_json")).getJSONObject(0)
        assertEquals("entry", step.getJSONArray("blocks").getJSONObject(0).getString("type"))
    }
}
