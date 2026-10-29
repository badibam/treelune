package app.treelune.core.ui.selectors

import androidx.compose.runtime.Composable
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.reading.GroupedReading
import app.treelune.core.reading.ReadingGroup
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.EntryPeriod
import app.treelune.core.selection.PeriodType
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import org.json.JSONObject

/** The choice beside the fields: no grouping. */
private const val NONE = "none"

/**
 * A reading's grouping (ReadingGroup), under its field and reduction: the field it groups by
 * (« Regrouper par »), a date's unit, the groups kept (all, or those whose value holds a
 * condition, ConditionSetting with the group's value on its left), then the reduction of the
 * groups. Grouped by the entries' date, the units of [period] read now are said, those to come
 * and those the period cuts: the traps of a period show without being refused.
 *
 * @param group Its stored form, null for none
 * @param value The field each group has a value of: the reading's field reduced, or a count
 * @param onChange The grouping, null to group no more
 */
@Composable
fun ReadingGroupPicker(
    group: JSONObject?,
    fields: Map<String, FieldDefinition>,
    value: FieldDefinition,
    period: EntryPeriod,
    s: StringsContext,
    where: ReadingContext,
    onChange: (JSONObject?) -> Unit
) {
    fun edit(change: JSONObject.() -> Unit) = onChange(JSONObject((group ?: JSONObject()).toString()).apply(change))
    val path = group?.optString(ReadingGroup.FIELD)?.takeIf { it.isNotEmpty() }

    FieldPicker(
        label = s.shared("reading_group_by"),
        fields = fields,
        selected = path?.let { FieldPick.Path(it) } ?: FieldPick.Other(NONE),
        onSelect = { pick ->
            when (pick) {
                is FieldPick.Path -> edit {
                    put(ReadingGroup.FIELD, pick.path)
                    // A date is rounded to a day at first, anything else is not
                    if (fields[pick.path]?.type in DATES) put(ReadingGroup.UNIT, optString(ReadingGroup.UNIT).ifEmpty { PeriodType.DAY.name }) else remove(ReadingGroup.UNIT)
                    if (!has(ReadingGroup.REDUCTION)) put(ReadingGroup.REDUCTION, Reduction.COUNT.name)
                }
                is FieldPick.Other -> onChange(null)
            }
        },
        accepts = { it.type in ReadingGroup.GROUPED },
        others = mapOf(NONE to s.shared("reading_group_none"))
    )
    if (group == null || path == null) return
    val by = fields[path] ?: return

    if (by.type in DATES) {
        val unit = ReadingGroup.UNITS.firstOrNull { it.name == group.optString(ReadingGroup.UNIT) }
        fun name(unit: PeriodType) = s.shared("period_type_${unit.name.lowercase()}")
        UI.FormSelection(
            label = s.shared("reading_group_unit"),
            options = ReadingGroup.UNITS.map(::name),
            selected = unit?.let(::name) ?: "",
            onSelect = { text -> edit { put(ReadingGroup.UNIT, ReadingGroup.UNITS.first { name(it) == text }.name) } },
            required = true
        )
        if (path == TIMESTAMP && unit != null) Span(period, unit, s)
    }

    // All the groups, or those whose value holds a condition
    val keep = group.optJSONObject(ReadingGroup.KEEP)
    val all = s.shared("reading_group_keep_all")
    val some = s.shared("reading_group_keep_some")
    UI.FormSelection(
        label = s.shared("reading_group_keep"),
        options = listOf(all, some),
        selected = if (keep != null) some else all,
        onSelect = { text -> edit { if (text == all) remove(ReadingGroup.KEEP) else if (!has(ReadingGroup.KEEP)) put(ReadingGroup.KEEP, JSONObject()) } },
        required = true
    )
    if (keep != null) {
        ConditionSetting(s.shared("reading_group_keep_where"), keep, value.copy(displayName = s.shared("reading_group_value")),
            { condition -> edit { put(ReadingGroup.KEEP, condition) } }, s, where)
    }

    val reductions = ReadingGroup.reductions(value).toList()
    ReductionPicker(
        label = s.shared("reading_group_reduction"),
        reductions = reductions,
        selected = reductions.firstOrNull { it.name == group.optString(ReadingGroup.REDUCTION) },
        onSelect = { reduction -> edit { put(ReadingGroup.REDUCTION, reduction.name) } },
        s = s
    )
}

/**
 * The units of [period] read now: « 7 days, 4 to come », « 8 days, 2 cut short ». Nothing without
 * a start, which the context gives (a goal's attempt) or the reading is refused for.
 */
@Composable
private fun Span(period: EntryPeriod, unit: PeriodType, s: StringsContext) {
    val calendar = TimeResolver.at(System.currentTimeMillis())
    val (start, end) = try { period.instants(calendar) } catch (e: IllegalArgumentException) { return }
    start ?: return
    val span = GroupedReading.span(start, end ?: calendar.reference, unit, calendar)
    val said = s.shared("reading_group_span").format(span.units, s.shared("reading_group_units_${unit.name.lowercase()}")) +
        (if (span.toCome > 0) s.shared("reading_group_span_to_come").format(span.toCome) else "") +
        (if (span.cut > 0) s.shared("reading_group_span_cut").format(span.cut) else "")
    UI.Text(said, TextType.CAPTION)
}

private const val TIMESTAMP = "timestamp"
private val DATES = setOf(FieldType.DATE, FieldType.DATETIME)
