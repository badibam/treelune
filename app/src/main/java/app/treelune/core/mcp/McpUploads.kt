package app.treelune.core.mcp

import android.content.Context
import android.net.Uri
import app.treelune.core.files.FileKind
import app.treelune.core.files.FilePreparation
import app.treelune.core.files.Files
import app.treelune.core.utils.LogManager
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.time.Duration

/**
 * Sending a file through the connector (docs/EXTERNAL_ACCESS.md, « Sending a file »): the bytes
 * never pass through the AI, which writes a tool's parameters and could not hold a file of some
 * MB. The client asks for an address (the connector's tool `upload_address`), valid once and a few
 * minutes, at which it sends the file with a PUT; the app checks it as a file picked on the phone
 * (FilePreparation: its kind, its format, its size, that Android plays it), sets it aside in the
 * pending folder, and answers its id, which the AI then writes in an entry's FILE field.
 *
 * Through the app's Tailscale node, the node writes the body straight into a file and hands over
 * its path, up to the kind's size; through the relay, a body holds 1 MB at most, which the relay
 * refuses beyond.
 */
object McpUploads {

    /** How long an address waits for its file. */
    const val TTL_SECONDS = 300

    /** An address handed out: what it takes, and until when. */
    private data class Expected(val kind: FileKind, val name: String, val expiresAt: Long)

    /** Where the access is open: its public address, and whether the Tailscale node writes uploads to disk. */
    private data class Opened(val base: String, val streamed: Boolean)

    @Volatile private var opened: Opened? = null
    private val expected = HashMap<String, Expected>()
    private val random = SecureRandom()

    /** The access opened at [base]; [streamed] for the Tailscale node. */
    fun open(base: String, streamed: Boolean) { opened = Opened(base, streamed) }

    /** The access closed: every address handed out is spent. */
    @Synchronized fun close() {
        opened = null
        expected.clear()
    }

    /** An address handed out, or why none is. */
    sealed interface Address {
        class Ready(val url: String) : Address
        class Refused(val message: String) : Address
    }

    /** A new address for a file of [kind], named [name], its extension saying its format. */
    @Synchronized
    fun address(context: Context, kind: FileKind, name: String, text: (String) -> String): Address {
        val open = opened ?: return Address.Refused(text("mcp_upload_closed"))
        try {
            app.treelune.core.utils.FileNames.clean(name)
        } catch (e: IllegalArgumentException) {
            return Address.Refused(text("mcp_upload_name").format(name))
        }
        val now = System.currentTimeMillis()
        expected.entries.removeAll { it.value.expiresAt < now }
        val token = ByteArray(24).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        expected[token] = Expected(kind, name, now + TTL_SECONDS * 1000L)
        // The node writes the body there itself, up to the kind's size
        if (open.streamed) FunnelNative.expectUpload(token, received(context, token).path, kind.maxBytes ?: STREAMED_MAX, TTL_SECONDS)
        return Address.Ready("${open.base}$PATH$token")
    }

    /** Where the Tailscale node writes the file sent to [token]. */
    private fun received(context: Context, token: String): File = File(context.cacheDir, "uploads/$token")

    /** The address [token], spent; null when it was never handed out or has expired. */
    @Synchronized
    private fun take(token: String): Expected? = expected.remove(token)?.takeIf { it.expiresAt >= System.currentTimeMillis() }

    /**
     * The file sent to an address: its body, or the file the node wrote; checked, set aside, and
     * answered by its id and what goes with its kind (a medium's duration in ISO 8601, as the AI
     * reads every duration). Refused with what to correct.
     */
    fun receive(context: Context, request: HttpRequest, text: (String) -> String): HttpResponse {
        val token = request.path.removePrefix(PATH)
        val expectation = take(token) ?: return HttpResponse.json(404, JSONObject().put("error", text("mcp_upload_unknown")))
        if (request.method != "PUT") return HttpResponse(405, mapOf("Allow" to "PUT"))
        // The node's file, or the relay's body written out, under the name the address was asked with
        val dir = File(context.cacheDir, "uploads/$token.in").apply { mkdirs() }
        val sent = File(dir, app.treelune.core.utils.FileNames.clean(expectation.name))
        try {
            val written = request.file?.let { File(it) }
            if (written != null) check(written.renameTo(sent)) { "upload $token: not moved" } else sent.writeBytes(request.body)
            return when (val prepared = FilePreparation.prepare(context, Uri.fromFile(sent), listOf(expectation.kind), name = expectation.name, text = text)) {
                is FilePreparation.Result.Refused -> HttpResponse.json(422, JSONObject().put("error", prepared.message))
                is FilePreparation.Result.Done -> {
                    val d = prepared.description
                    LogManager.service("External access: file ${prepared.id} received, ${d.kind}, ${d.sizeBytes} bytes", "INFO")
                    HttpResponse.json(200, JSONObject()
                        .put("file_id", prepared.id)
                        .put("kind", d.kind.name)
                        .put("format", d.format.name)
                        .put("size_bytes", d.sizeBytes)
                        .apply {
                            d.name?.let { put("name", it) }
                            d.durationMs?.let { put("duration", Duration.ofMillis(it).toString()) }
                            d.width?.let { put("width", it) }
                            d.height?.let { put("height", it) }
                            d.lineCount?.let { put("line_count", it) }
                        })
                }
            }
        } finally {
            dir.deleteRecursively()
            request.file?.let { File(it).delete() }
        }
    }

    /** The path an address lives under. */
    const val PATH = "/upload/"

    /** The most the node takes for a kind without a size of its own (an image, reduced; a text): a video's. */
    private val STREAMED_MAX = FileKind.VIDEO.maxBytes!!
}
