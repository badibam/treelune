package app.treelune.core.terms

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.reading.FieldReading
import app.treelune.core.reading.ReadingGroup
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

    /** What a reading reads from: a tool's entries, one entry, a variable's series. */
    val SOURCES = setOf(ReferenceKind.TOOL_INSTANCE, ReferenceKind.ENTRY, ReferenceKind.VARIABLE)

    /**
     * The field [term] gives values of; null for a constant, and for a reading computing a
     * formula in each entry, whose type only its variable deduces.
     *
     * @param name What the term is called where it is used, for the errors
     * @param text The shared string of a key, for the errors
     * @param targetFields The fields of the entries a reading's target holds, a tool instance's or
     *   one entry's tool's, a variable's series', by path (ToolFields.ofTarget), throwing IllegalStateException when they
     *   cannot be read
     * @param variableField The field of the variable of an id; null when there is no such variable
     * @throws IllegalArgumentException naming what does not read: a variable that does not
     *   exist, a reading of no tool instance, a tool that cannot be read, a field it has not, a
     *   reduction its type does not take
     */
    suspend fun of(
        term: Term,
        name: String,
        text: (String) -> String,
        targetFields: suspend (app.treelune.core.selection.Reference) -> Map<String, FieldDefinition>,
        variableField: suspend (String) -> FieldDefinition?
    ): FieldDefinition? = when (term) {
        is Term.Constant -> null
        is Term.Variable -> variableField(term.id) ?: throw IllegalArgumentException(text("variable_error_not_found").format(term.id))
        is Term.Reading -> reading(term, name, text, targetFields)
    }

    private suspend fun reading(term: Term.Reading, name: String, text: (String) -> String, targetFields: suspend (app.treelune.core.selection.Reference) -> Map<String, FieldDefinition>): FieldDefinition? {
        // A variable's series reads as a tool's entries (VariableSeries), its fields given by targetFields
        term.selection.problem(series = true, text = text)?.let { throw IllegalArgumentException(text("variable_error_term").format(name) + " " + it) }
        if (term.selection.target.kind !in SOURCES) {
            throw IllegalArgumentException(text("service_error_reading_source").format(term.selection.target.kind.name))
        }
        // A formula in each entry reads an entry's fields, which a step of a series has not
        if (term.perEntry != null && term.selection.target.kind == ReferenceKind.VARIABLE) {
            throw IllegalArgumentException(text("variable_error_term").format(name) + " " + text("selection_problem_variable"))
        }
        val fields = try {
            targetFields(term.selection.target)
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException(text("variable_error_term").format(name) + " " + (e.message ?: ""))
        }
        if (term.perEntry != null) {
            if (term.group != null) throw IllegalArgumentException(text("reading_error_group_per_entry"))
            return null
        }
        val field = term.field?.let { fields[it] ?: throw IllegalArgumentException(text("service_error_filter_unknown_field").format(it, fields.keys.joinToString(", "))) }
        if (term.reduction !in Reduction.forField(field)) {
            throw IllegalArgumentException(text("service_error_reading_reduction_type").format(term.reduction.name, field?.type?.name ?: "-",
                Reduction.forField(field).joinToString(", ") { it.name }))
        }
        Term.ifNoneProblem(term.ifNone, field, term.reduction, term.group != null, text)?.let { throw IllegalArgumentException(text("variable_error_term").format(name) + " " + it) }
        val value = if (term.reduction == Reduction.COUNT) FieldReading.COUNT_FIELD else field!!
        val group = term.group ?: return value
        group.problem(fields, value, text)?.let { throw IllegalArgumentException(text("variable_error_term").format(name) + " " + it) }
        return ReadingGroup.resultField(value, group.reduction)
    }
}
