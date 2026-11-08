package app.treelune.core.versioning

import app.treelune.core.apis.ApiSettings
import org.json.JSONObject

/**
 * Brings an API's config to its v114 form: an API says what it is (`kind`), a web service or a
 * recipe of the user's GitHub repository (docs/design/github-worker.md). Every API before v114 is
 * a web service.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object ApiKindAtV114 {

    /** An API's config (its JSON) at v114, null when it says its kind already. */
    fun config(json: String): String? {
        val config = JSONObject(json)
        if (config.has(ApiSettings.KIND)) return null
        return config.put(ApiSettings.KIND, ApiSettings.WEB).toString()
    }

    /** An API's row (column -> value) at v114. */
    fun api(row: JSONObject): JSONObject {
        row.optString("config_json").takeIf { it.isNotEmpty() && !row.isNull("config_json") }
            ?.let { json -> config(json)?.let { row.put("config_json", it) } }
        return row
    }

    /** Rewrites the backup document's APIs in place. */
    fun backup(data: JSONObject) {
        val apis = data.optJSONArray("apis") ?: return
        for (i in 0 until apis.length()) api(apis.getJSONObject(i))
    }
}
