package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/**
 * A Task's run following a deferred API's job (TaskRunner.poll): an asking that gets no answer for
 * now does not fail the run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeferredJobTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var server: HttpServer
    private lateinit var zoneId: String

    /** The status code and the body the job's status answers, set by each test. */
    private var statusCode = 200
    private var statusBody = """{"state": "pending"}"""

    /** How many jobs the API was asked to start. */
    private var calls = 0

    @Before
    fun setUp() = runBlocking {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/jobs") { exchange ->
                calls++
                answer(exchange, 200, """{"job": "job-$calls"}""")
            }
            createContext("/status") { exchange -> answer(exchange, statusCode, statusBody) }
            start()
        }
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        zoneId = ok("zones.create", mapOf("name" to "Lab"))["zone_id"] as String
    }

    @After
    fun tearDown() = server.stop(0)

    private fun answer(exchange: com.sun.net.httpserver.HttpExchange, code: Int, text: String) {
        val body = text.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    /** A Task of one step calling a deferred API that waits an hour at most, its value `text`. */
    private suspend fun task(): TaskEntity {
        val apiId = ok("apis.create", mapOf("name" to "Slow", "config" to mapOf(
            "kind" to "web", "url" to "http://127.0.0.1:${server.address.port}/jobs", "method" to "POST", "key_in" to "none",
            "response" to "deferred", "id_path" to "job", "status_url" to "http://127.0.0.1:${server.address.port}/status?job={id}",
            "ready_path" to "state", "ready_value" to "ready", "failed_path" to "state", "failed_value" to "failed", "wait_limit" to HOUR,
            "values" to listOf(mapOf("name" to "text", "type" to "text", "path" to "text"))
        )))["api_id"] as String
        val taskId = ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Ask", "steps" to listOf(
            mapOf("kind" to TaskSteps.CALL_API, "api_id" to apiId)
        )))["task_id"] as String
        return AppDatabase.getDatabase(context).taskDao().get(taskId)!!
    }

    private suspend fun started(task: TaskEntity): TaskRunEntity {
        val runId = TaskRunner.start(context, task, TaskRun.Trigger.MANUAL, System.currentTimeMillis(), awaited = true)
        return read(runId)
    }

    private suspend fun read(runId: String) = AppDatabase.getDatabase(context).taskDao().getRun(runId)!!

    private suspend fun polled(run: TaskRunEntity, at: Long): TaskRunEntity {
        TaskRunner.poll(context, run, at)
        return read(run.id)
    }

    @Test
    fun aServerBrokenForNow_leavesTheRunWaiting_saidUntilAnAnswerComes() = runBlocking {
        val run = started(task())
        assertEquals(TaskRun.State.WAITING.key, run.state)
        val first = TaskRun.Wait.of(run.waitJson!!).nextAt
        statusCode = 503
        statusBody = "{}"
        val missed = polled(run, first)
        assertEquals(TaskRun.State.WAITING.key, missed.state)
        assertNotNull(TaskRun.Wait.of(missed.waitJson!!).missed)
        statusCode = 200
        statusBody = """{"state": "pending"}"""
        val answered = polled(missed, first + MINUTE)
        assertEquals(TaskRun.State.WAITING.key, answered.state)
        assertNull(TaskRun.Wait.of(answered.waitJson!!).missed)
        statusBody = """{"state": "ready", "text": "done"}"""
        val ready = polled(answered, first + 2 * MINUTE)
        assertEquals(TaskRun.State.DONE.key, ready.state)
        assertEquals("done", TaskRun.results(ready.resultsJson).single().values["text"])
    }

    @Test
    fun noAnswerUntilTheDeadline_failsTheRunLate_sayingTheLastFailure() = runBlocking {
        val run = started(task())
        val wait = TaskRun.Wait.of(run.waitJson!!)
        statusCode = 502
        statusBody = "{}"
        val missed = polled(run, wait.nextAt)
        val late = polled(missed, wait.deadline)
        assertEquals(TaskRun.State.FAILED.key, late.state)
        val error = TaskRun.results(late.resultsJson).single().error!!
        assertTrue(error, error.contains("502"))
    }

    @Test
    fun aJobThatSaysItFailed_failsTheRunAtOnce() = runBlocking {
        val run = started(task())
        statusBody = """{"state": "failed"}"""
        val failed = polled(run, TaskRun.Wait.of(run.waitJson!!).nextAt)
        assertEquals(TaskRun.State.FAILED.key, failed.state)
    }

    @Test
    fun aRunFailedLate_resumed_readsTheSameJob_neverCallingAgain() = runBlocking {
        val task = task()
        val run = started(task)
        val wait = TaskRun.Wait.of(run.waitJson!!)
        val late = polled(run, wait.deadline)
        assertEquals(TaskRun.State.FAILED.key, late.state)
        statusBody = """{"state": "ready", "text": "done"}"""
        assertNull(TaskRunner.resume(context, task, late, awaited = true))
        val resumed = read(run.id)
        assertEquals(TaskRun.State.DONE.key, resumed.state)
        assertEquals(1, calls)
        val result = TaskRun.results(resumed.resultsJson).single()
        assertEquals("done", result.values["text"])
        assertNull(result.error)
    }

    @Test
    fun aRunWhoseJobFailed_resumed_callsAnew() = runBlocking {
        val task = task()
        val run = started(task)
        statusBody = """{"state": "failed"}"""
        val failed = polled(run, TaskRun.Wait.of(run.waitJson!!).nextAt)
        assertNull(TaskRunner.resume(context, task, failed, awaited = true))
        val resumed = read(run.id)
        assertEquals(TaskRun.State.WAITING.key, resumed.state)
        assertEquals(2, calls)
        assertEquals("job-2", TaskRun.Wait.of(resumed.waitJson!!).jobId)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
