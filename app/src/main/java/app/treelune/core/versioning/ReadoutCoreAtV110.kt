package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings a readout's settings to their v110 form: its short form is its core (`core`), which every
 * place shows where room is short, its sentence (`text`), optional, placing it with `{}`. The
 * sentence it had becomes its core, shown everywhere as it was, no sentence beside it.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object ReadoutCoreAtV110 {

    /** A readout's settings (their JSON) at v110, null when they hold a core already. */
    fun settings(json: String): String? {
        val settings = JSONObject(json)
        if (settings.has("core")) return null
        settings.put("core", settings.optString("text"))
        settings.remove("text")
        return settings.toString()
    }

    /** A readout's row (column -> value) at v110. */
    fun readout(row: JSONObject): JSONObject {
        row.optString("settings_json").takeIf { it.isNotEmpty() && !row.isNull("settings_json") }
            ?.let { json -> settings(json)?.let { row.put("settings_json", it) } }
        return row
    }

    /** Rewrites the backup document's readouts in place. */
    fun backup(data: JSONObject) {
        val readouts = data.optJSONArray("readouts") ?: return
        for (i in 0 until readouts.length()) readout(readouts.getJSONObject(i))
    }
}
