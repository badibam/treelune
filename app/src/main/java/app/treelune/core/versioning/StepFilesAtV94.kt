package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings a Sequence's steps to their v94 form (docs/TOOLS.md, « Session (Sequence) »): a step's
 * images become a term, their fixed value, `{"constant": [ids]}`, beside its sound or video, a
 * term too; a step without images holds none. The files, owned by the tool, do not move.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object StepFilesAtV94 {

    private const val TOOLTYPE = "sequence"

    /** A tool's [config] as it stands at v94, null when nothing in it changes (another tool type, no images). */
    fun toolConfig(tooltype: String, config: String): String? {
        if (tooltype != TOOLTYPE) return null
        val json = JSONObject(config)
        return if (steps(json.optJSONArray("steps"))) json.toString() else null
    }

    /** The steps and blocks of [elements], down every block, their images made terms in place; true when one changed. */
    private fun steps(elements: JSONArray?): Boolean {
        if (elements == null) return false
        var changed = false
        for (i in 0 until elements.length()) {
            val element = elements.optJSONObject(i) ?: continue
            if (steps(element.optJSONArray("steps"))) changed = true
            val images = element.optJSONArray("images") ?: continue
            if (images.length() == 0) element.remove("images") else element.put("images", JSONObject().put("constant", images))
            changed = true
        }
        return changed
    }

    /** A copy the history keeps of a row of [table] (column -> value), as it stands at v94. */
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
