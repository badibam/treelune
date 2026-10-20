package app.treelune.core.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * The ids a value holds, read without knowing its form (docs/design/share.md, « Les liens »): an
 * id stands as a whole word, a run of `[A-Za-z0-9_-]`, anywhere in a text. The value is walked as
 * JSON, and a text that is itself a JSON object or list (a config, an entry's fields, kept in a
 * column as a string) is walked in turn, so that no escape of a level (`\n` before an id) glues
 * a letter to it.
 */
object IdWords {

    private val WORD = Regex("[A-Za-z0-9_-]+")

    /** Every word of [value]: a JSONObject, a JSONArray, a text or a plain value. */
    fun words(value: Any?): Sequence<String> = when (value) {
        // A key may be an id too: a map from a tool to its place
        is JSONObject -> value.keys().asSequence().toList().asSequence().flatMap { key -> WORD.findAll(key).map { it.value } + words(value.get(key)) }
        is JSONArray -> (0 until value.length()).asSequence().flatMap { words(value.get(it)) }
        is String -> nested(value)?.let { words(it) } ?: WORD.findAll(value).map { it.value }
        else -> emptySequence()
    }

    /**
     * [value] with each word [ids] names replaced by its new id: a JSONObject or JSONArray changed
     * in place, a text given back changed. A text holding JSON is written again only when an id
     * in it changed.
     */
    fun replace(value: Any?, ids: Map<String, String>): Any? = when (value) {
        is JSONObject -> value.apply {
            keys().asSequence().toList().forEach { key ->
                val replaced = replace(get(key), ids)
                val newKey = WORD.replace(key) { ids[it.value] ?: it.value }
                if (newKey != key) remove(key)
                put(newKey, replaced)
            }
        }
        is JSONArray -> value.apply { (0 until length()).forEach { put(it, replace(get(it), ids)) } }
        is String -> nested(value)?.let { json -> if (words(json).any { it in ids }) replace(json, ids).toString() else value }
            ?: WORD.replace(value) { ids[it.value] ?: it.value }
        else -> value
    }

    /** [text] read as a JSON object or list, null when it is none. */
    private fun nested(text: String): Any? {
        val trimmed = text.trim()
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed)
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> null
            }
        } catch (e: org.json.JSONException) {
            null
        }
    }
}
