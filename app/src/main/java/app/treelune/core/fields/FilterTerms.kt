package app.treelune.core.fields

import android.content.Context
import app.treelune.core.conditions.Conditions
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermReader
import org.json.JSONArray
import org.json.JSONObject

/**
 * A filter compared with a variable or a reading (« kcal > objectif_calorique »): its right side is
 * read once, at the reference of what reads the entries (now, or the instant a reading or a
 * variable is read at), then written in its place, and the filter goes to SQL as a written one
 * (EntryFilters). A side already written stays as it is.
 */
object FilterTerms {

    /** Whether [filters] hold a side to read rather than a written one. */
    fun hasTerms(filters: JSONArray): Boolean = (0 until filters.length()).any { i ->
        (filters.optJSONObject(i)?.opt(Conditions.RIGHT) as? JSONObject)?.let { isTerm(it) } == true
    }

    private fun isTerm(side: JSONObject) = side.has(Term.Kind.VARIABLE.key) || side.has(Term.Kind.READING.key)

    /**
     * The variables the right sides of [filters] read, by id: those named, and those the filters
     * of a reading named there read in turn. A variable whose reading's filter reads it back is a
     * loop, which its writing refuses (VariableService).
     */
    fun variablesRead(filters: JSONArray?): List<String> = (0 until (filters?.length() ?: 0)).flatMap { i ->
        val side = filters!!.optJSONObject(i)?.opt(Conditions.RIGHT) as? JSONObject ?: return@flatMap emptyList()
        side.optString(Term.Kind.VARIABLE.key).takeIf { it.isNotEmpty() }?.let { return@flatMap listOf(it) }
        variablesRead(side.optJSONObject(Term.Kind.READING.key)?.optJSONObject("selection")?.optJSONArray("filters"))
    }

    /**
     * [filters] with each right side that is a variable or a reading read at [at] and written as
     * a constant.
     *
     * @throws IllegalStateException saying the term that could not be read, and why: a filter
     *   left out would widen the query in silence
     */
    suspend fun resolved(filters: JSONArray, at: Long, context: Context, text: (String) -> String): JSONArray {
        if (!hasTerms(filters)) return filters
        val reader = TermReader(context)
        return JSONArray().apply {
            for (i in 0 until filters.length()) {
                val filter = filters.opt(i) as? JSONObject
                val side = filter?.opt(Conditions.RIGHT) as? JSONObject
                if (filter == null || side == null || !isTerm(side)) { put(filters.opt(i)); continue }
                val path = Conditions.fieldOf(filter) ?: ""
                val term = try {
                    Term.fromJson(side, path, text)
                } catch (e: IllegalArgumentException) {
                    throw IllegalStateException(e.message)
                }
                when (val read = reader.read(term, at, null)) {
                    is TermReader.Read.Value -> put(JSONObject(filter.toString()).put(Conditions.RIGHT, JSONObject().put(Conditions.CONSTANT,
                        read.value?.let { JSONObject.wrap(it) } ?: throw IllegalStateException(text("filter_term_no_value").format(path)))))
                    is TermReader.Read.Failed -> throw IllegalStateException(text("filter_term_unread").format(path, read.message))
                }
            }
        }
    }
}
