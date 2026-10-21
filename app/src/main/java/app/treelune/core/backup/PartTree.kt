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
data class PartTree(val zones: List<Zone>, val links: Set<PartLink>) {

    data class Zone(val id: String, val name: String, val group: String?, val elements: List<Element>)

    /**
     * A tool, a variable, a routine, under the tool group [group] of its zone or none, or an
     * identity the zone or one of its routines speaks as, under none.
     *
     * @property tooltype A tool's type, null for anything else
     * @property entries A tool's number of entries, 0 for anything else
     */
    data class Element(val kind: PartElement.Kind, val id: String, val name: String, val group: String?, val tooltype: String?, val entries: Int)

    /** The name the user knows [element] by, a tool's for its entries. */
    fun name(element: PartElement): String = when (element.kind) {
        PartElement.Kind.ZONE -> zones.first { it.id == element.id }.name
        PartElement.Kind.ENTRIES -> elements.first { it.kind == PartElement.Kind.TOOL && it.id == element.id }.name
        else -> elements.first { it.kind == element.kind && it.id == element.id }.name
    }

    val elements: List<Element> get() = zones.flatMap { it.elements }

    /** The stored form, which the service answers. */
    fun toJson(): JSONObject = JSONObject()
        .put("zones", JSONArray(zones.map { zone ->
            JSONObject().put("id", zone.id).put("name", zone.name).put("group", zone.group ?: JSONObject.NULL)
                .put("elements", JSONArray(zone.elements.map { element ->
                    JSONObject().put("kind", element.kind.name).put("id", element.id).put("name", element.name)
                        .put("group", element.group ?: JSONObject.NULL).put("tooltype", element.tooltype ?: JSONObject.NULL).put("entries", element.entries)
                }))
        }))
        .put("links", JSONArray(links.map { link -> JSONObject().put("from", element(link.from)).put("to", element(link.to)) }))

    companion object {

        /**
         * The tree of [document], a whole backup at the current version or a part. The zones in
         * the home screen's order: its groups as the backup's settings list them, then those a
         * part names in its zones' order, then the zones without a group; in a group by their
         * place. In a zone, the identities it and its routines speak as, its own first; then its
         * tool groups in its order then the elements without one: the tools by their place, the
         * variables by theirs, the routines by name. An identity two zones speak as is under each.
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
            val routines = rows(data, "automations").groupBy { it.getString("zone_id") }
            val identities = rows(data, "identities").associateBy { it.getString("id") }

            val ordered = zones.sortedWith(compareBy<JSONObject>({ zone -> zone.group()?.let { homeGroups.indexOf(it) } ?: Int.MAX_VALUE }, { it.optInt("grid_y") }, { it.optInt("grid_x") }))
            return PartTree(ordered.map { zone ->
                val id = zone.getString("id")
                val toolGroups = ToolPositions.zoneGroups(zone.optString("tool_groups").takeIf { it.isNotEmpty() })
                val elements = tools[id].orEmpty().sortedWith(compareBy({ it.optInt("grid_y") }, { it.optInt("grid_x") })).map { tool ->
                    val config = JSONObject(tool.getString("config_json"))
                    Element(PartElement.Kind.TOOL, tool.getString("id"), config.optString("name"), config.optString("group").ifEmpty { null },
                        tool.getString("tooltype"), entries[tool.getString("id")] ?: 0)
                } + variables[id].orEmpty().sortedBy { it.optInt("order_index") }.map { variable ->
                    Element(PartElement.Kind.VARIABLE, variable.getString("id"), variable.getString("name"), variable.group(), null, 0)
                } + routines[id].orEmpty().sortedBy { it.getString("name") }.map { routine ->
                    Element(PartElement.Kind.ROUTINE, routine.getString("id"), routine.getString("name"), routine.group(), null, 0)
                }
                // A part holds only the identities kept: one left out is named by no zone nor routine there
                val spoken = (listOf(zone) + routines[id].orEmpty().sortedBy { it.getString("name") }).mapNotNull { BackupPart.identityOf(it) }.distinct()
                    .mapNotNull { identities[it] }.map { Element(PartElement.Kind.IDENTITY, it.getString("id"), it.getString("name"), null, null, 0) }
                Zone(id, zone.getString("name"), zone.group(), spoken + elements.sortedBy { element -> element.group?.let { toolGroups.indexOf(it) } ?: Int.MAX_VALUE })
            }, PartLinks.all(document))
        }

        /** The tree in its stored form [json]; one that does not read throws (org.json's JSONException). */
        fun read(json: JSONObject): PartTree = PartTree(
            json.getJSONArray("zones").let { zones ->
                (0 until zones.length()).map { zones.getJSONObject(it) }.map { zone ->
                    Zone(zone.getString("id"), zone.getString("name"), zone.text("group"), zone.getJSONArray("elements").let { elements ->
                        (0 until elements.length()).map { elements.getJSONObject(it) }.map { element ->
                            Element(PartElement.Kind.valueOf(element.getString("kind")), element.getString("id"), element.getString("name"),
                                element.text("group"), element.text("tooltype"), element.getInt("entries"))
                        }
                    })
                }
            },
            json.getJSONArray("links").let { links ->
                (0 until links.length()).map { links.getJSONObject(it) }.mapTo(LinkedHashSet()) { link ->
                    PartLink(element(link.getJSONObject("from")), element(link.getJSONObject("to")))
                }
            }
        )

        private fun element(element: PartElement): JSONObject = JSONObject().put("kind", element.kind.name).put("id", element.id)

        private fun element(json: JSONObject): PartElement = PartElement(PartElement.Kind.valueOf(json.getString("kind")), json.getString("id"))

        private fun JSONObject.text(key: String): String? = if (isNull(key)) null else getString(key).ifEmpty { null }

        private fun JSONObject.group(): String? = text("group")

        private fun rows(data: JSONObject, table: String): List<JSONObject> =
            data.optJSONArray(table)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()
    }
}
