package app.treelune.core.ai.ui.routine

import org.junit.Assert.assertEquals
import org.junit.Test

/** The line a run shows of its summary in its routine's history: its first sentence. */
class FirstSentenceTest {

    @Test
    fun endsAtTheFirstFullStopQuestionOrExclamationMark() {
        assertEquals("I logged 72.4 kg.", firstSentence("I logged 72.4 kg. It is the third rise."))
        assertEquals("Nothing to log?", firstSentence("Nothing to log? The tool was empty."))
    }

    @Test
    fun aNumberWithAPointIsNotAnEnd() {
        assertEquals("Weight 72.4 kg logged", firstSentence("Weight 72.4 kg logged"))
    }

    @Test
    fun endsAtALineEnd() {
        assertEquals("Done", firstSentence("Done\nDetails follow"))
    }
}
