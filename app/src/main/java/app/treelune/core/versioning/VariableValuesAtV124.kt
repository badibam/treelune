package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings what names a variable to its v124 form (docs/design/phone-inbox.md, « Une variable à
 * plusieurs valeurs »): a variable is a list of values, each with a key, and whatever reads one
 * names that key.
 *
 * - A variable's definition: `{"kind": "CONSTANT", "value", "field"}` becomes
 *   `{"values": [{"key": "value", "constant", "field"}]}`; `{"kind": "FORMULA", "formula",
 *   "terms", "field"?}` becomes `{"terms", "values": [{"key": "value", "formula", "field"?}]}`;
 *   its series kept. Its series' value thus stays under `data.value`: what reads it is unchanged.
 * - A formula's `{var:ID}` becomes `{var:ID.value}`, in a variable's values and in a readout's gauge.
 * - A term `{"variable": ID}`, an object holding that key alone, wherever it is stored, gets
 *   `"value": "value"`.
 *
 * Frozen: the names are those of v124. Shared by the database migration, the backup import and
 * the history's copies.
 */
object VariableValuesAtV124 {

    private const val FIRST_KEY = "value"
    private val REF = Regex("""\{var:([^}.]+)\}""")

    /** [formula] with each variable it names given its first value's key. */
    fun formula(formula: String): String = REF.replace(formula) { "{var:${it.groupValues[1]}.$FIRST_KEY}" }

    /** A variable's definition at v124, null when it already is. */
    fun definition(json: String): String? {
        val before = JSONObject(json)
        if (before.has("values")) return null
        val value = JSONObject().put("key", FIRST_KEY)
        before.optJSONObject("field")?.let { value.put("field", it) }
        val after = JSONObject()
        when (before.optString("kind")) {
            "CONSTANT" -> value.put("constant", before.opt("value"))
            "FORMULA" -> {
                value.put("formula", formula(before.optString("formula")))
                before.optJSONObject("terms")?.let { after.put("terms", it) }
            }
            else -> return null
        }
        after.put("values", JSONArray().put(value))
        before.optJSONObject("series")?.let { after.put("series", it) }
        walk(after)
        return after.toString()
    }

    /** [json] walked whole, in place: each variable term given its value, each gauge's formula its refs' keys. Whether anything changed. */
    private fun walk(json: Any?): Boolean {
        var changed = false
        when (json) {
            is JSONObject -> {
                if (json.length() == 1 && json.optString("variable").isNotEmpty()) {
                    json.put("value", FIRST_KEY)
                    return true
                }
                for (name in json.keys().asSequence().toList()) {
                    val child = json.opt(name)
                    if (name == "gauge" && child is String && REF.containsMatchIn(child)) {
                        json.put(name, formula(child))
                        changed = true
                    } else changed = walk(child) || changed
                }
            }
            is JSONArray -> for (i in 0 until json.length()) changed = walk(json.opt(i)) || changed
        }
        return changed
    }

    /** A JSON column that may hold variable terms, null when nothing changed. */
    fun json(json: String): String? {
        val value = if (json.trimStart().startsWith("[")) JSONArray(json) else JSONObject(json)
        return if (walk(value)) value.toString() else null
    }

    /** The JSON columns of each table that may hold a variable term, a variable's definition apart. */
    val COLUMNS = mapOf(
        "tool_instances" to listOf("config_json"),
        "tasks" to listOf("steps_json", "triggers_json"),
        "routines" to listOf("triggers_json"),
        "readouts" to listOf("settings_json")
    )

    /** A row (column -> value) of [table] at v124. */
    fun row(table: String, row: JSONObject): JSONObject {
        if (table == "variables") {
            row.optString("definition_json").takeIf { it.isNotEmpty() && !row.isNull("definition_json") }?.let { d -> definition(d)?.let { row.put("definition_json", it) } }
            return row
        }
        COLUMNS[table]?.forEach { column ->
            row.optString(column).takeIf { it.isNotEmpty() && !row.isNull(column) }?.let { j -> json(j)?.let { row.put(column, it) } }
        }
        return row
    }

    /** Rewrites the backup document's tables in place. */
    fun backup(data: JSONObject) {
        (COLUMNS.keys + "variables").forEach { table ->
            val rows = data.optJSONArray(table) ?: return@forEach
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { row(table, it) }
        }
    }
}
