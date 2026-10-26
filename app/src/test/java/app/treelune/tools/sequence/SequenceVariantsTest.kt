package app.treelune.tools.sequence

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.files.FileEntity
import app.treelune.core.files.FileKind
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.Strings
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The variants of a session (docs/TOOLS.md, « Session (Sequence) »): a timer and a medium each
 * make one step out of what is answered at launch — a timer of the length given, the config's
 * without one; the sound of an entry chosen among a selection's, lasting it, its entry kept
 * designated — and `complete` takes the same answer, a medium's entry required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SequenceVariantsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val s get() = Strings.`for`(tool = "sequence", context = context)

    private fun steps(made: SequenceVariants.Made) = (made as SequenceVariants.Made.Steps).steps

    @Test
    fun `a timer is one step of the length answered, the config's without one`() = runBlocking {
        val config = JSONObject().put("name", "Sitting").put(SequenceVariants.VARIANT, SequenceVariants.TIMER).put(SequenceVariants.TIMER_DURATION, 900_000L)

        val answered = steps(SequenceVariants.steps(context, config, SequenceVariants.Answer.Length(1_200_000L), 0L, s)).single()
        assertEquals("Sitting", answered.name)
        assertEquals(StepEnd.TIMED, answered.end)
        assertEquals(1_200_000L, answered.duration)
        assertEquals(900_000L, steps(SequenceVariants.steps(context, config, SequenceVariants.Answer.None, 0L, s)).single().duration)
    }

    @Test
    fun `a medium plays the entry chosen among its selection's, which complete requires`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Calm"))
        val library = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "notes",
            "config" to mapOf("name" to "Library", "extra_fields" to listOf(mapOf("display_name" to "Audio", "type" to "FILE", "always_visible" to false,
                "config" to mapOf("kinds" to listOf("AUDIO"), "max" to 1))))
        ))
        assertTrue(library.error, library.isSuccess)
        val libraryId = library.data!!["tool_instance_id"] as String
        suspend fun entry(content: String) = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to libraryId, "data" to mapOf("content" to content))).data!!["id"] as String
        val played = entry("Breathing 3")
        val silent = entry("Without a sound")
        AppDatabase.getDatabase(context).fileDao().insert(FileEntity(
            "breathing3", null, played, null, FileKind.AUDIO.name, "MP3", "breathing3.mp3", 10, null, null, 720_000L, null, 0L))
        assertTrue(coordinator.processUserAction("tool_data.update", mapOf("id" to played, "extra" to mapOf("audio" to listOf("breathing3")))).isSuccess)

        val config = JSONObject()
            .put("name", "Guided")
            .put(SequenceVariants.VARIANT, SequenceVariants.MEDIA)
            .put(SequenceVariants.MEDIA_SOURCE, JSONObject().put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", libraryId)))
            .put(SequenceVariants.MEDIA_FIELD, "extra.audio")
            .put(SequenceVariants.MEDIA_SORT, JSONArray().put(JSONObject().put("field", "created_at").put("direction", "asc")))
        val session = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone.data!!["zone_id"], "tooltype" to "sequence",
            "config" to app.treelune.core.utils.JsonUtils.toMap(config)))
        assertTrue("a medium's field of sounds, sorted by a field of its tool: ${session.error}", session.isSuccess)

        // Only the entry holding a sound is offered
        assertEquals(listOf(played), SequenceVariants.offered(context, config).map { it["id"] })

        val step = steps(SequenceVariants.steps(context, config, SequenceVariants.Answer.Entry(played), 0L, s)).single()
        assertEquals(StepEnd.TIMED, step.end)
        assertEquals(720_000L, step.duration)
        assertTrue(step.byMedium)
        assertEquals("breathing3", step.media!!.id)
        assertEquals(ReferenceKind.ENTRY, step.sourceEntry!!.kind)
        assertEquals(played, step.sourceEntry!!.id)

        assertTrue(SequenceVariants.steps(context, config, SequenceVariants.Answer.Entry(silent), 0L, s) is SequenceVariants.Made.Refused)
        assertTrue(SequenceVariants.steps(context, config, SequenceVariants.Answer.Entry(null), 0L, s) is SequenceVariants.Made.Refused)

        val sequenceId = session.data!!["tool_instance_id"] as String
        assertFalse(coordinator.processUserAction("sequence.complete", mapOf("tool_instance_id" to sequenceId, "timestamp" to 1_000L)).isSuccess)
        val done = coordinator.processUserAction("sequence.complete", mapOf("tool_instance_id" to sequenceId, "timestamp" to 1_000L, "entry" to played))
        assertTrue(done.error, done.isSuccess)
        val kept = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to sequenceId)).data!!["entries"] as List<*>
        val data = (kept.single() as Map<*, *>)["data"] as Map<*, *>
        assertEquals(720_000L, (data[SequenceToolType.DURATION] as Number).toLong())
        assertEquals(played, SequenceSteps.read(data[SequenceToolType.KEPT_STEPS] as String)!!.single().sourceEntry!!.id)
    }

    @Test
    fun `a medium's field must hold sounds or videos`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Calm"))
        val library = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone.data!!["zone_id"], "tooltype" to "notes", "config" to mapOf("name" to "Library")))
        val config = JSONObject()
            .put("name", "Guided")
            .put(SequenceVariants.VARIANT, SequenceVariants.MEDIA)
            .put(SequenceVariants.MEDIA_SOURCE, JSONObject().put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", library.data!!["tool_instance_id"])))
            .put(SequenceVariants.MEDIA_FIELD, "data.content")
        val session = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone.data!!["zone_id"], "tooltype" to "sequence",
            "config" to app.treelune.core.utils.JsonUtils.toMap(config)))
        assertFalse(session.isSuccess)
    }
}
