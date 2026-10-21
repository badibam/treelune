package app.treelune.core.fields

/**
 * An IMAGE field's value and settings (docs/DATA.md, « IMAGE »): its value is always a list of
 * image ids, in their order, even for one; its config may cap how many it holds.
 */
object ImageField {

    /** The config key of the most images a value holds; absent, no limit. */
    const val MAX = "max"

    /** The most images a field of [config] holds, null for no limit. */
    fun max(config: Map<String, Any>?): Int? = (config?.get(MAX) as? Number)?.toInt()

    /** The ids a stored [value] holds, in their order: a list of strings, as the field stores it. */
    fun ids(value: Any?): List<String> = when (value) {
        is List<*> -> value.map { it as String }
        is org.json.JSONArray -> (0 until value.length()).map { value.getString(it) }
        null, org.json.JSONObject.NULL -> emptyList()
        else -> throw IllegalArgumentException("Not a list of image ids: $value")
    }
}
