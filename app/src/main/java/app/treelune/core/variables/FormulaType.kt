package app.treelune.core.variables

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType

/**
 * The type and settings a formula's value takes, deduced from what it reads (docs/DATA.md,
 * « Variables »): a reduction keeps its source's, an operation the ones its two sides share
 * (km + km, a duration × 2), a number written in the formula taking the other side's
 * (`(mange - depense) / 7` stays in kcal), and anything else is a bare number (`km / heures(temps)`).
 * The user completes what this does not give (the unit "km/h") and corrects a setting.
 *
 * An instant (`now()`, a date read, DATETIME) enters a formula only to be subtracted from
 * another, which gives a duration (`now() - dernier_repas`, the time since a meal). Anything else
 * done with one has no meaning and is refused ([problem]): an instant is milliseconds since 1970,
 * a number that added, multiplied or turned into hours would give a value nobody meant.
 */
object FormulaType {

    /** A bare number: a NUMERIC with two decimals and no unit. */
    val BARE = Deduced(FieldType.NUMERIC, mapOf("decimals" to 2))

    /** A type and its settings, as a field carries them. */
    data class Deduced(val type: FieldType, val config: Map<String, Any>?)

    /** Why a formula's instants do not compute: [part] is the piece of the formula, as written. */
    sealed interface Problem {
        /** An instant in anything but a difference with another instant. */
        data class InstantMisused(val part: Formula) : Problem
        /** The whole is an instant, which no variable holds. */
        data object InstantResult : Problem
        /** A day or a time of day read, which is no instant and no number. */
        data class NotAnInstant(val part: Formula) : Problem
    }

    /**
     * What is wrong with the formula's instants, or null when it computes.
     *
     * @param leaf The field of a name or a variable the formula reads; null when unknown
     */
    fun problem(formula: Formula, leaf: (Formula) -> FieldDefinition?): Problem? = try {
        if (shape(formula, leaf)?.type == FieldType.DATETIME) Problem.InstantResult else null
    } catch (stop: Stop) {
        stop.problem
    }

    /**
     * The type of a formula [problem] accepts.
     *
     * @param leaf The field of a name or a variable the formula reads, as its term or variable
     *   gives it; null when unknown, which makes the whole a bare number
     */
    fun deduce(formula: Formula, leaf: (Formula) -> FieldDefinition?): Deduced = shape(formula, leaf) ?: BARE

    private class Stop(val problem: Problem) : Exception()

    private fun instant(shape: Deduced?) = shape?.type == FieldType.DATETIME

    /** Null for a number written in the formula, which takes the type of what it meets. */
    private fun shape(node: Formula, leaf: (Formula) -> FieldDefinition?): Deduced? = when (node) {
        is Formula.Number -> null
        is Formula.Now -> Deduced(FieldType.DATETIME, null)
        is Formula.Name, is Formula.VariableRef -> leaf(node)?.let { field ->
            if (field.type == FieldType.DATE || field.type == FieldType.TIME) throw Stop(Problem.NotAnInstant(node))
            Deduced(field.type, field.config)
        } ?: BARE
        is Formula.Negate -> shape(node.operand, leaf).also { if (instant(it)) throw Stop(Problem.InstantMisused(node)) }
        is Formula.Call -> BARE.also { if (instant(shape(node.argument, leaf))) throw Stop(Problem.InstantMisused(node)) }
        is Formula.Binary -> {
            val left = shape(node.left, leaf)
            val right = shape(node.right, leaf)
            when {
                // The time between two instants
                instant(left) && instant(right) && node.op == Formula.Operator.MINUS -> Deduced(FieldType.DURATION, null)
                instant(left) || instant(right) -> throw Stop(Problem.InstantMisused(node))
                left == null -> right
                right == null -> left
                node.op == Formula.Operator.PLUS || node.op == Formula.Operator.MINUS -> if (left == right) left else BARE
                // A duration by a duration is a ratio; anything else multiplied by its own kind has no field type
                else -> BARE
            }
        }
    }
}
