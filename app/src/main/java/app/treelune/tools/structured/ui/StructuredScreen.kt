package app.treelune.tools.structured.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.commands.CommandResult
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.CustomFieldsDisplay
import app.treelune.core.fields.CustomFieldsInput
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.formatValue
import app.treelune.core.fields.FieldValue
import app.treelune.core.fields.FieldsLayout
import app.treelune.core.fields.ToolFields
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.FieldType
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.selectors.PointerDescription
import app.treelune.core.ui.selectors.PointerFiltersDialog
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import app.treelune.tools.structured.StructuredToolType
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One sheet: its id, its name, the user's fields it holds, when it was created and last changed. */
data class Sheet(val id: String, val name: String, val extra: Map<String, Any?>, val createdAt: Long, val updatedAt: Long) {
    /** As the entry sort reads it, by path (EntrySort.ordered): name, created_at, updated_at, extra.<field>. */
    val row: Map<String, Any?> get() = mapOf("id" to id, "name" to name, "created_at" to createdAt, "updated_at" to updatedAt, "extra" to extra)

    companion object {
        fun of(map: Map<*, *>) = Sheet(
            id = map["id"] as String,
            name = map["name"] as? String ?: "",
            extra = (map["extra"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap(),
            createdAt = (map["created_at"] as Number).toLong(),
            updatedAt = (map["updated_at"] as Number).toLong()
        )
    }
}

/** The path of a sheet's name, the table's first column. */
private const val NAME = "name"

/** The system dates a sort may name, shown under a sheet's name while the table is sorted by one. */
private val DATES = setOf("created_at", "updated_at")

/**
 * The screen of a Records tool (docs/TOOLS.md, « Records »): a table
 * of the sheets — the name, then the first fields of the config, sorted by touching a column's
 * header — and the screen of one sheet, all its fields, a swipe or a button leading to its
 * neighbours. One filter header serves both: search by name, filters, sort, folded into one line
 * that sums them up with the position. It lasts while the tool is visited, rotation included.
 *
 * A sheet is changed on its own screen: « Modifier » turns it all into inputs, « Enregistrer »
 * writes it at once, and no swipe meanwhile. « + » opens an empty sheet being written, nothing
 * stored before « Enregistrer ». A sheet changed that leaves the filter stays shown until left.
 */
@Composable
fun StructuredScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit, openAt: app.treelune.core.tools.OpenAt? = null) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "structured", context = context) }
    val scope = rememberCoroutineScope()

    var config by app.treelune.core.ui.rememberShown<JSONObject?>("page:${toolInstanceId}:config") { null }
    var sheets by app.treelune.core.ui.rememberShown<List<Sheet>?>("page:${toolInstanceId}:sheets") { null }
    var filterable by app.treelune.core.ui.rememberShown<Map<String, FieldDefinition>>("page:${toolInstanceId}:filterable") { emptyMap() }
    var configVersion by remember { mutableIntStateOf(0) }
    var sheetsVersion by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // The filter header, kept for the visit
    var search by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf("[]") }
    // The sort chosen for the visit (EntrySort's stored form), the config's default sort until then
    var chosenSort by rememberSaveable { mutableStateOf<String?>(null) }
    var headerOpen by rememberSaveable { mutableStateOf(false) }
    var editingFilters by rememberSaveable { mutableStateOf(false) }

    // The sheet open: its id, "" for a new one; whether it is being written, and the draft
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var draftExtra by rememberSaveable(stateSaver = app.treelune.core.ui.FieldValuesSaver) { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // What the tile asked to open, once: kept across recreation, so a sheet left is not opened again
    var openHandled by rememberSaveable { mutableStateOf(false) }
    // Sheets changed while open that the filter may no longer keep, shown until the sheet is left
    var kept by remember { mutableStateOf<Map<String, Sheet>>(emptyMap()) }
    // A file picked to import, its text; too large to be kept across a rotation, it is picked again
    var importing by remember { mutableStateOf<String?>(null) }
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: java.io.IOException) {
                errorMessage = s.shared("import_error_file").format(e.message ?: ""); null
            }
        }
    }

    LaunchedEffect(toolInstanceId, configVersion) {
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) { errorMessage = result.error; return@LaunchedEffect }
        @Suppress("UNCHECKED_CAST")
        config = JsonUtils.toJSONObject((result.data?.get("tool_instance") as Map<*, *>)["config"] as Map<String, Any?>)
        // The fields not read are said: filters and sorting would otherwise offer nothing in silence
        filterable = try { ToolFields.filterable(toolInstanceId, context, s) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: IllegalStateException) { errorMessage = e.message; emptyMap() }
    }
    LaunchedEffect(toolInstanceId, sheetsVersion, search, filters) {
        val all = JSONArray(filters)
        if (search.isNotBlank()) all.put(app.treelune.core.conditions.Conditions.onField("name", "contains", search.trim()))
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId, "filters" to JsonUtils.toList(all)))
        if (!result.isSuccess) { errorMessage = result.error; return@LaunchedEffect }
        sheets = (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().map { Sheet.of(it) }
    }
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> if (event.toolInstanceId == toolInstanceId) sheetsVersion++
                is DataChangeEvent.ToolsChanged -> configVersion++
                else -> {}
            }
        }
    }
    errorMessage?.let { message ->
        LaunchedEffect(message) { UI.Toast(message, Duration.LONG); errorMessage = null }
    }

    fun write(action: suspend () -> CommandResult, onDone: (CommandResult) -> Unit = {}) {
        scope.launch {
            val result = action()
            if (result.isSuccess) onDone(result) else errorMessage = result.error ?: s.shared("message_error_simple")
        }
    }

    val loadedConfig = config
    val loadedSheets = sheets
    if (loadedConfig == null || loadedSheets == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { UI.LoadingIndicator() }
        return
    }
    val settings = ToolConfigSettings.read(StructuredToolType, loadedConfig, context)
    val fields: List<FieldDefinition> = loadedConfig.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
    val columns = fields.take((settings.number(StructuredToolType.TABLE_COLUMNS)?.toInt() ?: 2).coerceAtLeast(0))

    // The order asked (the Sort brick): the one chosen for the visit, the config's default otherwise
    val sortable = remember(filterable) { app.treelune.core.fields.EntrySort.sortableFields(filterable) }
    val sort = remember(chosenSort, loadedConfig) {
        chosenSort?.let { app.treelune.core.fields.EntrySort.read(JSONArray(it)) { k -> s.shared(k) } }
            ?: StructuredToolType.defaultSort(loadedConfig) { k -> s.shared(k) }
    }
    fun choose(next: List<app.treelune.core.fields.SortKey>) { chosenSort = app.treelune.core.fields.EntrySort.toJson(next).toString() }

    // What is shown: the filtered sheets, and those kept while open, in the order asked
    // Sorted once per change, not at every recomposition: a table can hold tens of thousands
    val shown = remember(loadedSheets, kept, sort, sortable) {
        (loadedSheets + kept.values.filter { k -> loadedSheets.none { it.id == k.id } })
            .let { list ->
                val byId = list.associateBy { it.id }
                app.treelune.core.fields.EntrySort.ordered(list.map { it.row }, sort, sortable).map { byId.getValue(it["id"] as String) }
            }
            .map { sheet -> kept[sheet.id]?.takeIf { loadedSheets.none { it.id == sheet.id } } ?: sheet }
    }
    val openSheet = openId?.takeIf { it.isNotEmpty() }?.let { id -> shown.find { it.id == id } }
    val position = openSheet?.let { shown.indexOf(it) }

    fun open(sheet: Sheet?) {
        openId = sheet?.id ?: ""
        editing = sheet == null
        draftName = sheet?.name ?: ""
        draftExtra = sheet?.extra ?: fields.associate { it.name to it.defaultValue }
    }
    fun close() { openId = null; editing = false; kept = emptyMap() }

    LaunchedEffect(openAt) {
        if (openHandled) return@LaunchedEffect
        openHandled = true
        when (openAt) {
            app.treelune.core.tools.OpenAt.New -> open(null)
            is app.treelune.core.tools.OpenAt.Entry -> shown.find { it.id == openAt.id }?.let { open(it) }
            // No view of its own: its screen as usual
            is app.treelune.core.tools.OpenAt.Row, null -> Unit
        }
    }

    // Handed to « back to top »
    val listState = app.treelune.core.ui.rememberPageListState()
    // A sheet opened, its neighbour's included, read from its top
    LaunchedEffect(openId) { if (openId != null) listState.scrollToItem(0) }

    // A lazy list: only the rows on screen are composed, whatever the size of the table
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        // The chat's button floats at the bottom: room for it after the last row
        contentPadding = PaddingValues(top = UI.Space.L, bottom = UI.Space.L + app.treelune.core.ui.chatButtonEnd()),
        verticalArrangement = Arrangement.spacedBy(UI.Space.M)
    ) {
        item { UI.PageHeader(
            title = if (openId == null) settings.string("name")!! else if (openId == "") s.tool("new_sheet") else openSheet?.name ?: "",
            subtitle = if (openId == null) StructuredToolType.pageSubtitle(context, settings.string("description")) else null,
            icon = settings.string("icon_name"),
            iconColor = app.treelune.core.themes.IconColor.of(settings.string(app.treelune.core.themes.IconColor.KEY)),
            leftButton = ButtonAction.BACK,
            rightButton = if (openId == null) ButtonAction.CONFIGURE else null,
            onLeftClick = { if (openId != null) close() else onNavigateBack() },
            onRightClick = onConfigureClick
        ) }

        // The filter header: one line summing it up, the whole of it when opened
        if (!editing) item {
            val summary = listOfNotNull(
                search.takeIf { it.isNotBlank() }?.let { s.shared("quoted").format(it) },
                *JSONArray(filters).let { a -> (0 until a.length()).map { PointerDescription.filter(a.getJSONObject(it), filterable, s) } }.toTypedArray(),
                sort.joinToString(", ") { key -> "${sortable[key.field]?.displayName ?: key.field} ${if (key.ascending) "↑" else "↓"}" },
                if (position != null) s.tool("position").format(position + 1, shown.size) else s.tool("count").format(shown.size)
            ).joinToString(" · ")
            UI.Card(type = CardType.SECTION_HEADER) {
                Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    Box(modifier = Modifier.fillMaxWidth().clickable { headerOpen = !headerOpen }) {
                        UI.Text(summary, TextType.CAPTION)
                    }
                    if (headerOpen) {
                        UI.FormField(label = s.tool("search"), value = search, onChange = { search = it }, fieldType = FieldType.SEARCH, required = false)
                        UI.Button(type = ButtonType.DEFAULT, onClick = { editingFilters = true }) { UI.Text(s.tool("filters"), TextType.LABEL) }
                        app.treelune.core.ui.selectors.SortSetting(s.tool("sort_by").format(""), sort, filterable) { choose(it) }
                    }
                }
            }
        }

        if (openId == null) {
            val empty = loadedSheets.isEmpty() && search.isBlank() && JSONArray(filters).length() == 0
            // Above the table: a table of thousands of lines would put them out of reach at its end
            item { app.treelune.core.ui.components.ButtonRow {
                UI.ActionButton(action = ButtonAction.ADD, onClick = { open(null) })
                // Always there; put forward while the table is empty, a new table being most often filled from a file
                UI.Button(type = if (empty) ButtonType.PRIMARY else ButtonType.SECONDARY, onClick = { pickFile.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }) {
                    UI.Text(s.shared("import_action"), TextType.LABEL)
                }
            } }
            sheetsTable(shown, columns, sort, sortable, empty, s, context,
                // A column's header sorts by it alone, its second touch the other way
                onSort = { path -> choose(listOf(app.treelune.core.fields.SortKey(path, ascending = !(sort.singleOrNull()?.let { it.field == path && it.ascending } ?: false)))) },
                onOpen = { open(it) })
        } else if (editing) { item { Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            // The name takes the length the config gives it
            UI.FormField(label = s.shared("label_name"), value = draftName, onChange = { draftName = it },
                fieldType = StructuredToolType.nameLength(loadedConfig, context).input, required = true)
            CustomFieldsInput(customFieldsMetadata = fields, values = draftExtra, onValuesChange = { draftExtra = it }, context = context, newEntry = openId == "")
            app.treelune.core.ui.components.ButtonRow {
                UI.Button(type = ButtonType.PRIMARY, state = if (draftName.isNotBlank()) app.treelune.core.ui.ComponentState.NORMAL else app.treelune.core.ui.ComponentState.DISABLED, onClick = {
                    val extra = draftExtra.filterValues { it != null }
                    val params = mutableMapOf<String, Any>("tool_instance_id" to toolInstanceId, "name" to draftName.trim(), "data" to emptyMap<String, Any>())
                    if (openId == "") {
                        if (extra.isNotEmpty()) params["extra"] = extra
                        write({ coordinator.processUserAction("tool_data.create", params) }) { result ->
                            val id = result.data?.get("id") as String
                            val now = System.currentTimeMillis()
                            kept = kept + (id to Sheet(id, draftName.trim(), extra, now, now))
                            openId = id
                            editing = false
                        }
                    } else {
                        val id = openId!!
                        // A field emptied is sent as null, which removes it
                        params["id"] = id
                        params["extra"] = fields.associate { it.name to draftExtra[it.name] }
                        val createdAt = shown.firstOrNull { it.id == id }?.createdAt ?: System.currentTimeMillis()
                        write({ coordinator.processUserAction("tool_data.update", params) }) {
                            kept = kept + (id to Sheet(id, draftName.trim(), extra, createdAt, System.currentTimeMillis()))
                            editing = false
                        }
                    }
                }) { UI.Text(s.shared("action_save"), TextType.LABEL) }
                UI.Button(type = ButtonType.SECONDARY, onClick = { if (openId == "") close() else editing = false }) { UI.Text(s.shared("action_cancel"), TextType.LABEL) }
            }
        } } } else if (openSheet != null && position != null) { item { Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            // Its gestures above it, its neighbours' buttons included: a long record keeps them in reach
            app.treelune.core.ui.components.ButtonRow {
                UI.Button(type = ButtonType.PRIMARY, onClick = { open(openSheet); editing = true }) { UI.Text(s.tool("action_edit"), TextType.LABEL) }
                UI.ActionButton(action = ButtonAction.DELETE, onClick = { confirmDelete = true })
            }
            SheetView(openSheet, loadedConfig, context,
                onPrevious = if (position > 0) ({ open(shown[position - 1]) }) else null,
                onNext = if (position < shown.size - 1) ({ open(shown[position + 1]) }) else null)
        } } }
    }

    // A sheet deleted meanwhile closes it
    LaunchedEffect(loadedSheets, openId) {
        if (!editing && openId?.isNotEmpty() == true && shown.none { it.id == openId }) close()
    }

    if (confirmDelete && openSheet != null) {
        val dependents = app.treelune.core.ui.components.rememberDependents(mapOf("kind" to "ENTRY", "id" to openSheet.id))
        UI.Dialog(type = DialogType.DANGER, onCancel = { confirmDelete = false }, onConfirm = {
            confirmDelete = false
            write({ coordinator.processUserAction("tool_data.delete", mapOf("tool_instance_id" to toolInstanceId, "id" to openSheet.id)) }) { close() }
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                UI.Text(s.tool("delete_confirm").format(openSheet.name), TextType.BODY)
                dependents?.let { UI.Text(s.shared("dependents_will_fail").format(it), TextType.WARNING) }
            }
        }
    }
    importing?.let { csv ->
        app.treelune.core.ui.imports.ImportDialog(toolInstanceId = toolInstanceId, csv = csv, onDismiss = { importing = null })
    }
    if (editingFilters) {
        PointerFiltersDialog(
            toolInstanceId = toolInstanceId,
            fields = filterable.filterKeys { it != "name" },
            filters = JSONArray(filters),
            chosenFields = null,
            offerFields = false,
            reference = null,
            onDismiss = { editingFilters = false },
            onConfirm = { chosen, _ -> filters = chosen.toString(); editingFilters = false }
        )
    }
}

/** The table: the names of its columns once, in the header, then one line of values per sheet, each an item of the list. */
private fun LazyListScope.sheetsTable(
    sheets: List<Sheet>,
    columns: List<FieldDefinition>,
    sort: List<app.treelune.core.fields.SortKey>,
    sortable: Map<String, FieldDefinition>,
    empty: Boolean,
    s: app.treelune.core.strings.StringsContext,
    context: android.content.Context,
    onSort: (String) -> Unit,
    onOpen: (Sheet) -> Unit
) {
    if (sheets.isEmpty()) {
        item { UI.Text(s.tool(if (empty) "table_empty" else "no_match"), TextType.CAPTION) }
        return
    }
    val keys = listOf(NAME to s.shared("label_name")) + columns.map { "extra.${it.name}" to it.displayName }
    // The first key's direction beside its column, when it is one of them
    val first = sort.firstOrNull()
    // Sorted by a system date, each sheet says it under its name: no column of its own, which a phone held upright has no room for
    val date = first?.field?.takeIf { it in DATES }?.let { path -> sortable[path]?.let { path to it } }
    item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            keys.forEach { (key, label) ->
                Box(modifier = Modifier.weight(1f).clickable { onSort(key) }) {
                    UI.Text(label + if (key == first?.field) (if (first.ascending) " ↑" else " ↓") else "", TextType.LABEL)
                }
            }
        }
    }
    item { UI.Divider() }
    items(sheets, key = { it.id }) { sheet ->
        Row(modifier = Modifier.fillMaxWidth().clickable { onOpen(sheet) }.padding(vertical = UI.Space.XS), horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            // A long name (the config's name_length) cut at three lines: the sheet opened says it all
            Column(modifier = Modifier.weight(1f)) {
                UI.Text(sheet.name, TextType.BODY, maxLines = if (date != null) 2 else 3)
                date?.let { (path, field) -> UI.Text(field.formatValue(sheet.row[path], context), TextType.CAPTION, maxLines = 1) }
            }
            columns.forEach { field ->
                Box(modifier = Modifier.weight(1f)) {
                    sheet.extra[field.name]?.let { FieldValue(field, it, context, oneLine = true) }
                }
            }
        }
    }
}

/**
 * One sheet, its name and every field in full; a swipe or the buttons above it lead to its neighbours. The
 * name heads it, whatever its length: the page's title, which every theme cuts at one or two
 * lines, may not hold it.
 */
@Composable
private fun SheetView(sheet: Sheet, config: JSONObject, context: android.content.Context, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?) {
    val s = remember { Strings.`for`(tool = "structured", context = context) }
    var dragged by remember(sheet.id) { mutableStateOf(0f) }
    Column(
        modifier = Modifier.fillMaxWidth().pointerInput(sheet.id, onPrevious, onNext) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    when {
                        dragged > 120f -> onPrevious?.invoke()
                        dragged < -120f -> onNext?.invoke()
                    }
                    dragged = 0f
                },
                onHorizontalDrag = { _, amount -> dragged += amount }
            )
        },
        verticalArrangement = Arrangement.spacedBy(UI.Space.M)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UI.ActionButton(action = ButtonAction.LEFT, display = ButtonDisplay.ICON, size = Size.S, enabled = onPrevious != null, onClick = { onPrevious?.invoke() })
            UI.ActionButton(action = ButtonAction.RIGHT, display = ButtonDisplay.ICON, size = Size.S, enabled = onNext != null, onClick = { onNext?.invoke() })
        }
        UI.Text(sheet.name, TextType.STRONG)
        CustomFieldsDisplay(StructuredToolType, config, sheet.extra, FieldsLayout.EXPANDED, context)
    }
}
