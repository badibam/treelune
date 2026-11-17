package app.treelune.core.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

/** A run's wait and its steps' results read back as they were written: they outlive the app. */
class TaskRunTest {

    @Test
    fun aWait_readsBackAsWritten() {
        val wait = TaskRun.Wait("api", "job-1", 1_000, 9_000)
        assertEquals(wait, TaskRun.Wait.of(wait.toJson()))
        val asked = wait.copy(askedAt = 5_000)
        assertEquals(asked, TaskRun.Wait.of(asked.toJson()))
    }

    @Test
    fun aStepResult_readsBackAsWritten() {
        val result = TaskRun.StepResult("Call", values = mapOf("script" to "Hello"), types = mapOf("script" to "text"), raw = "{}", at = 5)
        assertEquals(result, TaskRun.StepResult.of(result.toJson()))
        val skipped = TaskRun.StepResult("Call", skipped = "Skipped: step 1 was", at = 5)
        assertEquals(skipped, TaskRun.StepResult.of(skipped.toJson()))
    }
}
