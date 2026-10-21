package app.treelune.core.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.history.HistoryState
import app.treelune.core.history.CoveredTable
import app.treelune.core.history.ReadableChanges
import app.treelune.core.history.Rows
import app.treelune.core.fields.FieldValue
import app.treelune.core.strings.StringsContext
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.Duration
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.ButtonRow
import app.treelune.core.utils.DateUtils
import app.treelune.core.utils.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The history of writes (docs/DATA.md, « History of writes »): the steps, the most recent first,
 * each with who made it, when, and its state. Undo takes back the latest step done, Redo puts back
 * the one last undone; a step touched shows its rows, field by field, and is undone or redone
 * alone. A refusal is said as the service says it, naming what changed a row since. Above, the
 * room the history takes and how far back it goes.
 */
@Composable
fun WriteHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()

    var steps by remember { mutableStateOf<List<Map<String, Any?>>?>(null) }
    var summary by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var acting by remember { mutableStateOf(false) }
    // Read again after each undo and redo
    var version by remember { mutableIntStateOf(0) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(version) {
        val listed = coordinator.processUserAction("history.list", mapOf("limit" to SHOWN))
        val read = coordinator.processUserAction("history.summary", emptyMap())
        if (!listed.isSuccess || !read.isSuccess) {
            errorMessage = listed.error ?: read.error
            return@LaunchedEffect
        }
        @Suppress("UNCHECKED_CAST")
        steps = listed.data!!["steps"] as List<Map<String, Any?>>
        summary = read.data
    }

    fun act(operation: String, id: String) {
        acting = true
        scope.launch {
            val result = coordinator.processUserAction("history.$operation", mapOf("id" to id))
            acting = false
            if (result.isSuccess) {
                val phrase = result.data?.get("phrase") as? String ?: ""
                UI.Toast(s.shared(if (operation == "undo") "history_undone" else "history_redone").format(phrase), Duration.SHORT)
                version++
            } else {
                errorMessage = result.error
            }
        }
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(message, Duration.LONG)
            errorMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = s.shared("settings_write_history"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onBack
        )

        val listed = steps
        if (listed == null) {
            UI.Text(s.shared("message_loading"), TextType.BODY, fillMaxWidth = true)
            return@Column
        }

        // The latest step done, and the one last undone
        val toUndo = listed.firstOrNull { it["state"] == HistoryState.DONE }
        val toRedo = listed.filter { it["state"] == HistoryState.UNDONE }.maxByOrNull { (it["updated_at"] as Number).toLong() }

        UI.Card(type = CardType.DEFAULT, size = Size.M) {
            Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                UI.Text(s.shared("history_help"), TextType.BODY)
                summary?.get("timestamp")?.let { oldest ->
                    UI.Text(s.shared("history_room").format(
                        FormatUtils.formatFileSize((summary!!["size_bytes"] as Number).toLong(), context),
                        DateUtils.formatDateForDisplay((oldest as Number).toLong())
                    ), TextType.CAPTION)
                }
                ButtonRow {
                    UI.Button(
                        type = ButtonType.PRIMARY,
                        state = if (toUndo != null && !acting) ComponentState.NORMAL else ComponentState.DISABLED,
                        onClick = { toUndo?.let { act("undo", it["id"] as String) } }
                    ) { UI.Text(s.shared("history_undo"), TextType.LABEL) }
                    UI.Button(
                        type = ButtonType.DEFAULT,
                        state = if (toRedo != null && !acting) ComponentState.NORMAL else ComponentState.DISABLED,
                        onClick = { toRedo?.let { act("redo", it["id"] as String) } }
                    ) { UI.Text(s.shared("history_redo"), TextType.LABEL) }
                }
            }
        }

        DepthCard(s)

        if (listed.isEmpty()) {
            UI.Text(s.shared("write_history_empty"), TextType.BODY, fillMaxWidth = true)
            return@Column
        }

        listed.forEach { step ->
            val id = step["id"] as String
            StepCard(
                step = step,
                open = opened == id,
                acting = acting,
                version = version,
                s = s,
                onToggle = { opened = if (opened == id) null else id },
                onAct = { operation -> act(operation, id) }
            )
        }
    }
}

/**
 * How deep the history goes, its days and its room, from the data settings: written with its own
 * button, a slider written at each notch making a step of each.
 */
@Composable
private fun DepthCard(s: StringsContext) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val category = app.treelune.core.database.entities.AppSettingCategories.DATA
    val keys = setOf(app.treelune.core.config.AppSettings.DATA_HISTORY_DAYS, app.treelune.core.config.AppSettings.DATA_HISTORY_MEGABYTES)
    val nodes = remember { app.treelune.core.config.AppSettings.nodes(category, context).filter { it is app.treelune.core.fields.settings.SettingNode.Field && it.definition.name in keys } }
    var stored by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var draft by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var reads by remember { mutableIntStateOf(0) }
    LaunchedEffect(reads) {
        val read = coordinator.processUserAction("app_config.get", mapOf("category" to category))
        if (!read.isSuccess) {
            UI.Toast(read.error ?: "", Duration.LONG)
            return@LaunchedEffect
        }
        @Suppress("UNCHECKED_CAST")
        val settings = app.treelune.core.utils.JsonUtils.toJSONObject(read.data!!["settings"] as Map<String, Any?>)
        stored = settings
        draft = org.json.JSONObject(settings.toString())
    }
    val shown = draft ?: return
    UI.Card(type = CardType.DEFAULT, size = Size.M) {
        Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            app.treelune.core.fields.settings.SettingsForm(nodes = nodes, config = shown, onChange = { draft = it }, context = context)
            val changed = keys.any { shown.opt(it)?.toString() != stored?.opt(it)?.toString() }
            ButtonRow {
                UI.Button(
                    type = ButtonType.PRIMARY,
                    state = if (changed) ComponentState.NORMAL else ComponentState.DISABLED,
                    onClick = {
                        scope.launch {
                            val result = coordinator.processUserAction("app_config.set", mapOf("category" to category, "settings" to app.treelune.core.utils.JsonUtils.toMap(shown)))
                            if (!result.isSuccess) UI.Toast(result.error ?: "", Duration.LONG)
                            reads++
                        }
                    }
                ) { UI.Text(s.shared("action_save"), TextType.LABEL) }
            }
        }
    }
}

/** How many steps the screen lists: the most recent, which an undo reaches first. */
private const val SHOWN = 200

/** A step: what it did, who and when, its state; open, its rows field by field and its own undo or redo. */
@Composable
private fun StepCard(
    step: Map<String, Any?>,
    open: Boolean,
    acting: Boolean,
    version: Int,
    s: StringsContext,
    onToggle: () -> Unit,
    onAct: (String) -> Unit
) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val undone = step["state"] == HistoryState.UNDONE
    var rows by remember(step["id"], version) { mutableStateOf<List<ReadRow>?>(null) }
    if (open) LaunchedEffect(step["id"], version) {
        val read = coordinator.processUserAction("history.rows", mapOf("id" to step["id"]))
        @Suppress("UNCHECKED_CAST")
        val stored = if (read.isSuccess) read.data!!["rows"] as List<Map<String, Any?>> else emptyList()
        rows = readRows(stored, context)
    }

    UI.Card(type = CardType.DEFAULT, size = Size.S) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(UI.Space.M),
            verticalArrangement = Arrangement.spacedBy(UI.Space.S)
        ) {
            UI.Text((step["phrase"] as? String).orEmpty(), if (undone) TextType.CAPTION else TextType.BODY)
            val who = s.shared("history_origin_${(step["origin"] as String).lowercase()}")
            // Said again each minute, so that « 21 s ago » does not stay while the screen is open
            val minute by produceState(0) { while (true) { delay(60_000); value++ } }
            val whenText = remember(minute, step["timestamp"]) { FormatUtils.formatRelativeTimePast((step["timestamp"] as Number).toLong(), context) }
            UI.Text(listOfNotNull(who, whenText, s.shared("history_state_undone").takeIf { undone }).joinToString(" · "), TextType.CAPTION)

            if (open) {
                rows?.forEach { row -> RowLines(row, s) }
                ButtonRow {
                    UI.Button(
                        type = ButtonType.DEFAULT,
                        state = if (!acting) ComponentState.NORMAL else ComponentState.DISABLED,
                        onClick = { onAct(if (undone) "redo" else "undo") }
                    ) { UI.Text(s.shared(if (undone) "history_redo_this" else "history_undo_this"), TextType.LABEL) }
                }
            }
        }
    }
}

/** A row a step touched, read: its line (its kind and name), and what changed in it as the user reads it. */
private data class ReadRow(val title: String, val readable: ReadableChanges.Readable)

/**
 * The rows of a step as the history keeps them, read for the screen (ReadableChanges). A row
 * changed shows its fields before and after; a row created or deleted, its line alone — but an
 * entry alone, whose fields are what was written — and the entries of one tool created or
 * deleted together, one line counting them: a zone deleted is a few lines, not each of its entries.
 */
private suspend fun readRows(stored: List<Map<String, Any?>>, context: android.content.Context): List<ReadRow> {
    val s = Strings.`for`(context = context)
    val readable = ReadableChanges(context, app.treelune.core.database.AppDatabase.getDatabase(context))
    fun json(map: Any?) = (map as? Map<*, *>)?.let { org.json.JSONObject(it) }
    val all = stored.map { row -> CoveredTable.of(row["table"] as String) to json(row["before"] ?: row["after"])!! }
    val none = ReadableChanges.Readable(emptyList(), 0)

    // The entries created or deleted, by their tool and what befell them
    fun entryGroup(row: Map<String, Any?>): Pair<String, Boolean>? {
        if (row["table"] != CoveredTable.TOOL_DATA.tableName || (row["before"] != null && row["after"] != null)) return null
        val entry = (row["before"] ?: row["after"]) as Map<*, *>
        return (entry["tool_instance_id"] as String) to (row["after"] == null)
    }
    val groups = stored.mapNotNull { entryGroup(it) }.groupingBy { it }.eachCount()
    val counted = mutableSetOf<Pair<String, Boolean>>()

    return stored.mapNotNull { row ->
        val group = entryGroup(row)
        val many = group?.let { groups.getValue(it) }?.takeIf { it > 1 }
        if (group != null && many != null) {
            if (!counted.add(group)) return@mapNotNull null
            val (toolId, deleted) = group
            val tool = all.firstOrNull { (t, r) -> t == CoveredTable.TOOL_INSTANCES && r.optString("id") == toolId }?.second
                ?: Rows.read(app.treelune.core.database.AppDatabase.getDatabase(context), CoveredTable.TOOL_INSTANCES, toolId)
            val toolName = tool?.optString("config_json")?.let { runCatching { org.json.JSONObject(it).optString("name") }.getOrNull() }.orEmpty()
            return@mapNotNull ReadRow(s.shared(if (deleted) "history_entries_deleted" else "history_entries_created").format(many, toolName), none)
        }
        val before = json(row["before"])
        val after = json(row["after"])
        val table = CoveredTable.of(row["table"] as String)
        val kind = when {
            before == null -> "history_row_created"
            after == null -> "history_row_deleted"
            else -> "history_row_changed"
        }
        val tableName = s.shared("history_table_${row["table"]}")
        @Suppress("UNCHECKED_CAST")
        val named = nameOf((row["before"] ?: row["after"]) as Map<String, Any?>, context)?.let { s.shared("history_row_named").format(tableName, it) } ?: tableName
        val shown = before != null && after != null || table == CoveredTable.TOOL_DATA
        ReadRow(s.shared(kind).format(named), if (shown) readable.of(table, before, after, all) else none)
    }
}

/** A row a step touched: its kind, then each field changed by its label, its values before and after as the app shows them. */
@Composable
private fun RowLines(row: ReadRow, s: StringsContext) {
    val context = LocalContext.current
    UI.Text(row.title, TextType.LABEL)
    row.readable.lines.forEach { line ->
        when (line) {
            is ReadableChanges.Line.Value -> Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                UI.Text(line.field.displayName, TextType.BODY)
                // A row created shows its values alone, a row deleted what it held
                if (line.old != null || line.new == null) Labelled(s.shared("history_before")) { FieldValue(line.field, line.old, context, oneLine = true) }
                if (line.new != null || line.old == null) Labelled(s.shared("history_after")) { FieldValue(line.field, line.new, context, oneLine = true) }
            }
            is ReadableChanges.Line.Composite -> Column {
                UI.Text(line.label, TextType.BODY)
                UI.Text(s.shared("history_composite_changed"), TextType.CAPTION)
            }
        }
    }
    if (row.readable.internal > 0) UI.Text(
        if (row.readable.internal == 1) s.shared("history_internal_one") else s.shared("history_internal_many").format(row.readable.internal),
        TextType.CAPTION
    )
}

/** A value under its small label: « Before », « After ». */
@Composable
private fun Labelled(label: String, value: @Composable () -> Unit) {
    Column {
        UI.Text(label, TextType.CAPTION)
        value()
    }
}

/** The name a row goes by, when it has one: its own, its config's (a tool), its category's title (settings). */
private fun nameOf(row: Map<String, Any?>, context: android.content.Context): String? =
    (row["name"] as? String)?.takeIf { it.isNotBlank() }
        ?: (row["config_json"] as? String)?.let { runCatching { org.json.JSONObject(it).optString("name") }.getOrNull() }?.takeIf { it.isNotBlank() }
        ?: (row["category"] as? String)?.let { app.treelune.core.config.AppSettings.title(it, context) }
