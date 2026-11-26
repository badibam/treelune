package app.treelune.tools.path

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType

/**
 * The rows of a Path's two views (« Les vues »), shaped as entries are given
 * (ToolDataEntries.toMap), their values under `data.`: [STAGES], a row per stage of every run,
 * its state and `since`, its run; [MOVES], a row per move. A run is the row of its root.
 */
object PathRows {

    /** The Path's tool type, which its rows bear as entries do. */
    const val TOOLTYPE = "path"

    const val STAGES = "stages"
    const val MOVES = "moves"

    const val ANCESTORS = "ancestors"
    const val MARKS = "marks"
    const val RUN = "run"
    const val CLOSED = "closed"
    const val STAGE = "stage"
    const val NOTE = "note"
    const val VIRTUAL = "virtual"

    /**
     * The fields of a row of [STAGES] by path: its own, then each kind's fields. What designates
     * a stage, its kind and its state are choices, compared by `in`; its ancestors and its marks,
     * lists of them.
     */
    fun stageFields(grammar: PathGrammar): Map<String, FieldDefinition> = buildMap {
        val states = grammar.kinds.flatMap { k -> k.states.map { it.key } }.distinct()
        // Each state, kind and mark named as its kind names it, the first kind that has it
        val stateNames = grammar.kinds.flatMap { k -> k.states.map { it.key to it.name } }.reversed().toMap()
        val markNames = grammar.kinds.flatMap { k -> k.marks.map { it.key to it.name } }.reversed().toMap()
        put("id", PathSelf.choice("id", emptyList()))
        put("name", field("name", FieldType.TEXT))
        put("data.${PathSelf.KIND}", PathSelf.choice(PathSelf.KIND, grammar.kinds.map { it.key }, labels = grammar.kinds.associate { it.key to it.name }))
        listOf(PathSelf.PARENT, PathSelf.PREVIOUS, RUN).forEach { put("data.$it", PathSelf.choice(it, emptyList())) }
        put("data.$ANCESTORS", PathSelf.choice(ANCESTORS, emptyList(), multiple = true))
        put("data.${PathSelf.STATE}", PathSelf.choice(PathSelf.STATE, states, labels = stateNames))
        put("data.${PathSelf.LAST_MOVE}", PathSelf.choice(PathSelf.LAST_MOVE, states, labels = stateNames))
        put("data.$MARKS", PathSelf.choice(MARKS, grammar.kinds.flatMap { k -> k.marks.map { it.key } }.distinct(), multiple = true, labels = markNames))
        put("data.${PathSelf.POSITION}", field(PathSelf.POSITION, FieldType.NUMERIC))
        listOf(PathSelf.SINCE, PathSelf.LAST_MOVE_AT, PathSelf.RUN_START, PathSelf.RUN_END)
            .forEach { put("data.$it", field(it, FieldType.DATETIME)) }
        put("data.$CLOSED", field(CLOSED, FieldType.BOOLEAN))
        grammar.kinds.flatMap { it.fields }.forEach { putIfAbsent("data.${it.name}", it) }
    }

    /** The fields of a row of [MOVES] by path. */
    fun moveFields(grammar: PathGrammar): Map<String, FieldDefinition> = buildMap {
        put("id", PathSelf.choice("id", emptyList()))
        put("timestamp", field("timestamp", FieldType.DATETIME))
        put("data.$STAGE", PathSelf.choice(STAGE, emptyList()))
        put("data.$RUN", PathSelf.choice(RUN, emptyList()))
        put("data.${PathSelf.KIND}", PathSelf.choice(PathSelf.KIND, grammar.kinds.map { it.key }))
        put("data.${PathSelf.STATE}", PathSelf.choice(PathSelf.STATE, grammar.kinds.flatMap { k -> k.states.map { it.key } }.distinct() + listOf(Move.START, Move.END)))
        put("data.$NOTE", field(NOTE, FieldType.TEXT))
        put("data.$VIRTUAL", field(VIRTUAL, FieldType.BOOLEAN))
    }

    /** What the replay knows of a stage, for its row. */
    data class Known(
        val stage: Stage,
        val state: String?,
        val since: Long?,
        val lastMove: Move?,
        val previous: String?,
        val ancestors: List<String>,
        val marks: List<String>,
        val runStart: Long?,
        val runEnd: Long?,
        val closed: Boolean
    )

    /** The row of [STAGES] of [known]. */
    fun stageRow(known: Known, pathId: String): Map<String, Any?> = mapOf(
        "id" to known.stage.id,
        "tool_instance_id" to pathId,
        "tooltype" to TOOLTYPE,
        "timestamp" to null,
        "name" to known.stage.name,
        "data" to known.stage.fields + mapOf(
            PathSelf.KIND to known.stage.kind,
            PathSelf.PARENT to known.stage.parent,
            PathSelf.POSITION to known.stage.position,
            PathSelf.PREVIOUS to known.previous,
            ANCESTORS to known.ancestors,
            PathSelf.STATE to known.state,
            PathSelf.SINCE to known.since,
            PathSelf.LAST_MOVE to known.lastMove?.state,
            PathSelf.LAST_MOVE_AT to known.lastMove?.at,
            MARKS to known.marks,
            RUN to known.stage.run,
            PathSelf.RUN_START to known.runStart,
            PathSelf.RUN_END to known.runEnd,
            CLOSED to known.closed
        ),
        "extra" to null,
        "state" to null
    )

    /**
     * The row of [MOVES] of [move], the [index]th of [stage]'s journal (virtual ones after the
     * real, from the replay); its id stays the same while the journal before it does.
     */
    fun moveRow(stage: Stage, move: Move, index: Int, virtual: Boolean, pathId: String): Map<String, Any?> = mapOf(
        "id" to "${stage.id}:${if (virtual) "v" else ""}$index",
        "tool_instance_id" to pathId,
        "tooltype" to TOOLTYPE,
        "timestamp" to move.at,
        "name" to null,
        "data" to mapOf(
            STAGE to stage.id,
            PathSelf.KIND to stage.kind,
            PathSelf.STATE to move.state,
            NOTE to move.note,
            RUN to stage.run,
            VIRTUAL to virtual
        ),
        "extra" to null,
        "state" to null
    )

    private fun field(key: String, type: FieldType) = FieldDefinition(key, key, null, type, false, null)
}
