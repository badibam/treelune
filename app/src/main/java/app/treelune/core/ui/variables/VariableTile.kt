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
    val toolsRead: Set<String>
) {
    val tile: Grid.Tile get() = Grid.size(look.displayMode).let { Grid.Tile(id, gridX, gridY, it.width, it.height) }

    companion object {
        fun of(map: Map<*, *>) = VariableRow(
            id = map["id"] as String,
            name = map["name"] as String,
            group = (map["group"] as? String)?.takeIf { it.isNotEmpty() },
            look = Looks.read(VariableLook.SPEC, JsonUtils.toJSONObject(map.entries.associate { it.key.toString() to it.value })),
            gridX = (map["grid_x"] as Number).toInt(),
            gridY = (map["grid_y"] as Number).toInt(),
            definition = map["definition"] as? Map<*, *> ?: emptyMap<String, Any>(),
            toolsRead = (map["tools_read"] as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet()
        )
    }
}

/**
 * A variable's value now as its tile shows it, or why it has none; null while it is read. Read
 * again when an entry of a tool it reads changes, or a variable does.
 */
@Composable
fun rememberVariableValue(variable: VariableRow): String? {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var shown by remember(variable.id) { mutableStateOf<String?>(null) }
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
        shown = if (!result.isSuccess) (result.error ?: s.shared("field_reference_unreadable")) else {
            val field = (result.data?.get("field") as? Map<*, *>)?.let { fieldOf(variable.name, it) }
            val row = (result.data?.get("values") as? List<*>)?.firstOrNull() as? Map<*, *>
            when {
                row?.get("failure") != null -> s.shared("variable_no_value").format(((row["failure"] as Map<*, *>)["message"] as? String) ?: "")
                field != null && row?.get("value") != null -> field.formatValue(row["value"], context)
                else -> s.shared("field_reference_unreadable")
            }
        }
    }
    return shown
}

/**
 * A variable's tile among its section's tools (UI.LookCard): its look, its value beside its
 * header (LINE) or under it (CONDENSED). Touching it opens the variable.
 */
@Composable
fun VariableCard(variable: VariableRow, onOpen: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val value = rememberVariableValue(variable)
    @Composable
    fun Value() = UI.Text(value ?: s.shared("tools_loading"), TextType.BODY, maxLines = 2)
    UI.LookCard(
        look = variable.look,
        // Its kind under its label, as a tool's type under its name
        subtitle = s.shared("variable_display_name"),
        seed = variable.id,
        waiting = false,
        running = false,
        onClick = onOpen,
        onLongClick = onLongClick,
        summary = { Value() },
        glance = { Value() },
        body = { }
    )
}

/** A variable's field as the service gives it, `{type, config}`. */
fun fieldOf(name: String, json: Map<*, *>): FieldDefinition? {
    val type = FieldType.entries.firstOrNull { it.name == json["type"] } ?: return null
    @Suppress("UNCHECKED_CAST")
    return FieldDefinition(name, name, null, type, false, (json["config"] as? Map<String, Any>))
}
