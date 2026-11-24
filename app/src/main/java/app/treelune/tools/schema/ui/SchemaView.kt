package app.treelune.tools.schema.ui

import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolRequests
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.tools.schema.SchemaDetail
import app.treelune.tools.schema.SchemaScene
import app.treelune.tools.schema.SchemaSceneBuilder
import app.treelune.tools.schema.SchemaSpec
import app.treelune.tools.schema.SchemaText
import app.treelune.tools.schema.SchemaTouch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A schema's config as read for display: drawn with the current names and icons of its things, or why it cannot be. */
sealed interface SchemaReading {
    data class Problem(val message: String) : SchemaReading
    data class Drawn(val spec: SchemaSpec, val things: Map<Reference, ThingShown>) : SchemaReading

    companion object {
        suspend fun of(config: JSONObject, context: Context): SchemaReading {
            val s = Strings.`for`(tool = "schema", context = context)
            val spec = try { SchemaSpec.of(config) { s.tool(it) } } catch (e: IllegalArgumentException) { return Problem(e.message.orEmpty()) }
            val things = thingsShown(spec.nodes.mapNotNull { it.thing }, context) ?: return Problem(s.tool("mermaid_names_failed"))
            return Drawn(spec, things)
        }
    }
}

/** What a box shows of its thing: its current [name], null once it no longer exists, and its [icon], null when it has none. */
data class ThingShown(val name: String?, val icon: String?)

/** The current name of each of [things], null for one that no longer exists; null when they could not be read. */
suspend fun thingNames(things: List<Reference>, context: Context): Map<Reference, String?>? =
    thingsShown(things, context)?.mapValues { it.value.name }

/** What each of [things] shows now; null when they could not be read. */
suspend fun thingsShown(things: List<Reference>, context: Context): Map<Reference, ThingShown>? {
    if (things.isEmpty()) return emptyMap()
    val result = Coordinator(context).processUserAction("references.names", mapOf("references" to things.distinct().map { thing ->
        mapOf("kind" to thing.kind.name, "id" to thing.id, "field" to thing.field).filterValues { it != null }
    }))
    if (!result.isSuccess) return null
    return (result.data?.get("references") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { row ->
        ReferenceTarget.referenceOf(row)?.let { it to ThingShown(row["name"] as? String, row["icon"] as? String) }
    }.toMap()
}

/**
 * A schema laid out at the width it is given and drawn by the theme. On a screen, a drawing still
 * wider than the width scrolls sideways. [reduced], it is laid out at a reading width and scaled
 * down to the room, for a tile; [across], it runs from left to right, laid out at a reading height
 * in the same proportion (a wide tile), [orElse] shown where nothing of it holds even in dots;
 * [onTouch] null leaves the touch to the tile.
 *
 * A group touched stays where it is on the screen while the schema changes around it, as far as
 * the edges allow: the drawing shifts sideways and [pageScroll], the page's scroll, follows.
 */
@Composable
fun SchemaView(read: SchemaReading.Drawn, folded: Set<String>, onTouch: ((SchemaTouch) -> Unit)?, modifier: Modifier = Modifier, reduced: Boolean = false, pageScroll: ScrollState? = null, keptOpen: Set<String> = emptySet(), across: Boolean = false, orElse: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val style = UI.drawingTextStyle()
    val unit = UI.drawingUnit()
    val measurer = rememberTextMeasurer()
    val text = remember(style, measurer) {
        object : SchemaText {
            // A layout asks for the same few texts thousands of times, and measuring one is slow
            // Locked: a layout made obsolete may still be running beside the next one
            private val widths = HashMap<String, Float>()
            override fun width(text: String) = synchronized(widths) { widths.getOrPut(text) { measurer.measure(text, style).size.width.toFloat() } }
            override val height = measurer.measure("Ag", style).size.height.toFloat()
        }
    }
    val s = remember { Strings.`for`(tool = "schema", context = context) }
    val sideScroll = rememberScrollState()
    // The drawing's left in the view once a group was held in place; null keeps it centered
    var heldLeft by remember(read.spec) { mutableStateOf<Float?>(null) }
    var anchor by remember { mutableStateOf<GroupAnchor?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val available = constraints.maxWidth.toFloat()
        // The limit the placement keeps to: the width at a size one reads, or across the height in
        // the same proportion, the room's shape kept
        val reading = if (reduced) max(1f, READING_WIDTH * density / available) else 1f
        val width = if (across) constraints.maxHeight.toFloat() * reading else available * reading
        // Placing takes a good part of a second on a phone: done aside, the last drawing staying on
        // screen meanwhile, and kept, so that going back to a folding already seen is immediate
        val room = constraints.maxHeight.toFloat()
        val scenes = remember(read, width, text, density, unit, reduced, across, available, room) { HashMap<Pair<Set<String>, Set<String>>, Shown>() }
        var lastShown by remember { mutableStateOf<Shown?>(null) }
        LaunchedEffect(scenes, folded, keptOpen) {
            val key = folded to keptOpen
            lastShown = scenes[key] ?: withContext(Dispatchers.Default) {
                val start = System.currentTimeMillis()
                val builder = SchemaSceneBuilder(density, unit, text) { s.tool(it) }
                val nameOf = { thing: Reference -> read.things[thing]?.name }
                val iconOf = { thing: Reference -> read.things[thing]?.icon }
                if (!reduced) Shown(builder.build(read.spec, nameOf, folded, width, keptOpen, fit = true, iconOf = iconOf)
                    .also { app.treelune.core.utils.LogManager.ui("Schema laid out in ${System.currentTimeMillis() - start} ms (${it.autoFolded.size} folded to fit, scale ${it.scale})", "INFO") })
                else tileShown(read.spec, builder, nameOf, iconOf, folded, width, available, room, across, unit)
                    .also { app.treelune.core.utils.LogManager.ui("Schema tile laid out in ${System.currentTimeMillis() - start} ms: ${it.log}", "INFO") }
            }.also { scenes[key] = it }
        }
        val shown = lastShown ?: return@BoxWithConstraints UI.LoadingIndicator()
        // On a tile, a schema too dense even in dots shows nothing of it: what stands in its place
        val scene = shown.scene ?: return@BoxWithConstraints orElse()
        val shownScale = scene.scale
        val free = available - scene.drawing.width * shownScale
        // Computed while composing, for the first frame of the new drawing to stand right already
        val anchoredLeft = anchor?.let { a -> scene.groupArea(a.id)?.let { a.x - it.left * shownScale } }
        val left = if (free >= 0f) (anchoredLeft ?: heldLeft ?: (free / 2)).coerceIn(0f, free) else -sideScroll.value.toFloat()
        LaunchedEffect(scene) {
            val a = anchor ?: return@LaunchedEffect
            anchor = null
            val area = scene.groupArea(a.id) ?: return@LaunchedEffect
            if (free >= 0f) heldLeft = (a.x - area.left * shownScale).coerceIn(0f, free)
            // The new heights are laid out first: a scroll asked before would stop at the old end
            withFrameNanos { }
            if (free < 0f) sideScroll.scrollTo((area.left * shownScale - a.x).toInt())
            pageScroll?.scrollTo((area.top * shownScale - a.y).toInt())
        }
        val touch = if (onTouch == null) Modifier else Modifier.pointerInput(scene, left) {
            detectTapGestures { offset ->
                val found = scene.hitAt(offset.x, offset.y) ?: return@detectTapGestures
                (found as? SchemaTouch.Group)?.let { group ->
                    scene.groupArea(group.id)?.let { anchor = GroupAnchor(group.id, it.left * shownScale + left, it.top * shownScale - (pageScroll?.value ?: 0)) }
                }
                onTouch(found)
            }
        }
        if (reduced) {
            val scale = tileScale(scene, available, room, unit)
            Box(modifier = Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                UI.Drawing(scene.drawing, Modifier.wrapContentSize(unbounded = true).graphicsLayer { scaleX = scale; scaleY = scale })
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            val shown = Modifier.shownAt(shownScale).then(touch)
            if (free < 0f) Box(modifier = Modifier.fillMaxWidth().horizontalScroll(sideScroll)) { UI.Drawing(scene.drawing, shown) }
            else Box(modifier = Modifier.fillMaxWidth()) {
                UI.Drawing(scene.drawing, Modifier.offset { IntOffset(left.toInt(), 0) }.then(shown))
            }
            if (onTouch != null && scene.autoFolded.isNotEmpty()) UI.Text(
                if (scene.autoFolded.size == 1) s.tool("auto_folded_one") else s.tool("auto_folded").format(scene.autoFolded.size),
                TextType.CAPTION
            )
        }
    }
}

/** Shown at [scale], taking the room of that size; a touch inside still reads the drawing's own coordinates. */
private fun Modifier.shownAt(scale: Float): Modifier = if (scale == 1f) this else layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/** A schema laid out for where it shows: none on a tile it cannot fit even in dots; [log] says how it was chosen. */
private data class Shown(val scene: SchemaScene?, val log: String = "")

/**
 * A tile's drawing at the richest level of detail that holds (docs/design/progression.md): one
 * holds while the drawing, scaled down to the tile, keeps at least its bound of its size. The
 * level expected is read first from the room the boxes take, measured without placing them; the
 * drawing is placed there, and one level lower each time it does not hold. A level is judged at
 * the scale it will really be shown at, a pixel theme's whole steps included: in steps of a third,
 * a drawing wanting 0.61 shows at 0.33, and the next level, at 0.67, fills the tile better.
 */
private fun tileShown(spec: SchemaSpec, builder: SchemaSceneBuilder, nameOf: (Reference) -> String?, iconOf: (Reference) -> String?,
                      folded: Set<String>, width: Float, available: Float, room: Float, across: Boolean, unit: Float): Shown {
    val levels = SchemaDetail.entries
    val expected = levels.firstOrNull { detail ->
        val area = builder.boxesArea(spec, nameOf, folded, width, detail, iconOf, across) * SPREAD
        area <= 0f || sqrt(available * room / area) >= BOUNDS.getValue(detail)
    } ?: levels.last()
    val tried = mutableListOf<String>()
    for (detail in levels.drop(levels.indexOf(expected))) {
        val scene = builder.build(spec, nameOf, folded, width, fit = detail == SchemaDetail.TEXT, iconOf = iconOf, across = across, detail = detail)
        val scale = tileScale(scene, available, room, unit)
        tried += "$detail at ${"%.2f".format(scale)}"
        if (scale >= BOUNDS.getValue(detail)) return Shown(scene, "expected $expected, ${tried.joinToString()}")
    }
    return Shown(null, "expected $expected, ${tried.joinToString()}, none holds")
}

/**
 * What [scene] is shown at in a tile's room: as large as it fits, never above its size; for a theme
 * drawing in whole pixels ([unit] screen pixels to one of its own), a whole number of screen pixels
 * to each of its own, at least one.
 */
private fun tileScale(scene: SchemaScene, available: Float, room: Float, unit: Float): Float {
    val wanted = min(1f, min(available / scene.drawing.width, room / scene.drawing.height))
    return if (unit > 1f) max(1f, floor(wanted * unit)) / unit else wanted
}

/**
 * The smallest a tile shows a drawing at, per level, against its full size; to judge on the phone.
 * Below the last, nothing is drawn.
 */
private val BOUNDS = mapOf(SchemaDetail.TEXT to 0.6f, SchemaDetail.COMPACT to 0.6f, SchemaDetail.ICON to 0.5f, SchemaDetail.SHAPE to 0.5f, SchemaDetail.DOT to 0.5f)

/**
 * How much more room a drawing takes than its boxes alone, its gaps, arrows and groups. Set on the
 * phone (2026-11-24): at 3, every tile expected a level too rich and was placed twice, its text
 * coming out at about half the scale expected.
 */
private const val SPREAD = 8f

/** Where a touched group stood: its left in the view, its top against the page's scroll. */
private data class GroupAnchor(val id: String, val x: Float, val y: Float)

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
