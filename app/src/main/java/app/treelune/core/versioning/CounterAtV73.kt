package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import kotlin.math.abs

/**
 * Brings a counter tracking tool to its v73 form: a numeric one, the counter type being gone.
 *
 * Each of its shortcuts becomes one that enters its step as written, 1 when it had none; a
 * counter that could go down (allow_decrement, true when absent) gets for each a twin that enters
 * the opposite, named with a minus sign before it, so the count still goes both ways in a touch.
 * Its unit ("value.unit") becomes the units list and every shortcut's unit, and its decimals are
 * those its steps need (one for a half).
 *
 * Its entries, which recorded no unit, take the counter's, so they keep saying what they counted.
 *
 * Shared by the database migration and the backup import.
 */
object CounterAtV73 {

    /** The sign a decrementing shortcut's name starts with. */
    const val MINUS = "− "

    /** The unit a counter [config] of a [tooltype] tool counts in, if any. */
    fun counterUnit(tooltype: String, config: JSONObject): String? {
        if (!isCounter(tooltype, config)) return null
        return config.optJSONObject("value")?.optString("unit")?.takeIf { it.isNotEmpty() }
    }

    /** [config] of a [tooltype] tool, as it stands at v73. */
    fun config(tooltype: String, config: JSONObject): JSONObject {
        if (!isCounter(tooltype, config)) return config
        val unit = counterUnit(tooltype, config)
        val decrements = if (config.has("allow_decrement")) config.getBoolean("allow_decrement") else true
        val next = JSONObject(config.toString())
        next.put("type", "numeric")
        next.remove("allow_decrement")

        val items = next.optJSONArray("items") ?: JSONArray()
        val shortcuts = JSONArray()
        var decimals = 0
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val step = (if (item.has("value")) item.get("value") as Number else null)?.takeIf { it.toDouble() != 0.0 }?.let { magnitude(it) } ?: 1
            decimals = maxOf(decimals, decimalsOf(step))
            shortcuts.put(shortcut(item, item.getString("name"), step, unit))
            if (decrements) shortcuts.put(shortcut(item, MINUS + item.getString("name"), negated(step), unit))
        }
        next.put("items", shortcuts)

        val value = next.optJSONObject("value") ?: JSONObject()
        value.remove("unit")
        value.put("decimals", maxOf(decimals, value.optInt("decimals", 0)))
        next.put("value", value)
        if (unit != null) next.put("units", JSONArray(listOf(unit)))
        return next
    }

    /** An entry's [data] of a counter that counted in [unit]: with a unit. */
    fun entryData(data: JSONObject, unit: String): JSONObject {
        if (data.optString("unit").isNotEmpty()) return data
        return JSONObject(data.toString()).put("unit", unit)
    }

    /** Rewrites the backup document's tool configs and their entries in place. */
    fun backup(data: JSONObject) {
        val instances = data.optJSONArray("tool_instances") ?: return
        val units = mutableMapOf<String, String>()
        for (i in 0 until instances.length()) {
            val instance = instances.getJSONObject(i)
            val tooltype = instance.getString("tooltype")
            val config = JSONObject(instance.getString("config_json"))
            counterUnit(tooltype, config)?.let { units[instance.getString("id")] = it }
            instance.put("config_json", config(tooltype, config).toString())
        }
        val entries = data.optJSONArray("tool_data") ?: return
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val unit = units[entry.getString("tool_instance_id")] ?: continue
            entry.put("data", entryData(JSONObject(entry.getString("data")), unit).toString())
        }
    }

    private fun isCounter(tooltype: String, config: JSONObject) = tooltype == "tracking" && config.optString("type") == "counter"

    /** [item] under [name] entering [value], its other keys kept. */
    private fun shortcut(item: JSONObject, name: String, value: Number, unit: String?): JSONObject =
        JSONObject(item.toString()).put("name", name).put("value", value).also { if (unit != null) it.put("unit", unit) }

    /** A whole number stays one, so that 1 is written 1 and not 1.0. */
    private fun magnitude(n: Number): Number = if (n is Int || n is Long) abs(n.toLong()) else abs(n.toDouble())

    private fun negated(n: Number): Number = if (n is Int || n is Long) -n.toLong() else -n.toDouble()

    private fun decimalsOf(n: Number): Int = BigDecimal(n.toString()).stripTrailingZeros().scale().coerceAtLeast(0)
}
