package app.treelune.core.ai.validation

import android.content.Context
import app.treelune.core.ai.data.DataCommand
import app.treelune.core.ai.data.SessionValidation
import app.treelune.core.commands.CommandStatus
import app.treelune.core.config.ValidationConfig
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.services.AppConfigService
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager

/**
 * Whether the actions of an AI message wait for the user's approval, and what the approval shows
 * (docs/AI.md, « Levels »).
 *
 * Three levels, each guarding what it holds directly, nothing by default:
 * - the app (validation_config): the zones and the home screen's groups;
 * - a zone (its `validate`): its tools, their configs included, its tool groups, its variables, its routines;
 * - a tool (its `validate_data`): its entries.
 *
 * The session adds to them (SessionValidation: a level extended to all its objects), and the AI
 * may ask on its own (validation_request). An action is validated as soon as one of these asks.
 * What cannot be read to decide (a tool gone, a session unreadable) is asked too, saying why: an
 * unknown is never taken for a no.
 */
class ValidationResolver(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val appConfigService = AppConfigService(context)
    private val s = Strings.`for`(context = context)

    /**
     * Determines if actions require validation and generates context
     *
     * @param actions ALL actions IA wants to execute (validated and non-validated)
     * @param sessionId ID of active IA session
     * @param aiMessageId ID of AI message containing actions (for persistence)
     * @param aiRequestedValidation true if IA explicitly requested validation (validationRequest: true)
     * @return ValidationResult.RequiresValidation with context OR NoValidation
     */
    suspend fun shouldValidate(
        actions: List<DataCommand>,
        sessionId: String,
        aiMessageId: String,
        aiRequestedValidation: Boolean
    ): ValidationResult {
        LogManager.aiService("ValidationResolver: shouldValidate for ${actions.size} actions, session=$sessionId, aiRequested=$aiRequestedValidation")

        val session = try {
            loadSessionValidation(sessionId)
        } catch (e: Exception) {
            LogManager.aiService("ValidationResolver: session $sessionId unreadable: ${e.message}", "ERROR", e)
            null
        }
        val reasons = reasons(actions, session)

        if (!aiRequestedValidation && reasons.all { it == null }) return ValidationResult.NoValidation

        return ValidationResult.RequiresValidation(
            ValidationContext(
                aiMessageId = aiMessageId,
                actions = actions,
                verbalizedActions = actions.mapIndexed { i, action -> verbalize(action, reasons[i], aiRequestedValidation) }
            )
        )
    }

    /**
     * Why each of [actions] waits for approval, null for one nothing asks: the protections, and
     * what [session] adds to them -- none for an AI outside the app, which has no session. Settings
     * that cannot be read, the session's included (null), make every action asked, saying so.
     */
    suspend fun reasons(actions: List<DataCommand>, session: SessionValidation?): List<String?> {
        val levels = try {
            Levels(appConfigService.getValidationConfig(), session ?: error(s.shared("validation_reason_settings_unreadable")))
        } catch (e: Exception) {
            LogManager.aiService("ValidationResolver: settings unreadable: ${e.message}", "ERROR", e)
            null
        }
        return actions.map { action ->
            if (levels == null) s.shared("validation_reason_unknown").format(s.shared("validation_reason_settings_unreadable"))
            else try {
                reason(action, levels)
            } catch (e: Exception) {
                LogManager.aiService("ValidationResolver: cannot tell whether ${action.id} is protected: ${e.message}", "WARN", e)
                s.shared("validation_reason_unknown").format(e.message ?: "")
            }
        }
    }

    /** What the app and the session ask for. */
    private class Levels(val app: ValidationConfig, val session: SessionValidation)

    /**
     * Why [action] waits for approval, null when nothing asks: the level holding what it touches,
     * or the session extending that level.
     *
     * @throws IllegalStateException when what it touches cannot be read
     */
    private suspend fun reason(action: DataCommand, levels: Levels): String? {
        val params = action.params
        return when (action.type) {
            // The app's level: the zones and the home screen's groups. A zone's tool groups are its own content.
            "CREATE_ZONE", "DELETE_ZONE", "UPDATE_APP_CONFIG" -> appReason(levels)
            // The identities are the app's, as its zones
            "CREATE_IDENTITY", "UPDATE_IDENTITY", "DELETE_IDENTITY", "IMPORT_IDENTITY" -> appReason(levels)
            "UPDATE_ZONE" -> {
                val zoneId = params["zone_id"] as? String ?: error(s.shared("validation_reason_no_target"))
                val content = setOf("zone_id", "tool_groups", "renames")
                (if (params.keys.any { it !in content }) appReason(levels) else null)
                    ?: (if (params.containsKey("tool_groups") || params.containsKey("renames")) zoneReason(zoneId, levels) else null)
            }

            // A zone's level: its tools, their configs, its variables
            "CREATE_TOOL" -> zoneReason(params["zone_id"] as? String ?: error(s.shared("validation_reason_no_target")), levels)
            "UPDATE_TOOL", "DELETE_TOOL" -> {
                val zoneId = toolZone(toolId(action))
                // A tool moved to another zone changes that zone's content too
                val target = (params["zone_id"] as? String)?.takeIf { it != zoneId }
                // A config that touches recorded values asks, whatever the zone's protection
                (if (action.type == "UPDATE_TOOL") pastDataReason(action, levels) else null)
                    ?: zoneReason(zoneId, levels) ?: target?.let { zoneReason(it, levels) }
            }
            "CREATE_VARIABLE" -> zoneReason(params["zone_id"] as? String ?: error(s.shared("validation_reason_no_target")), levels)
            "UPDATE_VARIABLE", "DELETE_VARIABLE" -> zoneReason(variableZone(params["variable_id"] as? String ?: error(s.shared("validation_reason_no_target"))), levels)
            // A zone's routines, as its tools: one moved changes the zone it goes to as well
            "CREATE_AUTOMATION" -> zoneReason(params["zone_id"] as? String ?: error(s.shared("validation_reason_no_target")), levels)
            "UPDATE_AUTOMATION", "DELETE_AUTOMATION" -> {
                val zoneId = automationZone(params["automation_id"] as? String ?: error(s.shared("validation_reason_no_target")))
                val target = (params["zone_id"] as? String)?.takeIf { it != zoneId }
                zoneReason(zoneId, levels) ?: target?.let { zoneReason(it, levels) }
            }

            // A tool's level: its entries, whatever writes them -- a stopwatch, an import, the
            // tool type's own operations
            "CREATE_DATA", "UPDATE_DATA", "DELETE_DATA", "START_DURATION", "STOP_DURATION", "IMPORT_DATA", "TOOL_OPERATION" ->
                dataReason(toolId(action), levels)

            // A file written into the app's folder: nothing of the app is changed, nothing written over
            "EXPORT_DATA" -> null

            // An undo writes what its step wrote: each row asks as its own write would
            "UNDO_STEP", "REDO_STEP" -> stepReason(params["id"] as? String ?: error(s.shared("validation_reason_no_target")), levels)

            // An action this resolver does not know is asked rather than let through
            else -> s.shared("validation_reason_unknown").format(action.type)
        }
    }

    /**
     * Why a config change waits for approval for what it does to the recorded entries, null when
     * it touches none of them or the protection is off: its counts, read by a dry run of the
     * change (docs/DATA.md, « Config change »).
     */
    private suspend fun pastDataReason(action: DataCommand, levels: Levels): String? {
        if (!levels.app.validatePastData || action.params["config"] == null) return null
        // The change as it would run: its dates and durations, written ISO by the AI, in their
        // stored form; a change that does not transform is refused at its execution, saying why
        val transformed = app.treelune.core.ai.processing.AICommandProcessor(context).processActionCommands(listOf(action))
        val params = transformed.executableCommands.singleOrNull()?.takeIf { transformed.errors.isEmpty() }?.params ?: return null
        val result = coordinator.processUserAction("tools.update", params + mapOf("dry_run" to true))
        // A change refused for another reason is refused at its execution, saying why
        if (!result.isSuccess) return null
        val migration = result.data?.get("migration") as? Map<*, *> ?: return null
        val parts = app.treelune.core.fields.migration.MigrationCounts.parts(migration, s::shared)
        // The other tools whose filters it leaves aiming at an option gone are asked about too
        val aimedBy = (migration["aimed_by"] as? List<*>).orEmpty().map { it.toString() }
        val reasons = listOfNotNull(
            s.shared("validation_reason_past_data").format(parts.joinToString(", ")).takeIf { parts.isNotEmpty() },
            s.shared("validation_reason_filters_aimed").format(aimedBy.joinToString(", ")).takeIf { aimedBy.isNotEmpty() }
        )
        return reasons.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /**
     * Why an undo or a redo of the step [stepId] waits for approval: the first level one of its
     * rows asks, as the write of that row would — a zone, a setting, an identity or a client of
     * the connector the app's; a tool, a variable or a routine its zone's; an entry its tool's.
     */
    private suspend fun stepReason(stepId: String, levels: Levels): String? {
        val places = app.treelune.core.database.AppDatabase.getDatabase(context).historyDao().placesOf(stepId)
        if (places.isEmpty()) error(s.shared("validation_reason_no_target"))
        for (place in places) {
            val reason = when (place.tableName) {
                "tool_data", "images" -> place.toolInstanceId?.let { dataReason(it, levels) } ?: place.zoneId?.let { zoneReason(it, levels) }
                "tool_instances", "variables", "automations", "ai_sessions", "session_messages" -> place.zoneId?.let { zoneReason(it, levels) } ?: appReason(levels)
                else -> appReason(levels)
            }
            if (reason != null) return reason
        }
        return null
    }

    private fun appReason(levels: Levels): String? = when {
        levels.app.validateApp -> s.shared("validation_reason_app")
        levels.session.app -> s.shared("validation_reason_session")
        else -> null
    }

    private suspend fun zoneReason(zoneId: String, levels: Levels): String? {
        val zone = read("zones.get", mapOf("zone_id" to zoneId), "zone")
        return when {
            zone["validate"] as? Boolean ?: error("zone $zoneId without its protection") -> s.shared("validation_reason_zone").format(zone["name"] as? String ?: "")
            levels.session.zones -> s.shared("validation_reason_session")
            else -> null
        }
    }

    private suspend fun dataReason(toolId: String, levels: Levels): String? {
        val tool = read("tools.get", mapOf("tool_instance_id" to toolId), "tool_instance")
        val config = tool["config"] as? Map<*, *> ?: error("tool $toolId without its config")
        return when {
            config[app.treelune.core.tools.ToolConfigSettings.VALIDATE_DATA] == true -> s.shared("validation_reason_tool").format(config["name"] as? String ?: "")
            levels.session.data -> s.shared("validation_reason_session")
            else -> null
        }
    }

    /** The zone tool [toolId] is in. */
    private suspend fun toolZone(toolId: String): String =
        read("tools.get", mapOf("tool_instance_id" to toolId), "tool_instance")["zone_id"] as? String ?: error("tool $toolId without its zone")

    /** The zone variable [variableId] is in. */
    private suspend fun variableZone(variableId: String): String =
        read("variables.get", mapOf("variable_id" to variableId), "variable")["zone_id"] as? String ?: error("variable $variableId without its zone")

    /** The zone routine [automationId] is in. */
    private suspend fun automationZone(automationId: String): String =
        read("automations.get", mapOf("automation_id" to automationId), "automation")["zone_id"] as? String ?: error("routine $automationId without its zone")

    /** The tool an action names. */
    private fun toolId(action: DataCommand): String =
        action.params["tool_instance_id"] as? String ?: error(s.shared("validation_reason_no_target"))

    /**
     * The object [key] of [operation]'s result.
     *
     * @throws IllegalStateException when it cannot be read: what it says is then unknown
     */
    private suspend fun read(operation: String, params: Map<String, Any>, key: String): Map<*, *> {
        val result = coordinator.processUserAction(operation, params)
        return result.data?.get(key) as? Map<*, *>
            ?: error(result.error ?: s.shared("validation_reason_unreadable").format(params.values.joinToString()))
    }

    /** What the session adds to the protections. */
    private suspend fun loadSessionValidation(sessionId: String): SessionValidation =
        SessionValidation.fromMap(read("ai_sessions.get_session", mapOf("session_id" to sessionId), "session"))

    /**
     * [action] as the approval shows it: its description, why it is asked ([reason], or the AI's
     * request when it asked and nothing else did), and the entries it writes.
     */
    private suspend fun verbalize(action: DataCommand, reason: String?, aiRequested: Boolean): VerbalizedAction {
        val description = ActionVerbalizerHelper.verbalizeAction(action, context)

        // The entries a write proposes, shown by their fields. A value that does not read
        // is said on the card: the action itself will be refused for it.
        val (entries, entriesError) = try {
            ProposedEntries.read(action, context) to null
        } catch (e: IllegalArgumentException) {
            LogManager.aiService("ValidationResolver: proposed values of ${action.id} do not read: ${e.message}", "WARN")
            emptyList<ProposedEntry>() to s.shared("validation_values_unreadable").format(e.message ?: "")
        }

        return VerbalizedAction(
            actionId = action.id,
            description = description,
            requiresWarning = reason != null,
            validationReason = reason ?: if (aiRequested) s.shared("validation_reason_ai_request") else null,
            entries = entries,
            entriesError = entriesError
        )
    }
}

/**
 * Result of validation check
 */
sealed class ValidationResult {
    object NoValidation : ValidationResult()
    data class RequiresValidation(val context: ValidationContext) : ValidationResult()
}
