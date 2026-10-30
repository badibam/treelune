package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/**
 * A step repeated for each element of a list an API answers (docs/CORE.md, « Tasks »,
 * `for_each`): made once per element, reading its fields, its conditions judged on each; all or
 * nothing, an element that fails undoing what the others wrote; what it gives read by no step
 * after it, and an element's field read only in a repeated step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepForEachTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var server: HttpServer
    private lateinit var zoneId: String
    private lateinit var notesId: String
    private lateinit var weightId: String

    /** What the service answers, set by each test. */
    private var answer = "{}"

    /** What the service was last sent. */
    private var sent = ""

    @Before
    fun setUp() = runBlocking {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                sent = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                val body = answer.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        zoneId = ok("zones.create", mapOf("name" to "Portuguese"))["zone_id"] as String
        notesId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "notes", "config" to mapOf("name" to "Words")))["tool_instance_id"] as String
        weightId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "tracking",
            "config" to mapOf("name" to "Weight", "type" to "numeric", "value" to mapOf("decimals" to 1), "units" to listOf("kg"))))["tool_instance_id"] as String
    }

    @After
    fun tearDown() = server.stop(0)

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    /** An API answering at once a list `words`, each element with [items] (name to type). */
    private suspend fun api(vararg items: Pair<String, String>): String = ok("apis.create", mapOf("name" to "Lesson ${java.util.UUID.randomUUID()}", "config" to mapOf(
        "url" to "http://127.0.0.1:${server.address.port}/", "method" to "GET", "key_in" to "none", "response" to "immediate",
        "values" to listOf(mapOf("name" to "words", "type" to "list", "path" to "words",
            "items" to items.map { (name, type) -> mapOf("name" to name, "type" to type, "path" to name) }))
    )))["api_id"] as String

    private fun item(field: String) = mapOf("item" to mapOf("field" to field))
    private val words = mapOf("result" to mapOf("step" to 0, "value" to "words"))

    private suspend fun task(steps: List<Map<String, Any?>>): String =
        ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Store", "steps" to steps))["task_id"] as String

    /** [taskId] run by hand, awaited: its run once ended. */
    private suspend fun run(taskId: String): TaskRunEntity {
        val task = AppDatabase.getDatabase(context).taskDao().get(taskId)!!
        val runId = TaskRunner.start(context, task, TaskRun.Trigger.MANUAL, System.currentTimeMillis(), awaited = true)
        return AppDatabase.getDatabase(context).taskDao().getRun(runId)!!
    }

    private suspend fun entries(toolId: String): List<Map<*, *>> =
        (ok("tool_data.get", mapOf("tool_instance_id" to toolId))["entries"] as List<*>).map { it as Map<*, *> }

    @Test
    fun aRepeatedStep_isMadeOncePerElement_readingItsFields() = runBlocking {
        answer = """{"words": [{"pt": "Bom dia", "kind": "phrase"}, {"pt": "sim", "kind": "mot"}, {"pt": "galão", "kind": "mot"}]}"""
        val taskId = task(listOf(
            mapOf("kind" to TaskSteps.CALL_API, "api_id" to api("pt" to "text", "kind" to "text")),
            mapOf("kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to notesId, "for_each" to words,
                "values" to mapOf("data.content" to item("pt")),
                "when" to listOf(mapOf("left" to item("kind"), "op" to "=", "right" to mapOf("constant" to "mot"))))
        ))

        val run = run(taskId)
        assertEquals(run.resultsJson, TaskRun.State.DONE.key, run.state)
        assertEquals(2, TaskRun.results(run.resultsJson)[1].repeated)
        assertEquals(setOf("sim", "galão"), entries(notesId).map { (it["data"] as Map<*, *>)["content"] }.toSet())
    }

    @Test
    fun anElementThatFails_undoesWhatTheOthersWrote() = runBlocking {
        // The second weighing has no value: its entry is refused
        answer = """{"words": [{"kg": 70}, {}]}"""
        val taskId = task(listOf(
            mapOf("kind" to TaskSteps.CALL_API, "api_id" to api("kg" to "number")),
            mapOf("kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to weightId, "for_each" to words,
                "values" to mapOf("name" to mapOf("constant" to "Weighing"), "data.value" to item("kg"), "data.unit" to mapOf("constant" to "kg")))
        ))

        val run = run(taskId)
        assertEquals(TaskRun.State.FAILED.key, run.state)
        assertTrue(entries(weightId).isEmpty())
    }

    @Test
    fun anEmptyList_makesItNoTime() = runBlocking {
        answer = """{"words": []}"""
        val taskId = task(listOf(
            mapOf("kind" to TaskSteps.CALL_API, "api_id" to api("pt" to "text")),
            mapOf("kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to notesId, "for_each" to words, "values" to mapOf("data.content" to item("pt")))
        ))

        val run = run(taskId)
        assertEquals(TaskRun.State.DONE.key, run.state)
        assertEquals(0, TaskRun.results(run.resultsJson)[1].repeated)
    }

    @Test
    fun whatARepeatedStepGives_isReadByNoStepAfterIt() = runBlocking {
        val apiId = api("pt" to "text")
        val result = coordinator.processUserAction("tasks.create", mapOf("zone_id" to zoneId, "name" to "Store", "steps" to listOf(
            mapOf("kind" to TaskSteps.CALL_API, "api_id" to apiId),
            mapOf("kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to notesId, "for_each" to words, "values" to mapOf("data.content" to item("pt"))),
            mapOf("kind" to TaskSteps.UPDATE_ENTRY, "tool_instance_id" to notesId, "entry" to mapOf("result" to mapOf("step" to 1)),
                "values" to mapOf("data.content" to mapOf("constant" to "seen")))
        )))
        assertFalse(result.isSuccess)
    }

    @Test
    fun anElementsField_isReadOnlyInARepeatedStep() = runBlocking {
        val apiId = api("pt" to "text")
        val result = coordinator.processUserAction("tasks.create", mapOf("zone_id" to zoneId, "name" to "Store", "steps" to listOf(
            mapOf("kind" to TaskSteps.CALL_API, "api_id" to apiId),
            mapOf("kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to notesId, "values" to mapOf("data.content" to item("pt")))
        )))
        assertFalse(result.isSuccess)
    }

    /** Weighings named after the words of the answer, at [kg], an entry already holding a name [then] (skip or update). */
    private suspend fun storing(then: String, kg: Int): String {
        answer = """{"words": [{"pt": "sim"}, {"pt": "galão"}]}"""
        return task(listOf(
            mapOf("kind" to TaskSteps.CALL_API, "api_id" to api("pt" to "text")),
            mapOf("kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to weightId, "for_each" to words,
                "values" to mapOf("name" to item("pt"), "data.value" to mapOf("constant" to kg), "data.unit" to mapOf("constant" to "kg")),
                "existing" to mapOf("field" to "name", "then" to then))
        ))
    }

    private suspend fun weighings(): Map<Any?, Number> = entries(weightId).associate { it["name"] to (it["data"] as Map<*, *>)["value"] as Number }

    @Test
    fun anEntryAlreadyThere_isLeftAsItIs_notCreatedTwice() = runBlocking {
        ok("tool_data.create", mapOf("tool_instance_id" to weightId, "name" to "sim", "data" to mapOf("value" to 70, "unit" to "kg")))
        val run = run(storing("skip", 5))

        assertEquals(TaskRun.State.DONE.key, run.state)
        assertEquals(mapOf("sim" to 70.0, "galão" to 5.0), weighings().mapValues { it.value.toDouble() })
    }

    @Test
    fun anEntryAlreadyThere_isChanged_whenSaidSo() = runBlocking {
        ok("tool_data.create", mapOf("tool_instance_id" to weightId, "name" to "sim", "data" to mapOf("value" to 70, "unit" to "kg")))
        run(storing("update", 5))

        assertEquals(mapOf("sim" to 5.0, "galão" to 5.0), weighings().mapValues { it.value.toDouble() })
        assertEquals(2, entries(weightId).size)
    }

    @Test
    fun anEntryAlreadyThere_isFoundByAFieldTheStepWrites() = runBlocking {
        val result = coordinator.processUserAction("tasks.create", mapOf("zone_id" to zoneId, "name" to "Store", "steps" to listOf(
            mapOf("kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to notesId, "values" to mapOf("data.content" to mapOf("constant" to "sim")),
                "existing" to mapOf("field" to "name", "then" to "skip"))
        )))
        assertFalse(result.isSuccess)
    }

    @Test
    fun aTextField_isAddedTo_lineAfterLine() = runBlocking {
        val peopleId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "notes", "config" to mapOf("name" to "People",
            "extra_fields" to listOf(mapOf("display_name" to "Told", "type" to "TEXT", "always_visible" to false, "config" to mapOf("length" to "UNLIMITED"))))))["tool_instance_id"] as String
        ok("tool_data.create", mapOf("tool_instance_id" to peopleId, "data" to mapOf("content" to "fernando"), "extra" to mapOf("told" to "Has a café.")))
        answer = """{"words": [{"person": "fernando", "fact": "Has two daughters."}, {"person": "fernando", "fact": "Was born in Porto."}]}"""
        val taskId = task(listOf(
            mapOf("kind" to TaskSteps.CALL_API, "api_id" to api("person" to "text", "fact" to "text")),
            mapOf("kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to peopleId, "for_each" to words,
                "values" to mapOf("data.content" to item("person"), "extra.told" to item("fact")),
                "existing" to mapOf("field" to "data.content", "then" to "update"), "append" to listOf("extra.told"))
        ))

        assertEquals(TaskRun.State.DONE.key, run(taskId).state)
        val person = entries(peopleId).single()
        assertEquals("Has a café.\nHas two daughters.\nWas born in Porto.", (person["extra"] as Map<*, *>)["told"])
    }

    @Test
    fun addingTo_onAStepThatCreates_isRefused() = runBlocking {
        val result = coordinator.processUserAction("tasks.create", mapOf("zone_id" to zoneId, "name" to "Store", "steps" to listOf(
            mapOf("kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to notesId, "values" to mapOf("data.content" to mapOf("constant" to "sim")),
                "append" to listOf("data.content"))
        )))
        assertFalse(result.isSuccess)
    }

    /** An API taking the list `known` by POST. */
    private suspend fun posting(): String = ok("apis.create", mapOf("name" to "Session ${java.util.UUID.randomUUID()}", "config" to mapOf(
        "url" to "http://127.0.0.1:${server.address.port}/", "method" to "POST", "key_in" to "none", "response" to "immediate",
        "inputs" to listOf(mapOf("name" to "known", "type" to "list"))
    )))["api_id"] as String

    private fun known(vararg fields: String) = mapOf("selection" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to notesId), "fields" to fields.toList()))

    @Test
    fun entries_goToAnApi_asAList_inTheOrderTheyHappened() = runBlocking {
        val first = ok("tool_data.create", mapOf("tool_instance_id" to notesId, "timestamp" to 1_000L, "data" to mapOf("content" to "bom dia")))["id"]
        val second = ok("tool_data.create", mapOf("tool_instance_id" to notesId, "timestamp" to 2_000L, "data" to mapOf("content" to "sim")))["id"]
        val apiId = posting()

        assertEquals(TaskRun.State.DONE.key, run(task(listOf(mapOf("kind" to TaskSteps.CALL_API, "api_id" to apiId, "inputs" to mapOf("known" to known("data.content")))))).state)
        assertEquals("""["bom dia","sim"]""", org.json.JSONObject(sent).getJSONArray("known").toString())

        assertEquals(TaskRun.State.DONE.key, run(task(listOf(mapOf("kind" to TaskSteps.CALL_API, "api_id" to apiId, "inputs" to mapOf("known" to known("data.content", "id")))))).state)
        val objects = org.json.JSONObject(sent).getJSONArray("known")
        assertEquals(listOf(first to "bom dia", second to "sim"), (0 until objects.length()).map { objects.getJSONObject(it).let { o -> o.getString("id") to o.getString("content") } })
    }

    @Test
    fun aListInput_isFilledByEntriesAlone() = runBlocking {
        val result = coordinator.processUserAction("tasks.create", mapOf("zone_id" to zoneId, "name" to "Send", "steps" to listOf(
            mapOf("kind" to TaskSteps.CALL_API, "api_id" to posting(), "inputs" to mapOf("known" to mapOf("constant" to "sim"))))))
        assertFalse(result.isSuccess)
    }

    @Test
    fun aListInput_goesByPostAlone() = runBlocking {
        val result = coordinator.processUserAction("apis.create", mapOf("name" to "Get list", "config" to mapOf(
            "url" to "http://127.0.0.1/", "method" to "GET", "key_in" to "none", "response" to "immediate",
            "inputs" to listOf(mapOf("name" to "known", "type" to "list")))))
        assertFalse(result.isSuccess)
    }

    @Test
    fun entries_goInTheOrderOfTheirDate_orOfTheirCreationInATable() = runBlocking {
        // Created out of the order of their dates: they go by their dates
        ok("tool_data.create", mapOf("tool_instance_id" to notesId, "timestamp" to 2_000L, "data" to mapOf("content" to "later")))
        ok("tool_data.create", mapOf("tool_instance_id" to notesId, "timestamp" to 1_000L, "data" to mapOf("content" to "earlier")))
        val apiId = posting()
        run(task(listOf(mapOf("kind" to TaskSteps.CALL_API, "api_id" to apiId, "inputs" to mapOf("known" to known("data.content"))))))
        assertEquals("""["earlier","later"]""", org.json.JSONObject(sent).getJSONArray("known").toString())

        // A table's rows have no date: they go as they were created
        val tableId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "structured",
            "config" to mapOf("name" to "Episodes", "table_columns" to 0, "extra_fields" to emptyList<Any>())))["tool_instance_id"] as String
        ok("tool_data.create", mapOf("tool_instance_id" to tableId, "name" to "aire-1"))
        ok("tool_data.create", mapOf("tool_instance_id" to tableId, "name" to "pastelaria-1"))
        val rows = mapOf("selection" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to tableId), "fields" to listOf("name")))
        val run = run(task(listOf(mapOf("kind" to TaskSteps.CALL_API, "api_id" to apiId, "inputs" to mapOf("known" to rows)))))
        assertEquals(run.resultsJson, TaskRun.State.DONE.key, run.state)
        assertEquals("""["aire-1","pastelaria-1"]""", org.json.JSONObject(sent).getJSONArray("known").toString())
    }

    @Test
    fun aFileInAnElement_comesDownIntoItsEntry() = runBlocking {
        val sheetsId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "notes", "config" to mapOf("name" to "Sheets",
            "extra_fields" to listOf(mapOf("display_name" to "Sheet", "type" to "FILE", "always_visible" to false, "config" to mapOf("kinds" to listOf("TEXT"), "max" to 1))))))["tool_instance_id"] as String
        // The service answers the same text at every address: the list, and the file it names
        answer = """{"words": [{"pt": "sim", "sheet": "http://127.0.0.1:${server.address.port}/sim.txt"}]}"""
        val taskId = task(listOf(
            mapOf("kind" to TaskSteps.CALL_API, "api_id" to api("pt" to "text", "sheet" to "file")),
            mapOf("kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to sheetsId, "for_each" to words,
                "values" to mapOf("data.content" to item("pt"), "extra.sheet" to item("sheet")))
        ))

        val run = run(taskId)
        assertEquals(run.resultsJson, TaskRun.State.DONE.key, run.state)
        val sheet = (entries(sheetsId).single()["extra"] as Map<*, *>)["sheet"] as List<*>
        assertEquals(1, sheet.size)
    }

    @Test
    fun anEmptyFieldInAnElement_isNone() = runBlocking {
        val sheetsId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "notes", "config" to mapOf("name" to "Sheets",
            "extra_fields" to listOf(mapOf("display_name" to "Sheet", "type" to "FILE", "always_visible" to false, "config" to mapOf("kinds" to listOf("TEXT"), "max" to 1))))))["tool_instance_id"] as String
        // An empty address is no file: the element is stored without one, the call not refused
        answer = """{"words": [{"pt": "sim", "sheet": ""}]}"""
        val taskId = task(listOf(
            mapOf("kind" to TaskSteps.CALL_API, "api_id" to api("pt" to "text", "sheet" to "file")),
            mapOf("kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to sheetsId, "for_each" to words,
                "values" to mapOf("data.content" to item("pt"), "extra.sheet" to item("sheet")))
        ))

        val run = run(taskId)
        assertEquals(run.resultsJson, TaskRun.State.DONE.key, run.state)
        assertEquals("sim", (entries(sheetsId).single()["data"] as Map<*, *>)["content"])
    }
}
