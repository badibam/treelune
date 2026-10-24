package app.treelune.tools.sequence

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.files.FileEntity
import app.treelune.core.files.FileKind
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A step that follows an entry's sound (docs/TOOLS.md, « Session (Sequence) »): read when a session
 * starts, it lasts the sound when it ends with it, and keeps where it came from; the entry gone, it
 * plays nothing, is ended by hand, and says its source deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SequenceMediaTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a step following an entry's sound lasts it, then says its source deleted once the entry goes`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Calm"))
        val sheets = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "notes",
            "config" to mapOf("name" to "Sheets", "extra_fields" to listOf(mapOf("display_name" to "Induction", "type" to "FILE", "always_visible" to false,
                "config" to mapOf("kinds" to listOf("AUDIO"), "max" to 1))))
        ))
        assertTrue(sheets.error, sheets.isSuccess)
        val entry = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to sheets.data!!["tool_instance_id"], "data" to mapOf("content" to "Session 24")))
        val entryId = entry.data!!["id"] as String
        // A sound the entry already owns: Robolectric reads no medium, its row is written as a set-aside one would be
        AppDatabase.getDatabase(context).fileDao().insert(FileEntity(
            "induction1", null, entryId, null, FileKind.AUDIO.name, "MP3", "induction.mp3", 10, null, null, 95_000L, null, 0L))
        assertTrue(coordinator.processUserAction("tool_data.update", mapOf("id" to entryId, "extra" to mapOf("induction" to listOf("induction1")))).isSuccess)

        val follows = JSONObject().put("reading", JSONObject()
            .put("selection", JSONObject().put("target", JSONObject().put("kind", "ENTRY").put("id", entryId)))
            .put("field", "extra.induction").put("reduction", "LAST"))
        val config = JSONObject().put("steps", org.json.JSONArray().put(JSONObject()
            .put("kind", "step").put("name", "Induction").put("end", "media").put("media", follows)))
        val session = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone.data!!["zone_id"], "tooltype" to "sequence",
            "config" to app.treelune.core.utils.JsonUtils.toMap(JSONObject(config.toString()).put("name", "Calm session"))))
        assertTrue("the term follows a sound field of a single file: ${session.error}", session.isSuccess)

        val step = SequenceMedia.resolve(context, SequencePlan.unroll(config), 0L, "deleted").single()
        assertEquals(StepEnd.TIMED, step.end)
        assertEquals(95_000L, step.duration)
        assertEquals("induction1", step.media!!.id)
        assertTrue(step.source!!.endsWith(" › Induction"))

        assertTrue(coordinator.processUserAction("tool_data.delete", mapOf("id" to entryId)).isSuccess)
        val gone = SequenceMedia.resolve(context, SequencePlan.unroll(config), 0L, "deleted").single()
        assertEquals(StepEnd.MANUAL, gone.end)
        assertEquals(null, gone.media)
        assertEquals("deleted", gone.source)
    }
}
