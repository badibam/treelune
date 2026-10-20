package app.treelune.core.history

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * What a step changed in a row, field by field, for the history's screen to show: a column, or a
 * key of the JSON object a column holds (a tool's config, an entry's values), with its value
 * before and after. The row's id and its times are left out, saying nothing of the change.
 */
object RowChanges {

    /** One field changed: [old] null for a field the step gave, [new] null for one it took away. */
    data class Change(val field: String, val old: String?, val new: String?)

    /** The fields that differ between [before] and [after], copies of one row (Rows), in the row's order. */
    fun of(before: Map<String, Any?>, after: Map<String, Any?>): List<Change> =
        (before.keys + after.keys).distinct().filter { it !in LEFT_OUT }.flatMap { column ->
            val old = before[column]
            val new = after[column]
            val oldObject = (old as? String)?.let(::objectOf)
            val newObject = (new as? String)?.let(::objectOf)
            when {
                oldObject != null || newObject != null -> inner(oldObject ?: JSONObject(), newObject ?: JSONObject())
                text(old) != text(new) -> listOf(Change(column, text(old), text(new)))
                else -> emptyList()
            }
        }

    /** The keys of a JSON column that differ, each by its own name. */
    private fun inner(before: JSONObject, after: JSONObject): List<Change> =
        (before.keys().asSequence() + after.keys().asSequence()).distinct().mapNotNull { key ->
            val old = if (before.has(key)) text(before.get(key)) else null
            val new = if (after.has(key)) text(after.get(key)) else null
            if (old == new) null else Change(key, old, new)
        }.toList()

    /** A value as one line of text: a list or an object in its JSON, null for none. */
    private fun text(value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject, is JSONArray -> value.toString()
        is Map<*, *> -> JSONObject(value).toString()
        is List<*> -> JSONArray(value).toString()
        is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    /** [text] read as a JSON object; null when it is none. */
    private fun objectOf(text: String): JSONObject? {
        if (!text.trimStart().startsWith("{")) return null
        return try { JSONTokener(text).nextValue() as? JSONObject } catch (e: org.json.JSONException) { null }
    }

    private val LEFT_OUT = setOf("id", "created_at", "updated_at")
}
