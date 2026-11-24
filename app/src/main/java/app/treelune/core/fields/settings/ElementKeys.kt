package app.treelune.core.fields.settings

import org.json.JSONObject
import java.text.Normalizer

/**
 * The key the app gives each element of a config's list that has none (ToolTypeContract.completeConfig):
 * made from its name, apart from the keys already taken, then fixed — a key given, by the AI or an
 * import, is kept, so renaming an element keeps what points at it. A goal's criteria, a deck's
 * directions, an agenda's series, a schema's boxes, a session's programs.
 */
object ElementKeys {

    /** [prefix] then [name] without accents, in lowercase, each run of other characters one underscore. */
    fun keyOf(prefix: String, name: String): String = prefix + Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    /**
     * Gives each of [elements] without a value under [key] one, from [nameOf] it, numbered after the
     * first that is taken ("_2", "_3"); the elements are changed in place.
     */
    fun complete(elements: List<JSONObject>, key: String, nameOf: (JSONObject) -> String) {
        val taken = elements.mapNotNull { it.optString(key).takeIf { k -> k.isNotEmpty() } }.toMutableSet()
        elements.filter { it.optString(key).isEmpty() }.forEach { element ->
            val base = nameOf(element)
            var given = base
            var n = 2
            while (given in taken) given = "${base}_${n++}"
            taken.add(given)
            element.put(key, given)
        }
    }

    /** The objects of [array], none when it is absent. */
    fun objects(array: org.json.JSONArray?): List<JSONObject> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}
