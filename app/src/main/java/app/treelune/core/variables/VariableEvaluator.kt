package app.treelune.core.variables

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.terms.Term
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.reading.FailureReason
import app.treelune.core.reading.FieldReading
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.ReferenceKind

/** A variable as stored: its id, its name, what it is. */
data class StoredVariable(val id: String, val name: String, val zoneId: String, val definition: VariableDefinition)

/** What a variable's value gives at one instant: a value in the stored form of its field, or why not. */
sealed interface VariableValue {
    data class Value(val value: Any, val field: FieldDefinition) : VariableValue
    data class Failed(val causes: List<Cause>) : VariableValue
}

/** What the evaluator reads, from the database for the app, from memory for the tests. */
interface VariableSources {
    suspend fun variable(id: String): StoredVariable?

    /** A term's field reading at [at]: a number (a duration in milliseconds), or why not. */
    suspend fun read(term: Term.Reading, at: Long): Outcome

    /** The entries a term's selection gives at [at], newest first; null when its tool no longer exists. */
    suspend fun entries(selection: EntrySelection, at: Long): List<Map<String, Any?>>?

    /** One entry, for a reference read through; null when it no longer exists. */
    suspend fun entry(id: String): Map<String, Any?>?
}

/**
 * Computes a variable's values at an instant (docs/DATA.md, « Variables »): a constant is its
 * value; a formula reads the variable's terms at that instant, each reading's period relative to
 * it, each term read once for all its values, and another variable's value at the same instant.
 * Nothing is stored, nothing is made up: a term that fails, a division by zero, a variable deleted
 * since or reached again, a value of no such key fail the value, with the causes.
 */
class VariableEvaluator(private val sources: VariableSources) {

    /**
     * Each variable computed once per instant, for this evaluator's life alone (one call, one
     * read of a chart): a share read beside the totals it divides reuses them rather than reading
     * the same entries again. Nothing outlives the evaluator.
     */
    private val computed = mutableMapOf<Triple<String, String, Long>, VariableValue>()

    /** Each term of a variable read once per instant, shared by its values: by variable, term and instant. */
    private val termsRead = mutableMapOf<Triple<String, String, Long>, Outcome>()

    /** The value of [key] of [variable] at [at]. */
    suspend fun evaluate(variable: StoredVariable, key: String, at: Long): VariableValue = evaluate(variable, key, at, emptySet())

    private suspend fun evaluate(variable: StoredVariable, key: String, at: Long, visiting: Set<String>): VariableValue =
        computed[Triple(variable.id, key, at)] ?: compute(variable, key, at, visiting).also { computed[Triple(variable.id, key, at)] = it }

    private suspend fun compute(variable: StoredVariable, key: String, at: Long, visiting: Set<String>): VariableValue {
        val value = variable.definition.value(key) ?: return VariableValue.Failed(listOf(Cause(Cause.UNKNOWN_VALUE, term = "${variable.name}.$key")))
        val outcome = when (val expression = value.expression) {
            is VariableDefinition.Expression.Constant -> Outcome.Value(expression.value)
            is VariableDefinition.Expression.Computed -> when (val parsed = Formula.parse(expression.formula)) {
                is Formula.Parsed.Unreadable -> Outcome.Failed(listOf(Cause(Cause.UNREADABLE, term = variable.name)))
                is Formula.Parsed.Read -> evaluateFormula(parsed.formula, variable, at, visiting + variable.id)
            }
        }
        return when (outcome) {
            is Outcome.Failed -> VariableValue.Failed(outcome.causes)
            is Outcome.Value -> VariableValue.Value(stored(outcome.number, value.field), value.field)
        }
    }

    /** The formula computed on [variable]'s terms, each leaf read as a suspending lookup first. */
    private suspend fun evaluateFormula(formula: Formula, variable: StoredVariable, at: Long, visiting: Set<String>): Outcome {
        val terms = variable.definition.terms
        val leaves = mutableMapOf<Formula, Outcome>()
        for (leaf in leavesOf(formula)) {
            leaves[leaf] = when (leaf) {
                is Formula.Name -> when (val term = terms[leaf.name]) {
                    null -> Outcome.Failed(listOf(Cause(Cause.UNKNOWN_NAME, term = leaf.name)))
                    else -> termsRead[Triple(variable.id, leaf.name, at)] ?: readTerm(term, leaf.name, at, visiting).also { termsRead[Triple(variable.id, leaf.name, at)] = it }
                }
                is Formula.VariableRef -> other(leaf.id, leaf.key, at, visiting, null)
                // The instant the variable is read at, not the clock: read yesterday, it is yesterday
                is Formula.Now -> Outcome.Value(at.toDouble())
                else -> error("not a leaf")
            }
        }
        return formula.evaluate { leaves.getValue(it) }
    }

    /** One term of a variable read at [at]. */
    private suspend fun readTerm(term: Term, name: String, at: Long, visiting: Set<String>): Outcome = when (term) {
        // The service refuses any other constant in a formula: one here was written around it
        is Term.Constant -> Outcome.Value((term.value as? Number)?.toDouble() ?: error("the constant '$name' is not a number"))
        is Term.Variable -> other(term.id, term.value, at, visiting, name)
        is Term.Reading -> if (term.perEntry != null) perEntry(term, name, at) else named(sources.read(term, at), name)
        // The service refuses it: a variable judges nothing
        is Term.Self -> error("the term '$name' reads a thing judged")
    }

    private fun leavesOf(formula: Formula): Set<Formula> = when (formula) {
        is Formula.Name, is Formula.VariableRef, is Formula.Now -> setOf(formula)
        is Formula.Number -> emptySet()
        is Formula.Negate -> leavesOf(formula.operand)
        is Formula.Binary -> leavesOf(formula.left) + leavesOf(formula.right)
        is Formula.Call -> leavesOf(formula.argument)
    }

    /** Another variable's value at the same instant; its causes named by it. */
    private suspend fun other(id: String, key: String, at: Long, visiting: Set<String>, term: String?): Outcome {
        if (id in visiting) return Outcome.Failed(listOf(Cause(Cause.LOOP, term = term)))
        val variable = sources.variable(id) ?: return Outcome.Failed(listOf(Cause(Cause.VARIABLE_DELETED, term = term)))
        return when (val value = evaluate(variable, key, at, visiting)) {
            is VariableValue.Value -> Outcome.Value((value.value as Number).toDouble())
            is VariableValue.Failed -> Outcome.Failed(value.causes.map { it.copy(term = listOfNotNull(variable.name, it.term).joinToString(" › ")) })
        }
    }

    private fun named(outcome: Outcome, term: String): Outcome =
        if (outcome is Outcome.Failed) Outcome.Failed(outcome.causes.map { it.copy(term = it.term ?: term) }) else outcome

    /**
     * A formula computed in each selected entry, then reduced. A name reads a field of the entry
     * (its data, then its extra); `ref.field` reads through a REFERENCE to an entry, one step.
     */
    private suspend fun perEntry(term: Term.Reading, name: String, at: Long): Outcome {
        val formula = when (val parsed = Formula.parse(term.perEntry!!)) {
            is Formula.Parsed.Unreadable -> return Outcome.Failed(listOf(Cause(Cause.UNREADABLE, term = name)))
            is Formula.Parsed.Read -> parsed.formula
        }
        val entries = sources.entries(term.selection, at)
            ?: return Outcome.Failed(listOf(Cause(FailureReason.SOURCE_NOT_FOUND.name, term = name)))

        val numbers = mutableListOf<Double>()
        val causes = mutableListOf<Cause>()
        for (entry in entries) {
            val id = entry["id"] as String
            val leaves = mutableMapOf<Formula, Outcome>()
            for (leaf in leavesOf(formula)) leaves[leaf] = if (leaf is Formula.Now) Outcome.Value(at.toDouble()) else entryValue(entry, id, leaf, name)
            when (val outcome = formula.evaluate { leaves.getValue(it) }) {
                is Outcome.Value -> numbers.add(outcome.number)
                is Outcome.Failed -> causes.addAll(outcome.causes)
            }
        }
        if (causes.isNotEmpty()) {
            // One cause per reason and field, with every entry it concerns
            return Outcome.Failed(causes.groupBy { Triple(it.reason, it.term, it.field) }
                .map { (key, group) -> Cause(key.first, key.second, key.third, group.flatMap { it.entries }) })
        }
        if (numbers.isEmpty()) {
            return if (term.reduction == Reduction.SUM || term.reduction == Reduction.COUNT) Outcome.Value(0.0)
            else Outcome.Failed(listOf(Cause(FailureReason.NO_ENTRY.name, term = name)))
        }
        return Outcome.Value(when (term.reduction) {
            Reduction.SUM -> numbers.sum()
            Reduction.AVERAGE -> numbers.average()
            Reduction.MIN -> numbers.min()
            Reduction.MAX -> numbers.max()
            Reduction.FIRST -> numbers.first()
            Reduction.COUNT -> numbers.size.toDouble()
            // The service refuses a streak computed in each entry: it has no condition there
            Reduction.STREAK -> error("a streak is not computed per entry")
        })
    }

    private suspend fun entryValue(entry: Map<String, Any?>, id: String, leaf: Formula, term: String): Outcome {
        val path = (leaf as? Formula.Name)?.name ?: return Outcome.Failed(listOf(Cause(Cause.UNKNOWN_NAME, term = term)))
        val value = if ('.' in path) {
            val reference = ReferenceTarget.referenceOf(fieldOf(entry, path.substringBefore('.')))
            val target = reference?.takeIf { it.kind == ReferenceKind.ENTRY }?.let { sources.entry(it.id!!) }
                ?: return Outcome.Failed(listOf(Cause(Cause.REFERENCE_BROKEN, term, path.substringBefore('.'), listOf(id))))
            fieldOf(target, path.substringAfter('.'))
        } else fieldOf(entry, path)
        return (value as? Number)?.let { Outcome.Value(it.toDouble()) }
            ?: Outcome.Failed(listOf(Cause(FailureReason.MISSING_VALUE.name, term, path, listOf(id))))
    }

    /** A key of an entry's data, else of its extra. */
    private fun fieldOf(entry: Map<String, Any?>, key: String): Any? =
        FieldReading.valueAt(entry, "data.$key") ?: FieldReading.valueAt(entry, "extra.$key")

    /** [number] in the stored form of [field]: whole milliseconds for a DURATION. */
    private fun stored(number: Double, field: FieldDefinition): Any =
        if (field.type == FieldType.DURATION) Math.round(number) else number
}
