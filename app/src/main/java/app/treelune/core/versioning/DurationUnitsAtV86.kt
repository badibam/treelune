package app.treelune.core.versioning

import app.treelune.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the settings of the DURATION fields to their v86 form: the precision (the smallest unit)
 * and the form (several units from the hour, or the precision alone) become one setting, the
 * units from the largest to the smallest (DurationUnits), each field shown as before:
 *
 * - several units: from the hour down to the precision, « heures minutes » for minutes; days
 *   alone stay days alone;
 * - one unit: the precision alone.
 *
 * The millisecond leaves the units: a field set to it goes to the second, logged.
 *
 * A field's settings are found wherever a field is stored, as an object of type DURATION with
 * its "config": a tool's users' fields, a variable's value, a communication module's fields in a
 * message; and a Tracking tool's "value", whose type its kind gives. Shared by the database
 * migration and the backup import.
 */
object DurationUnitsAtV86 {

    private val UNITS = listOf("DAY", "HOUR", "MINUTE", "SECOND")

    /** The v86 units of a field's settings [config], or null when it names neither a precision nor a form. */
    fun units(config: JSONObject): String? {
        if (!config.has("precision") && !config.has("form")) return null
        var precision = config.optString("precision").ifEmpty { "MINUTE" }
        if (precision == "MILLISECOND") {
            LogManager.database("v86: a duration in milliseconds is now shown to the second", "INFO")
            precision = "SECOND"
        }
        require(precision in UNITS) { "unknown duration precision $precision" }
        return when (val form = config.optString("form").ifEmpty { "COMPOSED" }) {
            "SINGLE" -> precision
            "COMPOSED" -> if (precision == "DAY") "DAY" else UNITS.subList(UNITS.indexOf("HOUR"), UNITS.indexOf(precision) + 1).joinToString("_")
            else -> throw IllegalArgumentException("unknown duration form $form")
        }
    }

    /** [config] with its units in place of its precision and its form; true when it changed. */
    private fun rewrite(config: JSONObject): Boolean {
        val units = units(config) ?: return false
        config.remove("precision")
        config.remove("form")
        config.put("units", units)
        return true
    }

    /** Rewrites, in place, the settings of every DURATION field held anywhere in [json]; true when one changed. */
    fun walk(json: Any?): Boolean = when (json) {
        is JSONObject -> {
            var changed = false
            if (json.optString("type") == "DURATION") json.optJSONObject("config")?.let { changed = rewrite(it) }
            json.keys().asSequence().toList().forEach { key -> if (walk(json.opt(key))) changed = true }
            changed
        }
        is JSONArray -> (0 until json.length()).map { walk(json.opt(it)) }.any { it }
        else -> false
    }

    /** A tool's [config] as it stands at v86, null when nothing in it changes. */
    fun toolConfig(tooltype: String, config: String): String? {
        val json = JSONObject(config)
        var changed = walk(json)
        // A Tracking tool's value is a field without its type, which its kind gives
        if (tooltype == "tracking") json.optJSONObject("value")?.let { if (rewrite(it)) changed = true }
        return if (changed) json.toString() else null
    }

    /** A JSON text holding fields (a variable's definition, a message) as it stands at v86, null when nothing changes. */
    fun text(json: String): String? {
        val parsed = if (json.trimStart().startsWith("[")) JSONArray(json) else JSONObject(json)
        return if (walk(parsed)) parsed.toString() else null
    }

    /** The columns of session_messages that hold a communication module's fields. */
    val MESSAGE_COLUMNS = listOf("ai_message_json", "ai_message_parsed_json")

    /**
     * A message's column as it stands at v86, null when nothing changes. The column keeps the
     * model's answer as it came: one that is not JSON (a sentence before it, a code fence, cut
     * short) was never read as a message, holds no field, and is left as it is — it would
     * otherwise fail the migration of its row, and the whole import of a backup.
     */
    fun messageText(json: String): String? = try {
        text(json)
    } catch (e: org.json.JSONException) {
        null
    }

    /** A copy the history keeps of a row of [table] (column -> value), as it stands at v86. */
    fun historyCopy(table: String, copy: JSONObject): JSONObject {
        when (table) {
            "tool_instances" -> copy.optString("config_json").takeIf { it.isNotEmpty() }?.let { config ->
                toolConfig(copy.optString("tooltype"), config)?.let { copy.put("config_json", it) }
            }
            "variables" -> copy.optString("definition_json").takeIf { it.isNotEmpty() }?.let { definition ->
                text(definition)?.let { copy.put("definition_json", it) }
            }
            "session_messages" -> MESSAGE_COLUMNS.forEach { column ->
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
                MESSAGE_COLUMNS.forEach { column ->
                    if (!message.isNull(column) && message.has(column)) messageText(message.getString(column))?.let { message.put(column, it) }
                }
            }
        }
    }
}
