package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the identities to their v78 form, the identity file a public format (docs/IDENTITY_FILE.md):
 * each text and resource that carried the address its producer gave it is marked imported, the
 * address dropped; one without was retouched or written in the app, and is marked so. An identity
 * that has a publication day was imported: its import is dated from its last update, the one
 * moment the database knows.
 *
 * Shared by the database migration and the backup import.
 */
object IdentitiesAtV78 {

    /** The texts of [languagesJson], each marked imported or not by its former address. */
    fun languages(languagesJson: String): String {
        val languages = JSONObject(languagesJson)
        for (code in languages.keys()) {
            val section = languages.getJSONObject(code)
            for (piece in listOf("voice", "mind", "portrait")) section.optJSONObject(piece)?.let(::mark)
            val resources: JSONArray = section.optJSONArray("resources") ?: continue
            for (i in 0 until resources.length()) mark(resources.getJSONObject(i))
        }
        return languages.toString()
    }

    /** When an identity stored with [publishedAt] and [updatedAt] was imported; null for one written in the app. */
    fun importedAt(publishedAt: String?, updatedAt: Long): Long? = updatedAt.takeIf { publishedAt != null }

    private fun mark(text: JSONObject) {
        text.put("imported", !text.isNull("address") && text.has("address"))
        text.remove("address")
    }

    /** The identities of a backup's [data], each in its v78 form. */
    fun backup(data: JSONObject) {
        val identities = data.optJSONArray("identities") ?: return
        for (i in 0 until identities.length()) {
            val identity = identities.getJSONObject(i)
            identity.put("languages_json", languages(identity.getString("languages_json")))
            val publishedAt = if (identity.isNull("published_at")) null else identity.optString("published_at")
            identity.put("imported_at", importedAt(publishedAt, identity.getLong("updated_at")) ?: JSONObject.NULL)
        }
    }
}
