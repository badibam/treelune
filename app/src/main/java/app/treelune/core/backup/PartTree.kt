package app.treelune.core.backup

import app.treelune.core.grid.ToolPositions
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a backup holds, as the screens of sharing tick it (docs/DATA.md, « A part of the app »):
 * its zones in the home screen's order, under their group, and in each its elements under their
 * tool group; with every link between them (PartLinks.all), which the screen filters as the boxes
 * change. Read from the app's own backup to export, from a file to import.
 */
/**
 * @property demoLeftOut Whether the backup held the demo, which no part carries (withoutDemo)
 */
data class PartTree(val zones: List<Zone>, val links: Set<PartLink>, val apis: List<Element> = emptyList(), val events: List<Event> = emptyList(),
                    val demoLeftOut: Boolean = false) {

    data class Zone(val id: String, val name: String, val group: String?, val elements: List<Element>)

    /**
     * A tool, a variable, a routine, a Task, under the tool group [group] of its zone or none, or an
     * identity the zone or one of its routines speaks as, under none; or an API, beside the zones.
     *
     * @property tooltype A tool's type, null for anything else
     * @property entries A tool's number of entries, 0 for anything else
     */
    data class Element(val kind: PartElement.Kind, val id: String, val name: String, val group: String?, val tooltype: String?, val entries: Int)

    /** An event of a zone, which goes with what names it (eventArrivals). */
    data class Event(val id: String, val name: String, val zoneId: String)

    /** The name the user knows [element] by, a tool's for its entries. */
    fun name(element: PartElement): String = when (element.kind) {
        PartElement.Kind.ZONE -> zones.first { it.id == element.id }.name
        PartElement.Kind.ENTRIES -> elements.first { it.kind == PartElement.Kind.TOOL && it.id == element.id }.name
        PartElement.Kind.API -> apis.first { it.id == element.id }.name
        PartElement.Kind.EVENT -> events.first { it.id == element.id }.name
        else -> elements.first { it.kind == element.kind && it.id == element.id }.name
    }

    val elements: List<Element> get() = zones.flatMap { it.elements }

    /**
     * Each event [selection] carries — one a kept element names (PartLinks.carried) — and the zone
     * it arrives in: its own when kept, otherwise the zone of the first element of the list kept
     * that names it.
     */
    fun eventArrivals(selection: PartSelection): Map<String, String> {
        val carried = PartLinks.carried(links, selection)
        return events.filter { it.id in carried }.associate { event ->
            event.id to (event.zoneId.takeIf { it in selection.zones } ?: zones.first { zone ->
                zone.elements.any { element ->
                    val from = PartElement(element.kind, element.id)
                    PartLinks.kept(from, selection) && links.any { it.to == PartElement(PartElement.Kind.EVENT, event.id) && (it.from == from ||
                        (element.kind == PartElement.Kind.TOOL && it.from == PartElement(PartElement.Kind.ENTRIES, element.id))) }
                }
            }.id)
        }
    }

    /** The stored form, which the service answers. */
    fun toJson(): JSONObject = JSONObject()
        .put("zones", JSONArray(zones.map { zone ->
            JSONObject().put("id", zone.id).put("name", zone.name).put("group", zone.group ?: JSONObject.NULL)
                .put("elements", JSONArray(zone.elements.map { element(it) }))
        }))
        .put("links", JSONArray(links.map { link -> JSONObject().put("from", element(link.from)).put("to", element(link.to)) }))
        .put("apis", JSONArray(apis.map { element(it) }))
        .put("events", JSONArray(events.map { JSONObject().put("id", it.id).put("name", it.name).put("zone_id", it.zoneId) }))
        .put("demo_left_out", demoLeftOut)

    /**
     * The tree without the demo: it is never shared, each phone installing its own from Settings ›
     * Demo, in its language and up to date, and a copy of it would be a demo the reinstall does not
     * know. Its zones go with all they hold, its events, the links from it and to it — what of the
     * user's names it goes then as it names a thing deleted. An API or an identity of the demo
     * stays offered when something of the user's calls it or speaks as it.
     */
    fun withoutDemo(): PartTree {
        fun demo(element: PartElement) = element.id.startsWith(app.treelune.core.demo.DemoContent.PREFIX)
        val kept = links.filter { !demo(it.from) && (!demo(it.to) || it.to.kind == PartElement.Kind.API || it.to.kind == PartElement.Kind.IDENTITY) }.toSet()
        val called = kept.filter { it.to.kind == PartElement.Kind.API }.map { it.to.id }.toSet()
        val demoZones = zones.filter { demo(PartElement(PartElement.Kind.ZONE, it.id)) }.map { it.id }.toSet()
        return PartTree(
            zones.filter { it.id !in demoZones },
            kept,
            apis.filter { !demo(PartElement(PartElement.Kind.API, it.id)) || it.id in called },
            events.filter { it.zoneId !in demoZones },
            demoLeftOut = demoZones.isNotEmpty()
        )
    }

    companion object {

        /**
         * The tree of [document], a whole backup at the current version or a part. The zones in
         * the home screen's order: its groups as the backup's settings list them, then those a
         * part names in its zones' order, then the zones without a group; in a group by their
         * place. In a zone, the identities it and its routines speak as, its own first; then its
         * tool groups in its order then the elements without one: the tools by their place, the
         * variables by theirs, the routines and the Tasks by name. An identity two zones speak as
         * is under each. The APIs by name, beside the zones; the events, which go unticked. The
         * demo left out (withoutDemo).
         */
        fun of(document: JSONObject): PartTree {
            val data = document.getJSONObject("data")
            val zones = rows(data, "zones")
            val listed = rows(data, "app_settings_categories").firstOrNull { it.getString("category") == app.treelune.core.database.entities.AppSettingCategories.MAIN_SCREEN }
                ?.let { JSONObject(it.getString("settings")).optJSONArray("zone_groups") }
                ?.let { groups -> (0 until groups.length()).map { groups.getString(it) } }.orEmpty()
            val homeGroups = (listed + zones.mapNotNull { it.group() }).distinct()

            val entries = rows(data, "tool_data").groupingBy { it.getString("tool_instance_id") }.eachCount()
            val tools = rows(data, "tool_instances").groupBy { it.getString("zone_id") }
            val variables = rows(data, "variables").groupBy { it.getString("zone_id") }
            val routines = rows(data, "routines").groupBy { it.getString("zone_id") }
            val tasks = rows(data, "tasks").groupBy { it.getString("zone_id") }
            val identities = rows(data, "identities").associateBy { it.getString("id") }

            val ordered = zones.sortedWith(compareBy<JSONObject>({ zone -> zone.group()?.let { homeGroups.indexOf(it) } ?: Int.MAX_VALUE }, { it.optInt("grid_y") }, { it.optInt("grid_x") }))
            return PartTree(ordered.map { zone ->
                val id = zone.getString("id")
                val toolGroups = ToolPositions.zoneGroups(zone.optString("tool_groups").takeIf { it.isNotEmpty() })
                val elements = tools[id].orEmpty().sortedWith(compareBy({ it.optInt("grid_y") }, { it.optInt("grid_x") })).map { tool ->
                    val config = JSONObject(tool.getString("config_json"))
                    Element(PartElement.Kind.TOOL, tool.getString("id"), config.optString("name"), config.optString("group").ifEmpty { null },
                        tool.getString("tooltype"), entries[tool.getString("id")] ?: 0)
                } + variables[id].orEmpty().sortedWith(compareBy({ it.optInt("grid_y") }, { it.optInt("grid_x") })).map { variable ->
                    Element(PartElement.Kind.VARIABLE, variable.getString("id"), variable.getString("name"), variable.group(), null, 0)
                } + routines[id].orEmpty().sortedBy { it.getString("name") }.map { routine ->
                    Element(PartElement.Kind.ROUTINE, routine.getString("id"), routine.getString("name"), routine.group(), null, 0)
                } + tasks[id].orEmpty().sortedBy { it.getString("name") }.map { task ->
                    Element(PartElement.Kind.TASK, task.getString("id"), task.getString("name"), task.group(), null, 0)
                }
                // A part holds only the identities kept: one left out is named by no zone nor routine there
                val spoken = (listOf(zone) + routines[id].orEmpty().sortedBy { it.getString("name") }).mapNotNull { BackupPart.identityOf(it) }.distinct()
                    .mapNotNull { identities[it] }.map { Element(PartElement.Kind.IDENTITY, it.getString("id"), it.getString("name"), null, null, 0) }
                Zone(id, zone.getString("name"), zone.group(), spoken + elements.sortedBy { element -> element.group?.let { toolGroups.indexOf(it) } ?: Int.MAX_VALUE })
            }, PartLinks.all(document),
                rows(data, "apis").sortedBy { it.getString("name").lowercase() }.map { Element(PartElement.Kind.API, it.getString("id"), it.getString("name"), null, null, 0) },
                rows(data, "events").map { Event(it.getString("id"), it.getString("name"), it.getString("zone_id")) }).withoutDemo()
        }

        /** The tree in its stored form [json]; one that does not read throws (org.json's JSONException). */
        fun read(json: JSONObject): PartTree = PartTree(
            json.getJSONArray("zones").let { zones ->
                (0 until zones.length()).map { zones.getJSONObject(it) }.map { zone ->
                    Zone(zone.getString("id"), zone.getString("name"), zone.text("group"), zone.getJSONArray("elements").let { elements ->
                        (0 until elements.length()).map { treeElement(elements.getJSONObject(it)) }
                    })
                }
            },
            json.getJSONArray("links").let { links ->
                (0 until links.length()).map { links.getJSONObject(it) }.mapTo(LinkedHashSet()) { link ->
                    PartLink(element(link.getJSONObject("from")), element(link.getJSONObject("to")))
                }
            },
            json.getJSONArray("apis").let { apis -> (0 until apis.length()).map { treeElement(apis.getJSONObject(it)) } },
            json.getJSONArray("events").let { events ->
                (0 until events.length()).map { events.getJSONObject(it) }.map { Event(it.getString("id"), it.getString("name"), it.getString("zone_id")) }
            },
            json.getBoolean("demo_left_out")
        )

        private fun element(element: PartElement): JSONObject = JSONObject().put("kind", element.kind.name).put("id", element.id)

        private fun element(element: Element): JSONObject = JSONObject().put("kind", element.kind.name).put("id", element.id).put("name", element.name)
            .put("group", element.group ?: JSONObject.NULL).put("tooltype", element.tooltype ?: JSONObject.NULL).put("entries", element.entries)

        private fun treeElement(json: JSONObject): Element = Element(PartElement.Kind.valueOf(json.getString("kind")), json.getString("id"), json.getString("name"),
            json.text("group"), json.text("tooltype"), json.getInt("entries"))

        private fun element(json: JSONObject): PartElement = PartElement(PartElement.Kind.valueOf(json.getString("kind")), json.getString("id"))

        private fun JSONObject.text(key: String): String? = if (isNull(key)) null else getString(key).ifEmpty { null }

        private fun JSONObject.group(): String? = text("group")

        private fun rows(data: JSONObject, table: String): List<JSONObject> =
            data.optJSONArray(table)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()
    }
}
