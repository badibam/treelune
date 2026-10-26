package app.treelune.core.backup

import org.json.JSONObject

/**
 * What a link of a part goes from or to: an element that can be ticked, the entries of a tool
 * (kept by its switch, [id] being the tool's), a routine's provider, which never goes, or an
 * event, which goes with what names it (PartLinks.carried).
 */
data class PartElement(val kind: Kind, val id: String) {
    enum class Kind { ZONE, TOOL, ENTRIES, VARIABLE, ROUTINE, PROVIDER, IDENTITY, TASK, API, EVENT }
}

/**
 * An element kept that names one left out (docs/DATA.md, « A part of the app »).
 *
 * A tool, its entries, a variable, a Task or an event carried would arrive broken without its
 * target, the service refusing it: the link blocks the file. A routine arrives in a state the app
 * knows (switched off, without provider, an access or a pointer reaching nothing), and a zone or a
 * routine without its identity speaks as the app's own AI: these links only warn.
 */
data class PartLink(val from: PartElement, val to: PartElement) {
    val blocking: Boolean get() = from.kind != PartElement.Kind.ROUTINE && to.kind != PartElement.Kind.IDENTITY
}

/**
 * The links of a [PartSelection], found without reading their form: an element depends on
 * another as soon as the other's id stands as a whole word anywhere in its text (its rows of the
 * backup, a JSON kept in a string included: IdWords). A chart, a goal, a formula, a REFERENCE field, an
 * entry designating another, a routine's access or the pointers of its message are all found
 * alike, and a new way to link is found without being declared.
 */
object PartLinks {

    /**
     * Every link of [document], a whole backup at the current version or a part: from each tool,
     * each tool's entries, each variable, each routine, each Task and each event to what it names,
     * whatever is ticked — the screen filters them as the boxes change ([left]).
     */
    fun all(document: JSONObject): Set<PartLink> {
        val data = document.getJSONObject("data")
        val tools = rows(data, "tool_instances")
        val entries = rows(data, "tool_data")

        // What an id designates, among what may be ticked
        val targets = HashMap<String, PartElement>()
        rows(data, "zones").forEach { targets[it.getString("id")] = PartElement(PartElement.Kind.ZONE, it.getString("id")) }
        tools.forEach { targets[it.getString("id")] = PartElement(PartElement.Kind.TOOL, it.getString("id")) }
        entries.forEach { targets[it.getString("id")] = PartElement(PartElement.Kind.ENTRIES, it.getString("tool_instance_id")) }
        rows(data, "variables").forEach { targets[it.getString("id")] = PartElement(PartElement.Kind.VARIABLE, it.getString("id")) }
        rows(data, "automations").forEach { targets[it.getString("id")] = PartElement(PartElement.Kind.ROUTINE, it.getString("id")) }
        rows(data, "apis").forEach { targets[it.getString("id")] = PartElement(PartElement.Kind.API, it.getString("id")) }
        rows(data, "events").forEach { targets[it.getString("id")] = PartElement(PartElement.Kind.EVENT, it.getString("id")) }

        val links = LinkedHashSet<PartLink>()
        // A zone and the identity it speaks as
        rows(data, "zones").forEach { zone ->
            BackupPart.identityOf(zone)?.let { links += PartLink(PartElement(PartElement.Kind.ZONE, zone.getString("id")), PartElement(PartElement.Kind.IDENTITY, it)) }
        }
        fun scan(from: PartElement, row: JSONObject) {
            IdWords.words(row).forEach { word ->
                val to = targets[word] ?: return@forEach
                if (to != from) links += PartLink(from, to)
            }
        }

        tools.forEach { scan(PartElement(PartElement.Kind.TOOL, it.getString("id")), it) }
        entries.forEach { scan(PartElement(PartElement.Kind.ENTRIES, it.getString("tool_instance_id")), it) }
        rows(data, "variables").forEach { scan(PartElement(PartElement.Kind.VARIABLE, it.getString("id")), it) }

        // A routine: its row, and the message each of its runs starts from
        val messages = rows(data, "session_messages").groupBy { it.getString("session_id") }
        rows(data, "automations").forEach { automation ->
            val routine = PartElement(PartElement.Kind.ROUTINE, automation.getString("id"))
            scan(routine, automation)
            messages[automation.getString("seed_session_id")].orEmpty().forEach { scan(routine, it) }
            if (!automation.isNull("provider_id")) links += PartLink(routine, PartElement(PartElement.Kind.PROVIDER, automation.getString("provider_id")))
            BackupPart.identityOf(automation)?.let { links += PartLink(routine, PartElement(PartElement.Kind.IDENTITY, it)) }
        }
        rows(data, "tasks").forEach { scan(PartElement(PartElement.Kind.TASK, it.getString("id")), it) }
        // An event: the tools its values are; its own zone does not hold it back, it arrives
        // where what names it does (PartTree.eventArrivals)
        rows(data, "events").forEach { event ->
            val from = PartElement(PartElement.Kind.EVENT, event.getString("id"))
            IdWords.words(event).forEach { word ->
                val to = targets[word]?.takeIf { it.kind != PartElement.Kind.ZONE } ?: return@forEach
                if (to != from) links += PartLink(from, to)
            }
        }
        return links
    }

    /** The events [selection] carries: each one an element it keeps names. */
    fun carried(links: Set<PartLink>, selection: PartSelection): Set<String> =
        links.filter { it.to.kind == PartElement.Kind.EVENT && kept(it.from, selection) }.mapTo(HashSet()) { it.to.id }

    /** Of [links], those from what [selection] keeps, an event carried included, to what it leaves out. */
    fun left(links: Set<PartLink>, selection: PartSelection): Set<PartLink> {
        val carried = carried(links, selection)
        fun goes(element: PartElement) = if (element.kind == PartElement.Kind.EVENT) element.id in carried else kept(element, selection)
        return links.filterTo(LinkedHashSet()) { goes(it.from) && !goes(it.to) }
    }

    /**
     * The links from what [selection] keeps of [document] to what it leaves out. The selection is
     * one BackupPart.cut accepts.
     */
    fun of(document: JSONObject, selection: PartSelection): Set<PartLink> = left(all(document), selection)

    /** Whether [selection] ticks [element]; a provider never goes, an event is never ticked (carried). */
    fun kept(element: PartElement, selection: PartSelection): Boolean = when (element.kind) {
        PartElement.Kind.ZONE -> element.id in selection.zones
        PartElement.Kind.TOOL -> element.id in selection.tools
        PartElement.Kind.ENTRIES -> selection.tools[element.id] == true
        PartElement.Kind.VARIABLE -> element.id in selection.variables
        PartElement.Kind.ROUTINE -> element.id in selection.automations
        PartElement.Kind.IDENTITY -> element.id in selection.identities
        PartElement.Kind.TASK -> element.id in selection.tasks
        PartElement.Kind.API -> element.id in selection.apis
        PartElement.Kind.PROVIDER, PartElement.Kind.EVENT -> false
    }

    /**
     * What [link] means for the user, said with the names [name] gives its elements: for a
     * blocking one, what to tick or untick; for a routine's, how it will arrive; for an identity
     * left out, who will speak instead.
     *
     * @param text a shared string by its key
     */
    fun text(link: PartLink, name: (PartElement) -> String, text: (String) -> String): String {
        val fromEntries = link.from.kind == PartElement.Kind.ENTRIES
        val toEntries = link.to.kind == PartElement.Kind.ENTRIES
        val key = when {
            link.to.kind == PartElement.Kind.PROVIDER -> return text("share_link_routine_alone").format(name(link.from))
            link.to.kind == PartElement.Kind.IDENTITY && link.from.kind == PartElement.Kind.ZONE -> "share_link_zone_identity"
            link.to.kind == PartElement.Kind.IDENTITY -> "share_link_routine_identity"
            !link.blocking -> "share_link_routine"
            link.from.kind == PartElement.Kind.EVENT -> "share_link_event_to_element"
            link.from.kind == PartElement.Kind.TASK && !toEntries -> "share_link_task_to_element"
            fromEntries && toEntries -> "share_link_entries_to_entries"
            fromEntries -> "share_link_entries_to_element"
            toEntries -> "share_link_element_to_entries"
            else -> "share_link_element_to_element"
        }
        return text(key).format(name(link.from), name(link.to))
    }

    /**
     * The name the user knows [element] of [document] by: a zone's, a tool's (its entries' tool's),
     * a variable's, a routine's, an identity's, a Task's, an API's, an event's.
     */
    fun name(document: JSONObject, element: PartElement): String {
        val data = document.getJSONObject("data")
        fun find(table: String) = rows(data, table).first { it.getString("id") == element.id }
        return when (element.kind) {
            PartElement.Kind.ZONE -> find("zones").getString("name")
            PartElement.Kind.TOOL, PartElement.Kind.ENTRIES -> JSONObject(find("tool_instances").getString("config_json")).getString("name")
            PartElement.Kind.VARIABLE -> find("variables").getString("name")
            PartElement.Kind.ROUTINE -> find("automations").getString("name")
            PartElement.Kind.IDENTITY -> find("identities").getString("name")
            PartElement.Kind.TASK -> find("tasks").getString("name")
            PartElement.Kind.API -> find("apis").getString("name")
            PartElement.Kind.EVENT -> find("events").getString("name")
            PartElement.Kind.PROVIDER -> throw IllegalArgumentException("A ${element.kind} is never named here")
        }
    }

    private fun rows(data: JSONObject, table: String): List<JSONObject> =
        data.optJSONArray(table)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()
}
