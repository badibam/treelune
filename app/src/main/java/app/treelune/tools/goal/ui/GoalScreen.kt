package app.treelune.tools.goal.ui

import app.treelune.core.ui.chatButtonSpace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.formatValue
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DateUtils
import app.treelune.core.utils.JsonUtils
import app.treelune.core.fields.toFieldDefinition
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.fields.CustomFieldsDisplay
import app.treelune.core.fields.CustomFieldsInput
import app.treelune.core.fields.FieldsLayout
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.FieldValuesSaver
import androidx.compose.ui.Alignment
import app.treelune.tools.goal.GoalDefinition
import app.treelune.tools.goal.GoalToolType
import app.treelune.tools.goal.Met
import kotlinx.coroutines.launch
import org.json.JSONObject

/** An attempt as the screen lists it. */
data class AttemptRow(val id: String, val start: Long, val status: String, val periodEnd: Long?, val validatedBy: String?, val extra: Map<String, Any?> = emptyMap())

/**
 * A goal's screen (docs/TOOLS.md, « Goal »), in two tabs, always there, the
 * first open: « In progress », its label counting the attempts waiting to be validated, lists them
 * oldest first, then the attempt running, its criteria by sub-goal, each value against its
 * condition, entered where it is entered, the count against what is asked, the time left and
 * « Valider »; « History », each attempt opened read-only with « Rouvrir ».
 */
@Composable
fun GoalScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit, openEntryId: String? = null) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "goal", context = context) }
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<JSONObject?>(null) }
    var attempts by remember { mutableStateOf<List<AttemptRow>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // An attempt opened from the tile, one to validate, or none
    var openId by rememberSaveable { mutableStateOf(openEntryId) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(toolInstanceId, version) {
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        config = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        if (config == null) { errorMessage = tool.error; return@LaunchedEffect }
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) { errorMessage = result.error; return@LaunchedEffect }
        attempts = (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().map { e ->
            val state = e["state"] as? Map<*, *> ?: emptyMap<String, Any>()
            AttemptRow(e["id"] as String, (e["timestamp"] as Number).toLong(), state[GoalToolType.STATUS] as? String ?: "",
                (state[GoalToolType.PERIOD_END] as? Number)?.toLong(), state[GoalToolType.VALIDATED_BY] as? String,
                (e["extra"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap())
        }
    }
    LaunchedEffect(toolInstanceId) {
        // Its criteria read other tools' entries and variables
        DataChangeNotifier.changes.collect { event -> if (event.mayChangeReadings()) version++ }
    }
    errorMessage?.let { message -> LaunchedEffect(message) { UI.Toast(message, Duration.LONG); errorMessage = null } }

    fun operation(name: String, id: String) {
        scope.launch {
            val result = coordinator.processUserAction("goal.$name", mapOf("tool_instance_id" to toolInstanceId, "id" to id))
            if (!result.isSuccess) errorMessage = result.error else version++
        }
    }

    val loadedConfig = config
    val loaded = attempts
    if (loadedConfig == null || loaded == null) { UI.LoadingIndicator(); return }
    val settings = ToolConfigSettings.read(GoalToolType, loadedConfig, context)
    val current = loaded.filter { it.status == GoalToolType.Status.ACTIVE }.maxByOrNull { it.start }
    val toValidate = loaded.filter { it.status == GoalToolType.Status.TO_VALIDATE }.sortedBy { it.start }
    val history = loaded.filter { it.status in GoalToolType.Status.LOCKED }.sortedByDescending { it.start }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).chatButtonSpace().padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = settings.string("name")!!,
            subtitle = settings.string("description")?.takeIf { it.isNotBlank() },
            icon = settings.string("icon_name"),
            iconColor = app.treelune.core.themes.IconColor.of(settings.string(app.treelune.core.themes.IconColor.KEY)),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = { if (openId != null) openId = null else onNavigateBack() },
            onRightClick = onConfigureClick
        )

        val open = openId?.let { id -> loaded.find { it.id == id } }
        if (open != null) {
            AttemptCard(open, loadedConfig, toolInstanceId, version, s, onError = { errorMessage = it },
                onValidate = { operation("validate", open.id) }, onReopen = { operation("reopen", open.id) })
            return@Column
        }

        UI.Tabs(
            labels = listOf(
                if (toValidate.isEmpty()) s.tool("tab_now") else s.tool("tab_now_waiting").format(toValidate.size),
                s.tool("history")
            ),
            selected = tab,
            onSelect = { tab = it }
        )

        when (tab) {
            0 -> {
                if (toValidate.isNotEmpty()) {
                    UI.Text(s.tool("status_to_validate"), TextType.SUBTITLE)
                    toValidate.forEach { attempt -> AttemptLine(attempt, s) { openId = attempt.id } }
                }
                if (current != null) AttemptCard(current, loadedConfig, toolInstanceId, version, s, onError = { errorMessage = it },
                    onValidate = { operation("validate", current.id) }, onReopen = {})
                else UI.Text(s.tool("no_attempt"), TextType.CAPTION)
            }
            else -> {
                if (history.isEmpty()) UI.Text(s.tool("history_empty"), TextType.CAPTION)
                else UI.Text(history.take(30).joinToString(" ") { dot(it.status) }, TextType.BODY)
                history.forEach { attempt -> AttemptLine(attempt, s) { openId = attempt.id } }
            }
        }
    }
}

/** A verdict's dot in the history's strip. */
private fun dot(status: String): String = when (status) {
    GoalToolType.Status.SUCCEEDED -> "●"
    GoalToolType.Status.FAILED -> "○"
    else -> "·"
}

@Composable
private fun AttemptLine(attempt: AttemptRow, s: StringsContext, onOpen: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = UI.Space.XS), horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text("${dot(attempt.status)} ${DateUtils.formatFullDateTime(attempt.start)}", TextType.BODY)
        UI.Text(s.tool("status_${attempt.status}"), TextType.CAPTION)
    }
}

/**
 * One attempt: each criterion's line — its value against its condition, entered in place while
 * the attempt is open — by sub-goal, the count against what is asked, the time left, and what can
 * be done with it.
 */
@Composable
private fun AttemptCard(
    attempt: AttemptRow,
    config: JSONObject,
    toolInstanceId: String,
    version: Int,
    s: StringsContext,
    onError: (String?) -> Unit,
    onValidate: () -> Unit,
    onReopen: () -> Unit
) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    var judgement by remember(attempt.id) { mutableStateOf<Map<*, *>?>(null) }
    var local by remember(attempt.id) { mutableIntStateOf(0) }
    LaunchedEffect(attempt.id, version, local) {
        val result = coordinator.processUserAction("goal.evaluate", mapOf("tool_instance_id" to toolInstanceId, "id" to attempt.id))
        if (result.isSuccess) judgement = result.data else onError(result.error)
    }
    val locked = attempt.status in GoalToolType.Status.LOCKED
    val definition = GoalDefinition.of(config)
    val loaded = judgement

    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text(DateUtils.formatFullDateTime(attempt.start) + (attempt.periodEnd?.let { " → " + DateUtils.formatFullDateTime(it) } ?: ""), TextType.CAPTION)
            UI.Text(s.tool("status_${attempt.status}") + (attempt.validatedBy?.let { " · " + s.tool("validated_by_$it") } ?: ""), TextType.LABEL)
            if (loaded == null) { UI.LoadingIndicator(); return@Column }
            val criteria = (loaded["criteria"] as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
            // A verdict "no" while the attempt runs is not a failure yet: its time is not up
            val verdict = (loaded["verdict"] as? String ?: "UNKNOWN").lowercase()
                .let { if (it == "no" && attempt.status == GoalToolType.Status.ACTIVE) "not_yet" else it }
            UI.Text(s.tool("count").format(loaded["met"], loaded["required"]) + " · " + s.tool("verdict_$verdict"), TextType.SUBTITLE)
            attempt.periodEnd?.takeIf { attempt.status == GoalToolType.Status.ACTIVE }?.let { end ->
                val left = end - System.currentTimeMillis()
                // In the units of the goal's duration, days and hours; in minutes for the last hour, which would read 0 h
                val units = if (left < app.treelune.core.fields.DurationUnit.HOUR.millis) app.treelune.core.fields.DurationUnits.MINUTE else app.treelune.core.fields.DurationUnits.DAY_HOUR
                if (left > 0) UI.Text(s.tool("time_left").format(app.treelune.core.fields.Durations.format(left, units.units, Strings.`for`(context = context))), TextType.CAPTION)
            }
            criteria.forEach { row ->
                val key = row["key"] as String
                val criterion = definition.allCriteria.find { it.key == key }
                val met = Met.valueOf(row["met"] as? String ?: "UNKNOWN")
                val entered = criterion?.enteredField()
                // The field the value and what it is compared with are values of
                val field = entered ?: (row["field"] as? Map<*, *>)?.let { JsonUtils.toJSONObject(it.entries.associate { e -> e.key.toString() to e.value }).toFieldDefinition() }
                val compared = row["compared"] as? List<*> ?: emptyList<Any?>()
                val conditionText = condition(row["op"] as? String ?: "", compared, field, context, s)
                if (!locked && entered != null) {
                    FieldInput(entered, row["value"], { value ->
                        scope.launch {
                            val result = coordinator.processUserAction("tool_data.update", mapOf("id" to attempt.id, "data" to mapOf(key to value)))
                            if (!result.isSuccess) onError(result.error) else local++
                        }
                    }, context, required = false)
                    UI.Text(listOfNotNull(conditionText, row["failure"] as? String, s.tool("met_${met.name.lowercase()}")).joinToString(" · "), TextType.CAPTION)
                } else {
                    val shown = row["failure"] as? String ?: row["value"]?.let { value -> field?.formatValue(value, context) ?: value.toString() } ?: s.shared("label_no_value")
                    UI.Text("${row["name"]} : $shown" + (conditionText?.let { " / $it" } ?: "") + " · " + s.tool("met_${met.name.lowercase()}"), TextType.BODY)
                    // A gauge for an ordered value compared with one number
                    val value = (row["value"] as? Number)?.toDouble()
                    val target = (compared.singleOrNull() as? Number)?.toDouble()
                    if (value != null && target != null && target > 0 && field?.type in GAUGED) {
                        UI.Gauge(fraction = (value / target).toFloat().coerceIn(0f, 1f))
                    }
                }
            }
            (loaded["sub_goals"] as? Map<*, *>)?.forEach { (name, subMet) ->
                UI.Text("$name · " + s.tool("met_${(subMet as String).lowercase()}"), TextType.CAPTION)
            }
            UserFields(attempt, config, locked, onError, onSaved = { local++ })
            app.treelune.core.ui.components.ButtonRow {
                if (!locked) UI.Button(type = ButtonType.PRIMARY, onClick = onValidate) { UI.Text(s.tool("action_validate"), TextType.LABEL) }
                else UI.Button(type = ButtonType.SECONDARY, onClick = onReopen) { UI.Text(s.tool("action_reopen"), TextType.LABEL) }
            }
        }
    }
}

/**
 * The user's fields of an attempt, noted while it runs since it can stretch over weeks: shown as
 * written, a pencil opening them all in a form whose saving is one write, never one per letter.
 * A validated or expired attempt keeps them frozen with its judgement.
 */
@Composable
private fun UserFields(attempt: AttemptRow, config: JSONObject, locked: Boolean, onError: (String?) -> Unit, onSaved: () -> Unit) {
    val fields = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
    if (fields.isEmpty()) return
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable(attempt.id) { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        Column(modifier = Modifier.weight(1f)) {
            CustomFieldsDisplay(GoalToolType, config, attempt.extra, FieldsLayout.COMPACT, context)
        }
        if (!locked) UI.ActionButton(action = ButtonAction.EDIT, display = ButtonDisplay.ICON, onClick = { editing = true })
    }
    if (editing) UserFieldsDialog(attempt, fields, onCancel = { editing = false }) { values ->
        editing = false
        scope.launch {
            val result = coordinator.processUserAction("tool_data.update", mapOf("id" to attempt.id, "extra" to values))
            if (!result.isSuccess) onError(result.error) else onSaved()
        }
    }
}

/** The form of an attempt's user fields, prefilled; [onSave] gets every field's value. */
@Composable
private fun UserFieldsDialog(attempt: AttemptRow, fields: List<FieldDefinition>, onCancel: () -> Unit, onSave: (Map<String, Any?>) -> Unit) {
    val context = LocalContext.current
    var values by rememberSaveable(attempt.id, stateSaver = FieldValuesSaver) { mutableStateOf(attempt.extra) }
    UI.Dialog(type = DialogType.EDIT, onConfirm = { onSave(fields.associate { it.name to values[it.name] }) }, onCancel = onCancel) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(Strings.`for`(context = context).shared("custom_fields_section_title"), TextType.SUBTITLE)
            // An attempt's fields never filled yet: their default values proposed where they are empty
            CustomFieldsInput(customFieldsMetadata = fields, values = values, onValuesChange = { values = it }, context = context,
                newEntry = fields.none { attempt.extra[it.name] != null })
        }
    }
}

/** The values a gauge measures, against the number a criterion compares them with. */
private val GAUGED = setOf(FieldType.NUMERIC, FieldType.SCALE, FieldType.DURATION)

/**
 * A criterion's condition in words, what it is compared with shown as [field] shows its values:
 * "≥ 7 h", "= Yes", "between 70 kg and 80 kg"; null before it is judged.
 */
internal fun condition(op: String, compared: List<*>, field: FieldDefinition?, context: android.content.Context, s: StringsContext): String? {
    val operator = app.treelune.core.fields.FilterOperator.of(op) ?: return null
    fun shown(value: Any?) = value?.let { field?.formatValue(it, context) ?: it.toString() } ?: "?"
    val words = app.treelune.core.ui.selectors.PointerDescription.operator(operator, s)
    return when (operator) {
        app.treelune.core.fields.FilterOperator.ABSENT, app.treelune.core.fields.FilterOperator.PRESENT -> words
        app.treelune.core.fields.FilterOperator.BETWEEN -> s.shared("filter_between").format("", shown(compared.getOrNull(0)), shown(compared.getOrNull(1))).trim()
        else -> "$words ${shown(compared.firstOrNull())}"
    }
}
