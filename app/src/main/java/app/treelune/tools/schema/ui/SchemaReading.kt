package app.treelune.tools.schema.ui

import app.treelune.core.drawing.diagram.*
import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.drawing.diagram.DiagramReading
import app.treelune.core.drawing.diagram.thingsShown
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolRequests
import app.treelune.core.ui.UI
import app.treelune.tools.schema.SchemaConfig
import org.json.JSONObject

/** A schema's config read for display (SchemaConfig): its diagram, with the current names and icons of its things, or why it cannot be drawn. */
suspend fun schemaReading(config: JSONObject, context: Context): DiagramReading {
    val s = Strings.`for`(tool = "schema", context = context)
    val spec = try { SchemaConfig.read(config) { s.tool(it) } } catch (e: IllegalArgumentException) { return DiagramReading.Problem(e.message.orEmpty()) }
    val things = thingsShown(spec.nodes.mapNotNull { it.thing }, context) ?: return DiagramReading.Problem(s.tool("mermaid_names_failed"))
    return DiagramReading.Drawn(spec, things)
}

/** Opens what a box designates; says why when it cannot. */
suspend fun openThing(thing: Reference, context: Context) {
    val coordinator = Coordinator(context)
    val id = thing.id ?: return
    val s = Strings.`for`(tool = "schema", context = context)
    when (thing.kind) {
        ReferenceKind.TOOL_INSTANCE -> ToolRequests.open(id)
        ReferenceKind.ZONE -> Navigator.open(Place.Zone(id))
        ReferenceKind.ENTRY -> {
            val result = coordinator.processUserAction("tool_data.get_single", mapOf("entry_id" to id))
            val tool = (result.data?.get("entry") as? Map<*, *>)?.get("tool_instance_id") as? String
            if (tool != null) ToolRequests.open(tool, id) else UI.Toast(result.error ?: s.tool("thing_deleted"))
        }
        ReferenceKind.VARIABLE -> {
            val result = coordinator.processUserAction("variables.get", mapOf("variable_id" to id))
            val zone = (result.data?.get("variable") as? Map<*, *>)?.get("zone_id") as? String
            if (zone != null) Navigator.open(Place.Variable(zone, id)) else UI.Toast(result.error ?: s.tool("thing_deleted"))
        }
        // An event has no page of its own: the zone that holds it
        ReferenceKind.EVENT -> {
            val result = coordinator.processUserAction("events.get", mapOf("event_id" to id))
            val zone = (result.data?.get("event") as? Map<*, *>)?.get("zone_id") as? String
            if (zone != null) Navigator.open(Place.Zone(zone)) else UI.Toast(result.error ?: s.tool("thing_deleted"))
        }
        // A view's row: the tool whose view gives it, opened there as its type understands it
        ReferenceKind.ROW -> ToolRequests.open(id, app.treelune.core.tools.OpenAt.Row(thing.view!!, thing.row!!))
        ReferenceKind.APP -> Unit
    }
}
