package app.treelune.core.ai.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.treelune.core.access.AccessMask
import app.treelune.core.ai.data.MessageSegment
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.ui.components.RichComposer
import app.treelune.core.fields.settings.scheduleSummary
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import app.treelune.core.ui.components.ButtonRow
import app.treelune.core.utils.ScheduleConfig

/**
 * A routine's editor, the page of its message (AIScreen's SEED mode): the message, written in
 * blocks with its pointers (RichComposer, its periods stored as relative), then a button for
 * each of its settings, summing it up and opening its own window — the schedule (and what is
 * done of missed runs with it), the triggers, what its AI may reach — then saving or leaving.
 * Saving writes the message and the settings at once.
 */
@Composable
fun AutomationEditor(
    segments: List<MessageSegment>,
    onSegmentsChange: (List<MessageSegment>) -> Unit,
    scheduleConfig: ScheduleConfig?,
    onConfigureSchedule: () -> Unit,
    triggersCount: Int,
    onConfigureTriggers: () -> Unit,
    access: AccessMask,
    onConfigureAccess: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        // The message, a SEED session's template; its send button is the form's save
        UI.RichComposer(
            segments = segments,
            onSegmentsChange = onSegmentsChange,
            onSend = { },
            placeholder = s.shared("automation_message_placeholder"),
            showEnrichmentButtons = true,
            showSendButton = false,
            sessionType = SessionType.SEED,
            // The page scrolls as a whole: the message takes the height it needs
            boundedBlocks = false
        )

        // The settings, each summed up on its button
        ButtonRow {
            SettingButton("clock", scheduleConfig?.let { scheduleSummary(it, s) } ?: s.shared("schedule_summary_none"), onConfigureSchedule)
            SettingButton(
                "zap",
                if (triggersCount > 0) s.shared("automation_triggers_count").format(triggersCount) else s.shared("automation_triggers_none"),
                onConfigureTriggers
            )
            // Everything while nothing is listed (AccessMask)
            SettingButton(
                "shield",
                if (access.grants.isEmpty()) s.shared("automation_access_all") else s.shared("automation_access_count").format(access.grants.size),
                onConfigureAccess
            )
        }

        UI.FormActions {
            UI.ActionButton(action = ButtonAction.SAVE, display = ButtonDisplay.LABEL, onClick = onSave)
            UI.ActionButton(action = ButtonAction.CANCEL, display = ButtonDisplay.LABEL, onClick = onCancel)
        }
    }
}

/** A setting's button: its icon and the summary of what it holds. */
@Composable
private fun SettingButton(icon: String, summary: String, onClick: () -> Unit) {
    UI.Button(type = ButtonType.DEFAULT, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.CenterVertically) {
            UI.Icon(iconName = icon, size = 20.dp)
            UI.Text(text = summary, type = TextType.BODY)
        }
    }
}
