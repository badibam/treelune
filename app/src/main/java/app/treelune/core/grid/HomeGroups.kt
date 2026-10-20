package app.treelune.core.grid

import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * The home screen's zone groups, changed through the settings service by what writes a whole set
 * (the demo, an imported part): its groups added when they are missing, taken out when no zone
 * stands in them any more.
 *
 * @param run The settings' operations run by the caller, as itself, their data on success
 */
class HomeGroups(private val run: suspend (action: String, params: JSONObject) -> Map<String, Any?>) {

    /** [groups] added at the end of the home screen's, in their order, those already there left; the ones added. */
    suspend fun add(groups: List<String>): List<String> {
        val settings = settings()
        val listed = settings.getJSONArray("zone_groups").let { array -> (0 until array.length()).map { array.getString(it) } }
        val added = groups.distinct().filter { it !in listed }
        if (added.isEmpty()) return added
        write(settings, listed + added)
        return added
    }

    /** [groups] taken out of the home screen's; nothing when none is there. */
    suspend fun remove(groups: Set<String>) {
        if (groups.isEmpty()) return
        val settings = settings()
        val before = settings.getJSONArray("zone_groups")
        val kept = (0 until before.length()).map { before.getString(it) }.filter { it !in groups }
        if (kept.size == before.length()) return
        write(settings, kept)
    }

    private suspend fun settings(): JSONObject {
        val read = run("app_config.get", JSONObject().put("category", AppSettingCategories.MAIN_SCREEN))
        @Suppress("UNCHECKED_CAST")
        return JsonUtils.toJSONObject(read["settings"] as Map<String, Any?>)
    }

    private suspend fun write(settings: JSONObject, groups: List<String>) {
        run("app_config.set", JSONObject()
            .put("category", AppSettingCategories.MAIN_SCREEN)
            .put("settings", JSONObject(settings.toString()).put("zone_groups", JSONArray(groups))))
    }
}
