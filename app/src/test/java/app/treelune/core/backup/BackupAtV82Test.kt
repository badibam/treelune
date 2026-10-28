package app.treelune.core.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import app.treelune.core.ai.enrichments.PointerConfig
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.services.BackupService
import app.treelune.core.strings.Strings
import app.treelune.core.utils.StoredSchedule
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File

/**
 * A backup of the last version published, v0.8.1 (database 82), restored whole by this one: the
 * path an update from F-Droid's last release takes. `backups/demo_v82.json` is the demo of v0.8.1
 * with a routine pointing at a tool and at a zone, written by that version's own export (its
 * document, which the import reads bare as well as zipped, a compressed file being one F-Droid's
 * scanner refuses in the sources). Every
 * zone keeps its place on the home screen and every tool its place in its zone; the routines'
 * recurrences and the pointers of their messages read; the import logs no error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupAtV82Test {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val before get() = File(context.cacheDir, "before_v82.zip")

    /** The base outlives the class: the app as it was is backed up first, and restored after. */
    @Before
    fun backUp() = runBlocking {
        val service = BackupService(context)
        val token = CancellationToken()
        assertTrue(service.writeZip(before.outputStream(), service.build(token)!!, token))
    }

    @After
    fun restore() = runBlocking {
        val restored = Coordinator(context).processUserAction("backup.import", mapOf("uri" to Uri.fromFile(before).toString()))
        assertTrue(restored.error, restored.isSuccess)
    }

    /** The fixture copied where a uri can name it, and its document as v0.8.1 wrote it. */
    private fun fixture(): Pair<File, JSONObject> {
        val file = File(context.cacheDir, "demo_v82.json")
        javaClass.classLoader!!.getResourceAsStream("backups/demo_v82.json")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file to JSONObject(file.readText(Charsets.UTF_8))
    }

    private fun rows(data: JSONObject, table: String) = data.getJSONArray(table).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    @Test
    fun `a backup of v0_8_1 restored whole keeps every place, its recurrences and pointers reading, no error logged`() = runBlocking {
        val (file, document) = fixture()
        val data = document.getJSONObject("data")
        assertEquals(82, document.getJSONObject("metadata").getInt("db_schema_version"))

        ShadowLog.clear()
        val restored = Coordinator(context).processUserAction("backup.import", mapOf("uri" to Uri.fromFile(file).toString()))
        assertTrue(restored.error, restored.isSuccess)
        val errors = ShadowLog.getLogs().filter { it.type == Log.ERROR }.map { "${it.tag}: ${it.msg}" }
        assertEquals("no error logged by the import", emptyList<String>(), errors)

        val database = AppDatabase.getDatabase(context)
        // Each zone in its group at its place, each tool at its place in its zone
        val zones = database.zoneDao().getAllZones().associateBy { it.id }
        rows(data, "zones").forEach { zone ->
            val now = zones.getValue(zone.getString("id"))
            assertEquals(zone.getString("name"), Triple(zone.optString("group").ifEmpty { null }, zone.getInt("grid_x"), zone.getInt("grid_y")), Triple(now.group, now.grid_x, now.grid_y))
        }
        val tools = database.toolInstanceDao().getAllToolInstances().associateBy { it.id }
        rows(data, "tool_instances").forEach { tool ->
            val now = tools.getValue(tool.getString("id"))
            assertEquals(tool.getString("id"), Triple(tool.getString("zone_id"), tool.getInt("grid_x"), tool.getInt("grid_y")), Triple(now.zone_id, now.grid_x, now.grid_y))
        }

        // The routines' recurrences, and the pointers of their messages
        val routines = database.aiDao().getAllRoutines()
        assertEquals(rows(data, "automations").size, routines.size)
        val scheduled = routines.mapNotNull { it.scheduleJson }
        assertTrue(scheduled.isNotEmpty())
        scheduled.forEach { assertTrue(it, StoredSchedule.of(JSONObject().put("schedule", JSONObject(it))) is StoredSchedule.Readable) }

        val text: (String) -> String = Strings.`for`(context = context)::shared
        val pointers = routines.flatMap { database.aiDao().getMessagesForSession(it.seedSessionId) }.mapNotNull { it.richContentJson }.flatMap { rich ->
            val segments = JSONObject(rich).getJSONArray("segments")
            (0 until segments.length()).map { segments.getJSONObject(it) }.filter { it.optString("enrichment_type") == "POINTER" }
        }
        assertEquals(2, pointers.size)
        pointers.forEach { segment ->
            val pointer = PointerConfig.fromJson(segment.getString("config"), text)
            assertNull(segment.getString("config"), pointer.selection.problem(text))
        }
    }
}
