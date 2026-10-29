package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings a Task's `run_routine` steps to their v108 form: what joins the routine's message is a
 * list of blocks (`blocks`, StepBlocks), no longer the one input `data` under `inputs`. That input
 * becomes one block: an entry block, joined whole, when it designated an entry — the one a trigger
 * gave (`{"received": {}}`), the one a step created (`{"result": {"step"}}` without a value), one
 * designated (`{"constant": {"kind": "ENTRY"}}`) —, which the routine received as its id alone; a
 * value block otherwise. A step without it has no blocks.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object RunRoutineBlocksAtV108 {

    /** A Task's steps (their JSON) at v108, null when no step runs a routine with inputs. */
    fun steps(json: String): String? {
        val steps = JSONArray(json)
        var changed = false
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            if (step.optString("kind") != "run_routine" || !step.has("inputs")) continue
            val blocks = JSONArray()
            step.optJSONObject("inputs")?.optJSONObject("data")?.let { data ->
                blocks.put(if (designatesEntry(data)) JSONObject().put("type", "entry").put("entry", data).put("attach", true)
                    else JSONObject().put("type", "value").put("value", data))
            }
            step.remove("inputs")
            step.put("blocks", blocks)
            changed = true
        }
        return if (changed) steps.toString() else null
    }

    /** Whether the input [data] designates an entry rather than a value. */
    private fun designatesEntry(data: JSONObject): Boolean {
        data.optJSONObject("received")?.let { return !it.has("field") && !it.has("value") }
        data.optJSONObject("result")?.let { return !it.has("value") }
        return data.optJSONObject("constant")?.optString("kind") == "ENTRY"
    }

    /** A Task's row (column -> value) at v108. */
    fun task(row: JSONObject): JSONObject {
        row.optString("steps_json").takeIf { it.isNotEmpty() && !row.isNull("steps_json") }
            ?.let { json -> steps(json)?.let { row.put("steps_json", it) } }
        return row
    }

    /** Rewrites the backup document's Tasks in place. */
    fun backup(data: JSONObject) {
        val tasks = data.optJSONArray("tasks") ?: return
        for (i in 0 until tasks.length()) task(tasks.getJSONObject(i))
    }
}
