package app.treelune.tools.path

import app.treelune.core.database.entities.ToolDataEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * A stage stored as an entry of its Path: its name in the entry's name, its fields in `data`
 * (those of its kind), and what the Path's operations alone write in `state` — its kind, parent,
 * position, links and journal of moves, the state frozen when its run closed, its run's planned
 * end on a root, and on a root awaiting validation, since when.
 */
object PathStages {

    const val KIND = "kind"
    const val PARENT = "parent"
    const val POSITION = "position"
    const val LINKS = "links"
    const val MOVES = "moves"
    const val FINAL_STATE = "final_state"
    const val PLANNED_END = "planned_end"
    const val AWAITING = "awaiting"

    /** The state last told of the stage (notified, emitted): a note of the app's, out of the history. */
    const val TOLD = "told"

    /**
     * The stage [entity] holds.
     *
     * @throws IllegalStateException on an entry that holds no kind, a corrupt journal
     */
    fun of(entity: ToolDataEntity, byId: Map<String, ToolDataEntity>): Stage {
        val state = entity.state?.let { JSONObject(it) } ?: throw IllegalStateException("the stage ${entity.id} has no state")
        val kind = state.optString(KIND).takeIf { it.isNotEmpty() } ?: throw IllegalStateException("the stage ${entity.id} has no kind")
        val parent = state.optString(PARENT).takeIf { it.isNotEmpty() }
        return Stage(
            id = entity.id,
            run = runOf(entity.id, parent, byId),
            kind = kind,
            parent = parent,
            position = state.optInt(POSITION, 0),
            name = entity.name ?: "",
            links = objects(state.optJSONArray(LINKS)).map { Link(it.optString("from"), it.optString("type")) },
            fields = app.treelune.core.utils.JsonUtils.toMap(JSONObject(entity.data)),
            moves = objects(state.optJSONArray(MOVES)).map { Move.fromJson(it) },
            finalState = state.optString(FINAL_STATE).takeIf { it.isNotEmpty() },
            plannedEnd = (state.opt(PLANNED_END) as? Number)?.toLong()
        )
    }

    /** Every stage of [entities], each knowing its run. */
    fun all(entities: List<ToolDataEntity>): List<Stage> {
        val byId = entities.associateBy { it.id }
        return entities.map { of(it, byId) }
    }

    /** The root above [id], following parents; itself for a root. A parent gone makes it a root of its own. */
    private fun runOf(id: String, parent: String?, byId: Map<String, ToolDataEntity>): String {
        var current = id
        var up = parent
        val seen = mutableSetOf(id)
        while (up != null && byId.containsKey(up) && seen.add(up)) {
            current = up
            up = byId.getValue(up).state?.let { JSONObject(it).optString(PARENT).takeIf { p -> p.isNotEmpty() } }
        }
        return current
    }

    /** The state a stage of [kind] is written with, its journal [moves]. */
    fun state(kind: String, parent: String?, position: Int, links: List<Link>, moves: List<Move>, finalState: String? = null, plannedEnd: Long? = null): Map<String, Any?> = buildMap {
        put(KIND, kind)
        parent?.let { put(PARENT, it) }
        put(POSITION, position)
        put(LINKS, links.map { mapOf("from" to it.from, "type" to it.type) })
        put(MOVES, moves.map { m -> buildMap { put(Move.AT, m.at); put(Move.STATE, m.state); m.note?.let { put(Move.NOTE, it) } } })
        finalState?.let { put(FINAL_STATE, it) }
        plannedEnd?.let { put(PLANNED_END, it) }
    }

    /** [stage]'s state as stored, for a write that changes part of it. */
    fun stateOf(stage: Stage): Map<String, Any?> = state(stage.kind, stage.parent, stage.position, stage.links, stage.moves, stage.finalState, stage.plannedEnd)

    /** The schema of a stage's links, a list of objects no field type says (StateField.schema). */
    val LINKS_SCHEMA: JSONObject get() = JSONObject().put("type", "array").put("items", JSONObject()
        .put("type", "object")
        .put("properties", JSONObject().put("from", JSONObject().put("type", "string")).put("type", JSONObject().put("type", "string")))
        .put("required", JSONArray(listOf("from", "type")))
        .put("additionalProperties", false))

    /** The schema of a stage's journal of moves. */
    val MOVES_SCHEMA: JSONObject get() = JSONObject().put("type", "array").put("items", JSONObject()
        .put("type", "object")
        .put("properties", JSONObject()
            .put(Move.AT, JSONObject().put("type", "integer").put("format", "epoch-millis"))
            .put(Move.STATE, JSONObject().put("type", "string"))
            .put(Move.NOTE, JSONObject().put("type", "string")))
        .put("required", JSONArray(listOf(Move.AT, Move.STATE)))
        .put("additionalProperties", false))

    private fun objects(array: JSONArray?): List<JSONObject> = (0 until (array?.length() ?: 0)).mapNotNull { array!!.optJSONObject(it) }
}
