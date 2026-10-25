package app.treelune.core.mcp

import android.content.Context
import app.treelune.core.files.FileKind
import app.treelune.core.files.Files
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
 * Sending a file through the connector (docs/EXTERNAL_ACCESS.md, « Sending a file »): an address
 * good for one file of the kind asked, the file checked and set aside, its id answered; spent once
 * used, and none handed out while the access is closed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpUploadsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun closed() = McpUploads.close()

    private fun put(url: String, body: ByteArray) = HttpRequest("r", "PUT", url.substringAfter("https://example.org"), "", emptyMap(), body)

    @Test
    fun `a file sent to its address is set aside and answered by its id, the address then spent`() {
        McpUploads.open("https://example.org", streamed = false)
        val address = McpUploads.address(context, FileKind.TEXT, "notes.md", { it }) as McpUploads.Address.Ready
        assertTrue(address.url.startsWith("https://example.org/upload/"))

        val answer = McpUploads.receive(context, put(address.url, "a\nb\n".toByteArray())) { it }
        assertEquals(200, answer.status)
        val sent = JSONObject(answer.text)
        assertEquals("TEXT", sent.getString("kind"))
        assertEquals("notes.md", sent.getString("name"))
        assertTrue(Files.pendingFile(context, sent.getString("file_id"))!!.exists())

        assertEquals(404, McpUploads.receive(context, put(address.url, "again".toByteArray())) { it }.status)
    }

    @Test
    fun `a file of another kind than asked is refused, saying what was expected`() {
        McpUploads.open("https://example.org", streamed = false)
        val address = McpUploads.address(context, FileKind.AUDIO, "notes.txt", { it }) as McpUploads.Address.Ready
        assertEquals(422, McpUploads.receive(context, put(address.url, "text".toByteArray())) { it }.status)
    }

    @Test
    fun `no address while the access is closed, nor for a name that names no file`() {
        assertTrue(McpUploads.address(context, FileKind.IMAGE, "a.png", { it }) is McpUploads.Address.Refused)
        McpUploads.open("https://example.org", streamed = false)
        assertTrue(McpUploads.address(context, FileKind.IMAGE, "///", { it }) is McpUploads.Address.Refused)
    }
}
