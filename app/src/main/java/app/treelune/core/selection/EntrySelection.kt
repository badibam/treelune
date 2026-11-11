package app.treelune.core.selection

import app.treelune.core.conditions.Conditions
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * A period as two bounds, each absent for no limit: `{"start": …, "end": …}`, both inclusive.
 * Neither bound is a filter: a period is said apart from the filters on values.
 */
data class EntryPeriod(val start: TimePoint? = null, val end: TimePoint? = null) {

    val isEmpty: Boolean get() = start == null && end == null

    /** The period's two instants, resolved by [resolver]; null for a side without limit. */
    fun instants(resolver: TimeResolver): Pair<Long?, Long?> = start?.let { resolver.instant(it) } to end?.let { resolver.instant(it) }

    fun toJson(): JSONObject = JSONObject().apply {
        start?.let { put("start", it.toJson()) }
        end?.let { put("end", it.toJson()) }
    }

    /**
     * The period as the filters of tool_data.get on timestamp: ">=" its start, "<=" its end,
     * resolved by [resolver].
     */
    fun timestampFilters(resolver: TimeResolver): List<Map<String, Any?>> = listOfNotNull(
        start?.let { JsonUtils.toMap(Conditions.onField("timestamp", ">=", resolver.instant(it))) },
        end?.let { JsonUtils.toMap(Conditions.onField("timestamp", "<=", resolver.instant(it))) }
    )

    companion object {
        /** @throws IllegalArgumentException on a bound that does not read */
        fun fromJson(json: JSONObject, text: (String) -> String): EntryPeriod = EntryPeriod(
            start = json.opt("start")?.let { TimePoint.read(it, text) },
            end = json.opt("end")?.let { TimePoint.read(it, text) }
        )
    }
}

/**
 * Entries of the app: which (a thing, the reference; the period their timestamp falls in; the
 * filters on their values), in which order, how many, and the fields kept. The core's own notion,
 * read the same wherever it serves: a message's pointer, a chart, a reading, a variable's term,
 * an API's list, a Session's media. Stored as
 * `{"target", "period", "filters", "fields", "sort", "limit"}`, those that narrow nothing absent.
 *
 * Filters and fields are an instance's own; a period narrows an instance's entries, or a zone's,
 * applied to each of its tools. The sort (EntrySort) is never empty where entries are read in
 * order — a tool's, a variable's series —: it is written whole, its default by
 * SelectionOrder.defaultSort, never left to the reader. The limit keeps the first entries of
 * that order, after the period and the filters, before any grouping. [problem] names what does
 * not fit its target.
 *
 * @property filters Conditions put on each entry (Conditions), a date's value being a TimePoint's stored form
 * @property fields The fields kept, all of them when null
 * @property sort The order of the entries, key after key; empty where there is no order to read
 *   (one entry, a zone, a trigger's entry written)
 * @property limit The first entries of the order kept, all of them when null
 */
data class EntrySelection(
    val target: Reference,
    val period: EntryPeriod = EntryPeriod(),
    val filters: JSONArray = JSONArray(),
    val fields: List<String>? = null,
    val sort: List<app.treelune.core.fields.SortKey> = emptyList(),
    val limit: Int? = null
) {

    /** Whether it says an order or a count: what a zone and a trigger refuse. */
    val ordered: Boolean get() = sort.isNotEmpty() || limit != null


    /**
     * What the target does not take, in words ([text], the shared strings); null when it all fits.
     *
     * @param series Whether a variable may be the target: a Reading's selection, a variable's
     * series standing for a tool's entries (VariableSeries), whose having one is checked where it
     * is read; a pointer and an API's list select no variable
     * @param trigger Whether it says which entry written a trigger hears: one entry judged alone,
     * with no order to put it in nor count to keep
     */
    fun problem(series: Boolean = false, trigger: Boolean = false, text: (String) -> String): String? = when {
        // The field read is the reading's own, beside its selection
        target.field != null -> text("selection_problem_field")
        limit != null && limit < 1 -> text("selection_problem_limit").format(limit)
        trigger -> if (ordered) text("selection_problem_order_trigger") else null
        target.kind == ReferenceKind.TOOL_INSTANCE -> null
        // A variable has entries only through its series, which a reading alone selects
        target.kind == ReferenceKind.VARIABLE -> if (series && fields == null) null else text("selection_problem_variable")
        // A zone is read tool by tool: no field is common to put its entries in one order
        target.kind == ReferenceKind.ZONE && ordered -> text("selection_problem_order_zone")
        filters.length() > 0 || fields != null -> text("selection_problem_filters").format(target.kind.name)
        ordered && target.kind != ReferenceKind.ZONE -> text("selection_problem_order").format(target.kind.name)
        !period.isEmpty && target.kind != ReferenceKind.ZONE -> text("selection_problem_period").format(target.kind.name)
        else -> null
    }

    /**
     * Why it cannot be read in order; null when it can: a tool's or a series' entries are read in
     * their sort, which the writing completes (SelectionOrder) and the migration wrote — one
     * without is never read in an order nobody chose. What its structure fits is [problem]'s, which
     * a selection being written passes before its sort is completed.
     */
    fun unordered(text: (String) -> String): String? =
        if (sort.isEmpty() && (target.kind == ReferenceKind.TOOL_INSTANCE || target.kind == ReferenceKind.VARIABLE)) text("selection_problem_sort_missing") else null

    /**
     * Its order and its count as tool_data.get takes them: `sort` and, with a limit, `limit`
     * (a page of that many, the first), so that the limit is a limit of the order.
     */
    fun orderParams(): Map<String, Any> = buildMap {
        if (sort.isNotEmpty()) put("sort", JsonUtils.toList(app.treelune.core.fields.EntrySort.toJson(sort)))
        limit?.let { put("limit", it) }
    }

    /**
     * Whether a filter compares with a relative date, or with a variable or a reading read at
     * each instant (FilterTerms): its entries then change with the instant they are read at,
     * beyond the period.
     */
    val filtersMove: Boolean get() = (0 until filters.length()).any { i ->
        when (val right = Conditions.right(filters.getJSONObject(i))) {
            is Conditions.Right.Written -> (right.value as? JSONArray)?.let { pair -> (0 until pair.length()).any { TimePoint.isRelative(pair.opt(it)) } }
                ?: TimePoint.isRelative(right.value)
            is Conditions.Right.NotWritten -> true
            Conditions.Right.None -> false
        }
    }

    /**
     * The filters of tool_data.get this selection reads its entries with: its period on
     * timestamp, then its filters, each relative date resolved by [resolver] in the stored form of
     * its field ([fields], by path). A filter on a field the tool does not have is passed as it
     * is, for the service to refuse it.
     *
     * @throws IllegalArgumentException on a relative date that does not read
     */
    fun storedFilters(fields: Map<String, FieldDefinition>, resolver: TimeResolver, text: (String) -> String): JSONArray {
        val stored = JSONArray()
        period.timestampFilters(resolver).forEach { stored.put(JSONObject(it)) }
        for (i in 0 until filters.length()) {
            val filter = filters.getJSONObject(i)
            val type = Conditions.fieldOf(filter)?.let { fields[it]?.type }
            stored.put(
                if (type == FieldType.DATE || type == FieldType.DATETIME) Conditions.withValues(filter) { value ->
                    if (TimePoint.isRelative(value)) resolver.resolve(TimePoint.read(value!!, text), type) else value
                } else filter
            )
        }
        return stored
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("target", target.toJson())
        if (!period.isEmpty) put("period", period.toJson())
        if (filters.length() > 0) put("filters", filters)
        fields?.let { put("fields", JSONArray(it)) }
        if (sort.isNotEmpty()) put("sort", app.treelune.core.fields.EntrySort.toJson(sort))
        limit?.let { put("limit", it) }
    }

    companion object {
        /**
         * Its sort's keys are read as written, their fields checked against the target's where
         * its entries are read (EntrySort.parse).
         *
         * @throws IllegalArgumentException on a period bound, a sort key or a limit that does not read
         */
        fun fromJson(json: JSONObject, text: (String) -> String): EntrySelection = EntrySelection(
            target = Reference.fromJson(json.getJSONObject("target")),
            period = json.optJSONObject("period")?.let { EntryPeriod.fromJson(it, text) } ?: EntryPeriod(),
            filters = json.optJSONArray("filters") ?: JSONArray(),
            fields = json.optJSONArray("fields")?.let { array -> (0 until array.length()).map { array.getString(it) } },
            sort = json.opt("sort")?.takeIf { it != JSONObject.NULL }?.let { app.treelune.core.fields.EntrySort.read(it, text) } ?: emptyList(),
            limit = json.opt("limit")?.takeIf { it != JSONObject.NULL }?.let { value ->
                (value as? Number)?.takeIf { it.toDouble() == Math.floor(it.toDouble()) }?.toInt()
                    ?: throw IllegalArgumentException(text("selection_problem_limit").format(value))
            }
        )
    }
}
