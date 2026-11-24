package app.treelune.core.selection

import org.json.JSONObject

/** What a reference designates: the app, a zone, a tool or a variable in a zone, an entry of a tool, a row of a view. */
enum class ReferenceKind {
    APP,
    ZONE,
    TOOL_INSTANCE,
    ENTRY,
    VARIABLE,
    /** An event of a zone (docs/CORE.md, « Events »), reached through its zone beside its tools */
    EVENT,
    /** A row of a tool's or a variable's view (core/views) that is no entry: a deck's card, a program; not browsed */
    ROW;

    /** The kinds reached by going down from this one: what a browser standing here can still lead to. */
    val below: Set<ReferenceKind> get() = when (this) {
        APP -> setOf(ZONE, TOOL_INSTANCE, VARIABLE, ENTRY, EVENT)
        ZONE -> setOf(TOOL_INSTANCE, VARIABLE, ENTRY, EVENT)
        TOOL_INSTANCE -> setOf(ENTRY)
        ENTRY, VARIABLE, EVENT, ROW -> emptySet()
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
 * field `{"kind": "ENTRY", "id": "…", "field": "extra.induction"}`. A row of a view that is no entry
 * (a deck's card) is `{"kind": "ROW", "id": "<its tool or variable>", "view", "row", "field"?}`:
 * deleted with its thing, or once its view no longer gives the row; a row that is an entry is
 * referenced as the entry.
 *
 * @property id The thing's id; none for the app; for a row, its view's thing
 * @property field The path of one of an entry's or a row's fields (`data.x`, `extra.x`, ToolFields); none for the whole thing
 * @property view A row's view, by its name (Views)
 * @property row A row's id in its view
 */
data class Reference(val kind: ReferenceKind, val id: String?, val field: String? = null, val view: String? = null, val row: String? = null) {

    init {
        require((kind == ReferenceKind.APP) == (id == null)) { "a reference to $kind ${if (id == null) "needs" else "takes no"} id" }
        require(field == null || kind == ReferenceKind.ENTRY || kind == ReferenceKind.ROW) { "only an entry or a row is designated down to a field, not a $kind" }
        require((kind == ReferenceKind.ROW) == (view != null && row != null)) { "a row, and a row alone, names its view and its row" }
    }

    fun toJson(): JSONObject = JSONObject().put("kind", kind.name).apply {
        id?.let { put("id", it) }
        view?.let { put("view", it) }
        row?.let { put("row", it) }
        field?.let { put("field", it) }
    }

    companion object {
        fun fromJson(json: JSONObject) = Reference(
            ReferenceKind.valueOf(json.getString("kind")),
            json.optString("id").takeIf { it.isNotEmpty() },
            json.optString("field").takeIf { it.isNotEmpty() },
            json.optString("view").takeIf { it.isNotEmpty() },
            json.optString("row").takeIf { it.isNotEmpty() }
        )
    }
}
