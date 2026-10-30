package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.conditions.AppConditionSources
import app.treelune.core.conditions.ConditionCheck
import app.treelune.core.conditions.Conditions
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FilterOperator
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.StringsContext
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermFields
import org.json.JSONArray
import org.json.JSONObject

/**
 * A step's conditions (docs/CORE.md, « Tasks »), under `when`: a list of Conditions
 * (`{"left", "op", "right"}`, right a pair for between, none for absent and present) whose sides
 * are inputs of the step (TaskSteps.Input) — a term, what an earlier step gave, what a trigger
 * gave. Judged once when the step runs, all of them holding, the step runs; otherwise it is
 * skipped and the run goes on. A value missing where the operator needs one (a question left
 * unanswered facing "= 3") is not knowing, and skips the step as a condition that does not hold.
 * A step reading what a skipped step gave is skipped too.
 */
object StepConditions {

    const val WHEN = "when"

    /** One condition, its sides inputs in their stored form. */
    data class Condition(val left: JSONObject, val op: FilterOperator, val right: List<JSONObject>)

    /**
     * The conditions of [step], number [number]; none when it has none.
     *
     * @throws IllegalArgumentException naming what does not read
     */
    fun of(step: JSONObject, number: Int, s: StringsContext): List<Condition> {
        val list = step.opt(WHEN)?.takeIf { it != JSONObject.NULL } ?: return emptyList()
        if (list !is JSONArray) throw IllegalArgumentException(s.shared("task_error_when").format(number))
        return (0 until list.length()).map { index ->
            val label = label(number, index, s)
            val json = list.optJSONObject(index) ?: throw IllegalArgumentException(s.shared("condition_error_side").format(label, list.opt(index).toString()))
            val op = FilterOperator.of(json.optString(Conditions.OP))
                ?: throw IllegalArgumentException(s.shared("condition_error_op").format(label, json.optString(Conditions.OP)))
            fun side(raw: Any?): JSONObject = raw as? JSONObject ?: throw IllegalArgumentException(s.shared("condition_error_side").format(label, raw.toString()))
            val right = when (val raw = json.opt(Conditions.RIGHT)?.takeIf { it != JSONObject.NULL }) {
                null -> emptyList()
                is JSONArray -> (0 until raw.length()).map { side(raw.opt(it)) }
                else -> listOf(side(raw))
            }
            val expected = when (op) {
                FilterOperator.ABSENT, FilterOperator.PRESENT -> 0
                FilterOperator.BETWEEN -> 2
                else -> 1
            }
            if (right.size != expected) throw IllegalArgumentException(s.shared("condition_error_right").format(label, op.key, expected))
            Condition(side(json.opt(Conditions.LEFT)), op, right)
        }
    }

    /** What condition [index] of step [number] is called in an error: « step 2 › condition 1 ». */
    fun label(number: Int, index: Int, s: StringsContext) = s.shared("task_when_label").format(number, index + 1)

    /**
     * The field the value [input] gives is a value of: a field of the entry or a value a trigger
     * gives ([received], [values]), the type of a value an earlier step gave ([given], by step),
     * an entry, a term's field; null for a constant, which is read in the other side's type.
     *
     * @throws IllegalArgumentException when a term does not read, or reads in each entry
     */
    suspend fun fieldOf(input: TaskSteps.Input, given: List<List<TaskSteps.Output>>, received: Map<String, FieldDefinition>?, values: Map<String, FieldDefinition>?,
                        context: Context, s: StringsContext, label: String): FieldDefinition? = when (input) {
        is TaskSteps.Input.Of -> when (val term = input.term) {
            is Term.Constant -> null
            else -> {
                if (term is Term.Reading && term.perEntry != null) throw IllegalArgumentException(s.shared("condition_error_per_entry").format(label))
                val sources = AppConditionSources(context, null)
                TermFields.of(term, label, s::shared, sources::targetFields, sources::variableField)
            }
        }
        is TaskSteps.Input.Received -> when {
            input.value != null -> values?.get(input.value)
            input.field != null -> received?.get(input.field)
            else -> entryField(s)
        }
        is TaskSteps.Input.Result -> if (input.value == null) entryField(s)
            else given.getOrNull(input.step)?.firstOrNull { it.value == input.value }?.let { outputField(it, s) }
    }

    /**
     * Why the conditions of [step], number [number], cannot be stored; null when they can: each
     * side an input checked as any ([inputs], which knows what earlier steps give and what a
     * trigger does), then the types it compares (ConditionCheck.refuseTyped).
     */
    suspend fun refusal(step: JSONObject, number: Int, given: List<List<TaskSteps.Output>>, received: Map<String, FieldDefinition>?, values: Map<String, FieldDefinition>?,
                        context: Context, s: StringsContext, inputs: (JSONObject?, Set<String>?) -> String?): String? {
        val conditions = try {
            of(step, number, s)
        } catch (e: IllegalArgumentException) {
            return e.message
        }
        val check = ConditionCheck(AppConditionSources(context, null), s::shared)
        conditions.forEachIndexed { index, condition ->
            val label = label(number, index, s)
            val sides = listOf(condition.left) + condition.right
            inputs(JSONObject().apply { sides.forEachIndexed { i, side -> put("$label › ${i + 1}", side) } }, null)?.let { return it }
            val read = try {
                sides.map { TaskSteps.input(it, label) { key -> s.shared(key) } }
            } catch (e: IllegalArgumentException) {
                return e.message
            }
            val fields = try {
                read.map { fieldOf(it, given, received, values, context, s, label) }
            } catch (e: IllegalArgumentException) {
                return e.message
            }
            val written = read.mapNotNull { ((it as? TaskSteps.Input.Of)?.term as? Term.Constant)?.value }
            check.refuseTyped(label, condition.op, fields, written)?.let { return it }
        }
        return null
    }

    /**
     * The steps whose result [json] reads, anywhere in it — its inputs, its blocks, its
     * conditions —, by index: a step reading what a skipped one gave is skipped too.
     */
    fun resultsRead(json: Any?): Set<Int> = when (json) {
        is JSONObject -> json.optJSONObject(TaskSteps.RESULT)?.takeIf { it.has(TaskSteps.STEP) }?.let { setOf(it.optInt(TaskSteps.STEP)) }
            ?: json.keys().asSequence().flatMap { resultsRead(json.opt(it)) }.toSet()
        is JSONArray -> (0 until json.length()).flatMap { resultsRead(json.opt(it)) }.toSet()
        else -> emptySet()
    }

    /** An entry, of any tool: what a trigger's entry or a created one is a value of. */
    fun entryField(s: StringsContext) = FieldDefinition(TaskSteps.ENTRY, s.shared("task_output_entry"), null, FieldType.REFERENCE, false,
        mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.ENTRY.name))))

    /** The field a value an API gave is a value of, from its declared type. */
    fun outputField(output: TaskSteps.Output, s: StringsContext): FieldDefinition? {
        val type = when (output.type) {
            app.treelune.core.apis.ApiSettings.NUMBER -> FieldType.NUMERIC
            app.treelune.core.apis.ApiSettings.BOOLEAN -> FieldType.BOOLEAN
            app.treelune.core.apis.ApiSettings.TEXT -> FieldType.TEXT
            app.treelune.core.apis.ApiSettings.FILE -> FieldType.FILE
            else -> return null
        }
        return FieldDefinition(output.value ?: "", output.value ?: s.shared("task_output_entry"), null, type, false, null)
    }
}
