package app.treelune.core.reading

import app.treelune.core.conditions.Conditions
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.PeriodType
import app.treelune.core.selection.TimeResolver
import app.treelune.core.selection.normalizeTimestampWithConfig
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * A reading's grouping (docs/BRICKS.md, « Reading »): its entries grouped by the value of
 * [field], a date or an instant rounded to the [unit] holding it; each group reduced as the
 * reading's own field and reduction say; the groups whose value holds [keep] kept; then their
 * values reduced by [reduction]. Stored beside the reading's field and reduction, which keep
 * their sense, as `{"field", "unit"?, "keep"?, "reduction"}`: "2 per day, counted" is
 * `{"field": "timestamp", "unit": "DAY", "keep": {"op": ">=", "right": {"constant": 2}}, "reduction": "COUNT"}`.
 *
 * @property keep A condition without its left side, which is the group's value; null keeps them all
 */
data class ReadingGroup(val field: String, val unit: PeriodType?, val keep: JSONObject?, val reduction: Reduction) {

    fun toJson(): JSONObject = JSONObject().apply {
        put(FIELD, field)
        unit?.let { put(UNIT, it.name) }
        keep?.let { put(KEEP, it) }
        put(REDUCTION, reduction.name)
    }

    /** [keep] as a condition judged once, its left side standing for the group's value (ConfigConditions, ConditionJudge). */
    fun keepCondition(): JSONObject? = keep?.let { keepCondition(it) }

    /**
     * Why this grouping does not read on entries of [fields], each group's value being of
     * [value] (resultField of the reading's own field and reduction); null when it does.
     */
    fun problem(fields: Map<String, FieldDefinition>, value: FieldDefinition, text: (String) -> String): String? {
        val by = fields[field] ?: return text("service_error_filter_unknown_field").format(field, fields.keys.joinToString(", "))
        if (by.type !in GROUPED) return text("reading_error_group_type").format(by.displayName, by.type.name, GROUPED.joinToString(", ") { it.name })
        if (unit != null && by.type !in DATES) return text("reading_error_group_unit_type").format(by.displayName, by.type.name)
        if (unit != null && unit !in UNITS) return text("reading_error_group_unit").format(unit.name, UNITS.joinToString(", ") { it.name })
        if (unit == null && by.type == FieldType.DATETIME) return text("reading_error_group_unit_needed").format(by.displayName, UNITS.joinToString(", ") { it.name })
        if (reduction !in reductions(value)) return text("reading_error_group_reduction").format(reduction.name, reductions(value).joinToString(", ") { it.name })
        return null
    }

    companion object {
        const val FIELD = "field"
        const val UNIT = "unit"
        const val KEEP = "keep"
        const val REDUCTION = "reduction"

        /** The left side [keep] stands on once judged: the group's value, which no entry has. */
        const val VALUE = "group_value"

        /** The units a date is rounded to. */
        val UNITS = listOf(PeriodType.DAY, PeriodType.WEEK, PeriodType.MONTH)

        /** The types a grouping is by: a value that repeats across entries. */
        val GROUPED = setOf(FieldType.TEXT, FieldType.CHOICE, FieldType.REFERENCE, FieldType.BOOLEAN, FieldType.NUMERIC, FieldType.SCALE, FieldType.DATE, FieldType.DATETIME)

        /** The types rounded to a unit; an instant is always, no two being equal otherwise. */
        private val DATES = setOf(FieldType.DATE, FieldType.DATETIME)

        /** An average of counts is not a whole number: « 6.4 glasses a day ». */
        val AVERAGE_COUNT_FIELD = FieldReading.COUNT_FIELD.copy(config = mapOf("decimals" to 1))

        /**
         * The reductions of the groups' values, each being of [value]: counting the groups, and
         * those [value] takes, the last excepted, which groups have none of.
         */
        fun reductions(value: FieldDefinition): Set<Reduction> =
            (Reduction.forField(value) - Reduction.LAST) + Reduction.COUNT

        /** The field a reduction of the groups' values, each of [value], gives: a count is a whole number. */
        fun resultField(value: FieldDefinition, reduction: Reduction): FieldDefinition = when {
            reduction == Reduction.COUNT -> FieldReading.COUNT_FIELD
            reduction == Reduction.AVERAGE && value == FieldReading.COUNT_FIELD -> AVERAGE_COUNT_FIELD
            else -> value
        }

        /** [keep] with the group's value on its left. */
        fun keepCondition(keep: JSONObject): JSONObject = JSONObject(keep.toString()).put(Conditions.LEFT, JSONObject().put(Conditions.FIELD, VALUE))

        /** @throws IllegalArgumentException naming what does not read */
        fun fromJson(json: JSONObject, text: (String) -> String): ReadingGroup {
            val field = json.optString(FIELD).takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException(text("reading_error_group_field"))
            val unit = json.optString(UNIT).takeIf { it.isNotEmpty() }?.let { name ->
                PeriodType.entries.firstOrNull { it.name == name } ?: throw IllegalArgumentException(text("reading_error_group_unit").format(name, UNITS.joinToString(", ") { it.name }))
            }
            val reduction = Reduction.entries.firstOrNull { it.name == json.optString(REDUCTION) }
                ?: throw IllegalArgumentException(text("service_error_reading_reduction").format(json.optString(REDUCTION), Reduction.entries.joinToString(", ") { it.name }))
            return ReadingGroup(field, unit, json.optJSONObject(KEEP), reduction)
        }
    }
}

/**
 * A grouped reading computed (docs/BRICKS.md, « Reading »): pure, the entries given newest
 * first, as tool_data hands them out. A group without entries exists only where the values can
 * be listed ahead ([listed]): the units of the period, the options of a choice; its count and its
 * sum are 0, its other reductions have no value, and it is not kept. An entry holding several
 * values (a multiple choice, a multiple reference) goes into each of their groups.
 */
object GroupedReading {

    /**
     * @param path The field each group reduces ("data.duration"), null to count
     * @param field Its definition, null to count
     * @param by The definition of [group]'s field
     * @param listed The groups that exist without entries, as [keyOf] gives them
     * @param kept Whether a group's value is kept, [group] holding a keep; never asked otherwise
     * @param at The instant read at, up to which a running DURATION counts
     */
    fun reduce(
        entries: List<Map<String, Any?>>,
        path: String?,
        field: FieldDefinition?,
        reduction: Reduction,
        group: ReadingGroup,
        by: FieldDefinition,
        listed: List<Any>,
        calendar: TimeResolver,
        kept: (Any) -> Boolean,
        at: Long
    ): ReadingResult {
        val value = if (reduction == Reduction.COUNT) FieldReading.COUNT_FIELD else field!!
        require(group.reduction in ReadingGroup.reductions(value)) { "${group.reduction} does not reduce groups of ${value.type}" }

        // The groups listed first, in their order, then those only entries make
        val groups = LinkedHashMap<Any, MutableList<Map<String, Any?>>>()
        listed.forEach { groups[it] = mutableListOf() }
        entries.forEach { entry ->
            keysOf(FieldReading.valueAt(entry, group.field), by, group.unit, calendar).forEach { key -> groups.getOrPut(key) { mutableListOf() }.add(entry) }
        }

        val values = mutableListOf<Any>()
        for (rows in groups.values) {
            when (val result = FieldReading.reduce(rows, path, field, reduction, at)) {
                is ReadingResult.Value -> if (group.keep == null || kept(result.value)) values.add(result.value)
                // An entry without an answer fails the whole reading, as it would without groups
                is ReadingResult.Failure -> if (result.reason != FailureReason.NO_ENTRY) return result
            }
        }

        if (group.reduction == Reduction.COUNT) return ReadingResult.Value(values.size, FieldReading.COUNT_FIELD)
        // The groups' values reduced as entries holding them would be
        val rows = values.mapIndexed { i, v -> mapOf("id" to "$i", VALUE to v) }
        return when (val result = FieldReading.reduce(rows, VALUE, value, group.reduction, at)) {
            is ReadingResult.Value -> ReadingResult.Value(result.value, ReadingGroup.resultField(value, group.reduction))
            is ReadingResult.Failure -> ReadingResult.Failure(result.reason, path)
        }
    }

    /**
     * The groups an entry's [value] goes into: none without an answer, one per value of a list,
     * a reference by its id, a date or an instant by the start of the [unit] holding it, as the
     * user's [calendar] cuts it.
     */
    fun keysOf(value: Any?, by: FieldDefinition, unit: PeriodType?, calendar: TimeResolver): List<Any> {
        if (value == null) return emptyList()
        if (by.type == FieldType.REFERENCE) return ReferenceTarget.referencesOf(value).mapNotNull { it.id }
        if (value is List<*>) return value.flatMap { keysOf(it, by, unit, calendar) }
        if (value is org.json.JSONArray) return (0 until value.length()).flatMap { keysOf(value.opt(it), by, unit, calendar) }
        return listOf(when {
            unit == null -> if (value is Number && by.type != FieldType.DATETIME) value.toDouble() else value
            by.type == FieldType.DATE -> unitStart(calendar.startOf(LocalDate.parse(value.toString())), unit, calendar)
            else -> unitStart((value as Number).toLong(), unit, calendar)
        })
    }

    /**
     * The groups that exist without entries for a grouping by [by]: the units of [start] to [end]
     * when it is by the entries' timestamp, the options of a choice; none otherwise.
     */
    fun listed(group: ReadingGroup, by: FieldDefinition, start: Long?, end: Long, calendar: TimeResolver): List<Any> = when {
        group.field == TIMESTAMP && group.unit != null -> units(start!!, end, group.unit, calendar)
        by.type == FieldType.CHOICE -> ChoiceSettings.fromConfig(by.config).options
        else -> emptyList()
    }

    /** Whether [group] needs its period's start: its units are listed from there. */
    fun needsStart(group: ReadingGroup): Boolean = group.field == TIMESTAMP && group.unit != null

    /** The start of each [unit] from the one holding [start] to the one holding [end], as [calendar] cuts them. */
    fun units(start: Long, end: Long, unit: PeriodType, calendar: TimeResolver): List<Long> {
        val units = mutableListOf<Long>()
        var at = unitStart(start, unit, calendar)
        while (at <= end) {
            units.add(at)
            // A step on the calendar, not a duration: a day of a change of hour lasts 23 or 25 hours
            val date = Instant.ofEpochMilli(at).atZone(calendar.zone)
            val next = when (unit) {
                PeriodType.WEEK -> date.plusWeeks(1)
                PeriodType.MONTH -> date.plusMonths(1)
                else -> date.plusDays(1)
            }
            at = unitStart(next.toInstant().toEpochMilli(), unit, calendar)
        }
        return units
    }

    private fun unitStart(millis: Long, unit: PeriodType, calendar: TimeResolver): Long =
        normalizeTimestampWithConfig(millis, unit, calendar.dayStartHour, calendar.weekStartDay, calendar.zone)

    private const val TIMESTAMP = "timestamp"
    private const val VALUE = "value"
}
