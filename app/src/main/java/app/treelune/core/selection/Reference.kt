package app.treelune.core.selection

import org.json.JSONObject

/** What a reference designates: the app, a zone, a tool or a variable in a zone, an entry of a tool. */
enum class ReferenceKind {
    APP,
    ZONE,
    TOOL_INSTANCE,
    ENTRY,
    VARIABLE;

    /** The kinds reached by going down from this one: what a browser standing here can still lead to. */
    val below: Set<ReferenceKind> get() = when (this) {
        APP -> setOf(ZONE, TOOL_INSTANCE, VARIABLE, ENTRY)
        ZONE -> setOf(TOOL_INSTANCE, VARIABLE, ENTRY)
        TOOL_INSTANCE -> setOf(ENTRY)
        ENTRY, VARIABLE -> emptySet()
    }
}

/**
 * A thing of the app, named by its kind and its id, never by its name: renaming the thing or
 * moving a tool to another zone leaves the reference valid, and whoever shows it reads the name
 * as it is now. A thing deleted since keeps its reference, and shows as deleted. An entry may be
 * designated down to one of its fields (« Séance du 24 › Induction »): the field gone, the
 * designation shows as deleted too (references.names, the one place that says it).
 *
 * Stored as `{"kind": "TOOL_INSTANCE", "id": "…"}`, the id absent for the app, and for an entry's
 * field `{"kind": "ENTRY", "id": "…", "field": "extra.induction"}`.
 *
 * @property id The thing's id; none for the app
 * @property field The path of one of an entry's fields (`data.x`, `extra.x`, ToolFields); none for the whole thing
 */
data class Reference(val kind: ReferenceKind, val id: String?, val field: String? = null) {

    init {
        require((kind == ReferenceKind.APP) == (id == null)) { "a reference to $kind ${if (id == null) "needs" else "takes no"} id" }
        require(field == null || kind == ReferenceKind.ENTRY) { "only an entry is designated down to a field, not a $kind" }
    }

    fun toJson(): JSONObject = JSONObject().put("kind", kind.name).apply {
        id?.let { put("id", it) }
        field?.let { put("field", it) }
    }

    companion object {
        fun fromJson(json: JSONObject) = Reference(
            ReferenceKind.valueOf(json.getString("kind")),
            json.optString("id").takeIf { it.isNotEmpty() },
            json.optString("field").takeIf { it.isNotEmpty() }
        )
    }
}
