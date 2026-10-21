package app.treelune.core.history

import android.content.Context
import app.treelune.core.config.AppSettings
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.secrets.SecretSettings
import app.treelune.core.tools.ToolTypeManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The parts of a row the history leaves to the app (docs/DATA.md, « History of writes »): what the app alone writes, and the secrets. An undo neither looks at them, to tell
 * whether the row is still as the step left it, nor changes them: it puts the rest back and keeps
 * them as they are now. A secret is besides never copied.
 *
 * A part is a column, or a key of the JSON object a column holds (Part).
 */
internal class Kept(private val context: Context, private val database: AppDatabase) {

    /** A column, or the key [key] of the JSON object the column holds. */
    data class Part(val column: String, val key: String? = null)

    /** The parts of [row], a row of [table], that the app alone writes, secrets included. */
    suspend fun of(table: CoveredTable, row: JSONObject): List<Part> = TIMES + when (table) {
        CoveredTable.TOOL_DATA -> entryParts(row)
        CoveredTable.AI_SESSIONS -> SESSION_COLUMNS.map { Part(it) }
        CoveredTable.MCP_CLIENTS -> listOf(Part("secret_hash"), Part("last_used_at"))
        CoveredTable.APP_SETTINGS -> settingsParts(row.getString("category"), secrets = false)
        else -> emptyList()
    }

    /** The parts of [row] never copied: its secrets. */
    fun secrets(table: CoveredTable, row: JSONObject): List<Part> = when (table) {
        CoveredTable.MCP_CLIENTS -> listOf(Part("secret_hash"))
        CoveredTable.APP_SETTINGS -> settingsParts(row.getString("category"), secrets = true)
        else -> emptyList()
    }

    /** [row] without [parts]. */
    fun without(row: JSONObject, parts: List<Part>): JSONObject {
        val out = JSONObject(row.toString())
        parts.filter { it.key == null }.forEach { out.remove(it.column) }
        parts.filter { it.key != null }.groupBy { it.column }.forEach { (column, keys) ->
            val text = out.opt(column) as? String ?: return@forEach
            val inner = JSONObject(text)
            keys.forEach { inner.remove(it.key) }
            out.put(column, inner.toString())
        }
        return out
    }

    /** [copy] with each of [parts] as [current] holds it: what an undo writes over [current]. */
    fun over(copy: JSONObject, current: JSONObject, parts: List<Part>): JSONObject {
        val out = JSONObject(copy.toString())
        parts.filter { it.key == null }.forEach { part ->
            if (current.has(part.column)) out.put(part.column, current.get(part.column)) else out.remove(part.column)
        }
        parts.filter { it.key != null }.groupBy { it.column }.forEach { (column, keys) ->
            val now = (current.opt(column) as? String)?.let { JSONObject(it) } ?: JSONObject()
            val inner = (out.opt(column) as? String)?.let { JSONObject(it) } ?: JSONObject()
            keys.forEach { part -> if (now.has(part.key!!)) inner.put(part.key, now.get(part.key)) else inner.remove(part.key) }
            out.put(column, inner.toString())
        }
        return out
    }

    /**
     * Whether [a] and [b] hold the same values, a column holding a JSON object or list compared by
     * what it holds rather than by its text: the order of its keys says nothing.
     */
    fun same(a: JSONObject, b: JSONObject): Boolean =
        a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { sameValue(a.get(it), b.get(it)) }

    private fun sameValue(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> same(a, b)
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { sameValue(a.get(it), b.get(it)) }
        a is String && b is String -> a == b || run {
            val pa = parsed(a) ?: return@run false
            val pb = parsed(b) ?: return@run false
            sameValue(pa, pb)
        }
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }

    /** [text] read as a JSON object or list; null when it is neither. */
    private fun parsed(text: String): Any? {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        return try { JSONTokener(text).nextValue() } catch (e: JSONException) { null }
    }

    /** An entry's fields its tool type marks as the app's to write, and the notes the app keeps on it. */
    private suspend fun entryParts(row: JSONObject): List<Part> {
        val toolType = ToolTypeManager.getToolType(row.getString("tooltype"))
            ?: throw IllegalStateException("entry ${row.getString("id")}: tool type ${row.getString("tooltype")} unknown")
        // Its declaration depends on its tool's config; its tool gone, an entry's declaration reads an empty one
        val config = Rows.read(database, CoveredTable.TOOL_INSTANCES, row.getString("tool_instance_id"))
            ?.let { JSONObject(it.getString("config_json")) } ?: JSONObject()
        val declared = toolType.getEntryFields(config, context)
        return declared.data.filter { it.systemWritten }.map { Part("data", it.definition.name) } +
            declared.state.filter { it.bookkeeping }.map { Part("state", it.definition.name) }
    }

    /** A category's settings the app alone writes, or its secrets alone when [secrets]. */
    private fun settingsParts(category: String, secrets: Boolean): List<Part> {
        val nodes = AppSettings.nodes(category, context)
        val names = SecretSettings.names(nodes) + if (secrets) emptyList() else
            nodes.filterIsInstance<SettingNode.Field>().filter { it.systemWritten }.map { it.definition.name }
        return names.map { Part("settings", it) }
    }

    private companion object {
        /** When a row was made and last changed: the app's to write, in every table that has them. */
        val TIMES = listOf(Part("created_at"), Part("updated_at"))

        /** What a session's run writes on it: of a routine's message, only its name and settings are the user's. */
        val SESSION_COLUMNS = listOf(
            "phase", "total_roundtrips", "last_event_time", "last_user_interaction_time", "scheduled_execution_time",
            "provider_session_id", "last_activity", "is_active", "end_reason", "seen_at", "summary", "app_state_snapshot"
        )
    }
}
