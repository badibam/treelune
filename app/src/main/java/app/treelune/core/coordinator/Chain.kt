package app.treelune.core.coordinator

import android.content.Context
import app.treelune.core.ai.data.SessionType
import app.treelune.core.database.AppDatabase
import app.treelune.core.history.StepSession
import app.treelune.core.strings.Strings
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.LogManager
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The chain the running operation belongs to (docs/CORE.md, « Chains »), carried by the coroutine
 * that runs a Task's steps: what a step starts continues it. A routine's run carries its own
 * through its session (StepSession), its AI's commands running inside it.
 */
class ChainLink(val chainId: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ChainLink>
}

/**
 * The chains of runs (docs/CORE.md, « Chains »): a run of a Task or of a routine started by
 * another run belongs to its chain; one started by a person, an hour or the connector starts its
 * own, whose id is its own. A chain holds at most the cap of the AI limits (`chain_max_runs`):
 * past it, nothing it starts runs, the start refused, said in a notification, and the other
 * chains untouched. A run stores its chain only when it continues one (`chain_id`); null, it
 * starts the chain named after it.
 */
object Chains {

    /** What a start may do. */
    sealed interface Admission {
        /** It starts its own chain. */
        data object Starts : Admission
        /** It continues [chainId], within the cap. */
        data class Continues(val chainId: String) : Admission
        /** [chainId] holds the cap already: the start is refused, [message] saying why. */
        data class Capped(val chainId: String, val message: String) : Admission
    }

    /**
     * The chain the running operation continues: the Task run's whose step it is, or the
     * routine run's whose AI's command it is; null when nothing runs it — a screen, the
     * scheduler, a chat, the connector start a chain.
     */
    suspend fun current(context: Context): String? {
        coroutineContext[ChainLink]?.let { return it.chainId }
        val sessionId = coroutineContext[StepSession]?.sessionId ?: return null
        val session = AppDatabase.getDatabase(context).aiDao().getSession(sessionId) ?: return null
        if (session.type != SessionType.AUTOMATION) return null
        return session.chainId ?: session.id
    }

    /**
     * Whether a run asked now, of the thing named [name] (a Task, a routine), may start, given the
     * chain running ([current]). Refused past the cap, with a notification saying so.
     */
    suspend fun admit(context: Context, name: String): Admission {
        val chainId = current(context) ?: return Admission.Starts
        val database = AppDatabase.getDatabase(context)
        val runs = database.taskDao().countInChain(chainId) + database.aiDao().countInChain(chainId)
        val cap = AppConfigManager.getAILimits().chainMaxRuns
        if (runs < cap) return Admission.Continues(chainId)
        val s = Strings.`for`(context = context)
        val message = s.shared("chain_capped").format(name, cap)
        LogManager.service("Chain $chainId holds $runs runs, cap $cap: $name not started", "WARN")
        Coordinator(context).processScheduledTask("notifications.send", mapOf(
            "title" to name,
            "content" to message,
            "priority" to "default"
        ))
        return Admission.Capped(chainId, message)
    }
}
