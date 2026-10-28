package app.treelune.tools.tracking

import org.json.JSONArray
import org.json.JSONObject

/**
 * A shortcut of a tracking tool: a name given to the entries it creates, for a numeric tool the
 * value it enters and its unit, and values of the user's fields, by name ("Salmon" designating
 * the salmon's card): its entries take them, a field it leaves out its default value.
 */
data class TrackingShortcut(
    val name: String,
    val value: Number? = null,
    val unit: String? = null,
    val extra: Map<String, Any?> = emptyMap()
)

/**
 * What a tracking tool's config says beyond its field settings: its shortcuts and its units.
 * Read the same way by every screen.
 */
object TrackingConfig {

    fun shortcuts(config: JSONObject): List<TrackingShortcut> {
        val items = config.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            TrackingShortcut(
                name = item.getString("name"),
                value = if (item.has("value")) item.get("value") as Number else null,
                unit = item.optString("unit").takeIf { it.isNotEmpty() },
                extra = item.optJSONObject(EXTRA)?.let { app.treelune.core.utils.JsonUtils.toMap(it) }.orEmpty()
            )
        }
    }

    /** Where a shortcut holds the values of the user's fields its entries take. */
    const val EXTRA = "extra"

    /** The units a numeric value can be in, in the order the config lists them. */
    fun units(config: JSONObject): List<String> {
        val units = config.optJSONArray("units") ?: return emptyList()
        return (0 until units.length()).map { units.getString(it) }
    }

    /** [config] with [shortcut] appended to its shortcuts. */
    fun withShortcut(config: JSONObject, shortcut: TrackingShortcut): JSONObject {
        val next = JSONObject(config.toString())
        val items = next.optJSONArray("items") ?: JSONArray()
        items.put(JSONObject().apply {
            put("name", shortcut.name)
            shortcut.value?.let { put("value", it) }
            shortcut.unit?.let { put("unit", it) }
            if (shortcut.extra.isNotEmpty()) put(EXTRA, app.treelune.core.utils.JsonUtils.toJSONObject(shortcut.extra))
        })
        next.put("items", items)
        return next
    }

    /**
     * The data of an entry holding [value], and for a numeric tool [unit]: what a shortcut or
     * the entry dialog writes. An occurrence has no value, and a timer none until it stops.
     */
    fun entryData(value: Any?, unit: String?): JSONObject = JSONObject().apply {
        value?.let { put("value", if (it is List<*>) JSONArray(it) else it) }
        unit?.let { put("unit", it) }
    }
}
