package app.treelune.core.fields

import app.treelune.core.files.FileKind

/**
 * A FILE field's value and settings (docs/DATA.md, « FILE »): its value is always a list of file
 * ids, in their order, even for one; its config says the kinds of file it takes and may cap how
 * many it holds.
 */
object FileField {

    /** The config key of the kinds it takes, a list of FileKind names, at least one. */
    const val KINDS = "kinds"

    /** The config key of the most files a value holds; absent, no limit. */
    const val MAX = "max"

    /** The kinds a field may take: those the field's input knows how to add and show. */
    val FIELD_KINDS = listOf(FileKind.IMAGE, FileKind.AUDIO, FileKind.VIDEO, FileKind.TEXT)

    /** The kinds a field of [config] takes; none when its config does not say. */
    fun kinds(config: Map<String, Any>?): List<FileKind> =
        (config?.get(KINDS) as? List<*>).orEmpty().mapNotNull { name -> FileKind.entries.firstOrNull { it.name == name } }

    /** The config of a field taking [kinds], at most [max] files. */
    fun config(kinds: List<FileKind>, max: Int? = null): Map<String, Any> =
        listOfNotNull(KINDS to kinds.map { it.name }, max?.let { MAX to it }).toMap()

    /** The most files a field of [config] holds, null for no limit. */
    fun max(config: Map<String, Any>?): Int? = (config?.get(MAX) as? Number)?.toInt()

    /** The ids a stored [value] holds, in their order: a list of strings, as the field stores it. */
    fun ids(value: Any?): List<String> = when (value) {
        is List<*> -> value.map { it as String }
        is org.json.JSONArray -> (0 until value.length()).map { value.getString(it) }
        null, org.json.JSONObject.NULL -> emptyList()
        else -> throw IllegalArgumentException("Not a list of file ids: $value")
    }
}
