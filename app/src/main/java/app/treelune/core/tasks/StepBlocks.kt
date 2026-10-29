package app.treelune.core.tasks

import app.treelune.core.ai.data.RoutineMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a `run_routine` step joins to the end of its routine's message: a list of blocks under
 * `blocks`, in their order, written with the routine's own blocks (RoutineMessage) beside two that
 * the run fills:
 *
 * - `{"type": "text", "text"}`, a text as written;
 * - `{"type": "pointer", "pointer": {"selection", "attach"}}`, a pointer to a thing chosen, as a
 *   routine's message holds one;
 * - `{"type": "entry", "entry": <input>, "attach": true}`, an entry the run gives — the one a
 *   trigger gave, one an event carried, the one an earlier step created, or one designated —,
 *   joined whole (`attach` true) or only mentioned, the AI reading what it wants of it;
 * - `{"type": "value", "value": <input>}`, a value the run reads or was given, joined as a text.
 *
 * When the step runs, its blocks become a routine's blocks (text and pointer), which
 * `routines.execute_manual` takes under `joined`.
 */
object StepBlocks {

    const val BLOCKS = "blocks"
    const val TYPE = "type"
    const val TEXT = RoutineMessage.TEXT
    const val POINTER = RoutineMessage.POINTER
    const val ENTRY = "entry"
    const val VALUE = "value"
    const val ATTACH = "attach"

    /** One block as stored. */
    sealed interface Block {
        /** A text or a pointer, a routine's block as it is ([json]). */
        data class Fixed(val json: JSONObject) : Block
        /** An entry the run gives, by [input], joined whole when [attach], mentioned otherwise. */
        data class Entry(val input: JSONObject, val attach: Boolean) : Block
        /** A value the run reads or was given, by [input]. */
        data class Value(val input: JSONObject) : Block
    }

    /** The blocks [step] stores; none when it has none. */
    fun of(step: JSONObject): List<Block> = read(step.optJSONArray(BLOCKS) ?: JSONArray())

    /**
     * The blocks [json] holds, in their order.
     *
     * @throws IllegalArgumentException naming the first block that does not read: its index from 1
     */
    fun read(json: JSONArray): List<Block> = (0 until json.length()).map { i ->
        val block = json.optJSONObject(i) ?: throw IllegalArgumentException("${i + 1}")
        when (block.optString(TYPE)) {
            TEXT, POINTER -> Block.Fixed(block)
            ENTRY -> Block.Entry(block.optJSONObject(ENTRY) ?: throw IllegalArgumentException("${i + 1}"), block.optBoolean(ATTACH, true))
            VALUE -> Block.Value(block.optJSONObject(VALUE) ?: throw IllegalArgumentException("${i + 1}"))
            else -> throw IllegalArgumentException("${i + 1}")
        }
    }

    /** [block] in its stored form. */
    fun toJson(block: Block): JSONObject = when (block) {
        is Block.Fixed -> block.json
        is Block.Entry -> JSONObject().put(TYPE, ENTRY).put(ENTRY, block.input).put(ATTACH, block.attach)
        is Block.Value -> JSONObject().put(TYPE, VALUE).put(VALUE, block.input)
    }

    fun toJson(blocks: List<Block>): JSONArray = JSONArray().apply { blocks.forEach { put(toJson(it)) } }

    /** Whether [input] designates an entry rather than a value: the one a trigger gave, the one a step created, one designated. */
    fun designatesEntry(input: TaskSteps.Input): Boolean = when (input) {
        is TaskSteps.Input.Received -> input.field == null && input.value == null
        is TaskSteps.Input.Result -> input.value == null
        is TaskSteps.Input.Of -> (input.term as? app.treelune.core.terms.Term.Constant)?.value
            ?.let { app.treelune.core.fields.ReferenceTarget.referenceOf(it)?.kind == app.treelune.core.selection.ReferenceKind.ENTRY } == true
    }

    /** The pointers among [blocks] that name a thing chosen, in the stored form of their pointer. */
    fun fixedPointers(blocks: List<Block>): List<JSONObject> =
        blocks.filterIsInstance<Block.Fixed>().filter { it.json.optString(TYPE) == POINTER }.mapNotNull { it.json.optJSONObject(POINTER) }
}
