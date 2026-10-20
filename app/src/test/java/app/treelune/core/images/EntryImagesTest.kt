package app.treelune.core.images

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
 * What docs/design/image-field.md promised of an entry's write: a form's prepared image becomes
 * the entry's, a conversation's is copied under a new id, an image taken off the field loses its
 * row, an id that names no image refuses the write, and an entry deleted takes its rows with it.
 * A file stays while the history of writes names its image (docs/design/undo-history.md): an undo
 * puts the entry back with its images; the history done with it, the startup sweep removes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryImagesTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var toolId: String
    private val dao get() = AppDatabase.getDatabase(context).imageDao()

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
    private fun pending(id: String): File = File(Images.pendingDir(context), ImageFormat.PNG.fileName(id)).also {
        Images.write(it, byteArrayOf(1, 2, 3))
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
        assertTrue(Images.file(context, image).exists())
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
        assertTrue(Images.file(context, dao.getById(copied)!!).exists())
    }

    @Test
    fun `an image taken off the field loses its row, its file kept for the history`() = runBlocking {
        pending("p3")
        pending("p4")
        val entryId = create(mapOf("photos" to listOf("p3", "p4"))).data!!["id"] as String
        val file = Images.file(context, dao.getById("p3")!!)

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
        val file = Images.file(context, dao.getById("p6")!!)
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
        val file = Images.file(context, dao.getById("p5")!!)
        pending("never-written")

        assertTrue(coordinator.processUserAction("tool_data.delete", mapOf("id" to entryId)).isSuccess)
        assertEquals(null, dao.getById("p5"))
        assertTrue(file.exists())

        // The history pruned, nothing names the image any more
        AppDatabase.getDatabase(context).historyDao().clear()
        Images.sweepOnce(context)
        assertFalse(file.exists())
        assertEquals(null, Images.pendingFile(context, "never-written"))
    }
}
