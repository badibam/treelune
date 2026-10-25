package app.treelune.core.access

import android.content.Context
import app.treelune.core.database.AppDatabase
import app.treelune.core.selection.ReferenceKind

/** What an operation reaches, and how far it goes there. */
sealed class Reach {
    /** Nothing a mask holds: the lists of the zones, the tools and the variables, the schemas, the icons, the date */
    object Free : Reach()
    /** The app itself: a zone created, the home screen's settings */
    object App : Reach()
    data class Zone(val id: String, val level: AccessLevel) : Reach()
    data class Tool(val id: String, val level: AccessLevel) : Reach()
    /** A variable, by its id or by its name, held by its zone */
    data class Variable(val idOrName: String, val byName: Boolean, val level: AccessLevel) : Reach()
    /** A routine, held by its zone */
    data class Automation(val id: String, val level: AccessLevel) : Reach()
    /** A Task, held by its zone */
    data class Task(val id: String, val level: AccessLevel) : Reach()
    /** A routine's run, held by the zone of its routine */
    data class Run(val id: String, val level: AccessLevel) : Reach()
    /** An entry, held as its tool */
    data class Entry(val id: String, val level: AccessLevel) : Reach()
    /** A kept file: an entry's or a tool's held as that tool, a message's free as its conversation is */
    data class File(val id: String, val level: AccessLevel) : Reach()
    /** An operation these rules do not know: refused under a mask, rather than let through */
    data class Unknown(val action: String) : Reach()
}

/** Who holds a kept file, for the mask: a conversation, or the tool of the entry or of the config. */
sealed interface FileHolder {
    object Conversation : FileHolder
    data class Tool(val id: String) : FileHolder
}

/** What the rules read to place what an operation names. */
interface AccessLookups {
    suspend fun toolZone(toolId: String): String?
    suspend fun variableZone(idOrName: String, byName: Boolean): String?
    suspend fun automationZone(automationId: String): String?
    suspend fun taskZone(taskId: String): String?
    /** The routine run [runId] belongs to. */
    suspend fun runAutomation(runId: String): String?
    /** The tool entry [entryId] belongs to, null when there is no such entry. */
    suspend fun entryTool(entryId: String): String?
    /** Who holds the kept file [fileId], null when there is no such file. */
    suspend fun fileHolder(fileId: String): FileHolder?
    suspend fun zoneName(zoneId: String): String?
    suspend fun toolName(toolId: String): String?
    /** Whether [resource] is a tool type, whose service runs the type's own operations on a tool. */
    fun isToolType(resource: String): Boolean
}

/**
 * Where each operation of the services goes, and the refusal of one beyond a mask (AccessMask).
 * The coordinator asks it for every command the app itself does not make, under an AccessScope.
 */
object AccessRules {

    private val TOOL_DATA_READS = setOf("get", "values", "get_single", "writable")
    private val TOOL_DATA_WRITES = setOf("create", "update", "delete", "batch_create", "batch_update", "batch_delete", "start_duration", "stop_duration", "delete_all")

    /** What [resource].[operation] reaches with [params]. */
    fun reach(resource: String, operation: String, params: Map<String, Any?>, isToolType: (String) -> Boolean): List<Reach> {
        fun text(key: String) = params[key] as? String
        val tool = text("tool_instance_id") ?: text("id")
        fun toolAt(level: AccessLevel) = tool?.let { Reach.Tool(it, level) } ?: Reach.Unknown("$resource.$operation")
        fun zoneAt(key: String, level: AccessLevel) = text(key)?.let { Reach.Zone(it, level) } ?: Reach.Unknown("$resource.$operation")
        fun variableAt(level: AccessLevel) = text("variable_id")?.let { Reach.Variable(it, false, level) } ?: Reach.Unknown("$resource.$operation")
        fun automationAt(level: AccessLevel) = text("automation_id")?.let { Reach.Automation(it, level) } ?: Reach.Unknown("$resource.$operation")
        return when (resource) {
            "tool_data" -> when (operation) {
                in TOOL_DATA_READS -> listOf(toolAt(AccessLevel.READ))
                in TOOL_DATA_WRITES -> listOf(toolAt(AccessLevel.USE))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            "tools" -> when (operation) {
                "list", "list_all", "waiting" -> listOf(Reach.Free)
                "get" -> listOf(toolAt(AccessLevel.READ))
                "create" -> listOf(zoneAt("zone_id", AccessLevel.USE))
                // A tool moved goes into the zone it is moved to
                "update" -> listOfNotNull(toolAt(AccessLevel.FULL), text("zone_id")?.let { Reach.Zone(it, AccessLevel.USE) })
                "delete" -> listOf(toolAt(AccessLevel.FULL))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            "zones" -> when (operation) {
                "list" -> listOf(Reach.Free)
                "get" -> listOf(zoneAt("zone_id", AccessLevel.READ))
                "create" -> listOf(Reach.App)
                "update", "delete" -> listOf(zoneAt("zone_id", AccessLevel.FULL))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            "variables" -> when (operation) {
                "list", "list_all" -> listOf(Reach.Free)
                "get", "evaluate" -> listOf(variableAt(AccessLevel.READ))
                "create" -> listOf(zoneAt("zone_id", AccessLevel.USE))
                "update" -> listOfNotNull(variableAt(AccessLevel.USE), text("zone_id")?.let { Reach.Zone(it, AccessLevel.USE) })
                "delete" -> listOf(variableAt(AccessLevel.USE))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            // A zone's routines are written by who reaches the zone in full, as its own settings are
            "automations" -> when (operation) {
                "list", "list_all", "waiting" -> listOf(Reach.Free)
                "get", "config", "next_execution" -> listOf(automationAt(AccessLevel.READ))
                "create" -> listOf(zoneAt("zone_id", AccessLevel.FULL))
                // A routine moved goes into the zone it is moved to
                "update" -> listOfNotNull(automationAt(AccessLevel.FULL), text("zone_id")?.let { Reach.Zone(it, AccessLevel.FULL) })
                "delete" -> listOf(automationAt(AccessLevel.FULL))
                // A routine run uses its zone, as a Task's step does
                "execute_manual" -> listOf(automationAt(AccessLevel.USE))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            // A zone's Tasks, as its routines: read with the zone, run as a use of it, written in full;
            // what their steps reach is checked when an AI writes them (TaskSteps.reachRefusal)
            "tasks" -> {
                fun taskAt(level: AccessLevel) = text("task_id")?.let { Reach.Task(it, level) } ?: Reach.Unknown("$resource.$operation")
                when (operation) {
                    "list", "list_all", "outputs" -> listOf(Reach.Free)
                    "get", "runs", "next_execution" -> listOf(taskAt(AccessLevel.READ))
                    "run" -> listOf(taskAt(AccessLevel.USE))
                    "create" -> listOf(zoneAt("zone_id", AccessLevel.FULL))
                    "update" -> listOfNotNull(taskAt(AccessLevel.FULL), text("zone_id")?.let { Reach.Zone(it, AccessLevel.FULL) })
                    "delete" -> listOf(taskAt(AccessLevel.FULL))
                    else -> listOf(Reach.Unknown("$resource.$operation"))
                }
            }
            // An API reaches no data of the app: what it is given was read under the mask first
            "apis" -> when (operation) {
                "list", "call", "status" -> listOf(Reach.Free)
                else -> listOf(Reach.App)
            }
            // A routine's runs, read as the routine is; every other session is the screens'
            "ai_sessions" -> when (operation) {
                "list_sessions_for_automation" -> listOf(automationAt(AccessLevel.READ))
                "run_history" -> listOf(text("session_id")?.let { Reach.Run(it, AccessLevel.READ) } ?: Reach.Unknown("$resource.$operation"))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            // A reading of a variable by its name, or of a selection's target: a tool, a zone, one entry
            "readings" -> listOf(text("variable")?.let { Reach.Variable(it, true, AccessLevel.READ) }
                ?: ((params["selection"] as? Map<*, *>)?.get("target") as? Map<*, *>)?.let { target ->
                    val id = target["id"] as? String
                    when (target["kind"]) {
                        "TOOL_INSTANCE" -> id?.let { Reach.Tool(it, AccessLevel.READ) }
                        "ZONE" -> id?.let { Reach.Zone(it, AccessLevel.READ) }
                        "ENTRY" -> id?.let { Reach.Entry(it, AccessLevel.READ) }
                        else -> null
                    }
                }
                ?: Reach.Unknown("$resource.$operation"))
            "app_config" -> if (operation == "get_current_datetime") listOf(Reach.Free) else listOf(Reach.App)
            "imports" -> when (operation) {
                "detect" -> listOf(toolAt(AccessLevel.READ))
                "apply" -> listOf(toolAt(AccessLevel.USE))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            // A tool's entries read, written out of the app
            "exports" -> if (operation == "csv") listOf(toolAt(AccessLevel.READ)) else listOf(Reach.Unknown("$resource.$operation"))
            "schemas", "icons" -> listOf(Reach.Free)
            // An identity is read by whoever speaks as it, a routine under a mask included; it is written as the app is
            "identities" -> when (operation) {
                "list", "read", "read_resource" -> listOf(Reach.Free)
                "create", "update", "delete", "import", "export" -> listOf(Reach.App)
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            "files" -> if (operation == "read") listOf(text("id")?.let { Reach.File(it, AccessLevel.READ) } ?: Reach.Unknown("$resource.$operation"))
                else listOf(Reach.Unknown("$resource.$operation"))
            // The history of one tool or zone is read as it is; the whole of it, and an undo, whose rows may be anywhere, as the app
            "history" -> when (operation) {
                "list" -> listOf(text("tool_instance_id")?.let { toolAt(AccessLevel.READ) } ?: text("zone_id")?.let { zoneAt("zone_id", AccessLevel.READ) } ?: Reach.App)
                "undo", "redo", "summary", "rows" -> listOf(Reach.App)
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            // A tool type's own operation writes the tool's entries
            else -> if (isToolType(resource)) listOf(toolAt(AccessLevel.USE)) else listOf(Reach.Unknown("$resource.$operation"))
        }
    }

    /**
     * Why [reaches] go beyond [mask], naming [holder] and the first thing out of reach; null when
     * they do not. Something these rules cannot place is out of reach.
     */
    suspend fun refusal(mask: AccessMask, holder: String, reaches: List<Reach>, lookups: AccessLookups, text: (String) -> String): String? {
        if (mask.open) return null
        fun level(level: AccessLevel) = text("access_level_${level.key}")
        fun refused(what: String, level: AccessLevel?) =
            if (level == null) text("access_refused_unknown").format(holder, what)
            else text("access_refused").format(holder, what, level(level))
        // A routine, or a run by [what], held by the zone of [automationId]
        suspend fun automationRefusal(what: String, automationId: String?, level: AccessLevel): String? {
            val zoneId = automationId?.let { lookups.automationZone(it) } ?: return refused(what, level)
            val held = mask.zone(zoneId)
            return if (held == null || held < level) refused(zone(zoneId, lookups, text), level) else null
        }
        for (reach in reaches) {
            when (reach) {
                Reach.Free -> Unit
                Reach.App -> return refused(text("access_target_app"), null)
                is Reach.Unknown -> return refused(reach.action, null)
                is Reach.Zone -> if ((mask.zone(reach.id) ?: return refused(zone(reach.id, lookups, text), reach.level)) < reach.level)
                    return refused(zone(reach.id, lookups, text), reach.level)
                is Reach.Tool -> {
                    val zoneId = lookups.toolZone(reach.id) ?: return refused(tool(reach.id, lookups, text), reach.level)
                    if ((mask.tool(reach.id, zoneId) ?: return refused(tool(reach.id, lookups, text), reach.level)) < reach.level)
                        return refused(tool(reach.id, lookups, text), reach.level)
                }
                is Reach.Variable -> {
                    val zoneId = lookups.variableZone(reach.idOrName, reach.byName) ?: return refused(reach.idOrName, reach.level)
                    if ((mask.zone(zoneId) ?: return refused(zone(zoneId, lookups, text), reach.level)) < reach.level)
                        return refused(zone(zoneId, lookups, text), reach.level)
                }
                is Reach.Entry -> {
                    val toolId = lookups.entryTool(reach.id) ?: return refused(reach.id, reach.level)
                    val zoneId = lookups.toolZone(toolId) ?: return refused(tool(toolId, lookups, text), reach.level)
                    if ((mask.tool(toolId, zoneId) ?: return refused(tool(toolId, lookups, text), reach.level)) < reach.level)
                        return refused(tool(toolId, lookups, text), reach.level)
                }
                is Reach.Automation -> automationRefusal(reach.id, reach.id, reach.level)?.let { return it }
                is Reach.Task -> {
                    val zoneId = lookups.taskZone(reach.id) ?: return refused(reach.id, reach.level)
                    val held = mask.zone(zoneId)
                    if (held == null || held < reach.level) return refused(zone(zoneId, lookups, text), reach.level)
                }
                is Reach.Run -> automationRefusal(reach.id, lookups.runAutomation(reach.id), reach.level)?.let { return it }
                is Reach.File -> when (val holder = lookups.fileHolder(reach.id)) {
                    // An unknown id is the service's to refuse, naming it
                    null, FileHolder.Conversation -> Unit
                    is FileHolder.Tool -> {
                        val zoneId = lookups.toolZone(holder.id) ?: return refused(tool(holder.id, lookups, text), reach.level)
                        if ((mask.tool(holder.id, zoneId) ?: return refused(tool(holder.id, lookups, text), reach.level)) < reach.level)
                            return refused(tool(holder.id, lookups, text), reach.level)
                    }
                }
            }
        }
        return null
    }

    /**
     * Why [given], the access an AI writes for a routine, is wider than [holder], the mask of the
     * AI writing it; null when it is not or when [holder] reaches everything. Each grant is held
     * to what [holder] gives its target, a tool's zone included; a [given] that lists nothing
     * reaches everything, wider than any mask with limits.
     */
    suspend fun widening(given: AccessMask, holder: AccessMask, holderName: String, lookups: AccessLookups, text: (String) -> String): String? {
        if (holder.open) return null
        if (given.open) return text("access_widening_open").format(holderName)
        for (grant in given.grants) {
            val id = grant.target.id!!
            val held = when (grant.target.kind) {
                ReferenceKind.ZONE -> holder.zone(id)
                else -> lookups.toolZone(id)?.let { holder.tool(id, it) }
            }
            if (held == null || held < grant.level) {
                val what = if (grant.target.kind == ReferenceKind.ZONE) zone(id, lookups, text) else tool(id, lookups, text)
                return text("access_widening").format(holderName, what, text("access_level_${grant.level.key}"))
            }
        }
        return null
    }

    private suspend fun zone(id: String, lookups: AccessLookups, text: (String) -> String) =
        text("access_target_zone").format(lookups.zoneName(id) ?: id)

    private suspend fun tool(id: String, lookups: AccessLookups, text: (String) -> String) =
        text("access_target_tool").format(lookups.toolName(id) ?: id)
}

/** The lookups read from the database. */
class DatabaseAccessLookups(context: Context) : AccessLookups {
    private val database = AppDatabase.getDatabase(context)

    override suspend fun toolZone(toolId: String) = database.toolInstanceDao().getToolInstanceById(toolId)?.zone_id

    override suspend fun variableZone(idOrName: String, byName: Boolean) =
        if (byName) database.variableDao().getAll().firstOrNull { it.name == idOrName }?.zoneId
        else database.variableDao().getById(idOrName)?.zoneId

    override suspend fun automationZone(automationId: String) = database.aiDao().getAutomationById(automationId)?.zoneId

    override suspend fun taskZone(taskId: String) = database.taskDao().get(taskId)?.zoneId

    override suspend fun runAutomation(runId: String) = database.aiDao().getSession(runId)?.automationId

    override suspend fun entryTool(entryId: String) = database.toolDataDao().getById(entryId)?.toolInstanceId

    override suspend fun fileHolder(fileId: String): FileHolder? {
        val file = database.fileDao().getById(fileId) ?: return null
        return when {
            file.toolInstanceId != null -> FileHolder.Tool(file.toolInstanceId)
            file.entryId != null -> database.toolDataDao().getById(file.entryId)?.let { FileHolder.Tool(it.toolInstanceId) }
            else -> FileHolder.Conversation
        }
    }

    override suspend fun zoneName(zoneId: String) = database.zoneDao().getZoneById(zoneId)?.name

    override suspend fun toolName(toolId: String) =
        database.toolInstanceDao().getToolInstanceById(toolId)?.let { org.json.JSONObject(it.config_json).optString("name").ifEmpty { null } }

    override fun isToolType(resource: String) = app.treelune.core.tools.ToolTypeManager.getToolType(resource) != null
}
