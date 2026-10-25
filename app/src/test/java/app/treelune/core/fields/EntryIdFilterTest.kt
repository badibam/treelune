package app.treelune.core.fields

import android.content.Context
import app.treelune.core.conditions.Conditions
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.ui.selectors.Named
import app.treelune.core.ui.selectors.PointerSelection
import app.treelune.core.ui.selectors.SelectionDraft
import app.treelune.core.ui.selectors.ThingPath
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An entry read by its id (docs/DATA.md, « Filters »): a filter on `id` with `=` or `in` reads the
 * entries designated, never offered among a tool's fields; a message's pointer reaching an entry
 * points at it alone, nothing narrowing it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryIdFilterTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a filter on id reads the entry designated, and nothing else is taken with it`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Notes"))
        val tool = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone.data!!["zone_id"], "tooltype" to "notes", "config" to mapOf("name" to "Ideas")))
        val toolId = tool.data!!["tool_instance_id"] as String
        val ids = listOf("one", "two", "three").map { text ->
            coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to toolId, "data" to mapOf("content" to text))).data!!["id"] as String
        }
        suspend fun read(op: String, value: Any) = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolId,
            "filters" to listOf(JsonUtils.toMap(Conditions.onField(EntryFilters.ID, op, value)))))

        val one = read("=", ids[1])
        assertTrue(one.error, one.isSuccess)
        assertEquals(listOf(ids[1]), (one.data!!["entries"] as List<*>).map { (it as Map<*, *>)["id"] })
        val two = read("in", JSONArray(listOf(ids[0], ids[2])))
        assertEquals(setOf(ids[0], ids[2]), (two.data!!["entries"] as List<*>).map { (it as Map<*, *>)["id"] }.toSet())
        assertFalse("an id is met exactly", read("<", ids[0]).isSuccess)
        assertFalse("never a field of the tool", EntryFilters.ID in ToolFields.filterable(toolId, context, app.treelune.core.strings.Strings.`for`(context = context)))
    }

    @Test
    fun `a pointer reaching an entry points at it alone`() {
        val draft = SelectionDraft().at(ThingPath(Named("z", "Notes"), Named("t", "Ideas", "notes"), Named("e", "Two")))
        assertEquals(ReferenceKind.ENTRY, draft.level)
        val selection = draft.selection()
        assertEquals(ReferenceKind.ENTRY, selection.target.kind)
        assertTrue(selection.period.isEmpty && selection.filters.length() == 0 && selection.fields == null)
        assertTrue(PointerSelection(draft).complete)
        assertEquals(ReferenceKind.TOOL_INSTANCE, draft.at(ThingPath(Named("z", "Notes"), Named("t", "Ideas", "notes"))).level)
    }
}
