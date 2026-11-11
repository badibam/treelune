package app.treelune.tools.questionnaire.ui

import app.treelune.core.fields.defaultValues
import app.treelune.core.ui.components.ButtonRow
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
import app.treelune.core.commands.CommandResult
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.CustomFieldsDisplay
import app.treelune.core.fields.CustomFieldsInput
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldsLayout
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.FormatUtils
import app.treelune.core.utils.JsonUtils
import app.treelune.tools.questionnaire.QuestionnaireToolType
import kotlinx.coroutines.launch
import org.json.JSONObject

/** An entry as the screen lists it: its answers and where it stands. */
data class QuestionnaireEntry(val id: String, val timestamp: Long, val status: String, val answers: Map<String, Any?>)

/**
 * A questionnaire's screen (docs/TOOLS.md, « Questionnaire »), in two tabs, always
 * there, the first open: « To fill », its label counting the entries waiting, lists them oldest
 * first, with « Ignore all », then « Fill in now » and « With the AI »;
 * « History », newest first, each entry with its title and state, touching one opens it, its
 * answers changed there. An entry's title is the questionnaire's name and the moment it is about,
 * relative, never stored. [openEntryId], an entry asked for elsewhere (a Chart's point): passed
 * when it is to fill, opened as from the history otherwise.
 */
@Composable
fun QuestionnaireScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit, openEntryId: String? = null) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "questionnaire", context = context) }
    val scope = rememberCoroutineScope()

    var config by app.treelune.core.ui.rememberShown<JSONObject?>("page:${toolInstanceId}:config") { null }
    var entries by app.treelune.core.ui.rememberShown<List<QuestionnaireEntry>?>("page:${toolInstanceId}:entries") { null }
    var version by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // The passing under way: "" on demand, an entry's id when planned; null when none
    var passing by rememberSaveable { mutableStateOf<String?>(null) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    // The entry asked for, opened once the entries are read and its status known
    var asked by rememberSaveable { mutableStateOf(openEntryId) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(toolInstanceId, version) {
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        config = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        if (config == null) { errorMessage = tool.error; return@LaunchedEffect }
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) { errorMessage = result.error; return@LaunchedEffect }
        entries = (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().map { e ->
            QuestionnaireEntry(
                id = e["id"] as String,
                timestamp = (e["timestamp"] as Number).toLong(),
                status = (e["state"] as? Map<*, *>)?.get(QuestionnaireToolType.STATUS) as? String ?: "",
                answers = (e["extra"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
            )
        }
    }
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> if (event.toolInstanceId == toolInstanceId) version++
                is DataChangeEvent.ToolsChanged -> version++
                else -> {}
            }
        }
    }
    errorMessage?.let { message -> LaunchedEffect(message) { UI.Toast(message, Duration.LONG); errorMessage = null } }

    fun write(action: suspend () -> CommandResult, onDone: () -> Unit = {}) {
        scope.launch {
            val result = action()
            if (result.isSuccess) { onDone(); version++ } else errorMessage = result.error ?: s.shared("message_error_simple")
        }
    }

    val loadedConfig = config
    val loaded = entries
    if (loadedConfig == null || loaded == null) { UI.LoadingIndicator(); return }
    val settings = ToolConfigSettings.read(QuestionnaireToolType, loadedConfig, context)
    val name = settings.string("name")!!
    val questions: List<FieldDefinition> = loadedConfig.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
    fun title(entry: QuestionnaireEntry) = "$name · ${FormatUtils.formatRelativeTimePast(entry.timestamp, context)}"

    asked?.let { id ->
        LaunchedEffect(id) {
            val entry = loaded.find { it.id == id }
            if (entry?.status == QuestionnaireToolType.Status.TO_FILL) passing = id else if (entry != null) openId = id
            asked = null
        }
    }

    val current = passing
    if (current != null) {
        val entry = loaded.find { it.id == current }
        Passing(questions, entry, s, onCancel = { passing = null }, onAnswer = { key, value ->
            if (entry != null) write({ coordinator.processUserAction("tool_data.update", mapOf("id" to entry.id, "extra" to mapOf(key to value))) })
        }, onFinish = { answers ->
            if (entry != null) write({ coordinator.processUserAction("questionnaire.complete", mapOf("tool_instance_id" to toolInstanceId, "id" to entry.id)) }) { passing = null }
            else write({
                val now = System.currentTimeMillis()
                coordinator.processUserAction("tool_data.create", mapOf(
                    "tool_instance_id" to toolInstanceId,
                    "timestamp" to now,
                    "data" to emptyMap<String, Any>(),
                    "extra" to answers,
                    // The status alone: the tool type sets the rest (QuestionnaireToolType.START)
                    "state" to mapOf(QuestionnaireToolType.STATUS to QuestionnaireToolType.Status.FILLED)
                ))
            }) { passing = null }
        })
        return
    }

    val toFill = loaded.filter { it.status == QuestionnaireToolType.Status.TO_FILL }.sortedBy { it.timestamp }
    val history = loaded.filter { it.status != QuestionnaireToolType.Status.TO_FILL }.sortedByDescending { it.timestamp }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).chatButtonSpace().padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = name,
            subtitle = QuestionnaireToolType.pageSubtitle(context, settings.string("description")),
            icon = settings.string("icon_name"),
            iconColor = app.treelune.core.themes.IconColor.of(settings.string(app.treelune.core.themes.IconColor.KEY)),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = { if (openId != null) openId = null else onNavigateBack() },
            onRightClick = onConfigureClick
        )

        val open = openId?.let { id -> loaded.find { it.id == id } }
        if (open != null) {
            EntryView(open, title(open), loadedConfig, questions, s) { answers ->
                write({
                    val saved = coordinator.processUserAction("tool_data.update", mapOf("id" to open.id, "extra" to answers))
                    // An ignored or expired entry answered afterwards is filled: what listens to the
                    // questionnaire's filled entries (a routine, a Task) then starts as for any other
                    val answered = open.status == QuestionnaireToolType.Status.IGNORED || open.status == QuestionnaireToolType.Status.EXPIRED
                    if (saved.isSuccess && answered) coordinator.processUserAction("questionnaire.complete", mapOf("tool_instance_id" to toolInstanceId, "id" to open.id))
                    else saved
                })
            }
            return@Column
        }

        UI.Tabs(
            labels = listOf(
                if (toFill.isEmpty()) s.tool("tab_to_fill_none") else s.tool("tab_to_fill").format(toFill.size),
                s.tool("history")
            ),
            selected = tab,
            onSelect = { tab = it }
        )

        when (tab) {
            0 -> {
                if (toFill.isEmpty()) UI.Text(s.tool("to_fill_empty"), TextType.CAPTION)
                else UI.Button(type = ButtonType.DEFAULT, onClick = {
                    write({ coordinator.processUserAction("questionnaire.ignore_all", mapOf("tool_instance_id" to toolInstanceId)) })
                }) { UI.Text(s.tool("action_ignore_all"), TextType.LABEL) }
                toFill.forEach { entry ->
                    UI.Card(type = CardType.DEFAULT) {
                        Column(modifier = Modifier.padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                            UI.Text(title(entry), TextType.BODY)
                            ButtonRow {
                                UI.Button(type = ButtonType.PRIMARY, onClick = { passing = entry.id }) { UI.Text(s.tool("action_fill"), TextType.LABEL) }
                                UI.Button(type = ButtonType.DEFAULT, onClick = {
                                    write({ coordinator.processUserAction("questionnaire.ignore", mapOf("tool_instance_id" to toolInstanceId, "id" to entry.id)) })
                                }) { UI.Text(s.tool("action_ignore"), TextType.LABEL) }
                            }
                        }
                    }
                }
                ButtonRow {
                    UI.Button(type = ButtonType.PRIMARY, onClick = { passing = "" }) { UI.Text(s.tool("action_fill_now"), TextType.LABEL) }
                }
            }
            else -> {
                if (history.isEmpty()) UI.Text(s.tool("history_empty"), TextType.CAPTION)
                history.forEach { entry ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { openId = entry.id }.padding(vertical = UI.Space.XS), horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                        UI.Text(title(entry), TextType.BODY)
                        UI.Text(s.tool("status_${entry.status}"), TextType.CAPTION)
                    }
                }
            }
        }
    }
}

/**
 * The passing, one question per screen: « Next » moves on, a question passed stays without an
 * answer. Planned, each answer is saved as it is given ([onAnswer]) and the passing starts at the
 * first question without one; on demand, the answers stay here until the end ([onFinish]).
 */
@Composable
private fun Passing(
    questions: List<FieldDefinition>,
    entry: QuestionnaireEntry?,
    s: StringsContext,
    onCancel: () -> Unit,
    onAnswer: (String, Any?) -> Unit,
    onFinish: (Map<String, Any?>) -> Unit
) {
    val context = LocalContext.current
    // A question without an answer yet proposes its default value, saved once the question is passed
    var answers by rememberSaveable(stateSaver = app.treelune.core.ui.FieldValuesSaver) {
        val given = entry?.answers.orEmpty()
        mutableStateOf(questions.filter { it.name !in given }.defaultValues() + given)
    }
    var index by rememberSaveable { mutableIntStateOf(questions.indexOfFirst { entry?.answers?.get(it.name) == null }.coerceAtLeast(0)) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).chatButtonSpace().padding(vertical = UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.L)) {
        UI.PageHeader(title = s.tool("passing_title").format(index + 1, questions.size), leftButton = ButtonAction.BACK, onLeftClick = onCancel)
        if (questions.isEmpty()) { UI.Text(s.tool("no_question"), TextType.CAPTION); return@Column }
        val question = questions[index]
        question.description?.let { UI.Text(it, TextType.CAPTION) }
        FieldInput(question, answers[question.name], { answers = answers + (question.name to it) }, context, required = false)
        app.treelune.core.ui.components.ButtonRow {
            if (index > 0) UI.Button(type = ButtonType.SECONDARY, onClick = { index-- }) { UI.Text(s.tool("action_previous"), TextType.LABEL) }
            val last = index == questions.size - 1
            UI.Button(type = ButtonType.PRIMARY, onClick = {
                onAnswer(question.name, answers[question.name])
                if (last) onFinish(answers) else index++
            }) { UI.Text(s.tool(if (last) "action_finish" else "action_next"), TextType.LABEL) }
        }
    }
}

/** An entry read, every answer in full, and changed in place. */
@Composable
private fun EntryView(entry: QuestionnaireEntry, title: String, config: JSONObject, questions: List<FieldDefinition>, s: StringsContext, onSave: (Map<String, Any?>) -> Unit) {
    val context = LocalContext.current
    var editing by rememberSaveable(entry.id) { mutableStateOf(false) }
    var draft by rememberSaveable(entry.id, stateSaver = app.treelune.core.ui.FieldValuesSaver) { mutableStateOf(entry.answers) }
    UI.Text(title, TextType.SUBTITLE)
    UI.Text(s.tool("status_${entry.status}"), TextType.CAPTION)
    if (!editing) {
        CustomFieldsDisplay(QuestionnaireToolType, config, entry.answers, FieldsLayout.EXPANDED, context)
        UI.Button(type = ButtonType.PRIMARY, onClick = { draft = entry.answers; editing = true }) { UI.Text(s.tool("action_edit"), TextType.LABEL) }
    } else {
        CustomFieldsInput(customFieldsMetadata = questions, values = draft, onValuesChange = { draft = it }, context = context)
        app.treelune.core.ui.components.ButtonRow {
            UI.Button(type = ButtonType.PRIMARY, onClick = { onSave(questions.associate { it.name to draft[it.name] }); editing = false }) { UI.Text(s.shared("action_save"), TextType.LABEL) }
            UI.Button(type = ButtonType.SECONDARY, onClick = { editing = false }) { UI.Text(s.shared("action_cancel"), TextType.LABEL) }
        }
    }
}
