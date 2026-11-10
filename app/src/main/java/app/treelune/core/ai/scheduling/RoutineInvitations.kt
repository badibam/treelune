package app.treelune.core.ai.scheduling

import android.content.Context
import app.treelune.core.ai.database.RoutineEntity
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.strings.Strings
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.LogManager

/**
 * The invitations of a routine used as a conversation (RoutineSettings.USAGE_CONVERSATION): what
 * would run it alone -- its schedule, a trigger -- invites to its conversation instead. The
 * invitation marks its tile as waiting and sends a notification that opens its page, where its
 * conversation button opens the conversation with its message; opening it ends the wait
 * ([opened]). A scheduled occurrence invited is kept (`invited_for`), where the search for the
 * next one starts, as a run's planned time is for a routine run alone.
 */
object RoutineInvitations {

    /** [routine] invited to its conversation, for the scheduled [occurrence] (null for a trigger). */
    suspend fun invite(context: Context, routine: RoutineEntity, occurrence: Long?) {
        val dao = AppDatabase.getDatabase(context).aiDao()
        dao.setRoutineInvitation(routine.id, occurrence ?: routine.invitedFor, waiting = true)
        val s = Strings.`for`(context = context)
        val sent = Coordinator(context).processUserAction("notifications.send", mapOf(
            "title" to routine.name,
            "content" to s.shared("routine_invitation_notification"),
            "priority" to "default",
            "icon_name" to routine.iconName,
            // One per routine: a second invitation replaces the first
            "notification_id" to ("routine_invitation_" + routine.id).hashCode(),
            "place" to app.treelune.core.navigation.Place.Routine(routine.id, routine.zoneId).address
        ))
        if (!sent.isSuccess) LogManager.aiSession("RoutineInvitations: notification of ${routine.id} not sent: ${sent.error}", "ERROR")
        LogManager.aiSession("RoutineInvitations: routine ${routine.id} invited to its conversation (occurrence=$occurrence)", "INFO")
        DataChangeNotifier.notifyAISessionsChanged(routine.id)
    }

    /** [routineId]'s conversation opened: its invitation no longer waits. */
    suspend fun opened(context: Context, routineId: String) {
        val dao = AppDatabase.getDatabase(context).aiDao()
        val routine = dao.getRoutineById(routineId) ?: return
        if (!routine.invitationWaiting) return
        dao.setRoutineInvitation(routineId, routine.invitedFor, waiting = false)
        DataChangeNotifier.notifyAISessionsChanged(routineId)
    }
}
