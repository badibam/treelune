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
            .put(SequenceVariants.MEDIA_SOURCE, JSONObject().put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", libraryId))
                .put("sort", JSONArray().put(JSONObject().put("field", "created_at").put("direction", "asc"))))
            .put(SequenceVariants.MEDIA_FIELD, "extra.audio")
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

    @Test
    fun `a program is chosen at launch by its id, given from its name, written alone, and kept by the session that played it`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Drawing"))
        fun step(name: String) = mapOf("kind" to "step", "name" to name, "length" to "fixed", "duration" to 480_000L, "auto_next" to false)
        val created = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone.data!!["zone_id"], "tooltype" to "sequence",
            "config" to mapOf("name" to "Cycle", SequenceVariants.VARIANT to SequenceVariants.PROGRAMS, SequenceVariants.PROGRAMS to listOf(
                mapOf(SequenceVariants.PROGRAM_NAME to "Séance 1", "steps" to listOf(step("Emoji"), step("La Bean"))),
                mapOf(SequenceVariants.PROGRAM_NAME to "Séance 1", "steps" to listOf(step("Plante")))))))
        assertTrue(created.error, created.isSuccess)
        val sequenceId = created.data!!["tool_instance_id"] as String
        suspend fun config() = JSONObject((coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to sequenceId)).data!!["tool_instance"] as Map<*, *>)
            .let { @Suppress("UNCHECKED_CAST") app.treelune.core.utils.JsonUtils.toJSONObject(it["config"] as Map<String, Any?>) }.toString())
        assertEquals(listOf("seance_1", "seance_1_2"), SequenceVariants.programs(config()).map { it.getString(SequenceVariants.PROGRAM_ID) })

        assertEquals(listOf("Emoji", "La Bean"), steps(SequenceVariants.steps(context, config(), SequenceVariants.Answer.Program("seance_1"), 0L, s)).map { it.name })
        assertTrue(SequenceVariants.steps(context, config(), SequenceVariants.Answer.Program("gone"), 0L, s) is SequenceVariants.Made.Refused)
        assertTrue(SequenceVariants.steps(context, config(), SequenceVariants.Answer.Program(null), 0L, s) is SequenceVariants.Made.Refused)

        // One program added alone, its id given from its name and returned; the others untouched
        val added = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to sequenceId,
            "element" to mapOf("list" to SequenceVariants.PROGRAMS, "value" to mapOf(SequenceVariants.PROGRAM_NAME to "Séance 2", "steps" to listOf(step("Vases"))))))
        assertTrue(added.error, added.isSuccess)
        assertEquals("seance_2", added.data!!["element_id"])
        assertEquals(listOf("Séance 1", "Séance 1", "Séance 2"), SequenceVariants.programs(config()).map { it.getString(SequenceVariants.PROGRAM_NAME) })
        // A program whose step would never end is refused, written alone as whole
        val endless = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to sequenceId, "element" to mapOf("list" to SequenceVariants.PROGRAMS, "id" to "seance_2",
            "value" to mapOf(SequenceVariants.PROGRAM_NAME to "Séance 2", "steps" to listOf(step("Vases") + mapOf("length" to "none", "auto_next" to true))))))
        assertFalse(endless.isSuccess)

        assertFalse(coordinator.processUserAction("sequence.complete", mapOf("tool_instance_id" to sequenceId, "timestamp" to 1_000L)).isSuccess)
        val done = coordinator.processUserAction("sequence.complete", mapOf("tool_instance_id" to sequenceId, "timestamp" to 1_000L, SequenceToolType.PROGRAM to "seance_2"))
        assertTrue(done.error, done.isSuccess)
        val kept = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to sequenceId)).data!!["entries"] as List<*>
        val data = (kept.single() as Map<*, *>)["data"] as Map<*, *>
        assertEquals("seance_2", data[SequenceToolType.PROGRAM])
        assertEquals(listOf("Vases"), SequenceSteps.read(data[SequenceToolType.KEPT_STEPS] as String)!!.map { it.name })
    }

    @Test
    fun `the programs' fields are named and checked as the user's fields, their values checked and gone with them`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Yoga"))
        val step = mapOf("kind" to "step", "name" to "Posture", "length" to "fixed", "duration" to 60_000L, "auto_next" to false)
        val created = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone.data!!["zone_id"], "tooltype" to "sequence",
            "config" to mapOf("name" to "Yoga", SequenceVariants.VARIANT to SequenceVariants.PROGRAMS,
                SequenceVariants.PROGRAM_FIELDS to listOf(mapOf("display_name" to "Niveau", "type" to "NUMERIC", "always_visible" to false, "config" to mapOf("decimals" to 0))),
                SequenceVariants.PROGRAMS to listOf(mapOf(SequenceVariants.PROGRAM_NAME to "Doux", "steps" to listOf(step))))))
        assertTrue(created.error, created.isSuccess)
        val sequenceId = created.data!!["tool_instance_id"] as String
        suspend fun config() = JSONObject((coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to sequenceId)).data!!["tool_instance"] as Map<*, *>)
            .let { @Suppress("UNCHECKED_CAST") app.treelune.core.utils.JsonUtils.toJSONObject(it["config"] as Map<String, Any?>) }.toString())
        assertEquals("niveau", config().getJSONArray(SequenceVariants.PROGRAM_FIELDS).getJSONObject(0).getString("name"))

        fun program(values: Map<String, Any?>) = mapOf("tool_instance_id" to sequenceId, "element" to mapOf("list" to SequenceVariants.PROGRAMS, "id" to "doux",
            "value" to mapOf(SequenceVariants.PROGRAM_NAME to "Doux", SequenceVariants.PROGRAM_VALUES to values, "steps" to listOf(step))))
        val given = coordinator.processUserAction("tools.update", program(mapOf("niveau" to 2)))
        assertTrue(given.error, given.isSuccess)
        assertEquals(2, SequenceVariants.program(config(), "doux")!!.getJSONObject(SequenceVariants.PROGRAM_VALUES).getInt("niveau"))
        assertFalse(coordinator.processUserAction("tools.update", program(mapOf("niveau" to "haut"))).isSuccess)

        // Offered by a field, highest first, then by name; a key or a field shown that is no program field refused
        val ordered = config().put(SequenceVariants.PROGRAMS, JSONArray()
                .put(JSONObject().put("id", "doux").put("name", "Doux").put("values", JSONObject().put("niveau", 2)).put("steps", JSONArray().put(JSONObject(step))))
                .put(JSONObject().put("id", "b").put("name", "B").put("steps", JSONArray().put(JSONObject(step))))
                .put(JSONObject().put("id", "a").put("name", "A").put("values", JSONObject().put("niveau", 2)).put("steps", JSONArray().put(JSONObject(step)))))
            .put(SequenceVariants.PROGRAM_SORT, JSONArray().put(JSONObject().put("field", "data.niveau").put("direction", "desc")).put(JSONObject().put("field", "name").put("direction", "asc")))
            .put(SequenceVariants.PROGRAM_SHOWN, JSONArray().put("data.niveau"))
        val sorted = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to sequenceId, "config" to app.treelune.core.utils.JsonUtils.toMap(ordered)))
        assertTrue(sorted.error, sorted.isSuccess)
        assertEquals(listOf("a", "doux", "b"), SequenceVariants.offeredPrograms(context, config(), s).map { it.getString("id") })
        assertEquals(listOf("niveau"), SequenceVariants.shownFields(context, config(), s).map { it.name })
        // The same rows, by the same columns, for whatever reads the view: a program named by its name
        val rows = app.treelune.core.views.Views.of(app.treelune.core.selection.Reference(ReferenceKind.TOOL_INSTANCE, sequenceId), SequencePlayable.NAME, context, s).rows(null, null, 0L)
        assertEquals(listOf("Doux", "B", "A"), rows.map { it.name })
        assertEquals(2, JSONObject(rows.first().data!!).getInt("niveau"))
        assertFalse(coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to sequenceId, "config" to app.treelune.core.utils.JsonUtils.toMap(
            JSONObject(ordered.toString()).put(SequenceVariants.PROGRAM_SORT, JSONArray().put(JSONObject().put("field", "data.taille").put("direction", "asc")))))).isSuccess)
        assertFalse(coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to sequenceId, "config" to app.treelune.core.utils.JsonUtils.toMap(
            JSONObject(ordered.toString()).put(SequenceVariants.PROGRAM_SHOWN, JSONArray().put("data.taille"))))).isSuccess)

        // The field taken out, its values leave the programs
        val without = config().put(SequenceVariants.PROGRAM_FIELDS, JSONArray()).put(SequenceVariants.PROGRAM_SORT, JSONArray()).put(SequenceVariants.PROGRAM_SHOWN, JSONArray())
        val removed = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to sequenceId, "config" to app.treelune.core.utils.JsonUtils.toMap(without)))
        assertTrue(removed.error, removed.isSuccess)
        assertFalse(SequenceVariants.program(config(), "doux")!!.optJSONObject(SequenceVariants.PROGRAM_VALUES)?.has("niveau") ?: false)
    }
}
