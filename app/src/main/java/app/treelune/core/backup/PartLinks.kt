package app.treelune.core.backup

import org.json.JSONObject

/**
 * What a link of a part goes from or to: an element that can be ticked, the entries of a tool
 * (kept by its switch, [id] being the tool's), or what never goes with a routine.
 */
data class PartElement(val kind: Kind, val id: String) {
    enum class Kind { ZONE, TOOL, ENTRIES, VARIABLE, ROUTINE, PROVIDER, IDENTITY }
}

/**
 * An element kept that names one left out (docs/design/share.md, « Les liens »).
 *
 * A tool, its entries or a variable would arrive broken without its target: the link blocks the
 * file. A routine arrives in a state the app knows (switched off, without provider nor identity,
 * an access or a pointer reaching nothing): its links only warn.
 */
data class PartLink(val from: PartElement, val to: PartElement) {
    val blocking: Boolean get() = from.kind != PartElement.Kind.ROUTINE
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
     * each tool's entries, each variable and each routine to what it names, whatever is ticked —
     * the screen filters them as the boxes change ([left]).
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

        val links = LinkedHashSet<PartLink>()
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
            if (!automation.isNull("identity_id")) links += PartLink(routine, PartElement(PartElement.Kind.IDENTITY, automation.getString("identity_id")))
        }
        return links
    }

    /** Of [links], those from what [selection] keeps to what it leaves out. */
    fun left(links: Set<PartLink>, selection: PartSelection): Set<PartLink> =
        links.filterTo(LinkedHashSet()) { kept(it.from, selection) && !kept(it.to, selection) }

    /**
     * The links from what [selection] keeps of [document] to what it leaves out. The selection is
     * one BackupPart.cut accepts.
     */
    fun of(document: JSONObject, selection: PartSelection): Set<PartLink> = left(all(document), selection)

    /** Whether [selection] keeps [element]; a provider or an identity never goes. */
    fun kept(element: PartElement, selection: PartSelection): Boolean = when (element.kind) {
        PartElement.Kind.ZONE -> element.id in selection.zones
        PartElement.Kind.TOOL -> element.id in selection.tools
        PartElement.Kind.ENTRIES -> selection.tools[element.id] == true
        PartElement.Kind.VARIABLE -> element.id in selection.variables
        PartElement.Kind.ROUTINE -> element.id in selection.automations
        PartElement.Kind.PROVIDER, PartElement.Kind.IDENTITY -> false
    }

    /**
     * What [link] means for the user, said with the names [name] gives its elements: for a
     * blocking one, what to tick or untick; for a routine's, how it will arrive. A routine's
     * provider and identity are said in the same sentence.
     *
     * @param text a shared string by its key
     */
    fun text(link: PartLink, name: (PartElement) -> String, text: (String) -> String): String {
        val fromEntries = link.from.kind == PartElement.Kind.ENTRIES
        val toEntries = link.to.kind == PartElement.Kind.ENTRIES
        val key = when {
            link.to.kind == PartElement.Kind.PROVIDER || link.to.kind == PartElement.Kind.IDENTITY -> return text("share_link_routine_alone").format(name(link.from))
            !link.blocking -> "share_link_routine"
            fromEntries && toEntries -> "share_link_entries_to_entries"
            fromEntries -> "share_link_entries_to_element"
            toEntries -> "share_link_element_to_entries"
            else -> "share_link_element_to_element"
        }
        return text(key).format(name(link.from), name(link.to))
    }

    /**
     * The name the user knows [element] of [document] by: a zone's, a tool's (its entries' tool's),
     * a variable's, a routine's.
     */
    fun name(document: JSONObject, element: PartElement): String {
        val data = document.getJSONObject("data")
        fun find(table: String) = rows(data, table).first { it.getString("id") == element.id }
        return when (element.kind) {
            PartElement.Kind.ZONE -> find("zones").getString("name")
            PartElement.Kind.TOOL, PartElement.Kind.ENTRIES -> JSONObject(find("tool_instances").getString("config_json")).getString("name")
            PartElement.Kind.VARIABLE -> find("variables").getString("name")
            PartElement.Kind.ROUTINE -> find("automations").getString("name")
            PartElement.Kind.PROVIDER, PartElement.Kind.IDENTITY -> throw IllegalArgumentException("A ${element.kind} is never named here")
        }
    }

    private fun rows(data: JSONObject, table: String): List<JSONObject> =
        data.optJSONArray(table)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()
}
