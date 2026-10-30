package app.treelune.core.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A run waiting on a deferred API asks less and less often, never less than every half hour, and
 * its wait reads back as it was written: it outlives the app.
 */
class TaskRunTest {

    @Test
    fun aWait_asksLessAndLessOften_upToItsCeiling() {
        var wait = TaskRun.Wait("api", "job", 0, TaskRun.Wait.FIRST_INTERVAL, Long.MAX_VALUE)
        val intervals = (1..8).map { wait = wait.again(0); wait.interval }
        assertEquals(listOf(2L, 4, 8, 16, 30, 30, 30, 30).map { it * 60_000 }, intervals)
    }

    @Test
    fun aWait_readsBackAsWritten() {
        val wait = TaskRun.Wait("api", "job-1", 1_000, 120_000, 9_000)
        assertEquals(wait, TaskRun.Wait.of(wait.toJson()))
    }

    @Test
    fun aStepResult_readsBackAsWritten() {
        val result = TaskRun.StepResult("Call", values = mapOf("script" to "Hello"), types = mapOf("script" to "text"), raw = "{}", at = 5)
        assertEquals(result, TaskRun.StepResult.of(result.toJson()))
        val skipped = TaskRun.StepResult("Call", skipped = "Skipped: step 1 was", at = 5)
        assertEquals(skipped, TaskRun.StepResult.of(skipped.toJson()))
    }
}
