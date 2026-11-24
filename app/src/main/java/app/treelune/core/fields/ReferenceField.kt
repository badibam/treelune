package app.treelune.core.fields

import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import org.json.JSONObject

/**
 * What a REFERENCE field accepts, from its config: `{"target": {"kinds": [...], "tool_instances": [...]}, "multiple"?}`.
 *
 * [kinds] are the kinds of thing a value may designate; [toolInstances], with ENTRY among the
 * kinds, restricts the entries to those of these tool instances ("aliment": the entries of
 * Aliments and Recipes), and the input then lists their entries. Empty, any entry is accepted.
 *
 * A field set [MULTIPLE] holds a list of references, distinct, in the order they were added,
 * without a limit; one without holds a single reference ([referencesOf] reads both).
 */
data class ReferenceTarget(val kinds: Set<ReferenceKind>, val toolInstances: List<String>) {

    /** Whether [reference] is of a kind this field takes. Where an entry lives is checked apart, it needs the database. */
    fun acceptsKind(reference: Reference): Boolean = reference.kind in kinds

    /** Whether an entry of [toolInstanceId] may be designated. */
    fun acceptsEntryOf(toolInstanceId: String): Boolean = toolInstances.isEmpty() || toolInstanceId in toolInstances

    companion object {
        const val TARGET = "target"
        const val KINDS = "kinds"
        const val TOOL_INSTANCES = "tool_instances"
        /** Beside the target in a field's config, not inside it: a change of target narrows what the field takes */
        const val MULTIPLE = "multiple"

        /** Whether a field of [config] holds a list of references. */
        fun isMultiple(config: Map<String, Any>?): Boolean = config?.get(MULTIPLE) == true

        /** The target of a field's [config]; no kind when the config says none, which its validation refuses. */
        fun fromConfig(config: Map<String, Any>?): ReferenceTarget {
            val target = config?.get(TARGET) as? Map<*, *>
            val kinds = (target?.get(KINDS) as? List<*>)
                ?.mapNotNull { name -> ReferenceKind.entries.firstOrNull { it.name == name } }?.toSet()
                ?: emptySet()
            val instances = (target?.get(TOOL_INSTANCES) as? List<*>)?.mapNotNull { referenceOf(it)?.id ?: it as? String } ?: emptyList()
            return ReferenceTarget(kinds, instances)
        }

        /**
         * The reference a stored value holds, as a map (an entry read back) or a JSON object; null
         * when it is none. The tool instances of a target are stored as references too, being the
         * values of a REFERENCE setting.
         */
        fun referenceOf(value: Any?): Reference? {
            val read: (String) -> Any? = when (value) {
                is Map<*, *> -> { key -> value[key] }
                is JSONObject -> { key -> value.opt(key) }
                else -> return null
            }
            fun text(key: String) = (read(key) as? String)?.takeIf { it.isNotEmpty() }
            val kind = ReferenceKind.entries.firstOrNull { it.name == read("kind") } ?: return null
            // A view's row names its view and its row besides its thing
            return runCatching { Reference(kind, text("id"), text("field"), text("view"), text("row")) }.getOrNull()
        }

        /**
         * The references a stored value holds, in their order: the one of a single reference, each
         * of a list (a multiple field's), none for a value that holds none.
         */
        fun referencesOf(value: Any?): List<Reference> = when (value) {
            is List<*> -> value.mapNotNull { referenceOf(it) }
            is org.json.JSONArray -> (0 until value.length()).mapNotNull { referenceOf(value.opt(it)) }
            else -> listOfNotNull(referenceOf(value))
        }
    }
}
