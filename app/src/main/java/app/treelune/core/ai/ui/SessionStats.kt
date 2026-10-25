package app.treelune.core.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ai.utils.AIFormatUtils
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.*

/**
 * What a conversation cost and did, read by ai_sessions.get_stats, in a dialog closed by OK: its
 * total cost, its tokens by kind with their cost, the cost of each model when it called several,
 * its calls to the AI, the size of the last request sent, and the writes of its AI.
 */
@Composable
fun SessionStatsDialog(sessionId: String, sessionName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var stats by remember(sessionId) { mutableStateOf<Map<String, Any?>?>(null) }
    var error by remember(sessionId) { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId) {
        val result = Coordinator(context).processUserAction("ai_sessions.get_stats", mapOf("session_id" to sessionId))
        if (result.isSuccess) stats = result.data
        else error = result.error ?: s.shared("error_cost_calculation_failed")
    }

    UI.Dialog(type = DialogType.INFO, onConfirm = onDismiss, onCancel = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.L)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                UI.Text(text = s.shared("action_stats"), type = TextType.TITLE, fillMaxWidth = true)
                UI.Text(text = sessionName, type = TextType.CAPTION)
            }
            // What the whole day has spent, every conversation and routine together
            app.treelune.core.ai.budget.ui.BudgetLine()
            val data = stats
            when {
                error != null -> UI.Text(text = error!!, type = TextType.ERROR)
                data == null -> UI.Text(text = s.shared("ai_cost_loading"), type = TextType.CAPTION)
                else -> StatsContent(data, s)
            }
        }
    }
}

@Composable
private fun StatsContent(data: Map<String, Any?>, s: StringsContext) {
    // Calls of unknown cost: the costs shown only cover the others
    val unknownCalls = data["calls_with_unknown_cost"] as Int
    fun cost(value: Double, lowerBound: Boolean) =
        AIFormatUtils.formatCost(value).let { if (lowerBound) s.shared("ai_cost_at_least").format(it) else it }

    // The cost, and the tokens it comes from
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        StatRow(s.shared("ai_cost_total"), cost(data["total_cost"] as Double, unknownCalls > 0), TextType.SUBTITLE)
        if (unknownCalls > 0) UI.Text(text = s.shared("ai_cost_unknown_calls").format(unknownCalls), type = TextType.CAPTION)
        listOf(
            Triple("ai_cost_input", "total_uncached_input_tokens", "input_cost"),
            Triple("ai_cost_cache_write", "total_cache_write_tokens", "cache_write_cost"),
            Triple("ai_cost_cache_read", "total_cache_read_tokens", "cache_read_cost"),
            Triple("ai_cost_output", "total_output_tokens", "output_cost")
        ).forEach { (label, tokensKey, costKey) ->
            val tokens = data[tokensKey] as Int
            if (tokens > 0) StatRow(
                "${s.shared(label)} · ${AIFormatUtils.formatTokenCount(tokens)} ${s.shared("ai_cost_tokens")}",
                AIFormatUtils.formatCost(data[costKey] as Double)
            )
        }
    }

    // Each model's share, when the conversation called more than one
    val byModel = (data["by_model"] as List<*>).map { it as Map<*, *> }
    if (byModel.size > 1) Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(text = s.shared("ai_stats_by_model"), type = TextType.HEADING)
        byModel.forEach { model ->
            StatRow(model["model_id"] as String, cost(model["total_cost"] as Double, (model["calls_with_unknown_cost"] as Int) > 0))
        }
    }

    // What the conversation weighs, and what its AI did
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        StatRow(s.shared("ai_stats_calls"), AIFormatUtils.formatTokenCount(data["calls"] as Int))
        (data["last_sent_tokens"] as Int?)?.let { sent ->
            StatRow(s.shared("ai_stats_last_sent"), "${AIFormatUtils.formatTokenCount(sent)} ${s.shared("ai_cost_tokens")}")
            UI.Text(text = s.shared("ai_stats_last_sent_help"), type = TextType.CAPTION)
        }
        UI.Text(text = s.shared("run_facts_writes").format(data["writes_done"] as Int, data["writes_failed"] as Int), type = TextType.BODY)
    }
}

/** A label, and its value at the end of the line, kept whole: the label wraps, never the value. */
@Composable
private fun StatRow(label: String, value: String, type: TextType = TextType.BODY) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.M),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.weight(1f)) { UI.Text(text = label, type = type) }
        UI.Text(text = value, type = type)
    }
}
