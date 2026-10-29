package app.treelune.core.terms

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.toFieldDefinition
import app.treelune.core.selection.EntryPeriod
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * A term read once, at the context's reference: a constant as it is written, a variable through
 * `variables.evaluate`, a reading through `readings.read`. What a condition judged once compares.
 */
class TermReader(private val context: Context) {

    /** What reading a term gives. */
    sealed interface Read {
        /** A value, and the field it is a value of; none for a constant, which has no type of its own. */
        data class Value(val value: Any?, val field: FieldDefinition?) : Read
        /**
         * No value, and why in words: a reading without entries, a variable that failed.
         * [broken] when a cause is not the data's — what it reads gone (a tool, a variable), a
         * loop, a name unknown —, which the data filling in will not mend.
         */
        data class Failed(val message: String, val broken: Boolean = false) : Read
    }

    /**
     * [term] read at [at].
     *
     * @param period The period a reading without one of its own reads (a goal's attempt); null
     *   for its whole history
     * @throws IllegalStateException when a read cannot run: an unknown tool or variable, a field
     *   the tool has no more, a formula per entry (read in a variable only)
     */
    suspend fun read(term: Term, at: Long, period: EntryPeriod?): Read = when (term) {
        is Term.Constant -> Read.Value(term.value, null)
        is Term.Variable -> {
            val result = Coordinator(context).processUserAction("variables.evaluate", mapOf("variable_id" to term.id, "at" to listOf(at)))
            if (!result.isSuccess) throw IllegalStateException(result.error ?: "")
            val row = (result.data?.get("values") as? List<*>)?.firstOrNull() as? Map<*, *>
            val field = (result.data?.get("field") as? Map<*, *>)?.let { fieldOf(it, result.data?.get("name") as? String ?: term.id) }
            (row?.get("failure") as? Map<*, *>)?.let { failure ->
                val reasons = (failure["causes"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("reason")?.toString() }
                Read.Failed(failure["message"] as? String ?: "", reasons.any { it !in DATA_REASONS })
            } ?: Read.Value(row?.get("value"), field)
        }
        is Term.Reading -> {
            if (term.perEntry != null) throw IllegalStateException("a formula per entry is read in a variable")
            val selection = if (term.selection.period.isEmpty && period != null) term.selection.copy(period = period) else term.selection
            val result = Coordinator(context).processUserAction("readings.read", term.copy(selection = selection).params() + ("reference" to at))
            if (!result.isSuccess) throw IllegalStateException(result.error ?: "")
            (result.data?.get("failure") as? Map<*, *>)?.let { Read.Failed(it["message"] as? String ?: "", it["reason"]?.toString().let { reason -> reason != null && reason !in DATA_REASONS }) }
                // A count has no field: it is a number
                ?: Read.Value(result.data?.get("value"), (result.data?.get("field") as? Map<*, *>)?.let { fieldOf(it, term.field ?: "") }
                    ?: app.treelune.core.reading.FieldReading.COUNT_FIELD)
        }
    }

    private fun fieldOf(json: Map<*, *>, name: String): FieldDefinition {
        val obj = JsonUtils.toJSONObject(json.entries.associate { it.key.toString() to it.value })
        if (!obj.has("name")) obj.put("name", name)
        if (!obj.has("display_name")) obj.put("display_name", name)
        return obj.toFieldDefinition()
    }

    private companion object {
        /** The causes of a failure the data make, which entries written later may mend (FailureReason, a variable's causes). */
        val DATA_REASONS = setOf("NO_ENTRY", "MISSING_VALUE", "DIVISION_BY_ZERO")
    }
}
