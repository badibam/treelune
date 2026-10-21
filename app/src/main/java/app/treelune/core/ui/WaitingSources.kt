package app.treelune.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * The app's single things that wait for the user (docs/BRICKS.md, Attente, its second form;
 * docs/BRICKS.md, « Waiting »). Each says at all times whether it waits and keeps its own
 * rule of what makes it seen; what belongs to a zone (a tool's entries, a routine's runs) is the
 * first form, counted by zone ([Waiting]).
 */
enum class WaitingSource {
    /** The active chat, an AI message not shown yet (ChatWaiting) */
    CHAT,
    /** Settings › Data, the automatic backup with no folder or its folder lost (AutoBackup) */
    BACKUP,
    /** The Guide, a tutorial waiting (Guide.pending) */
    GUIDE
}

/** Whether one of [sources] waits: a button leading to them is marked (UI.WaitingMark). */
@Composable
fun waiting(vararg sources: WaitingSource): Boolean {
    val chat by app.treelune.core.ai.orchestration.ChatWaiting.waits.collectAsState()
    val backup by app.treelune.core.backup.AutoBackup.pending.collectAsState()
    return sources.any {
        when (it) {
            WaitingSource.CHAT -> chat
            WaitingSource.BACKUP -> backup
            WaitingSource.GUIDE -> app.treelune.core.guide.Guide.pending
        }
    }
}
