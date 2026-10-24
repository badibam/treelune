package app.treelune.core.backup

import android.net.Uri
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.demo.DemoContent
import app.treelune.core.demo.DemoFixture
import app.treelune.core.files.Files
import app.treelune.core.services.BackupService
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipInputStream

/**
 * What taking a part of a backup back promises (docs/DATA.md, « A part of the app »): the demo,
 * exported whole then brought back whole, arrives a second time through the services, each thing
 * under a new id that the things reading it follow, a variable whose name is taken under `_2`, a
 * routine off without provider; its identities, which the app holds, under their own ids, the one
 * retouched in the app said different and replaced when asked, the zones and routines speaking as
 * them; one step of the history, undone at once, the identity replaced back as it was; and a step
 * refused leaves nothing behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PartImportTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val database get() = AppDatabase.getDatabase(context)

    /**
     * The demo installed, then the whole app written into a file as the export writes it (the
     * app's folder, a document tree, has no stand-in here): its uri.
     */
    private suspend fun demoExported(): Uri {
        DemoFixture.installed(context)
        val file = File(context.cacheDir, "backup.zip")
        val service = BackupService(context)
        val token = CancellationToken()
        assertTrue(service.writeZip(file.outputStream(), service.build(token)!!, token))
        return Uri.fromFile(file)
    }

    /** The backup's document read from the file at [uri]. */
    private fun document(uri: Uri): JSONObject = ZipInputStream(File(uri.path!!).inputStream()).use { zip ->
        generateSequence { zip.nextEntry }.first { it.name == "backup.json" }
        JSONObject(zip.readBytes().toString(Charsets.UTF_8))
    }

    /** Everything of the demo ticked, each tool with its entries, and the identities its zones and routines speak as. */
    private fun everything(document: JSONObject): PartSelection {
        val data = document.getJSONObject("data")
        fun rows(table: String) = data.getJSONArray(table).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.filter { it.getString("id").startsWith(DemoContent.PREFIX) }
        fun ids(table: String) = rows(table).map { it.getString("id") }
        val spoken = (rows("zones") + rows("automations")).mapNotNull { BackupPart.identityOf(it) }.toSet()
        return PartSelection(ids("zones").toSet(), ids("tool_instances").associateWith { true }, ids("variables").toSet(), ids("automations").toSet(), spoken)
    }

    // One test, the demo being installed once: a refused import first, then the whole one
    @Test
    fun `the demo brought back whole arrives a second time under new ids, one step undone at once, a refused step leaving nothing`() = runBlocking {
        val uri = demoExported()

        // A step refused takes out what was written before it
        val whole = document(uri)
        val part = BackupPart.cut(whole, everything(whole))
        // The last tool of a type the app does not know: its creation is refused, after the others
        val tools = part.getJSONObject("data").getJSONArray("tool_instances")
        tools.getJSONObject(tools.length() - 1).put("tooltype", "nothing_such")
        val zones = database.zoneDao().getAllZones().size
        val tool = database.toolInstanceDao().getAllToolInstances().size
        val groups = database.appSettingsCategoryDao().getAllSettings().map { it.category to it.settings }

        val refused = try {
            PartImport(context).run(part, Files.importDir(context), emptyMap(), emptySet())
            null
        } catch (e: PartImport.Refused) {
            e
        }
        assertNotNull(refused)
        assertEquals(zones, database.zoneDao().getAllZones().size)
        assertEquals(tool, database.toolInstanceDao().getAllToolInstances().size)
        assertEquals(groups, database.appSettingsCategoryDao().getAllSettings().map { it.category to it.settings })

        // The whole demo brought back, one of its identities retouched in the app since, and replaced
        val selection = everything(document(uri))
        assertTrue(selection.identities.isNotEmpty())
        val retouched = selection.identities.first()
        val original = database.identityDao().getById(retouched)!!.name
        assertTrue(Coordinator(context).processUserAction("identities.update", mapOf("identity_id" to retouched, "name" to "Retouched")).isSuccess)
        @Suppress("UNCHECKED_CAST")
        val held = Coordinator(context).processUserAction("backup.tree", mapOf("uri" to uri.toString())).data!!["identities"] as Map<String, String>
        assertEquals("different", held[retouched])
        (selection.identities - retouched).forEach { assertEquals(it, "same", held[it]) }
        val identitiesBefore = database.identityDao().getAll().size
        val zonesBefore = database.zoneDao().getAllZones().size
        val entriesBefore = database.toolDataDao().getAllEntries().size
        val variablesBefore = database.variableDao().getAll()

        // The name the import's screen says each variable arrives under, from what backup.tree reads
        val read = Coordinator(context).processUserAction("backup.tree", mapOf("uri" to uri.toString()))
        assertTrue("tree: ${read.error}", read.isSuccess)
        @Suppress("UNCHECKED_CAST")
        val tree = PartTree.read(JsonUtils.toJSONObject(read.data!!["tree"] as Map<String, Any?>))
        @Suppress("UNCHECKED_CAST")
        val said = PartImport.arrivalNames(
            tree.elements.filter { it.kind == PartElement.Kind.VARIABLE && it.id in selection.variables }.map { it.id to it.name },
            (read.data!!["taken_variable_names"] as List<String>).toSet()
        )

        val imported = Coordinator(context).processUserAction("backup.import_part", mapOf("uri" to uri.toString(), "selection" to selection.toJson(),
            "replace_identities" to listOf(retouched)))
        assertTrue("import: ${imported.error}", imported.isSuccess)
        assertEquals(0, imported.data!!["identities_added"])
        assertEquals(1, imported.data!!["identities_replaced"])
        assertEquals(identitiesBefore, database.identityDao().getAll().size)
        val replaced = database.identityDao().getById(retouched)!!.toIdentity()
        assertEquals(original, replaced.name)
        assertTrue(replaced.retouched().isEmpty())

        // Every zone, tool and entry a second time, the variables under a free name
        assertEquals(zonesBefore + selection.zones.size, database.zoneDao().getAllZones().size)
        assertEquals(2 * entriesBefore, database.toolDataDao().getAllEntries().size)
        val variables = database.variableDao().getAll()
        assertEquals(2 * variablesBefore.size, variables.size)
        variablesBefore.forEach { variable -> assertTrue(variable.name, variables.any { it.name == "${variable.name}_2" }) }
        // Each under the name the screen said, which the import's window says again
        assertEquals(said.values.toSet(), variables.filter { !it.id.startsWith(DemoContent.PREFIX) }.map { it.name }.toSet())
        @Suppress("UNCHECKED_CAST")
        assertEquals(
            said.mapKeys { (id, _) -> tree.elements.first { it.id == id }.name }.filter { (name, arrived) -> name != arrived },
            imported.data!!["renamed"] as Map<String, String>
        )

        // What the import's window counts: the zones created, the elements, the entries
        assertEquals(selection.zones.size, imported.data!!["zones"])
        assertEquals(selection.tools.size + selection.variables.size + selection.automations.size, imported.data!!["elements"])
        assertEquals(entriesBefore, imported.data!!["entries"])

        // What arrived names nothing of the demo: each link followed its thing to its new id
        val demoIds = (selection.zones + selection.tools.keys + selection.variables + selection.automations +
            database.toolDataDao().getAllEntries().filter { it.id.startsWith(DemoContent.PREFIX) }.map { it.id }).toSet()
        val arrived = database.toolInstanceDao().getAllToolInstances().filter { !it.id.startsWith(DemoContent.PREFIX) }
        assertTrue(arrived.isNotEmpty())
        arrived.forEach { tool -> assertFalse(tool.config_json, IdWords.words(JSONObject(tool.config_json)).any { it in demoIds }) }
        database.toolDataDao().getAllEntries().filter { !it.id.startsWith(DemoContent.PREFIX) }.forEach { entry ->
            assertFalse(entry.data, IdWords.words(entry.data).any { it in demoIds })
        }
        variables.filter { !it.id.startsWith(DemoContent.PREFIX) }.forEach { variable ->
            assertFalse(variable.definitionJson, IdWords.words(variable.definitionJson).any { it in demoIds })
        }

        // A routine arrives off, without provider, speaking as its identity; a zone as its own
        val demoRoutines = database.aiDao().getAllAutomations().filter { it.id.startsWith(DemoContent.PREFIX) }
        val routines = database.aiDao().getAllAutomations().filter { !it.id.startsWith(DemoContent.PREFIX) }
        assertEquals(selection.automations.size, routines.size)
        routines.forEach { routine ->
            assertFalse(routine.isEnabled)
            assertNull(routine.providerId)
            assertEquals(routine.name, demoRoutines.first { it.name == routine.name }.identityId, routine.identityId)
        }
        val allZones = database.zoneDao().getAllZones()
        allZones.filter { !it.id.startsWith(DemoContent.PREFIX) }.forEach { zone ->
            assertEquals(zone.name, allZones.first { it.id.startsWith(DemoContent.PREFIX) && it.name == zone.name }.identity_id, zone.identity_id)
        }

        // One step of the history, which undoes it all
        @Suppress("UNCHECKED_CAST")
        val step = (Coordinator(context).processUserAction("history.list", mapOf("limit" to 1)).data!!["steps"] as List<Map<String, Any?>>).single()
        val undone = Coordinator(context).processUserAction("history.undo", mapOf("id" to step["id"]))
        assertTrue("undo: ${undone.error}", undone.isSuccess)
        assertEquals(zonesBefore, database.zoneDao().getAllZones().size)
        assertEquals(entriesBefore, database.toolDataDao().getAllEntries().size)
        assertEquals("Retouched", database.identityDao().getById(retouched)!!.name)
    }
}
