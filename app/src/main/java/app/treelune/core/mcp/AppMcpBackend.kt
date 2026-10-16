package app.treelune.core.mcp

import android.content.Context
import app.treelune.core.ai.data.AICommands
import app.treelune.core.ai.data.CommandKind
import app.treelune.core.ai.data.CommandStatus
import app.treelune.core.ai.data.DataCommand
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.data.SystemMessageType
import app.treelune.core.ai.data.withinChars
import app.treelune.core.ai.processing.AICommandProcessor
import app.treelune.core.ai.prompts.CommandExecutor
import app.treelune.core.ai.prompts.PromptChunks
import app.treelune.core.ai.prompts.PromptManager
import app.treelune.core.ai.prompts.toPromptSection
import app.treelune.core.ai.providers.toPromptText
import app.treelune.core.coordinator.Source
import app.treelune.core.strings.Strings
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.DateTimeConverter
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject
import java.util.UUID

/**
 * The app behind its MCP server: each command of AICommands is a tool named by its type in lower
 * case, described by its text and typed by its declaration, and runs the way the built-in AI's
 * does — AICommandProcessor, then CommandExecutor — as Source.EXTERNAL, with no session: no schema
 * is held back until sent. A write the protections guard (docs/design/validation.md) waits for the
 * user's approval, asked by a notification (McpApprovals); the client may ask before its calls too.
 * Its answer is the text the built-in AI would read, held to the chat's data threshold: an
 * action's results cut where the room ends (withinChars), the action being done; a read whole,
 * refused or in slices (McpReads).
 */
class AppMcpBackend(private val context: Context) : McpBackend {

    private val s = Strings.`for`(context = context)

    override suspend fun tools(): List<McpTool> = AICommands.ALL.map { command ->
        McpTool(
            name = command.type.lowercase(),
            description = PromptChunks.commandText(context, command),
            inputSchema = command.schema().apply {
                // A read takes where its slice starts, which the built-in AI, held otherwise, does not
                if (command.kind == CommandKind.QUERY) getJSONObject("properties").put(McpReads.PARAM,
                    JSONObject().put("type", "integer").put("description", s.shared("ai_mcp_text_from")))
            },
            readOnly = command.kind == CommandKind.QUERY
        )
    }

    override suspend fun appContext(caller: McpCaller): String = listOfNotNull(
        s.shared("ai_mcp_context_intro"),
        PromptChunks.buildAppNotions(context),
        PromptManager.buildLevel2Content(context, sessionId = null, sessionType = null),
        // What this client may reach, when its access is set
        PromptManager.accessText(context, McpClientAccess.read(context, caller.id)),
        PromptManager.buildAppStateContent(context)
    ).joinToString("\n\n")

    override suspend fun call(name: String, arguments: JSONObject, caller: McpCaller): McpToolResult {
        val command = AICommands.find(name.uppercase())
            ?: return McpToolResult(s.shared("ai_error_command_unknown_type").format(name), isError = true)

        // Where a read's slice starts is the connector's, not the command's: taken out before the
        // command's params are checked
        val params = JsonUtils.toMap(arguments)
        val from = params[McpReads.PARAM]?.let { value ->
            if (command.kind != CommandKind.QUERY) return McpToolResult(
                s.shared("service_error_param_unknown").format(McpReads.PARAM, command.params.joinToString(", ") { it.name }), isError = true)
            (value as? Number)?.takeIf { it.toDouble() % 1.0 == 0.0 }?.toInt()
                ?: return McpToolResult(s.shared("ai_error_param_type").format(McpReads.PARAM, "integer"), isError = true)
        }
        val dataCommand = DataCommand(id = "mcp_${UUID.randomUUID()}", type = command.type, params = params - McpReads.PARAM)

        // A protected write waits for the user, who answers from a notification; an outside AI has
        // no session to add to the protections
        if (command.kind == CommandKind.ACTION) {
            app.treelune.core.ai.validation.ValidationResolver(context).reasons(listOf(dataCommand), app.treelune.core.ai.data.SessionValidation()).single()?.let { reason ->
                val line = s.shared("external_access_approval_line").format(
                    app.treelune.core.ai.validation.ActionVerbalizerHelper.verbalizeAction(dataCommand, context), reason)
                when (McpApprovals.ask(context, caller.name, listOf(line))) {
                    McpApprovals.Answer.ALLOWED -> Unit
                    McpApprovals.Answer.REFUSED -> return McpToolResult(s.shared("external_access_approval_refused"), isError = true)
                    McpApprovals.Answer.NO_ANSWER -> return McpToolResult(
                        s.shared("external_access_approval_no_answer").format(McpApprovals.TIMEOUT_MS / 1000), isError = true)
                }
            }
        }

        val processor = AICommandProcessor(context)
        val transformation = when (command.kind) {
            CommandKind.QUERY -> processor.processDataCommands(listOf(dataCommand), System.currentTimeMillis())
            CommandKind.ACTION -> processor.processActionCommands(listOf(dataCommand))
        }
        if (transformation.errors.isNotEmpty()) return McpToolResult(transformation.errors.joinToString("\n"), isError = true)

        // Under the client's access, which the coordinator holds every command to
        val access = app.treelune.core.access.AccessScope(McpClientAccess.read(context, caller.id), caller.name)
        val executed = kotlinx.coroutines.withContext(access) {
            CommandExecutor(context).executeCommands(
                commands = transformation.executableCommands,
                messageType = if (command.kind == CommandKind.QUERY) SystemMessageType.DATA_ADDED else SystemMessageType.ACTIONS_EXECUTED,
                origin = Source.EXTERNAL,
                level = "mcp",
                sessionId = null
            )
        }
        // A query's data is in its results, as the built-in AI's session puts it in the message
        val message = executed.systemMessage.let { message ->
            executed.promptResults.toPromptSection().takeIf { it.isNotEmpty() }?.let { message.copy(formattedData = it) } ?: message
        }
        val maxChars = AppConfigManager.getAILimits().getLimitsForSessionType(SessionType.CHAT).maxDataChars
        val failed = message.commandResults.any { it.status == CommandStatus.FAILED }
        if (command.kind == CommandKind.ACTION) {
            val held = message.withinChars(maxChars) { s.shared("ai_system_result_cut").format(it) }
            return McpToolResult(held.toPromptText(), isError = failed)
        }

        // Entries are read whole or narrowed; only a read of a single entry goes in slices
        val sliceable = command.type != "TOOL_DATA" || (dataCommand.params["limit"] as? Number)?.toInt() == 1
        return when (val held = McpReads.hold(message.toPromptText(), maxChars, from, sliceable, readTexts)) {
            is McpReads.Held.Sent -> McpToolResult(held.text, isError = failed)
            is McpReads.Held.Refused -> McpToolResult(held.reason, isError = true)
        }
    }

    private val readTexts = object : McpReads.Texts {
        override fun entriesRefused(chars: Int, maxChars: Int) = s.shared("ai_mcp_read_entries_refused").format(chars, maxChars)
        override fun entriesNotSliced() = s.shared("ai_mcp_read_entries_not_sliced")
        override fun outside(from: Int, total: Int) = s.shared("ai_mcp_read_outside").format(from, total)
        override fun slice(from: Int, to: Int, total: Int) = s.shared("ai_mcp_read_slice").format(from, to, total, to)
        override fun lastSlice(from: Int, to: Int, total: Int) = s.shared("ai_mcp_read_last_slice").format(from, to, total)
    }

    override fun dateLine(): String {
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        return s.shared("ai_prompt_current_datetime").format(DateTimeConverter.timestampToISO(System.currentTimeMillis(), zone))
    }

    override fun text(key: String): String = s.shared(key)
}
