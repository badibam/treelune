package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the settings of the NUMERIC fields to their v91 form: their "step" goes. It was meant
 * for + and − buttons of a number's input, never made; nothing read it (a scale's step, the
 * slider's stops, stays).
 *
 * A field's settings are found wherever a field is stored, as an object of type NUMERIC with its
 * "config": a tool's users' fields, a goal's entered criterion, a variable's value, a
 * communication module's fields in a message; and a numeric Tracking tool's "value". Shared by
 * the database migration, the backup import and the history's copies.
 */
object NumericStepAtV91 {

    private const val STEP = "step"

    /** Removes, in place, the step of every NUMERIC field held anywhere in [json]; true when one went. */
    fun walk(json: Any?): Boolean = when (json) {
        is JSONObject -> {
            var changed = false
            if (json.optString("type") == "NUMERIC") json.optJSONObject("config")?.let { changed = it.remove(STEP) != null }
            json.keys().asSequence().toList().forEach { key -> if (walk(json.opt(key))) changed = true }
            changed
        }
        is JSONArray -> (0 until json.length()).map { walk(json.opt(it)) }.any { it }
        else -> false
    }

    /** A tool's [config] as it stands at v91, null when nothing in it changes. */
    fun toolConfig(tooltype: String, config: String): String? {
        val json = JSONObject(config)
        var changed = walk(json)
        // A numeric Tracking tool's value is a field without its type, which its kind gives
        if (tooltype == "tracking" && json.optString("type") == "numeric") json.optJSONObject("value")?.let { if (it.remove(STEP) != null) changed = true }
        return if (changed) json.toString() else null
    }

    /** A JSON text holding fields (a variable's definition, a message) as it stands at v91, null when nothing changes. */
    fun text(json: String): String? {
        val parsed = if (json.trimStart().startsWith("[")) JSONArray(json) else JSONObject(json)
        return if (walk(parsed)) parsed.toString() else null
    }

    /** A message's column at v91, null when nothing changes; one that is not JSON holds no field (DurationUnitsAtV86.messageText). */
    fun messageText(json: String): String? = try {
        text(json)
    } catch (e: org.json.JSONException) {
        null
    }

    /** A copy the history keeps of a row of [table] (column -> value), as it stands at v91. */
    fun historyCopy(table: String, copy: JSONObject): JSONObject {
        when (table) {
            "tool_instances" -> copy.optString("config_json").takeIf { it.isNotEmpty() }?.let { config ->
                toolConfig(copy.optString("tooltype"), config)?.let { copy.put("config_json", it) }
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
                toolConfig(instance.getString("tooltype"), instance.getString("config_json"))?.let { instance.put("config_json", it) }
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
