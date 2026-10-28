package app.treelune.core.ai.orchestration

import android.content.Context
import app.treelune.core.ai.data.RoutineSettings
import app.treelune.core.ai.data.CommandStatus
import app.treelune.core.ai.data.MessageSender
import app.treelune.core.ai.data.SessionEndReason
import app.treelune.core.ai.data.SessionMessage
import app.treelune.core.ai.data.SystemMessageType
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.StringsContext
import app.treelune.core.utils.LogManager

/**
 * The end of a routine's run (docs/AI.md, « Summary of the run »): the summary its AI gave, the facts
 * the app counts beside it, and whether the run waits for the user to read it, as the routine's
 * setting says (RoutineSettings.NOTIFY_*). A run that waits is notified, the notification
 * opening it.
 */
object RunEnd {

    /** What the app attests of a run: the writes done, the writes refused or failed, how it ended. */
    data class Facts(val written: Int, val failed: Int, val endReason: SessionEndReason?)

    /** The writes of [messages], counted from their results, and [endReason]. */
    fun facts(messages: List<SessionMessage>, endReason: SessionEndReason?): Facts {
        val results = messages
            .mapNotNull { it.systemMessage?.takeIf { message -> message.type == SystemMessageType.ACTIONS_EXECUTED } }
            .flatMap { it.commandResults }
            .filter { it.isActionCommand }
        return Facts(
            written = results.count { it.status == CommandStatus.SUCCESS },
            failed = results.count { it.status == CommandStatus.FAILED || it.status == CommandStatus.CANCELLED },
            endReason = endReason
        )
    }

    /**
     * The summary of a run that ended COMPLETED: the one its last AI message carries, the
     * answer that confirmed the end. None for any other end, nor when that answer has none.
     */
    fun summary(messages: List<SessionMessage>, endReason: SessionEndReason?): String? {
        if (endReason != SessionEndReason.COMPLETED) return null
        return messages.lastOrNull { it.sender == MessageSender.AI }?.aiMessage?.summary
    }

    /** Whether something went wrong: an end other than COMPLETED, no summary, a write refused or failed. */
    fun problem(facts: Facts, summary: String?): Boolean =
        facts.endReason != SessionEndReason.COMPLETED || summary == null || facts.failed > 0

    /** Whether the run waits for the user, as the routine's [notifyEnd] says. */
    fun waits(notifyEnd: String, problem: Boolean): Boolean = when (notifyEnd) {
        RoutineSettings.NOTIFY_ALWAYS -> true
        RoutineSettings.NOTIFY_ON_PROBLEM -> problem
        RoutineSettings.NOTIFY_NEVER -> false
        else -> throw IllegalArgumentException("Unknown notify_end '$notifyEnd'")
    }

    /**
     * What stopped a run that ended in error, as its last provider error said it (the day's
     * budget reached, a key refused): the line of its notification, which « Error » alone left
     * unexplained. None when no such error ended it.
     */
    fun cause(messages: List<SessionMessage>, endReason: SessionEndReason?): String? {
        if (endReason != SessionEndReason.ERROR) return null
        return messages.lastOrNull { it.systemMessage?.type == SystemMessageType.PROVIDER_ERROR }?.systemMessage?.summary
    }

    /** The facts in a line, the end said when it is not COMPLETED. */
    fun line(facts: Facts, s: StringsContext): String {
        val counts = s.shared("run_facts_writes").format(facts.written, facts.failed)
        val end = facts.endReason?.takeIf { it != SessionEndReason.COMPLETED }
            ?.let { s.shared("ai_end_reason_${it.name.lowercase()}") }
        return if (end == null) counts else s.shared("run_facts_with_end").format(counts, end)
    }

    /**
     * The run [sessionId] over with [endReason], its messages [messages]: its summary kept, seen
     * at once when it does not wait, notified when it does. A failure is logged; the run then
     * waits, its summary unkept, which shows rather than hides it.
     */
    suspend fun end(context: Context, sessionId: String, endReason: SessionEndReason?, messages: List<SessionMessage>) {
        val summary = summary(messages, endReason)
        val facts = facts(messages, endReason)
        val coordinator = Coordinator(context)
        val result = coordinator.processUserAction("ai_sessions.end_run", buildMap {
            put("session_id", sessionId)
            summary?.let { put("summary", it) }
            put("problem", problem(facts, summary))
        })
        if (!result.isSuccess) {
            LogManager.aiSession("RunEnd: end of run $sessionId not written: ${result.error}", "ERROR")
            return
        }
        // A run that ends well emits its routine's event, in its chain
        if (!problem(facts, summary)) {
            val database = app.treelune.core.database.AppDatabase.getDatabase(context)
            val session = database.aiDao().getSession(sessionId)
            val event = session?.routineId?.let { database.aiDao().getRoutineById(it) }?.emitEvent
            if (session != null && event != null) kotlinx.coroutines.withContext(app.treelune.core.coordinator.ChainLink(session.chainId ?: session.id)) {
                app.treelune.core.events.EventPoints.emit(context, app.treelune.core.events.EventPoints.stored(event), null)
            }
        }
        if (result.data?.get("waits") != true) return

        val s = app.treelune.core.strings.Strings.`for`(context = context)
        val place = app.treelune.core.navigation.Place.Execution(sessionId, result.data["automation_id"] as String, result.data["zone_id"] as String)
        val sent = coordinator.processUserAction("notifications.send", mapOf(
            "title" to result.data["automation_name"] as String,
            "content" to (summary ?: cause(messages, endReason) ?: line(facts, s)),
            "priority" to "default",
            "place" to place.address
        ))
        if (!sent.isSuccess) LogManager.aiSession("RunEnd: run $sessionId not notified: ${sent.error}", "ERROR")
    }
}
