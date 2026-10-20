package app.treelune.core.ai.services

import android.content.Context
import app.treelune.core.ai.data.Automation
import app.treelune.core.ai.data.AutomationMessage
import app.treelune.core.ai.data.AutomationSettings
import app.treelune.core.ai.data.MessageSender
import app.treelune.core.ai.data.RichMessage
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.database.AISessionEntity
import app.treelune.core.ai.database.SessionMessageEntity
import app.treelune.core.database.transaction
import app.treelune.core.fields.settings.SettingValues
import app.treelune.core.ai.database.AutomationEntity
import app.treelune.core.ai.orchestration.AIOrchestrator
import app.treelune.core.database.AppDatabase
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.grid.Groups
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
 * Service for automation CRUD operations
 *
 * Operations:
 * - create (with its seed session and message), update, delete, get
 * - config: its settings, access and message as they are written
 * - list (by zone), list_all
 * - enable, disable
 * - execute_manual (triggers manual execution via AIOrchestrator)
 */
class AutomationService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val json = Json { ignoreUnknownKeys = true }

    // Coroutine scope for async tick() calls
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Access common database
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val dao by lazy { database.aiDao() }
    private val writes by lazy { app.treelune.core.history.Writes(context) }

    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.service("AutomationService.execute - operation: $operation", "DEBUG")

        val result = try {
            when (operation) {
                "create" -> createAutomation(params, token)
                "update" -> updateAutomation(params, token)
                "delete" -> deleteAutomation(params, token)
                "duplicate" -> duplicateAutomation(params, token)
                "get" -> getAutomation(params, token)
                "get_by_seed_session" -> getAutomationBySeedSession(params, token)
                "config" -> getConfig(params, token)
                "list" -> listAutomations(params, token)
                "list_all" -> listAllAutomations(token)
                "waiting" -> waiting(params)
                "enable" -> setEnabled(params, token, true)
                "disable" -> setEnabled(params, token, false)
                "execute_manual" -> executeManual(params, token)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
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
     * Create new automation, with its seed session and the message in it, in one transaction: all
     * three, or none.
     *
     * Its settings (AutomationSettings) are given as their declaration describes them: "schedule"
     * an object, "catch_up" an object with the schedule and only with it. Its message, "message",
     * is given in blocks (AutomationMessage), empty when left out: the screen writes it afterwards.
     */
    private suspend fun createAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_zone_id_required"))
        val message = when (val read = messageGiven(params)) {
            is MessageGiven.Refused -> return OperationResult.error(read.message)
            is MessageGiven.Read -> read.message
            MessageGiven.None -> RichMessage(emptyList())
        }

        // Parse trigger IDs
        val triggerIdsArray = params.optJSONArray("trigger_ids") ?: JSONArray()
        val triggerIds = (0 until triggerIdsArray.length()).map { triggerIdsArray.getString(it) }

        // The app's own demo gives its ids, its seed session's too; every other caller gets them made here
        val automationId = when (val given = app.treelune.core.coordinator.GivenId.read(params)) {
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

        val entity = withSettings(AutomationEntity(
            id = automationId,
            name = "",
            zoneId = zoneId,
            seedSessionId = seedSessionId,
            scheduleJson = null,
            triggerIdsJson = json.encodeToString(triggerIds),
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
        ), settingsGiven(params, null)).getOrElse { return OperationResult.error(it.message ?: s.shared("message_validation_error_simple")) }
        groupRefusal(entity.group, zoneId)?.let { return OperationResult.error(it) }
        // Given, null for the app's own AI: never taken from the zone without the caller saying so
        if (!params.has(AutomationSettings.IDENTITY_ID)) return OperationResult.error(s.shared("automation_identity_required"))
        identityRefusal(entity.identityId)?.let { return OperationResult.error(it) }
        // Given too, null for none: never the default without the caller saying so
        if (!params.has(AutomationSettings.PROVIDER_ID)) return OperationResult.error(s.shared("automation_provider_required"))
        providerRefusal(entity)?.let { return OperationResult.error(it) }

        LogManager.service("Creating automation: name=${entity.name}, zoneId=$zoneId", "DEBUG")
        database.transaction {
            // A seed session is never run nor active: it holds the message each run starts from
            writes.insertSession(AISessionEntity(
                id = seedSessionId,
                name = entity.name,
                type = SessionType.SEED,
                automationId = null,
                scheduledExecutionTime = null,
                // Its runs take their routine's provider when they start
                providerId = null,
                providerSessionId = "",
                createdAt = now,
                lastActivity = now,
                isActive = false
            ))
            writes.insertMessage(seedMessage(seedSessionId, message, now))
            writes.insertAutomation(entity)
        }

        // A zone shown reads its routines again, whoever created this one
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()
        LogManager.service("Successfully created automation: $automationId", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to automationId,
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

    /** The message [params] give under "message", in blocks (AutomationMessage). */
    private fun messageGiven(params: JSONObject): MessageGiven {
        if (!params.has("message")) return MessageGiven.None
        val blocks = params.optJSONArray("message")
            ?: return MessageGiven.Refused(s.shared("automation_message_unreadable").format(params.opt("message")))
        return try {
            MessageGiven.Read(AutomationMessage.fromBlocks(blocks, s::shared))
        } catch (e: IllegalArgumentException) {
            MessageGiven.Refused(e.message ?: s.shared("automation_message_unreadable").format(blocks))
        }
    }

    /**
     * Update existing automation
     *
     * A partial update: a setting left out keeps its value, a setting given as null is removed.
     */
    private suspend fun updateAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Updating automation: $automationId", "DEBUG")

        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        val newZoneId = params.optString("zone_id").takeIf { it.isNotBlank() }

        // Parse trigger IDs if provided
        val triggerIdsArray = params.optJSONArray("trigger_ids")
        val triggerIdsJson = triggerIdsArray?.let { array -> json.encodeToString((0 until array.length()).map { array.getString(it) }) }
            ?: entity.triggerIdsJson

        // An automation changing zone without a group given leaves its group behind
        val settings = settingsGiven(params, settingsOf(entity))
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
            triggerIdsJson = triggerIdsJson,
            accessJson = accessJson,
            updatedAt = System.currentTimeMillis()
        ), settings).getOrElse { return OperationResult.error(it.message ?: s.shared("message_validation_error_simple")) }
        groupRefusal(updatedEntity.group, updatedEntity.zoneId)?.let { return OperationResult.error(it) }
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
            writes.updateAutomation(updatedEntity)
        }

        // A zone shown reads its routines again: their name, their state, their place
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()

        LogManager.service("Successfully updated automation: $automationId", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to automationId,
            "name" to updatedEntity.name,
            "zone_id" to updatedEntity.zoneId,
            "updated" to true
        ) + (emptiedGroup?.let { mapOf("group_emptied" to it) } ?: emptyMap()))
    }

    /** An automation's access as given: kept in its stored form, or refused with the reason. */
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
    private suspend fun providerRefusal(entity: AutomationEntity): String? = when {
        entity.providerId == null -> if (entity.isEnabled) s.shared("automation_provider_none_enabled") else null
        dao.getProviderConfig(entity.providerId) == null -> s.shared("ai_error_provider_not_found").format(entity.providerId)
        else -> null
    }

    /** Why [identityId] cannot be an automation's identity: one that does not exist. */
    private suspend fun identityRefusal(identityId: String?): String? =
        if (identityId == null || AppDatabase.getDatabase(context).identityDao().getById(identityId) != null) null
        else s.shared("identity_error_not_found").format(identityId)

    /**
     * Why [group] cannot be held by an automation of the zone [zoneId], whose tool groups it
     * names; null when it can (none, or one of them).
     */
    private suspend fun groupRefusal(group: String?, zoneId: String): String? {
        val zone = AppDatabase.getDatabase(context).zoneDao().getZoneById(zoneId)
            ?: return s.shared("service_error_zone_not_found")
        return Groups.refusal(group, ToolPositions.zoneGroups(zone.tool_groups), s)
    }

    /** The settings an automation stores, as its declaration describes them (AutomationSettings). */
    private fun settingsOf(entity: AutomationEntity): JSONObject = JSONObject().apply {
        put("name", entity.name)
        entity.providerId?.let { put(AutomationSettings.PROVIDER_ID, it) }
        entity.identityId?.let { put(AutomationSettings.IDENTITY_ID, it) }
        entity.group?.let { put("group", it) }
        put("is_enabled", entity.isEnabled)
        put("notify_end", entity.notifyEnd)
        entity.scheduleJson?.let { put("schedule", JSONObject(it)) }
        entity.catchUp?.let { limit ->
            put("catch_up", JSONObject().apply {
                put("limit", limit)
                entity.catchUpWindow?.let { put("window", it) }
                put("dismiss_older_instances", entity.dismissOlderInstances)
            })
        }
    }

    /**
     * [stored] with the settings [params] give: a setting left out keeps its value, a setting
     * given as null is removed. Without [stored], a creation, only what is given.
     */
    private fun settingsGiven(params: JSONObject, stored: JSONObject?): JSONObject {
        val settings = stored?.let { JSONObject(it.toString()) } ?: JSONObject()
        listOf("name", "provider_id", AutomationSettings.IDENTITY_ID, "group", "is_enabled", "notify_end", "schedule", "catch_up").forEach { key ->
            if (!params.has(key)) return@forEach
            if (params.isNull(key)) settings.remove(key) else settings.put(key, params.get(key))
        }
        return settings
    }

    /**
     * [entity] holding [settings], once they are checked against the schema generated from their
     * declaration, and against the rule it cannot say: the catch-up settings come with a schedule
     * and only with it.
     */
    private fun withSettings(entity: AutomationEntity, sent: JSONObject): Result<AutomationEntity> {
        // A key set to null inside a setting (a schedule's) is not given: taken out, as the merge
        // above takes out a setting set to null
        val settings = app.treelune.core.utils.JsonNulls.withoutNullKeys(sent)
        val checked = app.treelune.core.validation.SchemaValidator.validate(
            AutomationSettings.schema(context), app.treelune.core.utils.JsonUtils.toMap(settings), context)
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
            providerId = settings.optString(AutomationSettings.PROVIDER_ID).takeIf { it.isNotEmpty() },
            identityId = settings.optString(AutomationSettings.IDENTITY_ID).takeIf { it.isNotEmpty() },
            group = settings.optString("group").takeIf { it.isNotEmpty() },
            isEnabled = SettingValues(AutomationSettings.nodes(context), settings).boolean("is_enabled"),
            notifyEnd = SettingValues(AutomationSettings.nodes(context), settings).string("notify_end")
                ?: error("Setting 'notify_end' has neither a value nor a default"),
            scheduleJson = scheduleJson,
            catchUp = catchUp?.getString("limit"),
            catchUpWindow = catchUp?.takeIf { it.has("window") }?.getLong("window"),
            // Without a schedule nothing is missed: the column holds its declared default
            dismissOlderInstances = SettingValues(AutomationSettings.catchUpNodes(context), catchUp ?: JSONObject()).boolean("dismiss_older_instances")
        ))
    }

    /**
     * Delete automation
     */
    private suspend fun deleteAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Deleting automation: $automationId", "DEBUG")

        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        writes.deleteAutomation(automationId)
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()

        LogManager.service("Successfully deleted automation: $automationId", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to automationId,
            "deleted" to true
        ))
    }

    /**
     * Duplicate an existing automation
     *
     * Creates a copy of the source automation with:
     * - A new SEED session (duplicated from source)
     * - All messages from source SEED copied to new SEED
     * - Its name marked as a copy (copy_name)
     * - Created in the specified target zone and group
     */
    private suspend fun duplicateAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))
        val targetZoneId = params.optString("target_zone_id").takeIf { it.isNotBlank() }
            ?: return OperationResult.error(s.shared("error_param_zone_id_required"))
        val targetGroup = params.optString("target_group").takeIf { it.isNotBlank() }

        LogManager.service("Duplicating automation: $automationId to zone $targetZoneId", "DEBUG")
        groupRefusal(targetGroup, targetZoneId)?.let { return OperationResult.error(it) }

        // Load source automation
        val sourceEntity = dao.getAutomationById(automationId)
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
            name = s.shared("copy_name").format(sourceEntity.name), // Named as the automation
            createdAt = now,
            lastActivity = now,
            isActive = false, // SEED sessions are never active
            endReason = null,
            automationId = null, // Will be set after automation is created
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

        // Create new automation
        val newAutomationId = UUID.randomUUID().toString()
        val newName = s.shared("copy_name").format(sourceEntity.name)

        val newAutomation = sourceEntity.copy(
            id = newAutomationId,
            name = newName,
            zoneId = targetZoneId, // Target zone, not source zone
            seedSessionId = newSeedSessionId,
            group = targetGroup, // Use target group (can be null for ungrouped)
            createdAt = now,
            updatedAt = now,
            lastExecutionId = null, // Reset execution history
            executionHistoryJson = json.encodeToString(emptyList<String>())
        )

        writes.insertAutomation(newAutomation)

        // Notify UI
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()

        LogManager.service("Successfully duplicated automation $automationId to $newAutomationId", "INFO")

        // Convert timestamp to ISO 8601 for output
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()
        return OperationResult.success(mapOf(
            "automation_id" to newAutomationId,
            "source_automation_id" to automationId,
            "name" to newName,
            "zone_id" to newAutomation.zoneId,
            "seed_session_id" to newSeedSessionId,
            "created_at" to now
        ))
    }

    /**
     * Get automation by ID
     */
    private suspend fun getAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Getting automation: $automationId", "DEBUG")

        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        return OperationResult.success(mapOf(
            "automation" to automationToMap(entity)
        ))
    }

    /**
     * A routine as it is written: its zone, its settings (AutomationSettings), its access and its
     * message in blocks (AutomationMessage), each in the form create and update take.
     */
    private suspend fun getConfig(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))
        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))
        val seed = dao.getMessagesForSession(entity.seedSessionId).firstOrNull { it.sender == MessageSender.USER }
            ?: return OperationResult.error(s.shared("automation_seed_missing").format(entity.name))
        val message = seed.richContentJson?.let { RichMessage.fromJson(it) }
            ?: return OperationResult.error(s.shared("automation_seed_missing").format(entity.name))

        return OperationResult.success(mapOf(
            "automation_id" to entity.id,
            "zone_id" to entity.zoneId,
            "settings" to app.treelune.core.utils.JsonUtils.toMap(settingsOf(entity)),
            "access" to app.treelune.core.utils.JsonUtils.toList(entity.accessJson),
            "message" to app.treelune.core.utils.JsonUtils.toList(AutomationMessage.toBlocks(message))
        ))
    }

    /**
     * Get automation by seed session ID
     * Useful for SEED editor to load associated automation
     */
    private suspend fun getAutomationBySeedSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val seedSessionId = params.optString("seed_session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_seed_session_required"))

        LogManager.service("Getting automation by seed session: $seedSessionId", "DEBUG")

        val entity = dao.getAutomationBySeedSession(seedSessionId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        return OperationResult.success(mapOf(
            "automation" to automationToMap(entity)
        ))
    }

    /**
     * List automations for a zone
     */
    private suspend fun listAutomations(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_zone_id_required"))

        LogManager.service("Listing automations for zone: $zoneId", "DEBUG")

        val entities = dao.getAutomationsByZone(zoneId)
        return OperationResult.success(mapOf(
            "automations" to entities.map { automationToMap(it) },
            "count" to entities.size,
            "zone_id" to zoneId
        ))
    }

    /**
     * List all automations
     */
    private suspend fun listAllAutomations(token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.service("Listing all automations", "DEBUG")

        val entities = dao.getAllAutomations()
        return OperationResult.success(mapOf(
            "automations" to entities.map { automationToMap(it) },
            "count" to entities.size
        ))
    }

    /**
     * Enable or disable automation
     */
    private suspend fun setEnabled(
        params: JSONObject,
        token: CancellationToken,
        enabled: Boolean
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Setting automation $automationId enabled=$enabled", "DEBUG")

        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))
        // Without a provider it could not run
        if (enabled && entity.providerId == null) return OperationResult.error(s.shared("automation_provider_none_enabled"))

        writes.setAutomationEnabled(automationId, enabled, System.currentTimeMillis())

        // Notify UI of automation change
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()

        LogManager.service("Successfully set automation $automationId enabled=$enabled", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to automationId,
            "is_enabled" to enabled
        ))
    }

    /**
     * Execute automation manually
     * This delegates to AIOrchestrator.executeAutomation() with MANUAL trigger
     * Manual executions go through queue if slot occupied (priority after CHAT)
     */
    private suspend fun executeManual(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Manual execution requested for automation: $automationId", "INFO")

        // Verify automation exists
        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))
        if (AIOrchestrator.isSeedEmpty(entity.seedSessionId)) return OperationResult.error(s.shared("automation_message_empty_run"))
        if (entity.providerId == null) return OperationResult.error(s.shared("automation_provider_none"))

        // Delegate to AIOrchestrator V2
        // V2 handles session creation, trigger, and scheduling internally
        // For MANUAL: scheduledFor = click time
        try {
            val clickTime = System.currentTimeMillis()
            AIOrchestrator.executeAutomation(automationId, scheduledFor = clickTime)

            LogManager.service("Successfully triggered automation: $automationId", "INFO")

            return OperationResult.success(mapOf(
                "automation_id" to automationId,
                "status" to "triggered"
            ))
        } catch (e: Exception) {
            LogManager.service("Failed to execute automation: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to execute automation: ${e.message}")
        }
    }

    /**
     * What waits for the user among the routines (docs/design/waiting-sources.md): how many runs
     * of each routine are over and not yet seen, the routines of `zone_id` or all of them, and the
     * sum by zone; the first form of the waiting brick, beside tools.waiting.
     */
    private suspend fun waiting(params: JSONObject): OperationResult {
        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
        val automations = (if (zoneId != null) dao.getAutomationsByZone(zoneId) else dao.getAllAutomations()).associateBy { it.id }
        val counts = dao.getWaitingRuns().mapNotNull { it.automationId?.takeIf { id -> id in automations } }.groupingBy { it }.eachCount()
        return OperationResult.success(mapOf(
            "automations" to counts,
            "zones" to counts.entries.groupBy { automations.getValue(it.key).zoneId }.mapValues { (_, inZone) -> inZone.sumOf { it.value } }
        ))
    }

    /**
     * An automation as a result gives it: what locates it and its history, then its settings in
     * the form their declaration describes (settingsOf). Automation.fromResult reads it back.
     */
    private fun automationToMap(entity: AutomationEntity): Map<String, Any?> = mapOf(
        "id" to entity.id,
        "zone_id" to entity.zoneId,
        "seed_session_id" to entity.seedSessionId,
        "trigger_ids" to json.decodeFromString<List<String>>(entity.triggerIdsJson),
        "created_at" to entity.createdAt,
        "updated_at" to entity.updatedAt,
        "last_execution_id" to entity.lastExecutionId,
        "execution_history" to json.decodeFromString<List<String>>(entity.executionHistoryJson),
        "access" to app.treelune.core.utils.JsonUtils.toList(entity.accessJson)
    ) + app.treelune.core.utils.JsonUtils.toMap(settingsOf(entity))

    /** An operation said in words, its routine by its name: what a validation card and a result line show. */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        val name = params.optString("name").ifEmpty { null }
            ?: params.optString("automation_id").takeIf { it.isNotEmpty() }?.let { dao.getAutomationById(it)?.name ?: it }
            ?: ""
        return when (operation) {
            "create" -> s.shared("action_verbalize_automations_create").format(name)
            "update" -> s.shared("action_verbalize_automations_update").format(name)
            "delete" -> s.shared("action_verbalize_automations_delete").format(name)
            "config", "get" -> s.shared("action_verbalize_automations_config").format(name)
            "list", "list_all" -> s.shared("action_verbalize_automations_list")
            "waiting" -> s.shared("action_verbalize_automations_waiting")
            else -> s.shared("action_verbalize_unknown")
        }
    }
}
