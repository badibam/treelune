package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the IMAGE fields to their v93 form (docs/DATA.md, « FILE »): the type becomes FILE,
 * whose config says the kinds it takes, IMAGE alone for a field that took images; its `max`
 * stays. The values, lists of ids, do not change.
 *
 * A field is found wherever one is stored, as an object of type IMAGE: a tool's users' fields, a
 * goal's entered criterion, a variable's value, a message's fields. No other stored object bears
 * that type (an image of a message is a segment of type "image"). Shared by the database
 * migration, the backup import and the history's copies.
 */
object FileFieldAtV93 {

    /** Rewrites, in place, every IMAGE field held anywhere in [json]; true when one changed. */
    fun walk(json: Any?): Boolean = when (json) {
        is JSONObject -> {
            var changed = false
            if (json.optString("type") == "IMAGE") {
                val config = json.optJSONObject("config") ?: JSONObject().also { json.put("config", it) }
                config.put("kinds", JSONArray(listOf("IMAGE")))
                json.put("type", "FILE")
                changed = true
            }
            json.keys().asSequence().toList().forEach { key -> if (walk(json.opt(key))) changed = true }
            changed
        }
        is JSONArray -> (0 until json.length()).map { walk(json.opt(it)) }.any { it }
        else -> false
    }

    /** A JSON text holding fields (a tool's config, a variable's definition, a message) as it stands at v93, null when nothing changes. */
    fun text(json: String): String? {
        val parsed = if (json.trimStart().startsWith("[")) JSONArray(json) else JSONObject(json)
        return if (walk(parsed)) parsed.toString() else null
    }

    /** A message's column at v93, null when nothing changes; one that is not JSON holds no field (DurationUnitsAtV86.messageText). */
    fun messageText(json: String): String? = try {
        text(json)
    } catch (e: org.json.JSONException) {
        null
    }

    /** A copy the history keeps of a row of [table] (column -> value), as it stands at v93. */
    fun historyCopy(table: String, copy: JSONObject): JSONObject {
        when (table) {
            "tool_instances" -> copy.optString("config_json").takeIf { it.isNotEmpty() }?.let { config ->
                text(config)?.let { copy.put("config_json", it) }
            }
            "variables" -> copy.optString("definition_json").takeIf { it.isNotEmpty() }?.let { definition ->
                text(definition)?.let { copy.put("definition_json", it) }
            }
            "session_messages" -> DurationUnitsAtV86.MESSAGE_COLUMNS.forEach { column ->
                if (!copy.isNull(column)) copy.optString(column).takeIf { it.isNotEmpty() }?.let { value -> messageText(value)?.let { copy.put(column, it) } }
            }
        }
        return copy
    }

    /** Rewrites the backup document's tools, variables and messages in place. */
    fun backup(data: JSONObject) {
        data.optJSONArray("tool_instances")?.let { instances ->
            for (i in 0 until instances.length()) {
                val instance = instances.getJSONObject(i)
                text(instance.getString("config_json"))?.let { instance.put("config_json", it) }
            }
        }
        data.optJSONArray("variables")?.let { variables ->
            for (i in 0 until variables.length()) {
                val variable = variables.getJSONObject(i)
                text(variable.getString("definition_json"))?.let { variable.put("definition_json", it) }
            }
        }
        data.optJSONArray("session_messages")?.let { messages ->
            for (i in 0 until messages.length()) {
                val message = messages.getJSONObject(i)
                DurationUnitsAtV86.MESSAGE_COLUMNS.forEach { column ->
                    if (!message.isNull(column) && message.has(column)) messageText(message.getString(column))?.let { message.put(column, it) }
                }
            }
        }
    }
}
