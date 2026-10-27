package app.treelune.core.ai.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.treelune.core.ai.domain.Phase
import app.treelune.core.ai.utils.AIFormatUtils
import app.treelune.core.ai.utils.PhaseUtils
import app.treelune.core.ai.utils.toDisplayString
import app.treelune.core.ai.utils.toEndReasonDisplayString
import app.treelune.core.ai.utils.toEndReasonStatus
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import app.treelune.core.utils.DateUtils

/**
 * ExecutionCard - Display automation execution summary
 *
 * Shows:
 * - Scheduled execution time
 * - Actual start time (createdAt)
 * - Phase (current/final) with colored status indicator
 * - End reason (if ended) with colored status indicator
 * - Duration (lastActivity - createdAt)
 * - Total roundtrips
 * - Token usage (formatted)
 * - Cost (formatted)
 * - The first sentence of its summary, and the mark of a run not yet read
 * - VIEW button for detail navigation
 *
 * Layout: 2 columns with data rows, VIEW button at bottom
 *
 * Usage: In AutomationScreen execution list
 */
@Composable
fun ExecutionCard(
    sessionId: String,
    scheduledExecutionTime: Long?,
    trigger: app.treelune.core.ai.data.ExecutionTrigger?,
    createdAt: Long,
    phase: Phase,
    endReason: String?,
    duration: Long,
    totalRoundtrips: Int,
    totalTokens: Int,
    cost: Double,
    costIsLowerBound: Boolean,
    livePhase: Phase? = null, // Real-time phase from AIState if this session is active
    summary: String? = null,  // What its AI said of the run, its first sentence shown
    waits: Boolean = false,   // Over and not yet read: marked
    onViewClick: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    // Use live phase if provided, otherwise use stored phase
    val displayPhase = livePhase ?: phase

    // The state shown for the end reason (a warning while still running)
    val status = endReason.toEndReasonStatus()

    UI.Card(
        type = CardType.DEFAULT
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UI.Space.M),
            verticalArrangement = Arrangement.spacedBy(UI.Space.S)
        ) {
            // Row 1: Scheduled time | Status (EndReason if exists, else Phase)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Scheduled execution time, marked while the run waits to be read
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (waits) UI.WaitingMark()
                    UI.Text(
                        // Planned, it says when; otherwise what started it: by hand, an entry, an event, a condition
                        text = when {
                            scheduledExecutionTime != null -> s.shared("automation_scheduled_label").format(app.treelune.core.utils.DateTimeFormatter.formatForDisplay(scheduledExecutionTime, context))
                            trigger == app.treelune.core.ai.data.ExecutionTrigger.ENTRY -> s.shared("automation_entry_execution")
                            trigger == app.treelune.core.ai.data.ExecutionTrigger.EVENT -> s.shared("automation_event_execution")
                            trigger == app.treelune.core.ai.data.ExecutionTrigger.CONDITION -> s.shared("automation_condition_execution")
                            else -> s.shared("automation_manual_execution")
                        },
                        type = TextType.CAPTION
                    )
                }

                // Right column: EndReason if exists, else Phase (both with status indicator)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.XS),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UI.StatusIndicator(status = status, size = 8.dp)
                    UI.Text(
                        text = if (endReason != null) {
                            endReason.toEndReasonDisplayString(context)
                        } else {
                            displayPhase.toDisplayString(context)
                        },
                        type = TextType.CAPTION
                    )
                }
            }

            // Row 2: Started time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Started time
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = s.shared("automation_started_label").format(app.treelune.core.utils.DateTimeFormatter.formatForDisplay(createdAt, context)),
                        type = TextType.CAPTION
                    )
                }
            }

            // The summary's first sentence, to know the run without opening it
            summary?.let { UI.Text(text = firstSentence(it), type = TextType.BODY) }

            UI.Divider()

            // Row 3: Duration | Roundtrips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Duration
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = s.shared("automation_duration_label").format(app.treelune.core.fields.Durations.format(duration, app.treelune.core.fields.DurationUnits.HOUR_MINUTE_SECOND.units, s)),
                        type = TextType.CAPTION
                    )
                }

                // Right column: Roundtrips
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    UI.Text(
                        text = if (totalRoundtrips == 1) s.shared("automation_roundtrips_one") else s.shared("automation_roundtrips_label").format(totalRoundtrips),
                        type = TextType.CAPTION
                    )
                }
            }

            // Row 4: Tokens | Cost
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Tokens
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = "${AIFormatUtils.formatTokenCount(totalTokens)} ${s.shared("automation_tokens_label")}",
                        type = TextType.CAPTION
                    )
                }

                // Right column: Cost
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    UI.Text(
                        text = AIFormatUtils.formatCost(cost).let {
                            if (costIsLowerBound) s.shared("ai_cost_at_least").format(it) else it
                        },
                        type = TextType.CAPTION
                    )
                }
            }

            UI.Divider()

            // Row 5: VIEW button (right-aligned)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                UI.ActionButton(
                    action = ButtonAction.VIEW,
                    display = ButtonDisplay.LABEL,
                    size = Size.S,
                    onClick = onViewClick
                )
            }
        }
    }
}

/** The first sentence of [text]: up to its first full stop, question or exclamation mark, or line end. */
internal fun firstSentence(text: String): String {
    val end = Regex("""[.!?](\s|$)|\n""").find(text.trim())?.range?.first ?: return text.trim()
    return text.trim().substring(0, end + 1).trim()
}
