package app.treelune.core.views

import android.content.Context
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.SortKey
import app.treelune.core.strings.StringsContext
import org.json.JSONObject

/**
 * A view: rows a thing of the app computes when they are read, never stored, read as its entries
 * would be (docs/BRICKS.md, « View »). Each row is shaped as an entry — `timestamp`, its values
 * under `data.`, an id that stays the same from one read to the next (the card, the brick, the
 * step) — so that everything that reads entries reads a view without changing: its filters, by
 * the entries' own SQL (ViewRows), its sort, its readings and groupings, a chart, a readout.
 *
 * A selection names one with `view` beside its target (EntrySelection): a variable's series
 * (`series`), a tool's own views, which its type declares (ToolTypeContract.views).
 */
interface View {

    /** Its rows' fields by path, as a tool's are (EntryFilters.filterableFields). */
    val fields: Map<String, FieldDefinition>

    /** Whether a selection of it takes a period, on its rows' `timestamp`. */
    val period: ViewPeriod

    /**
     * Its rows at the instant [at], for a view that takes a period those of [start] to [end]
     * (its period's bounds, null for no limit), in no particular order.
     *
     * @throws IllegalStateException when they cannot be computed, with what to say
     */
    suspend fun rows(start: Long?, end: Long?, at: Long): List<ToolDataEntity>
}

/** What a view does with a selection's period. */
enum class ViewPeriod {
    /** A period is refused: its rows have no date of their own (a card, a brick). */
    NONE,
    /** A period narrows its rows by their date. */
    OPTIONAL,
    /** A period is needed, with a start: its rows are computed from it (a series' steps). */
    NEEDS_START
}

/**
 * A view a tool type declares (ToolTypeContract.views): its [name], under which a selection asks
 * for it, its default order, and how a tool of the type opens it.
 */
interface ToolView {

    /** Its name in a selection's `view`, a key in snake_case. */
    val name: String

    /** The order a selection of it is written with when none is given (SelectionOrder). */
    val defaultSort: List<SortKey>

    /**
     * The view of the tool [toolInstanceId], whose config is [config].
     *
     * @throws IllegalStateException when it cannot be opened, with what to say
     */
    suspend fun open(context: Context, toolInstanceId: String, config: JSONObject, s: StringsContext): View

    /**
     * Its rows' fields by path for a tool whose config is [config], as [open] gives them: what a
     * config being written, without a tool yet, offers to sort its rows by (SettingNode.Sort.view).
     *
     * @throws IllegalStateException when the config does not read, with what to say
     */
    suspend fun fields(context: Context, config: JSONObject, s: StringsContext): Map<String, FieldDefinition>
}
