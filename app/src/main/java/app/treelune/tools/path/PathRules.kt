package app.treelune.tools.path

import app.treelune.core.conditions.ConditionJudge
import app.treelune.core.conditions.Conditions
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FilterOperator
import app.treelune.core.reading.FieldReading
import app.treelune.core.reading.ReadingResult
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermReader
import app.treelune.core.views.RowsReading
import org.json.JSONArray
import org.json.JSONObject

/**
 * A rule of a Path judged on one stage (« Les règles »): a Condition of the core, comparisons
 * combined by `all` and `any`, whose sides may read `self`. A reading of the Path's own views
 * reads the rows the replay is computing, in memory (RowsReading); anything else — another tool,
 * a variable, the Path's entries themselves — is read by the core through [external], `self` put
 * in its place first.
 *
 * @param pathId The Path's tool, which a reading names as its target to read its own views — or
 *   `"self"` (Term.SELF_TOOL), as a rule is written before its tool exists
 * @param external A term of the core read at an instant (TermReader)
 * @param calendar The calendar a relative date resolves on, against an instant (TimeResolver.at)
 * @param text The core's strings, [own] the Path's
 */
class PathRules(
    private val pathId: String,
    private val external: suspend (Term, Long) -> TermReader.Read,
    private val calendar: (Long) -> TimeResolver,
    private val text: (String) -> String,
    private val own: (String) -> String
) {

    /** What judging a rule gives. */
    sealed interface Judged {
        /** Holds, does not, or not known (a value missing): not knowing is not holding. */
        data class Answer(val holds: Boolean?) : Judged
        /** The rule cannot be judged, and why: what it reads gone, a value that does not compare. */
        data class Failed(val message: String) : Judged
    }

    /** The rows a rule's readings of the Path read at the moment it is judged. */
    interface Rows {
        fun stages(): List<Map<String, Any?>>
        fun moves(): List<Map<String, Any?>>
        val stageFields: Map<String, FieldDefinition>
    }

    /** [rule] judged on [self] at [at], its readings of the Path reading [rows]. */
    suspend fun judge(rule: JSONObject, self: PathSelf.Of, rows: Rows, at: Long): Judged {
        Conditions.combinationProblem(rule, self.kind.name, text)?.let { return Judged.Failed(it) }
        var failure: Judged.Failed? = null
        val holds = Conditions.holds(rule) { comparison ->
            when (val judged = comparison(comparison, self, rows, at)) {
                is Judged.Failed -> { failure = failure ?: judged; null }
                is Judged.Answer -> judged.holds
            }
        }
        return failure ?: Judged.Answer(holds)
    }

    /** One comparison judged: each side read, compared as the side that has a type. */
    private suspend fun comparison(json: JSONObject, self: PathSelf.Of, rows: Rows, at: Long): Judged = try {
        val op = FilterOperator.of(json.optString(Conditions.OP))
            ?: throw IllegalArgumentException(text("condition_error_op").format(self.kind.name, json.optString(Conditions.OP)))
        val rawRight = json.opt(Conditions.RIGHT)?.takeIf { it != JSONObject.NULL }
        val rights = when (rawRight) {
            null -> emptyList()
            is JSONArray -> (0 until rawRight.length()).map { rawRight.opt(it) }
            else -> listOf(rawRight)
        }
        val sides = (listOf(json.opt(Conditions.LEFT)) + rights).map { side(it, self, rows, at) }
        when {
            // What it reads gone fails; data missing is not known
            sides.any { it is TermReader.Read.Failed && it.broken } -> Judged.Failed((sides.first { it is TermReader.Read.Failed && it.broken } as TermReader.Read.Failed).message)
            sides.any { it is TermReader.Read.Failed } -> Judged.Answer(null)
            else -> {
                val values = sides.map { it as TermReader.Read.Value }
                // Two written values compare when one is a relative date: an instant, unmistakably
                val field = values.firstNotNullOfOrNull { it.field }
                    ?: INSTANT.takeIf { values.any { TimePoint.isRelative(it.value) } }
                    ?: throw IllegalArgumentException(text("trigger_error_condition_constants"))
                // A relative date on the left is resolved here, the judge resolving only the right
                val left = values[0].value.let { v ->
                    if ((field.type == FieldType.DATE || field.type == FieldType.DATETIME) && TimePoint.isRelative(v)) calendar(at).resolve(TimePoint.read(v!!, text), field.type) else v
                }
                Judged.Answer(ConditionJudge.holds(field, op, left, values.drop(1).map { it.value }, calendar(at), text))
            }
        }
    } catch (e: IllegalArgumentException) {
        Judged.Failed(e.message ?: text("message_validation_error_simple"))
    } catch (e: IllegalStateException) {
        Judged.Failed(e.message ?: text("message_validation_error_simple"))
    }

    /** One side read: `self`, a written value, a reading of the Path's views, or a term of the core. */
    private suspend fun side(raw: Any?, self: PathSelf.Of, rows: Rows, at: Long): TermReader.Read {
        val json = raw as? JSONObject ?: throw IllegalArgumentException(text("condition_error_side").format(self.kind.name, raw.toString()))
        if (json.has(Term.SELF)) return PathSelf.value(json, self, own).let { TermReader.Read.Value(it.value, it.field) }
        if (Conditions.fieldOf(JSONObject().put(Conditions.LEFT, json)) != null) throw IllegalArgumentException(own("path_error_rule_field"))
        val written = try { PathSelf.substituted(json, self, own) as JSONObject } catch (e: PathSelf.Missing) { return TermReader.Read.Failed(e.message ?: "") }
        val term = Term.fromJson(written, self.kind.name, text)
        // A written value has no type of its own: it takes the other side's
        if (term is Term.Constant) return TermReader.Read.Value(term.value, null)
        val selection = (term as? Term.Reading)?.selection
        val own = selection != null && selection.target.kind == ReferenceKind.TOOL_INSTANCE && selection.target.id in setOf(pathId, Term.SELF_TOOL) &&
            selection.view in setOf(PathRows.STAGES, PathRows.MOVES)
        if (!own) return external(term, at)
        term as Term.Reading
        if (term.group != null || term.perEntry != null) throw IllegalArgumentException(own("path_error_rule_reading_group"))
        val (lines, fields) = if (term.selection.view == PathRows.STAGES) rows.stages() to rows.stageFields else rows.moves() to PathRows.moveFields()
        return when (val read = RowsReading.read(lines, term.selection, term.field, term.reduction, fields, calendar(at), text)) {
            is ReadingResult.Value -> TermReader.Read.Value(read.value, if (term.field == null) FieldReading.COUNT_FIELD else read.field)
            is ReadingResult.Failure -> TermReader.Read.Failed(text("reading_failure_no_entry").format(read.field ?: ""))
        }
    }

    private companion object {
        /** What two written values compare as when one is a relative date. */
        val INSTANT = FieldDefinition("instant", "instant", null, FieldType.DATETIME, false, null)
    }
}
