package app.treelune.core.ai.database

import androidx.room.*
import app.treelune.core.ai.data.RoutineSettings

/**
 * Routine database entity
 * Complex structures (schedule, triggers, executionHistory) stored as JSON
 *
 * Stored in common AppDatabase alongside AI sessions and provider configs
 */
@Entity(
    tableName = "automations",
    indices = [
        Index(value = ["zone_id"]),
        Index(value = ["is_enabled"]),
        Index(value = ["seed_session_id"])
    ]
)
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "seed_session_id") val seedSessionId: String,
    @ColumnInfo(name = "schedule_json") val scheduleJson: String?,              // JSON of ScheduleConfig
    /** What starts it beside its schedule, Triggers' stored form: `[]` when nothing does */
    @ColumnInfo(name = "triggers_json") val triggersJson: String,
    /** The event a run that ends well emits (EventPoints), its id; null for none */
    @ColumnInfo(name = "emit_event") val emitEvent: String? = null,
    // A scheduled routine's choice about missed runs, "limited" or "unlimited"; null without schedule
    @ColumnInfo(name = "catch_up") val catchUp: String?,
    // How late an occurrence may still run, in milliseconds, for a "limited" catch-up
    @ColumnInfo(name = "catch_up_window") val catchUpWindow: Long?,
    @ColumnInfo(name = "dismiss_older_instances") val dismissOlderInstances: Boolean,
    // The provider its runs call; null for none, the routine then neither switched on nor run
    @ColumnInfo(name = "provider_id") val providerId: String?,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,                    // Last modification timestamp (config change, enable/disable)
    @ColumnInfo(name = "last_execution_id") val lastExecutionId: String?,
    @ColumnInfo(name = "execution_history_json") val executionHistoryJson: String,       // JSON array of execution session IDs

    /**
     * Group assignment for this routine (nullable string)
     * Links to zone's tool_groups array
     * null = ungrouped routine
     */
    val group: String? = null,

    /** What its AI may reach, AccessMask's stored form; an empty list reaches everything */
    @ColumnInfo(name = "access_json") val accessJson: String = "[]",

    /** When its run waits for the user (RoutineSettings.NOTIFY_*): always, on a problem, never */
    @ColumnInfo(name = "notify_end") val notifyEnd: String = app.treelune.core.ai.data.RoutineSettings.NOTIFY_ALWAYS,

    /** The identity its runs speak as (docs/AI.md, « Identities »); null for the app's own AI */
    @ColumnInfo(name = "identity_id") val identityId: String? = null,

    /** Its look beside its name (RoutineSettings.LOOK): what it is for, null for nothing said */
    val description: String? = null,
    /** A Lucide icon name, stored under its current name */
    @ColumnInfo(name = "icon_name") val iconName: String = RoutineSettings.DEFAULT_ICON,
    /** Its icon's colour, a name of IconColor.NAMES; null for a neutral icon */
    @ColumnInfo(name = "icon_color") val iconColor: String? = null,
    /** The motif of its tile, a name of Texture.NAMES; null for none */
    val texture: String? = null,
    /** How its tile shows among its section's tools, ICON to SQUARE */
    @ColumnInfo(name = "display_mode") val displayMode: String = RoutineSettings.DEFAULT_MODE,
    /** Its column and row in the grid of its group's section (SectionGrid) */
    @ColumnInfo(name = "grid_x") val gridX: Int = 0,
    @ColumnInfo(name = "grid_y") val gridY: Int = 0
)
