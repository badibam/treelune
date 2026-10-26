package app.treelune.tools.sequence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers the steps a finished session keeps: their states and times, read off its run, and its counts following them. */
class SequenceStepsTest {

    private fun manual(name: String) = PlannedStep(name, null, StepEnd.MANUAL, null, emptyList())

    /** A run whose first step started at 0, the lead-in over. */
    private fun running(vararg steps: PlannedStep) = SequenceRun.start(steps.toList(), -SequenceRun.LEAD_IN)

    private fun Step.running() = (this as Step.Running).run

    @Test
    fun `a stopped session keeps the steps it left, with their times, the others not done`() {
        val run = SequenceGestures.skip(SequenceGestures.done(running(manual("Warm-up"), manual("Push-ups"), manual("Plank")), 10_000).running(), 25_000).running()
        val kept = SequenceSteps.of(SequenceGestures.stop(run, 30_000) as Step.Finished)

        assertEquals(listOf(
            SequenceSteps.Kept("Warm-up", SequenceSteps.DONE, 10_000),
            SequenceSteps.Kept("Push-ups", SequenceSteps.SKIPPED, 15_000),
            SequenceSteps.Kept("Plank", SequenceSteps.NOT_DONE, null)
        ), kept)
        assertEquals(StepCounts(1, 1, 1), SequenceSteps.counts(kept))
    }

    @Test
    fun `a completed session's last step runs to the end`() {
        val run = SequenceGestures.done(running(manual("Warm-up"), manual("Plank")), 10_000).running()
        val kept = SequenceSteps.of(SequenceGestures.done(run, 40_000) as Step.Finished)

        assertEquals(30_000L, kept.last().time)
        assertEquals(SequenceToolType.Status.DONE, SequenceSteps.counts(kept).status)
    }

    @Test
    fun `the steps read back are the steps written`() {
        val kept = listOf(SequenceSteps.Kept("Warm-up", SequenceSteps.DONE, 10_000), SequenceSteps.Kept("Plank", SequenceSteps.NOT_DONE, null))
        assertEquals(kept, SequenceSteps.read(SequenceSteps.toJson(kept)))
        assertNull(SequenceSteps.read(null))
    }

    /** Correcting a session is changing a step's state; a list that does not match its steps is refused. */
    @Test
    fun `a correction replaces the states, one a step`() {
        val kept = listOf(SequenceSteps.Kept("Warm-up", SequenceSteps.DONE, 10_000), SequenceSteps.Kept("Plank", SequenceSteps.NOT_DONE, null))

        val corrected = SequenceSteps.corrected(kept, listOf(SequenceSteps.DONE, SequenceSteps.DONE))!!
        assertEquals(StepCounts(2, 0, 0), SequenceSteps.counts(corrected))
        assertEquals(10_000L, corrected.first().time)
        assertNull(SequenceSteps.corrected(kept, listOf(SequenceSteps.DONE)))
        assertNull(SequenceSteps.corrected(kept, listOf(SequenceSteps.DONE, "maybe")))
    }
    /**
     * A timed step keeps the timer it was set to beside the time it ran, an extension left out, and
     * a step that read an entry's field keeps that entry designated beside its name then.
     */
    @Test
    fun `a step keeps its planned timer and the entry it read, written and read back`() {
        val entry = app.treelune.core.selection.Reference(app.treelune.core.selection.ReferenceKind.ENTRY, "e1", "data.audio")
        val timed = PlannedStep("Breathing 3", null, StepEnd.TIMED, 120_000, emptyList(), source = "Library › Breathing 3", sourceEntry = entry)
        val extended = SequenceGestures.extend(running(timed), 0)!!
        val kept = SequenceSteps.of(SequenceGestures.done(extended, 165_000) as Step.Finished)

        assertEquals(listOf(SequenceSteps.Kept("Breathing 3", SequenceSteps.DONE, 165_000, null, "Library › Breathing 3", 120_000, entry)), kept)
        assertEquals(kept, SequenceSteps.read(SequenceSteps.toJson(kept)))
        assertEquals(listOf(timed), SequencePlan.fromJson(SequencePlan.toJson(listOf(timed))))
        assertNull(SequenceSteps.of(SequenceGestures.done(running(manual("Plank")), 5_000) as Step.Finished).single().planned)
    }
}
