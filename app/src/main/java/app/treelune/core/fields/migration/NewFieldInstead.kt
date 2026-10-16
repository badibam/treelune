package app.treelune.core.fields.migration

import org.json.JSONArray
import org.json.JSONObject

/**
 * A change of meaning taken as a new field rather than applied to the recorded values
 * (docs/design/config-change.md): the field as edited goes without its key, so that the service
 * gives it a new one under which nothing recorded is read, and the field as it was stays beside
 * it, its key, its values and its settings kept, renamed so that both read apart.
 */
object NewFieldInstead {

    /**
     * The user's fields [after] edited, with each field of [names] split in two: the edited one,
     * without its key, then the former one from [before], its name given by [keptName] (its name
     * before the change, to the one shown).
     */
    fun apply(before: JSONArray, after: JSONArray, names: List<String>, keptName: (String) -> String): JSONArray {
        val former = (0 until before.length()).map { before.getJSONObject(it) }.associateBy { it.optString("name") }
        val result = JSONArray()
        (0 until after.length()).map { after.getJSONObject(it) }.forEach { field ->
            val name = field.optString("name")
            val was = former[name]
            if (name !in names || was == null) {
                result.put(field)
                return@forEach
            }
            result.put(JSONObject(field.toString()).apply { remove("name") })
            result.put(JSONObject(was.toString()).put("display_name", keptName(was.optString("display_name"))))
        }
        return result
    }
}
