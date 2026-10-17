package app.treelune.core.terms

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.reading.FieldReading
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.ReferenceKind

/**
 * The field a term gives values of, known without reading a single entry: what a condition
 * compares it as, and what a formula deduces its type from. A constant has none, its type being
 * the one it is compared with; a variable has the one its definition stores; a reading keeps its
 * field's (the average of durations is a duration), and a count is a whole number
 * (FieldReading.COUNT_FIELD).
 */
object TermFields {

    /**
     * The field [term] gives values of; null for a constant, and for a reading computing a
     * formula in each entry, whose type only its variable deduces.
     *
     * @param name What the term is called where it is used, for the errors
     * @param text The shared string of a key, for the errors
     * @param toolFields The fields of a tool instance's entries, by path (ToolFields.filterable),
     *   throwing IllegalStateException when the tool cannot be read
     * @param variableField The field of the variable of an id; null when there is no such variable
     * @throws IllegalArgumentException naming what does not read: a variable that does not
     *   exist, a reading of no tool instance, a tool that cannot be read, a field it has not, a
     *   reduction its type does not take
     */
    suspend fun of(
        term: Term,
        name: String,
        text: (String) -> String,
        toolFields: suspend (String) -> Map<String, FieldDefinition>,
        variableField: suspend (String) -> FieldDefinition?
    ): FieldDefinition? = when (term) {
        is Term.Constant -> null
        is Term.Variable -> variableField(term.id) ?: throw IllegalArgumentException(text("variable_error_not_found").format(term.id))
        is Term.Reading -> reading(term, name, text, toolFields)
    }

    private suspend fun reading(term: Term.Reading, name: String, text: (String) -> String, toolFields: suspend (String) -> Map<String, FieldDefinition>): FieldDefinition? {
        term.selection.problem(text)?.let { throw IllegalArgumentException(text("variable_error_term").format(name) + " " + it) }
        if (term.selection.target.kind != ReferenceKind.TOOL_INSTANCE) {
            throw IllegalArgumentException(text("service_error_reading_source").format(term.selection.target.kind.name))
        }
        val fields = try {
            toolFields(term.selection.target.id!!)
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException(text("variable_error_term").format(name) + " " + (e.message ?: ""))
        }
        if (term.perEntry != null) return null
        val field = term.field?.let { fields[it] ?: throw IllegalArgumentException(text("service_error_filter_unknown_field").format(it, fields.keys.joinToString(", "))) }
        if (term.reduction !in Reduction.forType(field?.type)) {
            throw IllegalArgumentException(text("service_error_reading_reduction_type").format(term.reduction.name, field?.type?.name ?: "-",
                Reduction.forType(field?.type).joinToString(", ") { it.name }))
        }
        return if (term.reduction == Reduction.COUNT) FieldReading.COUNT_FIELD else field
    }
}
