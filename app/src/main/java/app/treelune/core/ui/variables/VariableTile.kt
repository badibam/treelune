package app.treelune.core.ui.variables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.formatValue
import app.treelune.core.grid.Grid
import app.treelune.core.look.Look
import app.treelune.core.look.Looks
import app.treelune.core.strings.Strings
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import app.treelune.core.variables.VariableLook

/** A variable as a zone lists it (variables.list): its look and its place among its section's tools. */
data class VariableRow(
    val id: String,
    val name: String,
    val group: String?,
    val look: Look,
    val gridX: Int,
    val gridY: Int,
    val definition: Map<*, *>,
    val toolsRead: Set<String>,
    /** Hidden from its page, in its section's hidden part (docs/design/phone-inbox.md) */
    val hidden: Boolean = false
) {
    val tile: Grid.Tile get() = look.tileSize.cells.let { Grid.Tile(id, gridX, gridY, it.width, it.height) }

    companion object {
        fun of(map: Map<*, *>) = VariableRow(
            id = map["id"] as String,
            name = map["name"] as String,
            group = (map["group"] as? String)?.takeIf { it.isNotEmpty() },
            look = Looks.read(VariableLook.SPEC, JsonUtils.toJSONObject(map.entries.associate { it.key.toString() to it.value })),
            gridX = (map["grid_x"] as Number).toInt(),
            gridY = (map["grid_y"] as Number).toInt(),
            definition = map["definition"] as? Map<*, *> ?: emptyMap<String, Any>(),
            toolsRead = (map["tools_read"] as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet(),
            hidden = map["hidden"] as Boolean
        )
    }
}

/** One value of a variable as its tile shows it: its label (null for a variable of one value) and its value written, or why it has none. */
data class ShownValue(val label: String?, val text: String)

/**
 * A variable's values now as its tile shows them, those « On the tile » in their order, or why
 * none can be read; null while they are read. Read again when an entry of a tool it reads
 * changes, or a variable does.
 */
@Composable
fun rememberVariableValues(variable: VariableRow): List<ShownValue>? {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var shown by app.treelune.core.ui.rememberShown<List<ShownValue>?>("variable:${variable.id}") { null }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(variable.toolsRead) {
        DataChangeNotifier.changes.collect { event ->
            val read = event is DataChangeEvent.ToolDataChanged &&
                (event.toolInstanceId in variable.toolsRead || app.treelune.core.services.VariableService.ANY_TOOL in variable.toolsRead)
            if (read || event is DataChangeEvent.VariablesChanged) version++
        }
    }
    LaunchedEffect(variable, version) {
        val result = Coordinator(context).processUserAction("variables.evaluate", mapOf(
            "variable_id" to variable.id, "at" to listOf(System.currentTimeMillis())
        ))
        shown = if (!result.isSuccess) listOf(ShownValue(null, result.error ?: s.shared("field_reference_unreadable"))) else {
            val results = (result.data?.get("results") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            results.filter { results.size == 1 || it["on_tile"] != false }.map { value ->
                val field = (value["field"] as? Map<*, *>)?.let { fieldOf(variable.name, it) }
                val row = (value["values"] as? List<*>)?.firstOrNull() as? Map<*, *>
                ShownValue(
                    label = if (results.size == 1) null else value["label"] as? String,
                    text = when {
                        row?.get("failure") != null -> s.shared("variable_no_value").format(((row["failure"] as Map<*, *>)["message"] as? String) ?: "")
                        field != null && row?.get("value") != null -> field.formatValue(row["value"], context)
                        else -> s.shared("field_reference_unreadable")
                    }
                )
            }
        }
    }
    return shown
}

/**
 * A variable's tile among its section's tools (UI.LookCard): its look; a variable of one value
 * shows it beside its header (LINE) or under it (CONDENSED), one of several a line per value
 * shown on the tile, its label then its value, under its header, what does not hold cut by « … »,
 * the tile never growing by itself. Touching it opens the variable.
 */
@Composable
fun VariableCard(variable: VariableRow, onOpen: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val values = rememberVariableValues(variable)
    val single = values?.singleOrNull()?.takeIf { it.label == null }
    @Composable
    fun Value() = UI.Text(single?.text ?: values?.let { "" } ?: s.shared("tools_loading"), TextType.BODY, maxLines = 2)
    UI.LookCard(
        look = variable.look,
        // Its kind under its label, as a tool's type under its name
        subtitle = s.shared("variable_display_name"),
        seed = variable.id,
        waiting = false,
        running = false,
        onClick = onOpen,
        onLongClick = onLongClick,
        summary = if (values == null || single != null) ({ Value() }) else null,
        glance = { Value() },
        body = { _ ->
            if (values != null && single == null) androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(UI.Space.XS)
            ) {
                values.forEach { value ->
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(UI.Space.S)) {
                        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                            UI.Text(value.label ?: "", TextType.CAPTION, maxLines = 1)
                        }
                        UI.Text(value.text, TextType.BODY, maxLines = 1)
                    }
                }
            }
        }
    )
}

/** A variable's field as the service gives it, `{type, config}`. */
fun fieldOf(name: String, json: Map<*, *>): FieldDefinition? {
    val type = FieldType.entries.firstOrNull { it.name == json["type"] } ?: return null
    @Suppress("UNCHECKED_CAST")
    return FieldDefinition(name, name, null, type, false, (json["config"] as? Map<String, Any>))
}
