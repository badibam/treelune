package app.treelune.tools.path

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.toFieldDefinition
import org.json.JSONArray
import org.json.JSONObject

/*
 * The Path's model (docs/design/progression.md): its grammar, which the config holds — the kinds
 * of stage, their states, marks and effects, the link types —, and its stages, which are entries,
 * each of its run, with its parent, its position, its links and its moves. Nothing computed is
 * stored: a stage's state comes from replaying its moves (PathReplay).
 */

/**
 * One move of a stage's journal: at [at], it went to [state]; fixed, without a field of its own.
 * The run's own moves are [START] and [END], on its root.
 */
data class Move(val at: Long, val state: String, val note: String? = null) {

    fun toJson(): JSONObject = JSONObject().put(AT, at).put(STATE, state).apply { note?.let { put(NOTE, it) } }

    companion object {
        const val AT = "at"
        const val STATE = "state"
        const val NOTE = "note"

        /** The move that opens a run, on its root. */
        const val START = "start"

        /** The move that closes a run, on its root. */
        const val END = "end"

        /** @throws IllegalArgumentException on a move without its instant or its state */
        fun fromJson(json: JSONObject): Move = Move(
            at = (json.opt(AT) as? Number)?.toLong() ?: throw IllegalArgumentException("a move without its instant: $json"),
            state = json.optString(STATE).takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("a move without its state: $json"),
            note = json.optString(NOTE).takeIf { it.isNotEmpty() }
        )
    }
}

/** A link arriving on a stage, from the stage [from], of the Path's link type [type]. */
data class Link(val from: String, val type: String) {
    fun toJson(): JSONObject = JSONObject().put("from", from).put("type", type)
}

/**
 * A stage of a run, as the replay reads it.
 *
 * @property run The id of its run's root: a run is its root, the stage without a parent
 * @property parent The stage containing it; null for the root
 * @property position Its place among its parent's content, the order the rules read (`previous`)
 * @property fields Its values, of its kind's plan fields and fields to fill, by key
 * @property moves Its journal, in the order written
 * @property finalState The state it was frozen in when its run closed; null while it is open
 * @property plannedEnd On the root, the date its run closes at, which its rhythm set; null otherwise
 */
data class Stage(
    val id: String,
    val run: String,
    val kind: String,
    val parent: String?,
    val position: Int,
    val name: String,
    val links: List<Link> = emptyList(),
    val fields: Map<String, Any?> = emptyMap(),
    val moves: List<Move> = emptyList(),
    val finalState: String? = null,
    val plannedEnd: Long? = null
) {
    val isRoot: Boolean get() = parent == null
}

/** How a state shows on a box: a colour of the icons' palette, filled or outlined, faded (`paint`). */
data class Paint(val color: String?, val fill: Boolean, val faded: Boolean) {
    companion object {
        fun fromJson(json: JSONObject?): Paint? = json?.let {
            Paint(it.optString("color").takeIf { c -> c.isNotEmpty() }, it.optString("fill") == "full", it.optBoolean("faded", false))
        }
    }
}

/**
 * A state of a kind (« Les états »). The state of a stage is the first of its kind's list that is
 * active: a [manual] or [record] state when the stage's last move bears its key; a state with a
 * rule ([whenRule], not [record]) when it holds; the last, without a rule, as « otherwise ».
 *
 * @property noteIf When it may be noted, by anyone; noted always without it
 * @property record The rule is written once, by the Path's judge, as a real move, when it holds
 *   on a stage not in this state; never judged by the replay itself
 * @property closesRun On the root: the run closes when it is reached
 */
data class StateRule(
    val key: String,
    val name: String,
    val manual: Boolean,
    val whenRule: JSONObject?,
    val noteIf: JSONObject?,
    val record: Boolean,
    val closesRun: Boolean,
    val paint: Paint?,
    val backLabel: String?,
    val notify: Boolean,
    val emitEvent: String?
) {
    /** Whether it is active through the stage's last move alone. */
    val byMove: Boolean get() = manual || record
}

/** A mark of a kind: an indicator computed beside the state, top-down, never read by a state's rule. */
data class MarkRule(val key: String, val name: String, val whenRule: JSONObject, val paint: Paint?)

/**
 * An effect of a kind: when [on] becomes true on a stage of the kind, after a move, each stage of
 * the run [setFilters] keep (judged on `stages`, `self` being that stage) gets a virtual move to
 * [setState], at the same instant, never written.
 */
data class EffectRule(val on: JSONObject, val setFilters: JSONArray, val setState: String)

/**
 * A kind of stage (« Les briques »): its fields in two lists, what it may contain, its states,
 * marks and effects.
 */
data class Kind(
    val key: String,
    val name: String,
    val icon: String?,
    val contains: List<String>,
    val planFields: List<FieldDefinition>,
    val fillFields: List<FieldDefinition>,
    val states: List<StateRule>,
    val marks: List<MarkRule>,
    val effects: List<EffectRule>,
    val readout: JSONObject?,
    val inParts: Boolean
) {
    val fields: List<FieldDefinition> get() = planFields + fillFields

    fun state(key: String): StateRule? = states.firstOrNull { it.key == key }
}

/** A link type: what its links mean comes only from the rules that read them; it draws in its [stroke], a line of the diagram. */
data class LinkType(val key: String, val name: String, val stroke: app.treelune.core.drawing.diagram.DiagramLine, val color: String?)

/** How a run opens (the rhythm): by hand, at each unit of the calendar, or as soon as the one before closes. */
enum class Opens(val key: String) { MANUAL("manual"), SCHEDULED("scheduled"), CHAINED("chained") }

/**
 * A Path's grammar, its config read: the kinds, the root's among them, the link types, the rhythm.
 *
 * @property confirm At closing, the root's state is offered for validation rather than frozen
 * @property opens How a run opens
 * @property unit For a run opened at each unit of the calendar, the unit: it runs to its end
 * @property lasts For a run opened by hand or chained, how long it lasts, in milliseconds; null
 *   for a run without a planned end
 */
data class PathGrammar(
    val kinds: List<Kind>,
    val rootKind: String,
    val linkTypes: List<LinkType>,
    val confirm: Boolean,
    val opens: Opens = Opens.MANUAL,
    val unit: app.treelune.core.selection.PeriodType? = null,
    val lasts: Long? = null
) {
    fun kind(key: String): Kind? = kinds.firstOrNull { it.key == key }

    companion object {
        const val KINDS = "kinds"
        const val ROOT_KIND = "root_kind"
        const val LINK_TYPES = "link_types"
        const val CONFIRM = "confirm"
        const val OPENS = "run_opens"
        const val UNIT = "run_unit"
        const val LASTS = "run_lasts"

        /**
         * The grammar a config holds, read without judging it (PathCheck says what is wrong).
         *
         * @throws IllegalArgumentException on a part that does not read at all
         */
        fun fromJson(config: JSONObject): PathGrammar = PathGrammar(
            kinds = objects(config.optJSONArray(KINDS)).map { kind(it) },
            rootKind = config.optString(ROOT_KIND),
            linkTypes = objects(config.optJSONArray(LINK_TYPES)).map {
                LinkType(it.optString("key"), it.optString("name"),
                    app.treelune.core.drawing.diagram.DiagramLine.of(it.optString("stroke")) ?: app.treelune.core.drawing.diagram.DiagramLine.NORMAL,
                    it.optString("color").takeIf { c -> c.isNotEmpty() })
            },
            confirm = config.optBoolean(CONFIRM, false),
            opens = Opens.entries.firstOrNull { it.key == config.optString(OPENS) } ?: Opens.MANUAL,
            unit = app.treelune.core.selection.PeriodType.entries.firstOrNull { it.name == config.optString(UNIT) },
            lasts = (config.opt(LASTS) as? Number)?.toLong()?.takeIf { it > 0 }
        )

        private fun kind(json: JSONObject) = Kind(
            key = json.optString("key"),
            name = json.optString("name"),
            icon = json.optString("icon").takeIf { it.isNotEmpty() },
            contains = strings(json.optJSONArray("contains")),
            planFields = objects(json.optJSONArray("plan_fields")).map { it.toFieldDefinition() },
            fillFields = objects(json.optJSONArray("fill_fields")).map { it.toFieldDefinition() },
            states = objects(json.optJSONArray("states")).map { state ->
                StateRule(
                    key = state.optString("key"),
                    name = state.optString("name").ifEmpty { state.optString("key") },
                    manual = state.optBoolean("manual", false),
                    whenRule = state.optJSONObject("when"),
                    noteIf = state.optJSONObject("note_if"),
                    record = state.optBoolean("record", false),
                    closesRun = state.optBoolean("closes_run", false),
                    paint = Paint.fromJson(state.optJSONObject("paint")),
                    backLabel = state.optString("back_label").takeIf { it.isNotEmpty() },
                    notify = state.optBoolean("notify", false),
                    emitEvent = app.treelune.core.fields.ReferenceTarget.referenceOf(state.opt("emit_event"))?.id
                )
            },
            marks = objects(json.optJSONArray("marks")).map { mark ->
                MarkRule(mark.optString("key"), mark.optString("name").ifEmpty { mark.optString("key") }, mark.optJSONObject("when") ?: JSONObject(), Paint.fromJson(mark.optJSONObject("paint")))
            },
            effects = objects(json.optJSONArray("effects")).map { effect ->
                // The stages it gives a move to: a selection of the Path's own stages, its filters judged with self
                val set = effect.optJSONObject("set") ?: JSONObject()
                EffectRule(effect.optJSONObject("on") ?: JSONObject(), set.optJSONObject("stages")?.optJSONArray("filters") ?: JSONArray(), set.optString("state"))
            },
            readout = json.optJSONObject("readout"),
            inParts = json.optString("layout") == "parts"
        )

        private fun objects(array: JSONArray?): List<JSONObject> = (0 until (array?.length() ?: 0)).mapNotNull { array!!.optJSONObject(it) }
        private fun strings(array: JSONArray?): List<String> = (0 until (array?.length() ?: 0)).mapNotNull { array!!.optString(it).takeIf { s -> s.isNotEmpty() } }
    }
}
