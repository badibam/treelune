package app.treelune.core.mcp

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.files.FileKind
import app.treelune.core.files.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A CSV sent through the connector (upload_address) is imported by its id: detected, then applied,
 * its file left set aside, read again by the second call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpUploadImportTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun closed() = McpUploads.close()

    @Test
    fun `a CSV sent through the connector is detected and imported by its id`() = runBlocking<Unit> {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Kitchen"))
        assertTrue(zone.error, zone.isSuccess)
        val foods = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone.data!!["zone_id"], "tooltype" to "structured", "config" to mapOf("name" to "Foods")))
        assertTrue(foods.error, foods.isSuccess)
        val foodsId = foods.data!!["tool_instance_id"] as String

        McpUploads.open("https://example.org", streamed = false)
        val address = McpUploads.address(context, FileKind.TEXT, "foods.csv", { it }) as McpUploads.Address.Ready
        val answer = McpUploads.receive(context, HttpRequest("r", "PUT", address.url.substringAfter("https://example.org"), "", emptyMap(),
            "Name;kcal\nPomme;52\nRiz;130\n".toByteArray())) { it }
        assertEquals(answer.text, 200, answer.status)
        val fileId = JSONObject(answer.text).getString("file_id")

        val detected = coordinator.processUserAction("imports.detect", mapOf("tool_instance_id" to foodsId, "file" to fileId))
        assertTrue(detected.error, detected.isSuccess)
        @Suppress("UNCHECKED_CAST")
        val columns = detected.data!!["columns"] as List<Map<String, Any?>>
        val applied = withTimeout(60_000) {
            coordinator.processUserAction("imports.apply", mapOf("tool_instance_id" to foodsId, "file" to fileId, "columns" to columns))
        }
        assertTrue(applied.error, applied.isSuccess)
        assertEquals(listOf("Pomme", "Riz"), AppDatabase.getDatabase(context).toolDataDao().getByToolInstance(foodsId).mapNotNull { it.name }.sorted())
        assertTrue("the file stays set aside", Files.pendingFile(context, fileId)!!.exists())
    }
}
