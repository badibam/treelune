package app.treelune.core.ai.budget

import android.content.Context
import app.treelune.core.ai.data.SessionCost
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.database.AppDatabase
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.Strings
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * The daily budget of the AI calls (docs/AI.md, « Daily budget »): every call of the app,
 * conversations, routines and what a Task makes them run alike, summed over the app's day
 * (TimeResolver: the day starts at the hour the user set). Its amount and alert threshold are
 * AI limits (`daily_budget`, `budget_alert_percent`), each absent for none.
 *
 * Reached, no call starts any more: the session says so, a conversation handing back, a routine's
 * run ending. A call in progress finishes: the overrun is at most one call, whose cost is known
 * only once it answers. A call of unknown cost (a model without a price, a call cut before its
 * tokens came back) counts for nothing: the budget page names the providers whose model has no
 * price, and the day's sum shows « ≥ ».
 */
object Budget {

    /** What the day spent: [spent] in dollars over the known calls, [unknown] calls of unknown cost, against [budget]. */
    data class Today(val spent: Double, val unknown: Int, val budget: Double?, val alertPercent: Int?) {
        val reached: Boolean get() = budget != null && spent >= budget
        val alerted: Boolean get() = budget != null && alertPercent != null && spent >= budget * alertPercent / 100.0
    }

    /** What the app's day has spent so far, at [now]. */
    suspend fun today(context: Context, now: Long = System.currentTimeMillis()): Today {
        val resolver = TimeResolver.at(now)
        val since = resolver.startOf(resolver.today())
        val cost = SessionCost.of(AppDatabase.getDatabase(context).aiDao().getCallUsagesSince(since).map { it.toCallUsage() })
        val limits = AppConfigManager.getAILimits()
        return Today(cost.totalCost, cost.callsWithUnknownCost, limits.dailyBudget, limits.budgetAlertPercent)
    }

    /** Why no AI call starts now, the day's budget reached; null when one may. */
    suspend fun refusal(context: Context): String? {
        val today = today(context)
        if (!today.reached) return null
        val s = Strings.`for`(context = context)
        return s.shared("budget_reached").format(money(today.budget!!))
    }

    /**
     * After a call: the notification of the alert threshold crossed, then of the budget reached,
     * each once in the app's day (its marks kept on this phone alone), and the event the threshold
     * emits, if one is chosen, once a day as well.
     */
    suspend fun afterCall(context: Context) {
        val today = today(context)
        if (today.budget == null) return
        val day = TimeResolver.at(System.currentTimeMillis()).today().toString()
        val marks = context.getSharedPreferences(MARKS, Context.MODE_PRIVATE)
        val s = Strings.`for`(context = context)
        suspend fun once(key: String, content: String) {
            if (marks.getString(key, null) == day) return
            marks.edit().putString(key, day).apply()
            val sent = Coordinator(context).processScheduledTask("notifications.send", mapOf(
                "title" to s.shared("budget_title"),
                "content" to content,
                "priority" to "default"
            ))
            if (!sent.isError()) LogManager.aiSession("Budget: $key notified for $day", "INFO")
        }
        when {
            today.reached -> once(REACHED, s.shared("budget_reached").format(money(today.budget)))
            today.alerted -> once(ALERTED, s.shared("budget_alert").format(today.alertPercent!!, money(today.spent), money(today.budget)))
        }
        // The threshold's event, once a day too, even when one call went past the budget at once
        val event = AppConfigManager.getAILimits().budgetAlertEvent
        if (today.alerted && event != null && marks.getString(EMITTED, null) != day) {
            marks.edit().putString(EMITTED, day).apply()
            app.treelune.core.events.EventPoints.emit(context, app.treelune.core.events.EventPoints.stored(event), null)
            LogManager.aiSession("Budget: alert event emitted for $day", "INFO")
        }
    }

    /**
     * The providers configured whose model has no price, neither set by hand nor in the list of
     * prices: their calls count for nothing in the budget. By their name.
     */
    suspend fun unpriced(context: Context): List<String> {
        val kinds = app.treelune.core.ai.providers.AIProviderKinds(context)
        return AppDatabase.getDatabase(context).aiDao().getAllProviderConfigs().filter { stored ->
            val kind = kinds.of(stored.kind) ?: return@filter true
            val config = JSONObject(stored.configJson)
            val model = app.treelune.core.ai.providers.AIProviderSettings.read(kind, config, context).string("model") ?: return@filter true
            val own = kind.ownPrices(config, context)
            if (own != null) own.input == null || own.output == null
            else app.treelune.core.ai.utils.ModelPriceManager.getModelPrice(context, model).let { it?.inputCostPerToken == null || it.outputCostPerToken == null }
        }.map { it.name }
    }

    /** [amount] in dollars, as the costs screen writes it (AIFormatUtils). */
    fun money(amount: Double): String = app.treelune.core.ai.utils.AIFormatUtils.formatCost(amount)

    private fun app.treelune.core.commands.CommandResult.isError() = status != app.treelune.core.commands.CommandStatus.SUCCESS

    private const val MARKS = "budget_marks"
    private const val ALERTED = "alerted_on"
    private const val REACHED = "reached_on"
    private const val EMITTED = "event_emitted_on"
}
