package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings a Schema's config to its v122 form: a box's `folded`, a boolean, becomes `fold`, one of
 * three values (docs/design/phone-inbox.md). `true` becomes `folded`, folded at first; `false`
 * becomes `open`, never folded by the app: the form wrote `false` only when the person chose « No »,
 * which the app's own folding to fit did not respect before. A box without it stays without it,
 * read as `fit`, folded only for the schema to fit, as before.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object SchemaFoldAtV122 {

    private const val TOOLTYPE = "schema"
    private const val NODES = "nodes"
    private const val OLD = "folded"
    private const val NEW = "fold"

    /** A tool's [config] as it stands at v122, null when nothing in it changes. */
    fun toolConfig(tooltype: String, config: String): String? {
        if (tooltype != TOOLTYPE) return null
        val json = JSONObject(config)
        val nodes = json.optJSONArray(NODES) ?: return null
        var changed = false
        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: continue
            if (!node.has(OLD)) continue
            val folded = node.opt(OLD)
            node.remove(OLD)
            when (folded) {
                true -> node.put(NEW, "folded")
                false -> node.put(NEW, "open")
            }
            changed = true
        }
        return if (changed) json.toString() else null
    }

    /** A copy the history keeps of a tool's row (column -> value), as it stands at v122. */
    fun historyCopy(copy: JSONObject): JSONObject {
        copy.optString("config_json").takeIf { it.isNotEmpty() }?.let { config ->
            toolConfig(copy.optString("tooltype"), config)?.let { copy.put("config_json", it) }
        }
        return copy
    }

    /** Rewrites the backup document's tools in place. */
    fun backup(data: JSONObject) {
        data.optJSONArray("tool_instances")?.let { instances ->
            for (i in 0 until instances.length()) {
                val instance = instances.getJSONObject(i)
                toolConfig(instance.getString("tooltype"), instance.getString("config_json"))?.let { instance.put("config_json", it) }
            }
        }
    }
}
