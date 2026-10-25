package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings a Sequence's steps to their v95 form (docs/TOOLS.md, « Session (Sequence) »): how a step
 * ends becomes two settings, how long it lasts (`length`: none, fixed, media) and whether the next
 * step then comes by itself (`auto_next`). timed is fixed and auto, timed_then_manual fixed and
 * not, manual none and not, media media and auto; the duration stays.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object StepEndsAtV95 {

    private const val TOOLTYPE = "sequence"

    /** A step's former end, as its length and its auto_next. */
    private val ENDS = mapOf(
        "timed" to ("fixed" to true),
        "timed_then_manual" to ("fixed" to false),
        "manual" to ("none" to false),
        "media" to ("media" to true)
    )

    /** A tool's [config] as it stands at v95, null when nothing in it changes. */
    fun toolConfig(tooltype: String, config: String): String? {
        if (tooltype != TOOLTYPE) return null
        val json = JSONObject(config)
        return if (steps(json.optJSONArray("steps"))) json.toString() else null
    }

    /** The steps of [elements], down every block, their end made a length and an auto_next in place; true when one changed. */
    private fun steps(elements: JSONArray?): Boolean {
        if (elements == null) return false
        var changed = false
        for (i in 0 until elements.length()) {
            val element = elements.optJSONObject(i) ?: continue
            if (steps(element.optJSONArray("steps"))) changed = true
            val (length, autoNext) = ENDS[element.optString("end")] ?: continue
            element.remove("end")
            element.put("length", length).put("auto_next", autoNext)
            changed = true
        }
        return changed
    }

    /** A copy the history keeps of a tool's row (column -> value), as it stands at v95. */
    fun historyCopy(copy: JSONObject): JSONObject {
        copy.optString("config_json").takeIf { it.isNotEmpty() }?.let { config ->
            toolConfig(copy.optString("tooltype"), config)?.let { copy.put("config_json", it) }
        }
        return copy
    }

    /** Rewrites the backup document's tools in place. */
    fun backup(data: JSONObject) {
        data.optJSONArray("tool_instances")?.let { instances ->
            for (i in 0 until instances.length()) {
                val instance = instances.getJSONObject(i)
                toolConfig(instance.getString("tooltype"), instance.getString("config_json"))?.let { instance.put("config_json", it) }
            }
        }
    }
}
