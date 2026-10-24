package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.ReferenceName
import app.treelune.core.fields.loadReferenceNames
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An entry designated down to one of its fields (docs/BRICKS.md, « Thing »): named after its
 * entry, deleted once the field is gone from its tool; written into a REFERENCE value only when
 * the entry has that field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReferenceFieldNamesTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `an entry's field is named after its entry, deleted once the field is gone`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Calm"))
        val field = mapOf("display_name" to "Induction", "type" to "TEXT", "always_visible" to false)
        val sheets = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "notes", "config" to mapOf("name" to "Sheets", "extra_fields" to listOf(field))
        ))
        assertTrue(sheets.error, sheets.isSuccess)
        val sheetsId = sheets.data!!["tool_instance_id"] as String
        val entry = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to sheetsId, "data" to mapOf("content" to "x")))
        assertTrue(entry.error, entry.isSuccess)
        val induction = Reference(ReferenceKind.ENTRY, entry.data!!["id"] as String, "extra.induction")

        val whole = Reference(ReferenceKind.ENTRY, induction.id)
        val label = (loadReferenceNames(listOf(whole), context)[whole] as ReferenceName.Named).name
        assertEquals(ReferenceName.Named("$label › Induction"), loadReferenceNames(listOf(induction), context)[induction])

        // A sheet's reference to another's field: refused on a field the entry does not have
        val log = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "notes",
            "config" to mapOf("name" to "Log", "extra_fields" to listOf(mapOf("display_name" to "Played", "type" to "REFERENCE", "always_visible" to false,
                "config" to mapOf("target" to mapOf("kinds" to listOf("ENTRY"))))))
        ))
        assertTrue(log.error, log.isSuccess)
        suspend fun played(path: String) = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to log.data!!["tool_instance_id"],
            "data" to mapOf("content" to "y"), "extra" to mapOf("played" to mapOf("kind" to "ENTRY", "id" to induction.id, "field" to path))))
        assertTrue(played("extra.induction").isSuccess)
        assertFalse(played("extra.nothing").isSuccess)

        val emptied = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to sheetsId, "config" to mapOf("name" to "Sheets", "extra_fields" to emptyList<Any>())))
        assertTrue(emptied.error, emptied.isSuccess)
        assertEquals(ReferenceName.Deleted, loadReferenceNames(listOf(induction), context)[induction])
    }
}
