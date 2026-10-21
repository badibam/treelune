package app.treelune.core.history

import app.treelune.core.coordinator.Source
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The step of the history an operation writes into, carried by the coroutine running it as its
 * origin is (Origin): the coordinator opens one for an operation asked from outside, and every
 * operation called from inside it writes into the same one, whatever instance of the coordinator
 * runs it (docs/DATA.md, « History of writes »).
 *
 * Its row in history_steps is written with the first row it touches, in that write's transaction
 * (Writes): an operation that writes nothing leaves no step, and a transaction rolled back takes
 * its step with it, the next write giving it again.
 *
 * @property source Who asked for the operation
 * @property isStep Whether it is a step of the history: one asked by someone (a screen, the AI, a
 *           client of the connector). The app's own writes, asked by no one (a scheduler, the
 *           app's start), are noted under one too, never shown nor undone, so that a refused undo
 *           can name them.
 * @property sessionId The AI's session whose command it is, null for any other
 */
class Step(
    val source: Source,
    val isStep: Boolean,
    val sessionId: String?,
    val id: String = UUID.randomUUID().toString(),
    val at: Long = System.currentTimeMillis()
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<Step>
}

/**
 * The AI's session whose commands run inside it (CommandExecutor): the step each command opens
 * keeps its id, for the history to show them grouped by answer.
 */
class StepSession(val sessionId: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<StepSession>
}

/**
 * The step the running operation writes into.
 *
 * @throws IllegalStateException outside an operation of the coordinator: a write out of every
 *         operation would escape the history
 */
suspend fun currentStep(): Step =
    coroutineContext[Step] ?: throw IllegalStateException("no step: a write outside an operation of the coordinator")
