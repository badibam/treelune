package app.treelune.core.tools

import android.content.Context

/**
 * Interface for tool types that require periodic scheduling.
 *
 * Tools implementing this interface can participate in the centralized
 * scheduling system managed by CoreScheduler.
 *
 * Discovery pattern: CoreScheduler discovers schedulers via ToolTypeManager,
 * no hardcoded dependencies required.
 *
 * Use cases:
 * - Messages tool: Check for scheduled messages to send
 * - Future Alerts tool: Evaluate alert conditions
 * - Any tool needing periodic background execution
 *
 * Scheduling lifecycle:
 * 1. CoreScheduler.tick() called every 1 min (app-open) or 10 min (app-closed), sooner when a
 *    scheduler's next action (nextAction) comes before
 * 2. CoreScheduler discovers all tools via ToolTypeManager.getAllToolTypes()
 * 3. For each tool with getScheduler() != null, calls checkScheduled()
 * 4. Tool scheduler performs its periodic checks and actions
 *
 * @see app.treelune.core.scheduling.CoreScheduler
 */
interface ToolScheduler {

    /**
     * Called periodically by CoreScheduler to check for scheduled actions.
     *
     * Implementation responsibilities:
     * - Load tool instances for this tool type
     * - Check configuration for scheduled items
     * - Execute scheduled actions if conditions met
     * - Handle errors gracefully (log + continue, don't throw)
     *
     * Pattern: Autonomous execution, no user interaction expected
     *
     * @param context Android context for accessing services and resources
     */
    suspend fun checkScheduled(context: Context)

    /**
     * The instant of this scheduler's next action, when it knows it and wants it on time (an
     * event's reminder): the core wakes the app then, rather than at its next 10-minute ring
     * (SchedulerAlarm). Null, the default, leaves the tick at its own pace. Asked after every
     * tick, once checkScheduled has run; an instant not after now is ignored.
     */
    suspend fun nextAction(context: Context): Long? = null
}
