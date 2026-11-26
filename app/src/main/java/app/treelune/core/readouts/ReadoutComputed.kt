package app.treelune.core.readouts

import android.content.Context
import app.treelune.core.conditions.Condition
import app.treelune.core.conditions.ConditionJudge
import app.treelune.core.conditions.Conditions
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.formatValue
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.Strings
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermReader
import app.treelune.core.variables.Cause
import app.treelune.core.variables.Formula
import app.treelune.core.variables.Outcome
import org.json.JSONArray
import org.json.JSONObject

/**
 * A readout computed (docs/BRICKS.md): its values read once, its core and its sentence written
 * with them, its gauge, its colour. The same for a readout of the app (ReadoutService) and a
 * readout a tool computes on one of its things (a Path's stage, read with `self`): only how a
 * value is read differs, which its caller gives.
 *
 * @property gauge The ratio its gauge shows, bounded full and empty; null without a gauge or when it fails
 * @property gaugeFailure Why its gauge computes nothing
 * @property failures Each value that gave nothing: its name, why, and whether what it reads is broken
 */
data class ReadoutComputed(
    val core: String,
    val text: String,
    val color: String?,
    val signal: Boolean,
    val gauge: Double?,
    val gaugeFailure: String?,
    val failures: List<Map<String, Any?>>
) {
    /** As readouts.read gives it. */
    fun toMap(): Map<String, Any> = buildMap {
        put("core", core)
        put("text", text)
        color?.let { put("color", it) }
        put("signal", signal)
        gauge?.let { put("gauge", it) }
        gaugeFailure?.let { put("gauge_failure", it) }
        put("failures", failures)
    }

    companion object {
        /** A value written in a sentence: `{name}`, a name a formula can write. */
        val PLACEHOLDER = Regex("\\{([\\p{L}_][\\p{L}\\p{N}_]*)\\}")

        /**
         * The readout [settings] describe, computed at [at]; [read] reads one of its values, its
         * stored term and its name. [label] names the readout in what a condition says.
         */
        suspend fun of(settings: JSONObject, at: Long, label: String, context: Context, read: suspend (JSONObject, String) -> TermReader.Read): ReadoutComputed {
            val s = Strings.`for`(context = context)
            val values = settings.optJSONArray(ReadoutSettings.VALUES) ?: JSONArray()

            // Each value read once, kept with the field it is a value of
            val got = mutableMapOf<String, Pair<Any?, FieldDefinition?>>()
            val failures = mutableListOf<Map<String, Any?>>()
            for (index in 0 until values.length()) {
                val value = values.getJSONObject(index)
                val name = value.getString(ReadoutSettings.NAME)
                when (val one = try { read(value.getJSONObject(ReadoutSettings.VALUE), name) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: IllegalStateException) { TermReader.Read.Failed(e.message ?: "", broken = true) } catch (e: IllegalArgumentException) { TermReader.Read.Failed(e.message ?: "", broken = true) }) {
                    is TermReader.Read.Value -> got[name] = one.value to one.field
                    is TermReader.Read.Failed -> {
                        got[name] = null to null
                        failures += mapOf("name" to name, "message" to one.message, "broken" to one.broken)
                    }
                }
            }

            // Its core and its sentence, each value as its field shows it, one that gave nothing as « ? »;
            // the sentence with its core in its place, the core alone without one
            fun written(template: String) = PLACEHOLDER.replace(template) { match ->
                val (value, field) = got[match.groupValues[1]] ?: (null to null)
                if (value == null) s.shared("readout_value_missing") else field?.formatValue(value, context) ?: value.toString()
            }
            val core = written(settings.optString(ReadoutSettings.CORE))
            val text = settings.optString(ReadoutSettings.TEXT).takeIf { it.isNotBlank() }?.let { written(it).replace(ReadoutSettings.CORE_PLACE, core) } ?: core

            // Its gauge, the ratio its formula gives bounded full and empty; one that computes nothing says why
            val gauge = settings.optString(ReadoutSettings.GAUGE).ifEmpty { null }?.let { gaugeOf(it, got, at) }

            // The first colour whose condition holds; one not knowing is not holding
            val colors = settings.optJSONArray(ReadoutSettings.COLORS) ?: JSONArray()
            val resolver = TimeResolver.at(at)
            var color: String? = null
            var signal = false
            for (index in 0 until colors.length()) {
                val line = colors.getJSONObject(index)
                // A combination (all, any) judged comparison by comparison
                val holds = Conditions.holds(line.getJSONObject(ReadoutSettings.WHEN)) { comparison ->
                    holds(Condition.fromJson(comparison, label) { s.shared(it) }, got, resolver) { s.shared(it) }
                }
                if (holds != true) continue
                color = line.getString(ReadoutSettings.COLOR)
                signal = line.optBoolean(ReadoutSettings.SIGNAL, false)
                break
            }
            return ReadoutComputed(
                core = core,
                text = text,
                color = color,
                signal = signal,
                gauge = (gauge as? Outcome.Value)?.number?.coerceIn(0.0, 1.0),
                gaugeFailure = (gauge as? Outcome.Failed)?.let { failed ->
                    s.shared("readout_error_gauge_failed").format(failed.causes.joinToString("; ") { cause ->
                        val why = s.shared("variable_cause_${cause.reason.lowercase()}").format(cause.field ?: "", cause.entries.size)
                        cause.term?.let { "$it: $why" } ?: why
                    })
                },
                failures = failures
            )
        }

        /**
         * The gauge [written] computes on the values [read] at [at]: a value that gave nothing, or is
         * no number, fails it with its name, as a division by zero does (Formula.evaluate).
         */
        private fun gaugeOf(written: String, read: Map<String, Pair<Any?, FieldDefinition?>>, at: Long): Outcome {
            val formula = (Formula.parse(written) as? Formula.Parsed.Read)?.formula ?: return Outcome.Failed(listOf(Cause(Cause.UNREADABLE)))
            return formula.evaluate { leaf ->
                when (leaf) {
                    is Formula.Now -> Outcome.Value(at.toDouble())
                    is Formula.Name -> when (val value = read[leaf.name]?.first) {
                        null -> Outcome.Failed(listOf(Cause(Cause.UNREADABLE, term = leaf.name)))
                        is Number -> Outcome.Value(value.toDouble())
                        else -> Outcome.Failed(listOf(Cause(Cause.NOT_A_NUMBER, term = leaf.name)))
                    }
                    else -> Outcome.Failed(listOf(Cause(Cause.UNKNOWN_NAME)))
                }
            }
        }

        /** Whether [condition] holds on the values [read], each side a value by its name or a written one; null when one is missing. */
        private fun holds(condition: Condition, read: Map<String, Pair<Any?, FieldDefinition?>>, resolver: TimeResolver, text: (String) -> String): Boolean? {
            fun valueOf(side: Condition.Side): Any? = when (side) {
                is Condition.Side.Field -> read[side.path]?.first
                is Condition.Side.Of -> (side.term as? Term.Constant)?.value
            }
            val left = condition.left as? Condition.Side.Field ?: return null
            val field = read[left.path]?.second ?: return null
            return ConditionJudge.holds(field, condition.op, valueOf(left), condition.right.map { valueOf(it) }, resolver, text)
        }
    }
}
