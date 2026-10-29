package app.treelune.core.tasks

import app.treelune.core.terms.Term
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A `run_routine` step's blocks read back as written, in their order; an entry block tells joined
 * from mentioned; what designates an entry is told from a value.
 */
class StepBlocksTest {

    private val stored = JSONArray("""[
        {"type": "text", "text": "The session:"},
        {"type": "entry", "entry": {"received": {}}, "attach": false},
        {"type": "value", "value": {"variable": "v-kcal"}},
        {"type": "pointer", "pointer": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "weight"}}, "attach": {"config": false, "entries": true}}}
    ]""")

    @Test
    fun theBlocks_readBackAsWritten_inTheirOrder() {
        val blocks = StepBlocks.read(stored)
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is StepBlocks.Block.Fixed)
        assertFalse((blocks[1] as StepBlocks.Block.Entry).attach)
        assertTrue(blocks[2] is StepBlocks.Block.Value)
        assertEquals(stored.toString(), StepBlocks.toJson(blocks).toString())
    }

    @Test
    fun anEntryBlock_isJoinedWhole_unlessSaidOtherwise() {
        val block = StepBlocks.read(JSONArray("""[{"type": "entry", "entry": {"result": {"step": 0}}}]""")).single() as StepBlocks.Block.Entry
        assertTrue(block.attach)
    }

    @Test
    fun aBlockOfNoKnownType_isRefused() {
        assertThrows(IllegalArgumentException::class.java) { StepBlocks.read(JSONArray("""[{"type": "image", "image_id": "i"}]""")) }
        assertThrows(IllegalArgumentException::class.java) { StepBlocks.read(JSONArray("""[{"type": "entry"}]""")) }
    }

    @Test
    fun anEntry_isToldFromAValue() {
        assertTrue(StepBlocks.designatesEntry(TaskSteps.Input.Received(null)))
        assertTrue(StepBlocks.designatesEntry(TaskSteps.Input.Result(1, null)))
        assertTrue(StepBlocks.designatesEntry(TaskSteps.Input.Of(Term.Constant(mapOf("kind" to "ENTRY", "id" to "e1")))))
        assertFalse(StepBlocks.designatesEntry(TaskSteps.Input.Received("data.title")))
        assertFalse(StepBlocks.designatesEntry(TaskSteps.Input.Received(null, "fiche")))
        assertFalse(StepBlocks.designatesEntry(TaskSteps.Input.Result(0, "temperature")))
        assertFalse(StepBlocks.designatesEntry(TaskSteps.Input.Of(Term.Constant("Hello"))))
    }

    @Test
    fun theFixedPointers_areThoseNamingAThing() {
        assertEquals(listOf("weight"), StepBlocks.fixedPointers(StepBlocks.read(stored)).map { it.getJSONObject("selection").getJSONObject("target").getString("id") })
    }
}
