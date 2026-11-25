package app.treelune.core.views

import app.treelune.core.conditions.Conditions
import app.treelune.core.conditions.ConditionJudge
import app.treelune.core.fields.EntrySort
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FilterOperator
import app.treelune.core.reading.FieldReading
import app.treelune.core.reading.ReadingResult
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.TimeResolver
import org.json.JSONArray
import org.json.JSONObject

/**
 * A reading of rows already in memory, shaped as entries are given (ToolDataEntries.toMap): the
 * rows a thing is computing and has not written, which its own rules read while it computes them
 * (a Path's stages, each state judged on those computed before it). The base cannot judge them,
 * not holding them yet, so each filter is judged here row by row, by the comparison a condition
 * judged once makes (ConditionJudge), which compares two values as EntryFilters does in SQL.
 *
 * Only written filters are judged: a variable or a reading on a filter's right is read beforehand
 * by whoever gives the selection. A grouping is refused.
 */
object RowsReading {

    /**
     * The rows of [rows] [selection] keeps: in its period (on `timestamp`), passing each of its
     * filters, in its order, the first of its limit.
     *
     * @param fields The rows' fields by path, as the view declares them
     * @throws IllegalStateException on a filter that does not read, with what to say
     */
    fun selected(rows: List<Map<String, Any?>>, selection: EntrySelection, fields: Map<String, FieldDefinition>, resolver: TimeResolver, text: (String) -> String): List<Map<String, Any?>> {
        val (start, end) = selection.period.instants(resolver)
        val filters = parsed(selection.filters, fields, text)
        val kept = rows.filter { row ->
            val at = row["timestamp"] as? Long
            (start == null || (at != null && at >= start)) && (end == null || (at != null && at <= end)) &&
                filters.all { (field, path, op, right) -> ConditionJudge.holds(field, op, valueAt(row, path), right, resolver, text) == true }
        }
        val ordered = EntrySort.ordered(kept, selection.sort, fields)
        return selection.limit?.let { ordered.take(it) } ?: ordered
    }

    /**
     * The value of [path] in [row] reduced by [reduction] across the rows [selection] keeps;
     * [path] null to count.
     *
     * @throws IllegalStateException on a filter or a field that does not read, with what to say
     */
    fun read(rows: List<Map<String, Any?>>, selection: EntrySelection, path: String?, reduction: Reduction, fields: Map<String, FieldDefinition>, resolver: TimeResolver, text: (String) -> String): ReadingResult {
        val field = path?.let { fields[it] ?: throw IllegalStateException(text("service_error_filter_unknown_field").format(it, fields.keys.joinToString(", "))) }
        // A streak needs its condition, which a reading of rows in memory does not carry
        if (reduction == Reduction.STREAK) throw IllegalStateException(text("reading_error_streak_rows"))
        if (reduction !in Reduction.forField(field)) {
            throw IllegalStateException(text("service_error_reading_reduction_type").format(
                reduction.name, field?.type?.name ?: "-", Reduction.forField(field).joinToString(", ") { it.name }))
        }
        return FieldReading.reduce(selected(rows, selection, fields, resolver, text), path, field, reduction, resolver.reference)
    }

    /** The value at [path] of [row]: a column (`timestamp`, `name`, `id`), or a key under `data.`, `state.` or `extra.`. */
    fun valueAt(row: Map<String, Any?>, path: String): Any? {
        val dot = path.indexOf('.')
        if (dot < 0) return row[path]
        return (row[path.substring(0, dot)] as? Map<*, *>)?.get(path.substring(dot + 1))
    }

    /** One filter read: the field it compares, its path, its operator and its written values on the right. */
    private data class Filter(val field: FieldDefinition, val path: String, val op: FilterOperator, val right: List<Any?>)

    private fun parsed(filters: JSONArray, fields: Map<String, FieldDefinition>, text: (String) -> String): List<Filter> = (0 until filters.length()).map { i ->
        val filter = filters.optJSONObject(i) ?: throw IllegalStateException(text("condition_error_side").format("", filters.opt(i).toString()))
        val path = Conditions.fieldOf(filter) ?: throw IllegalStateException(text("condition_error_side").format("", filter.toString()))
        val field = fields[path] ?: throw IllegalStateException(text("service_error_filter_unknown_field").format(path, fields.keys.joinToString(", ")))
        val op = FilterOperator.of(filter.optString(Conditions.OP))
            ?: throw IllegalStateException(text("condition_error_op").format(path, filter.optString(Conditions.OP)))
        val right = when (val read = Conditions.right(filter)) {
            Conditions.Right.None -> emptyList()
            // A between's two bounds are one written array; any other value is one side
            is Conditions.Right.Written -> if (op == FilterOperator.BETWEEN) (read.value as JSONArray).let { pair -> (0 until pair.length()).map { plain(pair.opt(it)) } } else listOf(plain(read.value))
            is Conditions.Right.NotWritten -> throw IllegalStateException(text("rows_reading_term").format(path))
        }
        Filter(field, path, op, right)
    }

    /** A written value as the judge compares it: an array as a list, a relative date left as its object. */
    private fun plain(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONArray -> (0 until value.length()).map { plain(value.opt(it)) }
        else -> value
    }
}
