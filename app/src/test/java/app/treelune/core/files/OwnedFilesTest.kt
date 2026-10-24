package app.treelune.core.files

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * What docs/DATA.md (« IMAGE ») promises of an entry's write, and of a tool's config the same way: a form's prepared image becomes
 * the entry's, a conversation's is copied under a new id, an image taken off the field loses its
 * row, an id that names no image refuses the write, and an entry deleted takes its rows with it.
 * A file stays while the history of writes names its image (docs/DATA.md, « History of writes »): an undo
 * puts the entry back with its images; the history done with it, the startup sweep removes it.
 * A tool owns the images its config holds (a step's): a copy of the tool gets copies of them, and
 * the tool deleted takes their rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OwnedFilesTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var toolId: String
    private val dao get() = AppDatabase.getDatabase(context).fileDao()

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Garden"))
        assertTrue(zone.error, zone.isSuccess)
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"],
            "tooltype" to "notes",
            "config" to mapOf(
                "name" to "Plants",
                "extra_fields" to listOf(mapOf("display_name" to "Photos", "type" to "IMAGE", "always_visible" to false))
            )
        ))
        assertTrue(tool.error, tool.isSuccess)
        toolId = tool.data!!["tool_instance_id"] as String
    }

    /** An image prepared as a form prepares it: its file alone, in the pending folder. */
    private fun pending(id: String): File = File(Files.pendingDir(context), FileFormat.PNG.fileName(id)).also {
        Files.write(it, byteArrayOf(1, 2, 3))
    }

    private suspend fun create(extra: Map<String, Any?>) =
        coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to toolId, "data" to mapOf("content" to "Fern"), "extra" to extra))

    private suspend fun photos(entryId: String): List<String> {
        val entry = AppDatabase.getDatabase(context).toolDataDao().getById(entryId)!!
        val array = org.json.JSONObject(entry.extra!!).getJSONArray("photos")
        return (0 until array.length()).map { array.getString(it) }
    }

    @Test
    fun `a form's prepared image becomes the entry's, its file moved`() = runBlocking {
        val file = pending("p1")
        val created = create(mapOf("photos" to listOf("p1")))
        assertTrue(created.error, created.isSuccess)
        val entryId = created.data!!["id"] as String

        val image = dao.getById("p1")!!
        assertEquals(entryId, image.entryId)
        assertEquals(null, image.sessionId)
        assertEquals("PNG", image.format)
        assertFalse(file.exists())
        assertTrue(Files.file(context, image).exists())
    }

    @Test
    fun `an image owned elsewhere is copied under a new id, the original kept`() = runBlocking {
        pending("p2")
        val first = create(mapOf("photos" to listOf("p2"))).data!!["id"] as String
        val second = create(mapOf("photos" to listOf("p2")))
        assertTrue(second.error, second.isSuccess)
        val secondId = second.data!!["id"] as String

        val copied = photos(secondId).single()
        assertNotEquals("p2", copied)
        assertEquals(secondId, dao.getById(copied)!!.entryId)
        assertEquals(first, dao.getById("p2")!!.entryId)
        assertTrue(Files.file(context, dao.getById(copied)!!).exists())
    }

    @Test
    fun `an image taken off the field loses its row, its file kept for the history`() = runBlocking {
        pending("p3")
        pending("p4")
        val entryId = create(mapOf("photos" to listOf("p3", "p4"))).data!!["id"] as String
        val file = Files.file(context, dao.getById("p3")!!)

        val updated = coordinator.processUserAction("tool_data.update", mapOf("id" to entryId, "extra" to mapOf("photos" to listOf("p4"))))
        assertTrue(updated.error, updated.isSuccess)
        assertEquals(null, dao.getById("p3"))
        assertTrue("the history names it", file.exists())
        assertEquals(listOf("p4"), photos(entryId))
    }

    @Test
    fun `an entry deleted comes back by an undo, its images with it`() = runBlocking {
        pending("p6")
        val entryId = create(mapOf("photos" to listOf("p6"))).data!!["id"] as String
        val file = Files.file(context, dao.getById("p6")!!)
        assertTrue(coordinator.processUserAction("tool_data.delete", mapOf("id" to entryId)).isSuccess)
        assertEquals(null, dao.getById("p6"))

        val step = AppDatabase.getDatabase(context).historyDao().getRowsOf("tool_data", entryId).last().stepId
        val undone = coordinator.processUserAction("history.undo", mapOf("id" to step))
        assertTrue(undone.error, undone.isSuccess)
        assertEquals(entryId, dao.getById("p6")!!.entryId)
        assertTrue(file.exists())
        assertEquals(listOf("p6"), photos(entryId))
    }

    @Test
    fun `an id that names no image refuses the write`() = runBlocking {
        val refused = create(mapOf("photos" to listOf("nowhere")))
        assertFalse(refused.isSuccess)
        assertTrue(refused.error, refused.error!!.contains("nowhere"))
    }

    @Test
    fun `an entry deleted takes its rows, the sweep its files once the history is done with them`() = runBlocking {
        pending("p5")
        val entryId = create(mapOf("photos" to listOf("p5"))).data!!["id"] as String
        val file = Files.file(context, dao.getById("p5")!!)
        pending("never-written")

        assertTrue(coordinator.processUserAction("tool_data.delete", mapOf("id" to entryId)).isSuccess)
        assertEquals(null, dao.getById("p5"))
        assertTrue(file.exists())

        // The history pruned, nothing names the image any more
        AppDatabase.getDatabase(context).historyDao().clear()
        Files.sweepOnce(context)
        assertFalse(file.exists())
        assertEquals(null, Files.pendingFile(context, "never-written"))
    }

    /** A session whose one step shows [images]. */
    private suspend fun session(images: List<String>) = coordinator.processUserAction("tools.create", mapOf(
        "zone_id" to AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(toolId)!!.zone_id,
        "tooltype" to "sequence",
        "config" to mapOf("name" to "Yoga", "steps" to listOf(mapOf("kind" to "step", "name" to "Tree", "end" to "manual", "images" to images)))
    ))

    private suspend fun stepImages(sessionId: String): List<String> {
        val tool = AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(sessionId)!!
        val array = org.json.JSONObject(tool.config_json).getJSONArray("steps").getJSONObject(0).getJSONArray("images")
        return (0 until array.length()).map { array.getString(it) }
    }

    @Test
    fun `a step's prepared image becomes the tool's`() = runBlocking {
        val file = pending("s1")
        val created = session(listOf("s1"))
        assertTrue(created.error, created.isSuccess)
        val sessionId = created.data!!["tool_instance_id"] as String

        val image = dao.getById("s1")!!
        assertEquals(sessionId, image.toolInstanceId)
        assertEquals(null, image.entryId)
        assertFalse(file.exists())
        assertTrue(Files.file(context, image).exists())
    }

    @Test
    fun `a tool copied gets copies of its images, the source keeping its own`() = runBlocking {
        pending("s2")
        val sourceId = session(listOf("s2")).data!!["tool_instance_id"] as String
        val zoneId = AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(sourceId)!!.zone_id
        val copy = coordinator.processUserAction("tools.duplicate", mapOf("tool_instance_id" to sourceId, "target_zone_id" to zoneId))
        assertTrue(copy.error, copy.isSuccess)
        val copyId = copy.data!!["tool_instance_id"] as String

        val copied = stepImages(copyId).single()
        assertNotEquals("s2", copied)
        assertEquals(copyId, dao.getById(copied)!!.toolInstanceId)
        assertEquals(sourceId, dao.getById("s2")!!.toolInstanceId)
    }

    @Test
    fun `an image taken off a step loses its row, and the tool deleted takes the others`() = runBlocking {
        pending("s3")
        pending("s4")
        val sessionId = session(listOf("s3", "s4")).data!!["tool_instance_id"] as String
        val config = org.json.JSONObject(AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(sessionId)!!.config_json)
        config.getJSONArray("steps").getJSONObject(0).put("images", org.json.JSONArray(listOf("s4")))

        val updated = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to sessionId, "config" to app.treelune.core.utils.JsonUtils.toMap(config)))
        assertTrue(updated.error, updated.isSuccess)
        assertEquals(null, dao.getById("s3"))
        assertEquals(listOf("s4"), stepImages(sessionId))

        assertTrue(coordinator.processUserAction("tools.delete", mapOf("tool_instance_id" to sessionId)).isSuccess)
        assertEquals(null, dao.getById("s4"))
    }

    @Test
    fun `a step's id that names no image refuses the tool`() = runBlocking {
        val refused = session(listOf("nowhere"))
        assertFalse(refused.isSuccess)
        assertTrue(refused.error, refused.error!!.contains("nowhere"))
    }
}
