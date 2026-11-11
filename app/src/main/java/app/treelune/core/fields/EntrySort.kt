package app.treelune.core.fields

import org.json.JSONArray
import org.json.JSONObject

/**
 * One key of an entry sort: a field by its path, and its direction.
 *
 * @property field The field's path, as a filter names it ("name", "data.number")
 * @property ascending Smallest first: A before Z, the earliest first, a choice in its options' order
 */
data class SortKey(val field: String, val ascending: Boolean)

/**
 * The order of an entry query (docs/BRICKS.md, « Sort »): keys applied one after the other, the
 * second ordering what the first leaves equal ("programme", then "number"), stored as
 * `[{"field": "data.programme", "direction": "asc"}]`. An empty list is the order without a sort,
 * the newest first, which also settles what every key leaves equal.
 *
 * A key names a field a filter may name (EntryFilters.filterableFields) of a type that has an
 * order: a number, a duration, an instant, a date, a time, a text, a yes/no, one option of a
 * choice (in its options' order). An entry without a value comes after the others, in either
 * direction. The sort runs in SQL with the filters, so that a page is a page of the order.
 */
object EntrySort {

    const val FIELD = "field"
    const val DIRECTION = "direction"
    const val ASC = "asc"
    const val DESC = "desc"
    val DIRECTIONS = listOf(ASC, DESC)

    /** Whether entries can be put in order by a field of [field]'s type and shape. */
    fun sortable(field: FieldDefinition): Boolean = when (field.type) {
        FieldType.NUMERIC, FieldType.SCALE, FieldType.DURATION, FieldType.DATETIME,
        FieldType.DATE, FieldType.TIME, FieldType.TEXT, FieldType.BOOLEAN -> true
        // A set of options has no place among others; one option has its own
        FieldType.CHOICE -> ChoiceSettings.fromConfig(field.config).shape == ChoiceShape.SINGLE
        FieldType.RANGE, FieldType.REFERENCE, FieldType.FILE -> false
    }

    /** The fields of [fields] a sort may name. */
    fun sortableFields(fields: Map<String, FieldDefinition>): Map<String, FieldDefinition> = fields.filterValues { sortable(it) }

    /** The keys of a sort, or why it cannot be run. */
    sealed interface Parsed {
        data class Ready(val keys: List<SortKey>) : Parsed
        data class Refused(val error: String) : Parsed
    }

    /**
     * Read and check [sort] against the [fields] of a tool (EntryFilters.filterableFields). A key
     * that does not hold is refused, named: left out, it would hand back another order in silence.
     *
     * @param text The shared string of a key, for the errors
     */
    fun parse(sort: JSONArray, fields: Map<String, FieldDefinition>, text: (String) -> String): Parsed {
        val keys = mutableListOf<SortKey>()
        for (i in 0 until sort.length()) {
            val key = sort.opt(i) as? JSONObject
                ?: return Parsed.Refused(text("service_error_sort_unreadable").format(sort.opt(i).toString()))
            val path = key.optString(FIELD)
            val field = fields[path]
            if (field == null || !sortable(field)) {
                return Parsed.Refused(text("service_error_sort_field").format(path, sortableFields(fields).keys.joinToString(", ")))
            }
            val direction = key.optString(DIRECTION)
            if (direction !in DIRECTIONS) return Parsed.Refused(text("service_error_sort_direction").format(path, direction, DIRECTIONS.joinToString(", ")))
            if (keys.any { it.field == path }) return Parsed.Refused(text("service_error_sort_twice").format(path))
            keys.add(SortKey(path, direction == ASC))
        }
        return Parsed.Ready(keys)
    }

    /**
     * The keys [json] stores, as written: a list of `{"field", "direction"}`, its fields not
     * checked against any tool (parse does that, where the fields are known).
     *
     * @throws IllegalArgumentException on what is not a list of keys
     */
    fun read(json: Any, text: (String) -> String): List<SortKey> {
        val array = json as? JSONArray ?: throw IllegalArgumentException(text("service_error_sort_unreadable").format(json.toString()))
        return (0 until array.length()).map { i ->
            val key = array.opt(i) as? JSONObject ?: throw IllegalArgumentException(text("service_error_sort_unreadable").format(array.opt(i).toString()))
            val direction = key.optString(DIRECTION)
            if (direction !in DIRECTIONS) throw IllegalArgumentException(text("service_error_sort_direction").format(key.optString(FIELD), direction, DIRECTIONS.joinToString(", ")))
            SortKey(key.optString(FIELD).ifEmpty { throw IllegalArgumentException(text("service_error_sort_unreadable").format(key.toString())) }, direction == ASC)
        }
    }

    /**
     * [rows] put in the order of [keys] in memory, for entries no query reads (a variable's
     * series, computed): what the SQL does (EntryFilters.select), the entries without a value
     * after the others, a text without its case, a choice by its option's place, the order given
     * kept where the keys leave them equal.
     */
    fun ordered(rows: List<Map<String, Any?>>, keys: List<SortKey>, fields: Map<String, FieldDefinition>): List<Map<String, Any?>> {
        if (keys.isEmpty()) return rows
        val comparator = keys.map { key ->
            val field = fields[key.field]
            val rank: (Any) -> Comparable<Any> = when (field?.type) {
                FieldType.TEXT -> { v -> (v as String).lowercase() as Comparable<Any> }
                FieldType.BOOLEAN -> { v -> (v as Boolean) as Comparable<Any> }
                FieldType.DATE, FieldType.TIME -> { v -> (v as String) as Comparable<Any> }
                FieldType.CHOICE -> {
                    val options = ChoiceSettings.fromConfig(field.config).options
                    ({ v -> options.indexOf(v as String).let { if (it < 0) options.size else it } as Comparable<Any> })
                }
                else -> { v -> (v as Number).toDouble() as Comparable<Any> }
            }
            Comparator<Map<String, Any?>> { a, b ->
                val left = app.treelune.core.reading.FieldReading.valueAt(a, key.field)
                val right = app.treelune.core.reading.FieldReading.valueAt(b, key.field)
                when {
                    left == null && right == null -> 0
                    left == null -> 1
                    right == null -> -1
                    else -> rank(left).compareTo(rank(right)).let { if (key.ascending) it else -it }
                }
            }
        }.reduce { first, next -> first.then(next) }
        return rows.sortedWith(comparator)
    }

    /** The stored form of [keys]. */
    fun toJson(keys: List<SortKey>): JSONArray = JSONArray(keys.map {
        JSONObject().put(FIELD, it.field).put(DIRECTION, if (it.ascending) ASC else DESC)
    })
}
