package app.treelune.tools.schema.ui

import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolRequests
import app.treelune.core.ui.UI
import app.treelune.tools.schema.SchemaSceneBuilder
import app.treelune.tools.schema.SchemaSpec
import app.treelune.tools.schema.SchemaText
import app.treelune.tools.schema.SchemaTouch
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** A schema's config as read for display: drawn with the current names of its things, or why it cannot be. */
sealed interface SchemaReading {
    data class Problem(val message: String) : SchemaReading
    data class Drawn(val spec: SchemaSpec, val names: Map<Reference, String?>) : SchemaReading

    companion object {
        suspend fun of(config: JSONObject, context: Context): SchemaReading {
            val s = Strings.`for`(tool = "schema", context = context)
            val spec = try { SchemaSpec.of(config) { s.tool(it) } } catch (e: IllegalArgumentException) { return Problem(e.message.orEmpty()) }
            val things = spec.nodes.mapNotNull { it.thing }.distinct()
            if (things.isEmpty()) return Drawn(spec, emptyMap())
            val result = Coordinator(context).processUserAction("references.names", mapOf("references" to things.map { thing ->
                mapOf("kind" to thing.kind.name, "id" to thing.id, "field" to thing.field).filterValues { it != null }
            }))
            if (!result.isSuccess) return Problem(result.error.orEmpty())
            val names = (result.data?.get("references") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { row ->
                ReferenceTarget.referenceOf(row)?.let { it to (row["name"] as? String) }
            }.toMap()
            return Drawn(spec, names)
        }
    }
}

/**
 * A schema laid out at the width it is given and drawn by the theme. On a screen, a drawing still
 * wider than the width scrolls sideways. [reduced], it is laid out at a reading width and scaled
 * down to the room, for a tile; [onTouch] null leaves the touch to the tile.
 */
@Composable
fun SchemaView(read: SchemaReading.Drawn, folded: Set<String>, onTouch: ((SchemaTouch) -> Unit)?, modifier: Modifier = Modifier, reduced: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val style = UI.drawingTextStyle()
    val unit = UI.drawingUnit()
    val measurer = rememberTextMeasurer()
    val text = remember(style, measurer) {
        object : SchemaText {
            override fun width(text: String) = measurer.measure(text, style).size.width.toFloat()
            override val height = measurer.measure("Ag", style).size.height.toFloat()
        }
    }
    val s = remember { Strings.`for`(tool = "schema", context = context) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val available = constraints.maxWidth.toFloat()
        val width = if (reduced) max(available, READING_WIDTH * density) else available
        val scene = remember(read, folded, width, text, density, unit) {
            SchemaSceneBuilder(density, unit, text) { s.tool(it) }.build(read.spec, { read.names[it] }, folded, width)
        }
        val touch = if (onTouch == null) Modifier else Modifier.pointerInput(scene) {
            detectTapGestures { offset -> scene.hitAt(offset.x, offset.y)?.let(onTouch) }
        }
        if (reduced) {
            val room = constraints.maxHeight.toFloat()
            val scale = min(1f, min(available / scene.drawing.width, room / scene.drawing.height))
            Box(modifier = Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                UI.Drawing(scene.drawing, Modifier.wrapContentSize(unbounded = true).graphicsLayer { scaleX = scale; scaleY = scale })
            }
        } else if (scene.drawing.width > available) {
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { UI.Drawing(scene.drawing, touch) }
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { UI.Drawing(scene.drawing, touch) }
        }
    }
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
        ReferenceKind.APP -> Unit
    }
}

/** The width a schema is laid out at on a tile before being scaled down, in dp. */
private const val READING_WIDTH = 340f
