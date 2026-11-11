package app.treelune.core.ui.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.PeriodPicker

/**
 * The Sélection d'entrées brick's selector (docs/BRICKS.md): the thing (ThingBrowser), its period,
 * and for a tool the conditions on its entries and, when [offerFields], the fields kept.
 *
 * The browser is open while nothing final is chosen; once the thing reached has nothing deeper
 * [target] takes (a tool, for the pointer and a reading), it folds into a line that names it, and
 * "change" opens it again in place. A place [target] takes is chosen as soon as it is reached, so
 * a zone's period is offered while its tools are still listed.
 *
 * @param fields The fields of the tool reached, by path (rememberToolFields)
 * @param reference The name of what relative dates resolve against, null where a date is fixed
 *   when it is chosen
 * @param emptyPeriod What no period means in this context, said while none is set
 * @param offerPeriod Whether the period is chosen here; not where the context gives it (a chart's
 *   displayed period)
 * @param fixed Whether the thing is the context's own and stays it (the tool whose entries are
 *   exported): named without "change"
 * @param offerOrder Whether a tool's or a series' entries are read in order here, their sort and
 *   their count chosen below their conditions (not a trigger's entry, nor an export's); the sort
 *   is never empty, its default written as soon as the tool's fields are read (SelectionOrder)
 */
@Composable
fun SelectionPicker(
    draft: SelectionDraft,
    onChange: (SelectionDraft) -> Unit,
    target: ReferenceTarget,
    fields: Map<String, FieldDefinition>,
    reference: String?,
    offerFields: Boolean,
    emptyPeriod: String? = null,
    offerPeriod: Boolean = true,
    fixed: Boolean = false,
    offerOrder: Boolean = false
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    fun final(path: ThingPath) = path.kind in target.kinds && target.kinds.none { it in path.kind.below }

    // Where the browser stands: the draft's place, or while changing, the zone above it
    var browsing by rememberSaveable(stateSaver = ThingPathSaver) { mutableStateOf(if (final(draft.path)) null else draft.path) }
    val chosen = draft.level in target.kinds

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
        val open = browsing
        if (open != null) {
            ThingBrowser(open, { path ->
                if (path.kind in target.kinds || path.kind == ReferenceKind.APP) onChange(draft.at(path))
                browsing = if (final(path)) null else path
            }, target, series = ReferenceKind.VARIABLE in target.kinds)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(text = draft.entry?.let { s.shared("selection_entry_in_tool").format(it.name, draft.tool?.name ?: "") }
                        ?: listOfNotNull(draft.tool?.name ?: draft.variable?.name, draft.zone?.name).let { names ->
                            if (names.size == 2) s.shared("selection_place_in_zone").format(names[0], names[1]) else names.firstOrNull() ?: ""
                        }, type = TextType.SUBTITLE)
                }
                if (!fixed) UI.Button(type = ButtonType.DEFAULT, onClick = { browsing = draft.path.upTo(ReferenceKind.ZONE) }) {
                    UI.Text(text = s.shared("selection_change"), type = TextType.LABEL)
                }
            }
        }
        // One entry is that entry: nothing narrows it
        if (!chosen || draft.level == ReferenceKind.ENTRY) return@Column

        if (offerPeriod) {
            UI.Text(text = s.shared("pointer_period"), type = TextType.SUBTITLE)
            if (draft.period.isEmpty && emptyPeriod != null) UI.Text(text = emptyPeriod, type = TextType.CAPTION)
            PeriodPicker(draft.period, { onChange(draft.copy(period = it)) }, FieldType.DATETIME, reference)
        }

        // A tool's entries, or a variable's series, are narrowed by conditions on their fields
        val tool = draft.tool ?: draft.variable
        if (tool != null) {
            var editing by rememberSaveable { mutableStateOf(false) }
            UI.Button(type = ButtonType.DEFAULT, onClick = { editing = true }) {
                UI.Text(text = s.shared(if (offerFields) "pointer_filters_and_fields" else "selection_filters"), type = TextType.BODY)
            }
            // The period is said above: only the conditions and the fields here
            PointerDescription.narrowing(draft.copy(period = app.treelune.core.selection.EntryPeriod()), fields, s).forEach {
                UI.Text(text = it, type = TextType.CAPTION)
            }
            if (offerOrder) OrderSetting(draft, onChange, tool.id.takeIf { draft.variable == null }, fields, s)
            if (editing) {
                PointerFiltersDialog(
                    toolInstanceId = tool.id,
                    fields = fields,
                    filters = draft.filters,
                    chosenFields = draft.fields,
                    offerFields = offerFields,
                    reference = reference,
                    onDismiss = { editing = false },
                    onConfirm = { filters, kept ->
                        onChange(draft.copy(filters = filters, fields = kept))
                        editing = false
                    }
                )
            }
        }
    }
}

/**
 * The order and the count of [draft]'s entries: its sort (SortSetting) on the keys of the tool
 * [toolId] (a manual position included), or of the series' [fields]; then « The first N ».
 * A draft without a sort gets its default once the keys are read.
 */
@Composable
private fun OrderSetting(draft: SelectionDraft, onChange: (SelectionDraft) -> Unit, toolId: String?, fields: Map<String, FieldDefinition>, s: app.treelune.core.strings.StringsContext) {
    val keys = if (toolId != null) rememberToolFields(toolId, sortKeys = true) else fields
    androidx.compose.runtime.LaunchedEffect(keys, draft.sort.isEmpty()) {
        if (draft.sort.isEmpty() && keys.isNotEmpty()) {
            onChange(draft.copy(sort = if (toolId == null) app.treelune.core.selection.SelectionOrder.SERIES_DEFAULT
                else app.treelune.core.selection.SelectionOrder.defaultSort(dated = "timestamp" in keys)))
        }
    }
    if (draft.sort.isEmpty()) return
    SortSetting(s.shared("selection_order"), draft.sort, keys) { onChange(draft.copy(sort = it)) }
    UI.FormField(
        label = s.shared("selection_limit"),
        value = draft.limit?.toString() ?: "",
        onChange = { text -> onChange(draft.copy(limit = text.filter { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 })) },
        fieldType = app.treelune.core.ui.FieldType.NUMERIC,
        required = false
    )
    UI.Text(text = s.shared("selection_limit_hint"), type = TextType.CAPTION)
}

/** The browser's place across a rotation, null while folded. */
private val ThingPathSaver: Saver<ThingPath?, String> = Saver(
    save = { it?.toJson() ?: "" },
    restore = { saved -> saved.takeIf { it.isNotEmpty() }?.let { ThingPath.fromJson(it) } }
)
