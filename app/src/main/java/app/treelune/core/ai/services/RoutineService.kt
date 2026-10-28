package app.treelune.core.ai.services

import android.content.Context
import app.treelune.core.ai.data.Routine
import app.treelune.core.ai.data.RoutineMessage
import app.treelune.core.ai.data.RoutineSettings
import app.treelune.core.ai.data.MessageSender
import app.treelune.core.ai.data.RichMessage
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.database.AISessionEntity
import app.treelune.core.ai.database.SessionMessageEntity
import app.treelune.core.database.transaction
import app.treelune.core.fields.settings.SettingValues
import app.treelune.core.ai.database.RoutineEntity
import app.treelune.core.ai.orchestration.AIOrchestrator
import app.treelune.core.database.AppDatabase
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.grid.Groups
import app.treelune.core.grid.SectionItem
import app.treelune.core.grid.ToolPositions
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.DateTimeConverter
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.ScheduleCalculator
import app.treelune.core.utils.ScheduleConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Service for routine CRUD operations
 *
 * Operations:
 * - create (with its seed session and message), update, delete, get
 * - config: its settings, access and message as they are written
 * - list (by zone), list_all
 * - next_execution: when it runs next
 * - enable, disable
 * - execute_manual (triggers manual execution via AIOrchestrator), with `data` a Task passes it, joined to its message
 */
class RoutineService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val json = Json { ignoreUnknownKeys = true }

    // Coroutine scope for async tick() calls
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Access common database
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val dao by lazy { database.aiDao() }
    private val writes by lazy { app.treelune.core.history.Writes(context) }
    private val sections by lazy { app.treelune.core.grid.SectionGrid(context) }

    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.service("AutomationService.execute - operation: $operation", "DEBUG")

        val result = try {
            when (operation) {
                "create" -> createRoutine(params, token)
                "update" -> updateRoutine(params, token)
                "delete" -> deleteRoutine(params, token)
                "duplicate" -> duplicateRoutine(params, token)
                "get" -> getRoutine(params, token)
                "get_by_seed_session" -> getRoutineBySeedSession(params, token)
                "config" -> getConfig(params, token)
                "list" -> listRoutines(params, token)
                "list_all" -> listAllRoutines(token)
                "next_execution" -> nextExecution(params, token)
                "waiting" -> waiting(params)
                "enable" -> setEnabled(params, token, true)
                "disable" -> setEnabled(params, token, false)
                "execute_manual" -> executeManual(params, token)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // The screen that asked left: a cancellation goes on up, no error
            throw e
        } catch (e: Exception) {
            LogManager.service("AutomationService.execute - Error: ${e.message}", "ERROR", e)
            OperationResult.error(s.shared("service_error_automation").format(e.message ?: ""))
        }

        // Trigger tick() after CRUD operations that affect scheduling (if successful)
        if (result.success && operation in listOf("create", "update", "enable", "disable")) {
            LogManager.service("AutomationService: Triggering tick() after $operation", "DEBUG")
            scope.launch {
                try {
                    AIOrchestrator.tick()
                } catch (e: Exception) {
                    LogManager.service("AutomationService: Error calling tick(): ${e.message}", "ERROR", e)
                }
            }
        }

        return result
    }

    /**
     * Create new routine, with its seed session and the message in it, in one transaction: all
     * three, or none.
     *
     * Its settings (RoutineSettings) are given as their declaration describes them: "schedule"
     * an object, "catch_up" an object with the schedule and only with it. Its message, "message",
     * is given in blocks (RoutineMessage), empty when left out: the screen writes it afterwards.
     */
    private suspend fun createRoutine(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_zone_id_required"))
        val message = when (val read = messageGiven(params)) {
            is MessageGiven.Refused -> return OperationResult.error(read.message)
            is MessageGiven.Read -> read.message
            MessageGiven.None -> RichMessage(emptyList())
        }

        // The app's own demo gives its ids, its seed session's too; every other caller gets them made here
        val routineId = when (val given = app.treelune.core.coordinator.GivenId.read(params)) {
            app.treelune.core.coordinator.GivenId.Read.None -> UUID.randomUUID().toString()
            is app.treelune.core.coordinator.GivenId.Read.Accepted -> given.id
            is app.treelune.core.coordinator.GivenId.Read.Refused -> return OperationResult.error(s.shared("service_error_id_not_given").format(given.id))
        }
        val seedSessionId = params.optString("seed_session_id").takeIf { it.isNotEmpty() }?.let { given ->
            if (!app.treelune.core.coordinator.GivenId.isAccepted(given, app.treelune.core.coordinator.currentOrigin()))
                return OperationResult.error(s.shared("service_error_id_not_given").format(given))
            given
        } ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val entity = withSettings(RoutineEntity(
            id = routineId,
            name = "",
            zoneId = zoneId,
            seedSessionId = seedSessionId,
            scheduleJson = null,
            triggersJson = "[]",
            catchUp = null,
            catchUpWindow = null,
            dismissOlderInstances = false,
            providerId = null,
            isEnabled = true,
            group = null,
            createdAt = now,
            updatedAt = now,
            lastExecutionId = null,
            executionHistoryJson = json.encodeToString(emptyList<String>()),
            accessJson = when (val access = accessGiven(params, "[]")) {
                is Given.Refused -> return OperationResult.error(access.message)
                is Given.Kept -> access.json
            }
        ), settingsGiven(params, null), null).getOrElse { return OperationResult.error(it.message ?: s.shared("message_validation_error_simple")) }
        groupRefusal(entity.group, zoneId)?.let { return OperationResult.error(it) }
        app.treelune.core.triggers.Triggers.refusal(settingsGiven(params, null), RoutineSettings.nodes(context), context, s)?.let { return OperationResult.error(it) }
        app.treelune.core.events.EventPoints.refusal(settingsGiven(params, null), null, context, s)?.let { return OperationResult.error(it) }
        // Given, null for the app's own AI: never taken from the zone without the caller saying so
        if (!params.has(RoutineSettings.IDENTITY_ID)) return OperationResult.error(s.shared("automation_identity_required"))
        identityRefusal(entity.identityId)?.let { return OperationResult.error(it) }
        // Given too, null for none: never the default without the caller saying so
        if (!params.has(RoutineSettings.PROVIDER_ID)) return OperationResult.error(s.shared("automation_provider_required"))
        providerRefusal(entity)?.let { return OperationResult.error(it) }

        LogManager.service("Creating automation: name=${entity.name}, zoneId=$zoneId", "DEBUG")
        database.transaction {
            // A seed session is never run nor active: it holds the message each run starts from
            writes.insertSession(AISessionEntity(
                id = seedSessionId,
                name = entity.name,
                type = SessionType.SEED,
                routineId = null,
                scheduledExecutionTime = null,
                // Its runs take their routine's provider when they start
                providerId = null,
                providerSessionId = "",
                createdAt = now,
                lastActivity = now,
                isActive = false
            ))
            writes.insertMessage(seedMessage(seedSessionId, message, now))
            // At the end of its section's grid, among the tools and the variables
            val place = sections.arrive(SectionItem.of(entity), database.zoneDao().getZoneById(zoneId)?.tool_groups)
            writes.insertRoutine(entity.copy(gridX = place.column, gridY = place.row))
        }

        // A zone shown reads its routines again, whoever created this one
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()
        LogManager.service("Successfully created automation: $routineId", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to routineId,
            "name" to entity.name,
            "zone_id" to zoneId,
            "seed_session_id" to seedSessionId,
            "created_at" to now
        ))
    }

    /** The one USER message of seed session [sessionId], holding [message]. */
    private fun seedMessage(sessionId: String, message: RichMessage, now: Long) = SessionMessageEntity(
        id = UUID.randomUUID().toString(),
        sessionId = sessionId,
        timestamp = now,
        sender = MessageSender.USER,
        richContentJson = message.toJson(),
        textContent = null,
        aiMessageJson = null,
        aiMessageParsedJson = null,
        systemMessageJson = null,
        executionMetadataJson = null,
        excludeFromPrompt = false,
        inputTokens = 0,
        cacheWriteTokens = 0,
        cacheReadTokens = 0,
        outputTokens = 0
    )

    /** A routine's message as given: none, read, or refused with the reason. */
    private sealed class MessageGiven {
        object None : MessageGiven()
        data class Read(val message: RichMessage) : MessageGiven()
        data class Refused(val message: String) : MessageGiven()
    }

    /** The message [params] give under "message", in blocks (RoutineMessage). */
    private fun messageGiven(params: JSONObject): MessageGiven {
        if (!params.has("message")) return MessageGiven.None
        val blocks = params.optJSONArray("message")
            ?: return MessageGiven.Refused(s.shared("automation_message_unreadable").format(params.opt("message")))
        return try {
            MessageGiven.Read(RoutineMessage.fromBlocks(blocks, s::shared))
        } catch (e: IllegalArgumentException) {
            MessageGiven.Refused(e.message ?: s.shared("automation_message_unreadable").format(blocks))
        }
    }

    /**
     * Update existing routine
     *
     * A partial update: a setting left out keeps its value, a setting given as null is removed.
     */
    private suspend fun updateRoutine(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val routineId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Updating automation: $routineId", "DEBUG")

        val entity = dao.getRoutineById(routineId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        val newZoneId = params.optString("zone_id").takeIf { it.isNotBlank() }

        // A routine changing zone without a group given leaves its group behind
        val settings = settingsGiven(params, RoutineSettings.of(entity))
        val emptiedGroup = entity.group?.takeIf { newZoneId != null && newZoneId != entity.zoneId && !params.has("group") }
        if (emptiedGroup != null) settings.remove("group")

        val accessJson = when (val access = accessGiven(params, entity.accessJson)) {
            is Given.Refused -> return OperationResult.error(access.message)
            is Given.Kept -> access.json
        }
        val message = when (val read = messageGiven(params)) {
            is MessageGiven.Refused -> return OperationResult.error(read.message)
            is MessageGiven.Read -> read.message
            MessageGiven.None -> null
        }

        val updatedEntity = withSettings(entity.copy(
            zoneId = newZoneId ?: entity.zoneId,
            accessJson = accessJson,
            updatedAt = System.currentTimeMillis()
        ), settings, RoutineSettings.of(entity)).getOrElse { return OperationResult.error(it.message ?: s.shared("message_validation_error_simple")) }
        groupRefusal(updatedEntity.group, updatedEntity.zoneId)?.let { return OperationResult.error(it) }
        app.treelune.core.triggers.Triggers.refusal(settings, RoutineSettings.nodes(context), context, s)?.let { return OperationResult.error(it) }
        app.treelune.core.events.EventPoints.refusal(settings, null, context, s)?.let { return OperationResult.error(it) }
        // Only an identity that changes is checked: a routine saved again keeps the one it has
        if (updatedEntity.identityId != entity.identityId) identityRefusal(updatedEntity.identityId)?.let { return OperationResult.error(it) }
        providerRefusal(updatedEntity)?.let { return OperationResult.error(it) }

        // The message replaces the one of its seed session, with the settings or not at all
        val seed = message?.let {
            dao.getMessagesForSession(entity.seedSessionId).firstOrNull { it.sender == MessageSender.USER }
                ?: return OperationResult.error(s.shared("automation_seed_missing").format(entity.name))
        }
        database.transaction {
            if (message != null && seed != null) writes.updateMessage(seed.copy(richContentJson = message.toJson()))
            // Its section or its size changed, it moves in the grids, the tiles around with it
            val place = sections.move(SectionItem.of(entity), SectionItem.of(updatedEntity),
                database.zoneDao().getZoneById(entity.zoneId)?.tool_groups, database.zoneDao().getZoneById(updatedEntity.zoneId)?.tool_groups)
            writes.updateRoutine(updatedEntity.copy(gridX = place.column, gridY = place.row))
        }

        // A zone shown reads its routines again: their name, their state, their place
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()

        LogManager.service("Successfully updated automation: $routineId", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to routineId,
            "name" to updatedEntity.name,
            "zone_id" to updatedEntity.zoneId,
            "updated" to true
        ) + (emptiedGroup?.let { mapOf("group_emptied" to it) } ?: emptyMap()))
    }

    /** A routine's access as given: kept in its stored form, or refused with the reason. */
    private sealed class Given {
        data class Kept(val json: String) : Given()
        data class Refused(val message: String) : Given()
    }

    /**
     * The access [params] give (`access`, a list of `{"target": {"kind", "id"}, "level"}`,
     * AccessMask), [stored] when they give none. An AI held to a mask (AccessScope: a routine's, a
     * connector client's) leaves a routine no wider than its own, whether it gives the access or
     * keeps it: a routine it writes runs with it, and would reach what the AI writing it cannot.
     */
    private suspend fun accessGiven(params: JSONObject, stored: String): Given {
        val mask = if (!params.has("access")) app.treelune.core.access.AccessMask.fromJson(stored) else {
            val given = params.optJSONArray("access") ?: return Given.Refused(s.shared("automation_access_unreadable").format(params.opt("access")))
            try {
                app.treelune.core.access.AccessMask.fromJson(given.toString())
            } catch (e: Exception) {
                return Given.Refused(s.shared("automation_access_unreadable").format(e.message ?: ""))
            }
        }
        kotlin.coroutines.coroutineContext[app.treelune.core.access.AccessScope]?.let { scope ->
            app.treelune.core.access.AccessRules.widening(mask, scope.mask, scope.holder,
                app.treelune.core.access.DatabaseAccessLookups(context), s::shared)?.let { return Given.Refused(it) }
        }
        return Given.Kept(mask.toJson().toString())
    }

    /**
     * Why [entity] cannot hold its provider: one that does not exist, or none while it is on, which
     * it could not run without.
     */
    private suspend fun providerRefusal(entity: RoutineEntity): String? = when {
        entity.providerId == null -> if (entity.isEnabled) s.shared("automation_provider_none_enabled") else null
        dao.getProviderConfig(entity.providerId) == null -> s.shared("ai_error_provider_not_found").format(entity.providerId)
        else -> null
    }

    /** Why [identityId] cannot be a routine's identity: one that does not exist. */
    private suspend fun identityRefusal(identityId: String?): String? =
        if (identityId == null || AppDatabase.getDatabase(context).identityDao().getById(identityId) != null) null
        else s.shared("identity_error_not_found").format(identityId)

    /**
     * Why [group] cannot be held by a routine of the zone [zoneId], whose tool groups it
     * names; null when it can (none, or one of them).
     */
    private suspend fun groupRefusal(group: String?, zoneId: String): String? {
        val zone = AppDatabase.getDatabase(context).zoneDao().getZoneById(zoneId)
            ?: return s.shared("service_error_zone_not_found")
        return Groups.refusal(group, ToolPositions.zoneGroups(zone.tool_groups), s)
    }

    /**
     * [stored] with the settings [params] give: a setting left out keeps its value, a setting
     * given as null is removed. Without [stored], a creation, only what is given.
     */
    private fun settingsGiven(params: JSONObject, stored: JSONObject?): JSONObject {
        val settings = stored?.let { JSONObject(it.toString()) } ?: JSONObject()
        (app.treelune.core.look.Looks.keys(RoutineSettings.LOOK) + listOf("provider_id", RoutineSettings.IDENTITY_ID, "group", "is_enabled", "notify_end", "schedule", "catch_up", app.treelune.core.triggers.Triggers.KEY, app.treelune.core.events.EventPoints.KEY)).forEach { key ->
            if (!params.has(key)) return@forEach
            if (params.isNull(key)) settings.remove(key) else settings.put(key, params.get(key))
        }
        return settings
    }

    /**
     * [entity] holding [settings], once they are checked against the schema generated from their
     * declaration, and against what it cannot say: its look as every look is (Looks, an icon that
     * changes from [before]'s checked), the catch-up settings with a schedule and only with it.
     */
    private fun withSettings(entity: RoutineEntity, sent: JSONObject, before: JSONObject?): Result<RoutineEntity> {
        // A key set to null inside a setting (a schedule's) is not given: taken out, as the merge
        // above takes out a setting set to null
        val settings = app.treelune.core.utils.JsonNulls.withoutNullKeys(sent)
        // A look's setting left out takes its declared default, as a tool's (an icon, a mode)
        if (!settings.has(app.treelune.core.look.Looks.ICON)) settings.put(app.treelune.core.look.Looks.ICON, RoutineSettings.DEFAULT_ICON)
        if (!settings.has(app.treelune.core.look.Looks.DISPLAY_MODE)) settings.put(app.treelune.core.look.Looks.DISPLAY_MODE, RoutineSettings.DEFAULT_MODE)
        (app.treelune.core.look.Looks.check(RoutineSettings.LOOK, settings, before, context) as? app.treelune.core.look.Looks.Checked.Refused)
            ?.let { return Result.failure(IllegalArgumentException(it.message)) }
        val look = app.treelune.core.look.Looks.read(RoutineSettings.LOOK, settings)
        val checked = app.treelune.core.validation.SchemaValidator.validate(
            RoutineSettings.schema(context), app.treelune.core.utils.JsonUtils.toMap(settings), context)
        if (!checked.isValid) return Result.failure(IllegalArgumentException(checked.errorMessage))
        val schedule = settings.optJSONObject("schedule")
        val catchUp = settings.optJSONObject("catch_up")
        if (schedule != null && catchUp == null) return Result.failure(IllegalArgumentException(s.shared("automation_catch_up_required")))
        if (schedule == null && catchUp != null) return Result.failure(IllegalArgumentException(s.shared("automation_catch_up_without_schedule")))

        // A schedule the scheduler cannot read is refused here rather than failing each tick
        val scheduleJson = schedule?.toString()?.also {
            try {
                json.decodeFromString<ScheduleConfig>(it)
            } catch (e: Exception) {
                return Result.failure(IllegalArgumentException("Invalid schedule: ${e.message}"))
            }
        }
        return Result.success(entity.copy(
            name = settings.getString("name"),
            description = look.description,
            iconName = look.iconName!!,
            iconColor = look.iconColor,
            texture = look.texture,
            displayMode = look.displayMode.name,
            providerId = settings.optString(RoutineSettings.PROVIDER_ID).takeIf { it.isNotEmpty() },
            identityId = settings.optString(RoutineSettings.IDENTITY_ID).takeIf { it.isNotEmpty() },
            group = settings.optString("group").takeIf { it.isNotEmpty() },
            isEnabled = SettingValues(RoutineSettings.nodes(context), settings).boolean("is_enabled"),
            notifyEnd = SettingValues(RoutineSettings.nodes(context), settings).string("notify_end")
                ?: error("Setting 'notify_end' has neither a value nor a default"),
            scheduleJson = scheduleJson,
            catchUp = catchUp?.getString("limit"),
            catchUpWindow = catchUp?.takeIf { it.has("window") }?.getLong("window"),
            // Without a schedule nothing is missed: the column holds its declared default
            dismissOlderInstances = SettingValues(RoutineSettings.catchUpNodes(context), catchUp ?: JSONObject()).boolean("dismiss_older_instances"),
            triggersJson = (app.treelune.core.triggers.Triggers.of(settings) ?: JSONArray()).toString(),
            emitEvent = app.treelune.core.events.EventPoints.idIn(settings)
        ))
    }

    /**
     * Delete routine
     */
    private suspend fun deleteRoutine(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val routineId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Deleting automation: $routineId", "DEBUG")

        val entity = dao.getRoutineById(routineId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        database.transaction {
            sections.leave(SectionItem.of(entity), database.zoneDao().getZoneById(entity.zoneId)?.tool_groups)
            writes.deleteRoutine(routineId)
        }
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()

        LogManager.service("Successfully deleted automation: $routineId", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to routineId,
            "deleted" to true
        ))
    }

    /**
     * Duplicate an existing routine
     *
     * Creates a copy of the source routine with:
     * - A new SEED session (duplicated from source)
     * - All messages from source SEED copied to new SEED
     * - Its name marked as a copy (copy_name)
     * - Created in the specified target zone and group
     */
    private suspend fun duplicateRoutine(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val routineId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))
        val targetZoneId = params.optString("target_zone_id").takeIf { it.isNotBlank() }
            ?: return OperationResult.error(s.shared("error_param_zone_id_required"))
        val targetGroup = params.optString("target_group").takeIf { it.isNotBlank() }

        LogManager.service("Duplicating automation: $routineId to zone $targetZoneId", "DEBUG")
        groupRefusal(targetGroup, targetZoneId)?.let { return OperationResult.error(it) }

        // Load source routine
        val sourceEntity = dao.getRoutineById(routineId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        if (token.isCancelled) return OperationResult.cancelled()

        // Load source SEED session
        val sourceSeedSession = dao.getSession(sourceEntity.seedSessionId)
            ?: return OperationResult.error("Source SEED session not found: ${sourceEntity.seedSessionId}")

        if (token.isCancelled) return OperationResult.cancelled()

        // Load all messages from source SEED session
        val sourceMessages = dao.getMessagesForSession(sourceEntity.seedSessionId)

        LogManager.service("Loaded ${sourceMessages.size} messages from source SEED session", "DEBUG")

        if (token.isCancelled) return OperationResult.cancelled()

        // Create new SEED session (copy of source)
        val newSeedSessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val newSeedSession = sourceSeedSession.copy(
            id = newSeedSessionId,
            name = s.shared("copy_name").format(sourceEntity.name), // Named as the routine
            createdAt = now,
            lastActivity = now,
            isActive = false, // SEED sessions are never active
            endReason = null,
            routineId = null, // Will be set after routine is created
            phase = "IDLE",
            totalRoundtrips = 0,
            lastEventTime = now,
            lastUserInteractionTime = now
        )

        writes.insertSession(newSeedSession)

        LogManager.service("Created new SEED session: $newSeedSessionId", "DEBUG")

        if (token.isCancelled) return OperationResult.cancelled()

        // Duplicate all messages to new SEED session
        sourceMessages.forEach { sourceMessage ->
            val newMessage = sourceMessage.copy(
                id = UUID.randomUUID().toString(),
                sessionId = newSeedSessionId,
                timestamp = now + sourceMessages.indexOf(sourceMessage) // Preserve order with slight offset
            )
            writes.insertMessage(newMessage)
        }

        LogManager.service("Duplicated ${sourceMessages.size} messages to new SEED session", "DEBUG")

        if (token.isCancelled) return OperationResult.cancelled()

        // Create new routine
        val newRoutineId = UUID.randomUUID().toString()
        val newName = s.shared("copy_name").format(sourceEntity.name)

        val newRoutine = sourceEntity.copy(
            id = newRoutineId,
            name = newName,
            zoneId = targetZoneId, // Target zone, not source zone
            seedSessionId = newSeedSessionId,
            group = targetGroup, // Use target group (can be null for ungrouped)
            // Born off: with a schedule or a trigger, it would run as a double of the original
            isEnabled = false,
            createdAt = now,
            updatedAt = now,
            lastExecutionId = null, // Reset execution history
            executionHistoryJson = json.encodeToString(emptyList<String>())
        )

        // At the end of its section's grid in the target zone
        database.transaction {
            val place = sections.arrive(SectionItem.of(newRoutine), database.zoneDao().getZoneById(targetZoneId)?.tool_groups)
            writes.insertRoutine(newRoutine.copy(gridX = place.column, gridY = place.row))
        }

        // Notify UI
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()

        LogManager.service("Successfully duplicated automation $routineId to $newRoutineId", "INFO")

        // Convert timestamp to ISO 8601 for output
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()
        return OperationResult.success(mapOf(
            "automation_id" to newRoutineId,
            "source_automation_id" to routineId,
            "name" to newName,
            "zone_id" to newRoutine.zoneId,
            "seed_session_id" to newSeedSessionId,
            "created_at" to now
        ))
    }

    /**
     * Get routine by ID
     */
    private suspend fun getRoutine(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val routineId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Getting automation: $routineId", "DEBUG")

        val entity = dao.getRoutineById(routineId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        return OperationResult.success(mapOf(
            "automation" to routineToMap(entity) + ("condition_judgements" to
                app.treelune.core.triggers.ConditionTriggers.judgements(context, app.treelune.core.triggers.ConditionTriggers.ROUTINE, entity.id, entity.triggersJson))
        ))
    }

    /**
     * A routine as it is written: its zone, its settings (RoutineSettings), its access and its
     * message in blocks (RoutineMessage), each in the form create and update take.
     */
    private suspend fun getConfig(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val routineId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))
        val entity = dao.getRoutineById(routineId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))
        val seed = dao.getMessagesForSession(entity.seedSessionId).firstOrNull { it.sender == MessageSender.USER }
            ?: return OperationResult.error(s.shared("automation_seed_missing").format(entity.name))
        val message = seed.richContentJson?.let { RichMessage.fromJson(it) }
            ?: return OperationResult.error(s.shared("automation_seed_missing").format(entity.name))

        return OperationResult.success(mapOf(
            "automation_id" to entity.id,
            "zone_id" to entity.zoneId,
            "settings" to app.treelune.core.utils.JsonUtils.toMap(RoutineSettings.of(entity)),
            "access" to app.treelune.core.utils.JsonUtils.toList(entity.accessJson),
            "message" to app.treelune.core.utils.JsonUtils.toList(RoutineMessage.toBlocks(message))
        ))
    }

    /**
     * Get routine by seed session ID
     * Useful for SEED editor to load associated routine
     */
    private suspend fun getRoutineBySeedSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val seedSessionId = params.optString("seed_session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_seed_session_required"))

        LogManager.service("Getting automation by seed session: $seedSessionId", "DEBUG")

        val entity = dao.getRoutineBySeedSession(seedSessionId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        return OperationResult.success(mapOf(
            "automation" to routineToMap(entity)
        ))
    }

    /**
     * List routines for a zone
     */
    private suspend fun listRoutines(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_zone_id_required"))

        LogManager.service("Listing automations for zone: $zoneId", "DEBUG")

        val entities = dao.getRoutinesByZone(zoneId)
        return OperationResult.success(mapOf(
            "automations" to entities.map { routineToMap(it) },
            "count" to entities.size,
            "zone_id" to zoneId
        ))
    }

    /**
     * List all routines
     */
    private suspend fun listAllRoutines(token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.service("Listing all automations", "DEBUG")

        val entities = dao.getAllRoutines()
        return OperationResult.success(mapOf(
            "automations" to entities.map { routineToMap(it) },
            "count" to entities.size
        ))
    }

    /**
     * When a routine runs next (RoutineScheduler.nextExecutionAt): `next_execution_at` left
     * out when it will not run (disabled, without a schedule, its schedule ended).
     */
    private suspend fun nextExecution(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val routineId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))
        dao.getRoutineById(routineId) ?: return OperationResult.error(s.shared("error_automation_not_found"))

        val at = app.treelune.core.ai.scheduling.RoutineScheduler(context).nextExecutionAt(routineId)
        return OperationResult.success(mapOf("automation_id" to routineId) + (at?.let { mapOf("next_execution_at" to it) } ?: emptyMap()))
    }

    /**
     * Enable or disable routine
     */
    private suspend fun setEnabled(
        params: JSONObject,
        token: CancellationToken,
        enabled: Boolean
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val routineId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Setting automation $routineId enabled=$enabled", "DEBUG")

        val entity = dao.getRoutineById(routineId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))
        // Without a provider it could not run
        if (enabled && entity.providerId == null) return OperationResult.error(s.shared("automation_provider_none_enabled"))

        writes.setRoutineEnabled(routineId, enabled, System.currentTimeMillis())

        // Notify UI of routine change
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()

        LogManager.service("Successfully set automation $routineId enabled=$enabled", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to routineId,
            "is_enabled" to enabled
        ))
    }

    /**
     * Execute routine manually
     * This delegates to AIOrchestrator.executeRoutine() with MANUAL trigger
     * Manual executions go through queue if slot occupied (priority after CHAT)
     */
    private suspend fun executeManual(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val routineId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Manual execution requested for automation: $routineId", "INFO")

        // Verify routine exists
        val entity = dao.getRoutineById(routineId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))
        if (AIOrchestrator.isSeedEmpty(entity.seedSessionId)) return OperationResult.error(s.shared("automation_message_empty_run"))
        if (entity.providerId == null) return OperationResult.error(s.shared("automation_provider_none"))

        // Delegate to AIOrchestrator V2
        // V2 handles session creation, trigger, and scheduling internally
        // For MANUAL: scheduledFor = click time
        try {
            val clickTime = System.currentTimeMillis()
            // What a Task passes it, joined to its message
            val data = params.opt("data")?.takeIf { it != JSONObject.NULL }?.toString()
            // Asked by a Task's step, it continues that run's chain, refused past its cap
            val chainId = when (val admission = app.treelune.core.coordinator.Chains.admit(context, entity.name)) {
                is app.treelune.core.coordinator.Chains.Admission.Capped -> return OperationResult.error(admission.message)
                is app.treelune.core.coordinator.Chains.Admission.Continues -> admission.chainId
                app.treelune.core.coordinator.Chains.Admission.Starts -> null
            }
            AIOrchestrator.executeRoutine(routineId, scheduledFor = clickTime, trigger = app.treelune.core.ai.data.ExecutionTrigger.MANUAL, data = data, chainId = chainId)

            LogManager.service("Successfully triggered automation: $routineId", "INFO")

            return OperationResult.success(mapOf(
                "automation_id" to routineId,
                "status" to "triggered"
            ))
        } catch (e: Exception) {
            LogManager.service("Failed to execute automation: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to execute automation: ${e.message}")
        }
    }

    /**
     * What waits for the user among the routines (docs/AI.md, « Summary of the run »): how many runs
     * of each routine are over and not yet seen, the routines of `zone_id` or all of them, and the
     * sum by zone; the first form of the waiting brick, beside tools.waiting.
     */
    private suspend fun waiting(params: JSONObject): OperationResult {
        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
        val routines = (if (zoneId != null) dao.getRoutinesByZone(zoneId) else dao.getAllRoutines()).associateBy { it.id }
        val counts = dao.getWaitingRuns().mapNotNull { it.routineId?.takeIf { id -> id in routines } }.groupingBy { it }.eachCount()
        return OperationResult.success(mapOf(
            "automations" to counts,
            "zones" to counts.entries.groupBy { routines.getValue(it.key).zoneId }.mapValues { (_, inZone) -> inZone.sumOf { it.value } }
        ))
    }

    /**
     * A routine as a result gives it: what locates it and its history, then its settings in
     * the form their declaration describes (settingsOf). Routine.fromResult reads it back.
     */
    private fun routineToMap(entity: RoutineEntity): Map<String, Any?> = mapOf(
        "id" to entity.id,
        "zone_id" to entity.zoneId,
        "seed_session_id" to entity.seedSessionId,
        "created_at" to entity.createdAt,
        "updated_at" to entity.updatedAt,
        "last_execution_id" to entity.lastExecutionId,
        "execution_history" to json.decodeFromString<List<String>>(entity.executionHistoryJson),
        "access" to app.treelune.core.utils.JsonUtils.toList(entity.accessJson),
        "grid_x" to entity.gridX,
        "grid_y" to entity.gridY
    ) + app.treelune.core.utils.JsonUtils.toMap(RoutineSettings.of(entity))

    /** An operation said in words, its routine by its name: what a validation card and a result line show. */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        val name = params.optString("name").ifEmpty { null }
            ?: params.optString("automation_id").takeIf { it.isNotEmpty() }?.let { dao.getRoutineById(it)?.name ?: it }
            ?: ""
        return when (operation) {
            "create" -> s.shared("action_verbalize_automations_create").format(name)
            "update" -> s.shared("action_verbalize_automations_update").format(name)
            "delete" -> s.shared("action_verbalize_automations_delete").format(name)
            "config", "get", "get_by_seed_session" -> s.shared("action_verbalize_automations_config").format(name)
            "duplicate" -> s.shared("action_verbalize_automations_duplicate").format(name)
            "enable" -> s.shared("action_verbalize_automations_enable").format(name)
            "disable" -> s.shared("action_verbalize_automations_disable").format(name)
            "execute_manual" -> s.shared("action_verbalize_automations_execute_manual").format(name)
            "next_execution" -> s.shared("action_verbalize_automations_next_execution").format(name)
            "list", "list_all" -> s.shared("action_verbalize_automations_list")
            "waiting" -> s.shared("action_verbalize_automations_waiting")
            else -> s.shared("action_verbalize_unknown")
        }
    }
}
