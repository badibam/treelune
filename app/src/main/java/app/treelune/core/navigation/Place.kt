package app.treelune.core.navigation

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A place of the app: a screen one may want to send someone to (a tutorial, a notification) or
 * come back to, which therefore has a name in the breadcrumb. Passing windows (a confirmation,
 * an entry's form, a date picker) are not places: they belong to the screen that opens them.
 *
 * A place is written as an address, its kind then its parameters, each URL-encoded, an absent
 * one empty: `zone/<id>`, `tool/<id>/<zone id>`, `settings/ai_providers`. The address is what the
 * stack saves, what a notification carries, and what a tutorial's step names.
 *
 * Each place knows its parent, the place that holds it (a tool's zone, a settings page's
 * settings), which rebuilds the stack when a place is opened from outside with nothing open, and
 * names the parent in the breadcrumb when the place was stacked without it.
 */
sealed class Place {

    /** The kind, first segment of the address. */
    protected abstract val kind: String

    /** The parameters, the address's other segments, null for an absent one. */
    protected open val parameters: List<String?> = emptyList()

    /** The place that holds this one, null for the home screen and the chat. */
    abstract fun parent(): Place?

    /** The zone the place belongs to, null for one outside any zone: dropped with that zone. */
    open val zoneId: String? = null

    /** Laid over the place under it instead of replacing it: the chat alone. */
    open val overlay: Boolean = false

    /**
     * Whether the chat's button floats over the place: a place one reads (home, zone, tool, the
     * Guide, an automation), not a form, whose fixed buttons are at the bottom.
     */
    open val chatButton: Boolean = false

    val address: String
        get() = (listOf(kind) + parameters.map { it.orEmpty() }).joinToString("/") { URLEncoder.encode(it, "UTF-8") }

    override fun toString() = address

    object Home : Place() {
        override val kind = "home"
        override val chatButton = true
        override fun parent(): Place? = null
    }

    /** The home screen's own settings: its zone groups. */
    object HomeConfig : Place() {
        override val kind = "home_config"
        override fun parent() = Home
    }

    object Settings : Place() {
        override val kind = "settings"
        override fun parent() = Home
    }

    /** A page of the settings, by the id its tile has (SettingsScreen). */
    data class SettingsPage(val id: String) : Place() {
        override val kind = "settings"
        override val parameters get() = listOf(id)
        override fun parent() = Settings
    }

    /** The export of a part of the app or of the whole, from the data's settings page. */
    object Export : Place() {
        override val kind = "export"
        override fun parent() = SettingsPage("data")
    }

    /** The import of a part of the backup file at [uri], chosen from the data's settings page. */
    data class Import(val uri: String) : Place() {
        override val kind = "import"
        override val parameters get() = listOf(uri)
        override fun parent() = SettingsPage("data")
    }

    /** The choice of a new AI provider's kind, from the providers' settings page. */
    object AIProviderKind : Place() {
        override val kind = "ai_provider_kind"
        override fun parent() = SettingsPage("ai_providers")
    }

    /**
     * An AI provider's config: a stored one's when [id] is set, otherwise a new one's of
     * [providerKind], copied from [copyOf] when duplicated.
     */
    data class AIProvider(val id: String?, val providerKind: String?, val copyOf: String?) : Place() {
        override val kind = "ai_provider"
        override val parameters get() = listOf(id, providerKind, copyOf)
        override fun parent() = SettingsPage("ai_providers")
    }

    /** An API's config: a stored one's when [id] is set, a new one's otherwise, copied from [copyOf] when duplicated. */
    data class Api(val id: String?, val copyOf: String?) : Place() {
        override val kind = "api"
        override val parameters get() = listOf(id, copyOf)
        override fun parent() = SettingsPage("apis")
    }

    /** An identity, opened from the identities' settings page. */
    data class Identity(val id: String) : Place() {
        override val kind = "identity"
        override val parameters get() = listOf(id)
        override fun parent() = SettingsPage("identities")
    }

    /** A zone to create, in a group of the home screen or none. */
    data class CreateZone(val group: String?) : Place() {
        override val kind = "zone_new"
        override val parameters get() = listOf(group)
        override fun parent() = Home
    }

    data class Zone(val id: String) : Place() {
        override val kind = "zone"
        override val chatButton = true
        override val parameters get() = listOf(id)
        override val zoneId get() = id
        override fun parent() = Home
    }

    data class ZoneConfig(val id: String) : Place() {
        override val kind = "zone_config"
        override val parameters get() = listOf(id)
        override val zoneId get() = id
        override fun parent() = Zone(id)
    }

    data class Tool(val id: String, override val zoneId: String) : Place() {
        override val kind = "tool"
        override val chatButton = true
        override val parameters get() = listOf(id, zoneId)
        override fun parent() = Zone(zoneId)
    }

    /** A tool's configuration: an existing tool's when [toolId] is set, a new one's of [tooltype] otherwise. */
    data class ToolConfig(override val zoneId: String, val tooltype: String, val toolId: String?, val group: String?) : Place() {
        override val kind = "tool_config"
        override val parameters get() = listOf(zoneId, tooltype, toolId, group)
        override fun parent() = toolId?.let { Tool(it, zoneId) } ?: Zone(zoneId)
    }

    /** A zone's variable: an existing one when [variableId] is set, a new one otherwise. */
    data class Variable(override val zoneId: String, val variableId: String?, val group: String?) : Place() {
        override val kind = "variable"
        override val parameters get() = listOf(zoneId, variableId, group)
        override fun parent() = Zone(zoneId)
    }

    /** An automation and the history of its executions. */
    data class Automation(val id: String, override val zoneId: String) : Place() {
        override val kind = "automation"
        override val chatButton = true
        override val parameters get() = listOf(id, zoneId)
        override fun parent() = Zone(zoneId)
    }

    /** One execution of an automation, by its session. */
    data class Execution(val sessionId: String, val automationId: String, override val zoneId: String) : Place() {
        override val kind = "execution"
        override val chatButton = true
        override val parameters get() = listOf(sessionId, automationId, zoneId)
        override fun parent() = Automation(automationId, zoneId)
    }

    /** A Task: its state and its runs. */
    data class Task(val id: String, override val zoneId: String) : Place() {
        override val kind = "task"
        override val chatButton = true
        override val parameters get() = listOf(id, zoneId)
        override fun parent() = Zone(zoneId)
    }

    /** A Task's configuration, its steps included: an existing Task's when [taskId] is set, a new one's otherwise. */
    data class TaskConfig(override val zoneId: String, val taskId: String?, val group: String?) : Place() {
        override val kind = "task_config"
        override val parameters get() = listOf(zoneId, taskId, group)
        override fun parent() = taskId?.let { Task(it, zoneId) } ?: Zone(zoneId)
    }

    /** One run of a Task: each step and what it gave. */
    data class TaskRun(val runId: String, val taskId: String, override val zoneId: String) : Place() {
        override val kind = "task_run"
        override val chatButton = true
        override val parameters get() = listOf(runId, taskId, zoneId)
        override fun parent() = Task(taskId, zoneId)
    }

    /** An automation's starting message, edited in its seed session. */
    data class Seed(val sessionId: String, override val zoneId: String) : Place() {
        override val kind = "seed"
        override val parameters get() = listOf(sessionId, zoneId)
        override fun parent() = Zone(zoneId)
    }

    /** The Guide's page: its journey, then its reference. */
    object Guide : Place() {
        override val kind = "guide"
        override val chatButton = true
        override fun parent() = Home
    }

    /** A chapter of the Guide, read in full. */
    data class Chapter(val id: String) : Place() {
        override val kind = "chapter"
        override val chatButton = true
        override val parameters get() = listOf(id)
        override fun parent() = Guide
    }

    /** The conversation with the AI, laid over the place it was opened from. */
    object Chat : Place() {
        override val kind = "chat"
        override val overlay = true
        override fun parent(): Place? = null
    }

    companion object {
        /** The place an address names; fails on an address no place writes. */
        fun of(address: String): Place {
            val segments = address.split("/").map { URLDecoder.decode(it, "UTF-8") }
            val p = { i: Int -> segments.getOrNull(i)?.ifEmpty { null } }
            val r = { i: Int -> requireNotNull(p(i)) { "Address '$address' lacks its parameter $i" } }
            return when (segments[0]) {
                "home" -> Home
                "home_config" -> HomeConfig
                "settings" -> p(1)?.let { SettingsPage(it) } ?: Settings
                "export" -> Export
                "import" -> Import(r(1))
                "identity" -> Identity(r(1))
                "ai_provider_kind" -> AIProviderKind
                "ai_provider" -> AIProvider(p(1), p(2), p(3))
                "api" -> Api(p(1), p(2))
                "zone_new" -> CreateZone(p(1))
                "zone" -> Zone(r(1))
                "zone_config" -> ZoneConfig(r(1))
                "tool" -> Tool(r(1), r(2))
                "tool_config" -> ToolConfig(r(1), r(2), p(3), p(4))
                "variable" -> Variable(r(1), p(2), p(3))
                "automation" -> Automation(r(1), r(2))
                "execution" -> Execution(r(1), r(2), r(3))
                "seed" -> Seed(r(1), r(2))
                "task" -> Task(r(1), r(2))
                "task_config" -> TaskConfig(r(1), p(2), p(3))
                "task_run" -> TaskRun(r(1), r(2), r(3))
                "guide" -> Guide
                "chapter" -> Chapter(r(1))
                "chat" -> Chat
                else -> throw IllegalArgumentException("No place at address '$address'")
            }
        }
    }
}
