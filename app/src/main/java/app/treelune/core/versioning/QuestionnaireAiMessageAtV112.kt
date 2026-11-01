package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings a Questionnaire's config to its v112 form: `ai_message`, the text its « With the AI »
 * button opened a chat with, is gone with the button — a routine beside the tool, its message
 * written with pointers, opens the same conversation from its tile.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object QuestionnaireAiMessageAtV112 {

    private const val TOOLTYPE = "questionnaire"
    private const val KEY = "ai_message"

    /** A tool's [config] as it stands at v112, null when nothing in it changes. */
    fun toolConfig(tooltype: String, config: String): String? {
        if (tooltype != TOOLTYPE) return null
        val json = JSONObject(config)
        if (!json.has(KEY)) return null
        json.remove(KEY)
        return json.toString()
    }

    /** A copy the history keeps of a tool's row (column -> value), as it stands at v112. */
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
