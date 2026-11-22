package app.treelune.core.tasks

import app.treelune.core.apis.ApiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The guarantees of an API's whole answer read by a path (docs/CORE.md, « APIs », « Tasks »): the
 * first part names a value a step gave, the rest goes into it through keys and indexes; a path that
 * holds nothing gives nothing, for the step that reads it to fail naming it; a value read by its
 * name alone is the value whole, as before.
 */
class ValuePathTest {

    private val answer = mapOf("session" to mapOf("title" to "Ancrage", "steps" to listOf(mapOf("name" to "Induction"), mapOf("name" to "Retour"))))

    private val result = TaskRun.StepResult("call", values = mapOf("temperature" to 21, ApiSettings.ANSWER to answer),
        types = mapOf("temperature" to ApiSettings.TEXT, ApiSettings.ANSWER to ApiSettings.OBJECT), at = 0)

    @Test
    fun theFirstPartNamesTheValue_theRestGoesIntoIt() {
        assertEquals("answer", TaskSteps.ValuePath.head("answer.session.title"))
        assertEquals("session.title", TaskSteps.ValuePath.rest("answer.session.title"))
        assertNull(TaskSteps.ValuePath.rest("temperature"))
    }

    @Test
    fun aPathReadsKeysAndIndexes_dotsOrBrackets() {
        assertEquals("Ancrage", result.valueOf("answer.session.title"))
        assertEquals("Retour", result.valueOf("answer.session.steps.1.name"))
        assertEquals("Induction", result.valueOf("answer.session.steps[0].name"))
    }

    @Test
    fun aPathThatHoldsNothingGivesNothing() {
        assertNull(result.valueOf("answer.session.subtitle"))
        assertNull(result.valueOf("answer.session.steps.5.name"))
        // A path into a value that is no object reads nothing in it
        assertNull(result.valueOf("temperature.unit"))
    }

    @Test
    fun aValueByItsNameIsWhole() {
        assertEquals(21, result.valueOf("temperature"))
        assertEquals(answer, result.valueOf("answer"))
    }

    @Test
    fun aListAtAPathIsWhatAStepRepeatsOver() {
        assertEquals(2, (result.valueOf("answer.session.steps") as List<*>).size)
    }
}
