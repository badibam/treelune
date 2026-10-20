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
     * The links from what [selection] keeps of [document], a whole backup at the current version,
     * to what it leaves out. The selection is one BackupPart.cut accepts.
     */
    fun of(document: JSONObject, selection: PartSelection): Set<PartLink> {
        val data = document.getJSONObject("data")
        val tools = rows(data, "tool_instances")
        val entries = rows(data, "tool_data")

        // What an id designates, among what may be ticked
        val targets = HashMap<String, PartElement>()
        rows(data, "zones").forEach { targets[it.getString("id")] = PartElement(PartElement.Kind.ZONE, it.getString("id")) }
        tools.forEach { targets[it.getString("id")] = PartElement(PartElement.Kind.TOOL, it.getString("id")) }
        entries.forEach { targets[it.getString("id")] = PartElement(PartElement.Kind.ENTRIES, it.getString("tool_instance_id")) }
        rows(data, "variables").forEach { targets[it.getString("id")] = PartElement(PartElement.Kind.VARIABLE, it.getString("id")) }

        fun kept(element: PartElement): Boolean = when (element.kind) {
            PartElement.Kind.ZONE -> element.id in selection.zones
            PartElement.Kind.TOOL -> element.id in selection.tools
            PartElement.Kind.ENTRIES -> selection.tools[element.id] == true
            PartElement.Kind.VARIABLE -> element.id in selection.variables
            PartElement.Kind.ROUTINE, PartElement.Kind.PROVIDER, PartElement.Kind.IDENTITY -> false
        }

        val links = LinkedHashSet<PartLink>()
        fun scan(from: PartElement, row: JSONObject) {
            IdWords.words(row).forEach { word ->
                val to = targets[word] ?: return@forEach
                if (to != from && !kept(to)) links += PartLink(from, to)
            }
        }

        tools.filter { it.getString("id") in selection.tools }.forEach { scan(PartElement(PartElement.Kind.TOOL, it.getString("id")), it) }
        entries.filter { selection.tools[it.getString("tool_instance_id")] == true }
            .forEach { scan(PartElement(PartElement.Kind.ENTRIES, it.getString("tool_instance_id")), it) }
        rows(data, "variables").filter { it.getString("id") in selection.variables }
            .forEach { scan(PartElement(PartElement.Kind.VARIABLE, it.getString("id")), it) }

        // A routine: its row, and the message each of its runs starts from
        val messages = rows(data, "session_messages").groupBy { it.getString("session_id") }
        rows(data, "automations").filter { it.getString("id") in selection.automations }.forEach { automation ->
            val routine = PartElement(PartElement.Kind.ROUTINE, automation.getString("id"))
            scan(routine, automation)
            messages[automation.getString("seed_session_id")].orEmpty().forEach { scan(routine, it) }
            if (!automation.isNull("provider_id")) links += PartLink(routine, PartElement(PartElement.Kind.PROVIDER, automation.getString("provider_id")))
            if (!automation.isNull("identity_id")) links += PartLink(routine, PartElement(PartElement.Kind.IDENTITY, automation.getString("identity_id")))
        }
        return links
    }

    /**
     * What to do about the blocking [link], said with the names [document] gives its elements:
     * tick the target, or untick what names it.
     *
     * @param text a shared string by its key
     */
    fun blockingText(link: PartLink, document: JSONObject, text: (String) -> String): String {
        require(link.blocking) { "Only a blocking link is said here: $link" }
        val from = name(document, link.from)
        val to = name(document, link.to)
        val fromEntries = link.from.kind == PartElement.Kind.ENTRIES
        val toEntries = link.to.kind == PartElement.Kind.ENTRIES
        val key = when {
            fromEntries && toEntries -> "share_link_entries_to_entries"
            fromEntries -> "share_link_entries_to_element"
            toEntries -> "share_link_element_to_entries"
            else -> "share_link_element_to_element"
        }
        return text(key).format(from, to)
    }

    /** The name the user knows [element] by: a zone's, a tool's (its entries' tool's), a variable's. */
    private fun name(document: JSONObject, element: PartElement): String {
        val data = document.getJSONObject("data")
        fun find(table: String) = rows(data, table).first { it.getString("id") == element.id }
        return when (element.kind) {
            PartElement.Kind.ZONE -> find("zones").getString("name")
            PartElement.Kind.TOOL, PartElement.Kind.ENTRIES -> JSONObject(find("tool_instances").getString("config_json")).getString("name")
            PartElement.Kind.VARIABLE -> find("variables").getString("name")
            PartElement.Kind.ROUTINE, PartElement.Kind.PROVIDER, PartElement.Kind.IDENTITY ->
                throw IllegalArgumentException("A ${element.kind} never blocks a part")
        }
    }

    private fun rows(data: JSONObject, table: String): List<JSONObject> =
        data.optJSONArray(table)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()
}
