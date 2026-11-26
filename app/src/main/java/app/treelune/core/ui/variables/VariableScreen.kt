package app.treelune.core.ui.variables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.formatValue
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.selectors.ReadingContext
import app.treelune.core.ui.selectors.TermPicker
import app.treelune.core.utils.JsonUtils
import app.treelune.core.variables.Formula
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The keys of a variable's stored definition. */
private typealias Def = app.treelune.core.variables.VariableDefinition.Companion

/**
 * A variable seen and changed (docs/DATA.md, « Variables »): its name, its look (Looks: its label,
 * description, icon, colour, texture and the mode of its tile, the colour and the texture each on
 * a page of the form, which then fills the screen), the terms its values share, each with its
 * period relative to the instant it is read at, then its values, each a constant or a formula
 * written in text with buttons inserting the names and a check at each keystroke, its type, and
 * with several its key, its label and whether its tile shows it; and its values now.
 * The service checks it again when it is saved, as it checks the AI's.
 *
 * The draft is the definition the service takes, written with names, kept as JSON across a rotation.
 *
 * @param variableId The variable changed; null to create one in [zoneId]
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VariableScreen(zoneId: String, variableId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    // Its look, a new variable's at its declared defaults (the icon variable, LINE)
    val lookNodes = remember { app.treelune.core.look.Looks.nodes(app.treelune.core.variables.VariableLook.SPEC, s::shared) }
    var look by rememberSaveable(stateSaver = app.treelune.core.ui.JsonObjectSaver) { mutableStateOf(app.treelune.core.fields.settings.SettingDefaults.of(lookNodes)) }
    val pages = app.treelune.core.fields.settings.rememberSettingsPages()
    var draft by rememberSaveable { mutableStateOf(JSONObject().put(Def.TERMS, JSONObject())
        .put(Def.VALUES, org.json.JSONArray().put(JSONObject().put(Def.KEY, Def.FIRST_KEY).put(Def.FORMULA, ""))).toString()) }
    var loaded by rememberSaveable { mutableStateOf(variableId == null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Each value now, by key: its value written, or why there is none
    var current by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var currentError by remember { mutableStateOf<String?>(null) }
    var others by remember { mutableStateOf<List<VariableRow>>(emptyList()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Its group among the zone's, none for the ungrouped section
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    // How its tile lays its values when it has several (VariableLook.ITEMS)
    var itemWidth by rememberSaveable { mutableStateOf(app.treelune.core.variables.VariableLook.ITEMS.defaultWidth.stored) }
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(variableId) {
        groups = ((coordinator.processUserAction("zones.get", mapOf("zone_id" to zoneId)).data?.get("zone") as? Map<*, *>)?.get("tool_groups") as? List<*>)
            .orEmpty().filterIsInstance<String>()
        val all = coordinator.processUserAction("variables.list_all", emptyMap())
        others = (all.data?.get("variables") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().map { VariableRow.of(it) }.filter { it.id != variableId }
        if (variableId != null && !loaded) {
            val result = coordinator.processUserAction("variables.get", mapOf("variable_id" to variableId))
            val variable = result.data?.get("variable") as? Map<*, *>
            if (variable != null) {
                name = variable["name"] as String
                group = (variable["group"] as? String)?.takeIf { it.isNotEmpty() }
                (variable[app.treelune.core.look.ItemLayout.WIDTH] as? String)?.let { itemWidth = it }
                look = JSONObject().apply {
                    app.treelune.core.look.Looks.keys(app.treelune.core.variables.VariableLook.SPEC).forEach { key -> variable[key]?.let { put(key, it) } }
                }
                @Suppress("UNCHECKED_CAST")
                val definition = JsonUtils.toJSONObject(variable["definition"] as Map<String, Any?>)
                // Shown with each function in the app's language, as it is written here
                fun localized(text: String) = (Formula.parse(text) as? Formula.Parsed.Read)?.formula?.text({ Formula.stored(it) }, { f -> s.shared("formula_function_${f.canonical}") }) ?: text
                definition.optJSONArray(Def.VALUES)?.let { values ->
                    for (i in 0 until values.length()) values.optJSONObject(i)?.let { v -> v.optString(Def.FORMULA).takeIf { it.isNotEmpty() }?.let { v.put(Def.FORMULA, localized(it)) } }
                }
                definition.optJSONObject("terms")?.let { terms ->
                    terms.keys().forEach { key ->
                        terms.getJSONObject(key).optJSONObject("reading")?.let { r -> r.optString("per_entry").takeIf { it.isNotEmpty() }?.let { r.put("per_entry", localized(it)) } }
                    }
                }
                draft = definition.toString()
                loaded = true
            } else error = result.error
        }
        if (variableId != null) {
            val value = coordinator.processUserAction("variables.evaluate", mapOf("variable_id" to variableId, "at" to listOf(System.currentTimeMillis())))
            if (!value.isSuccess) currentError = value.error
            else current = (value.data?.get("results") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { result ->
                val row = (result["values"] as? List<*>)?.firstOrNull() as? Map<*, *>
                val field = (result["field"] as? Map<*, *>)?.let { fieldOf(name, it) }
                val written = when {
                    row?.get("failure") != null -> s.shared("variable_no_value").format((row["failure"] as Map<*, *>)["message"] as? String ?: "")
                    field != null && row?.get("value") != null -> field.formatValue(row["value"], context)
                    else -> null
                } ?: return@mapNotNull null
                (result["key"] as String) to written
            }.toMap()
        }
    }

    val json = JSONObject(draft)
    fun edit(change: JSONObject.() -> Unit) { draft = JSONObject(draft).apply(change).toString() }
    val terms = json.optJSONObject(Def.TERMS) ?: JSONObject()
    val values = json.optJSONArray(Def.VALUES) ?: org.json.JSONArray()
    // What a formula may name besides its terms: each other variable, by its name alone for one of
    // one value, by `name.key` for each value of one of several
    val variableTokens = others.flatMap { other ->
        val keys = (other.definition[Def.VALUES] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get(Def.KEY) as? String }
        if (keys.size == 1) listOf(other.name) else keys.map { "${other.name}.$it" }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = look.optString("label").ifBlank { name.ifBlank { s.shared("variable_new") } },
            subtitle = s.shared("variable_display_name"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onDone
        )
        if (!loaded) {
            UI.LoadingIndicator()
            return@Column
        }
        if (pages.atRoot) {
            currentError?.let { UI.Text(text = s.shared("variable_value_now").format(it), type = TextType.SUBTITLE) }
            // Its value now, or each of its values, by its label
            if (values.length() == 1) current.values.firstOrNull()?.let { UI.Text(text = s.shared("variable_value_now").format(it), type = TextType.SUBTITLE) }
            else (0 until values.length()).mapNotNull { values.optJSONObject(it) }.forEach { v ->
                current[v.optString(Def.KEY)]?.let { UI.Text(text = s.shared("variable_value_now_of").format(v.optString(Def.LABEL).ifBlank { v.optString(Def.KEY) }, it), type = TextType.BODY) }
            }
            UI.FormField(label = s.shared("label_name"), value = name, onChange = { name = it }, required = true)
        }
        app.treelune.core.fields.settings.SettingsForm(lookNodes, look, { look = it }, context,
            app.treelune.core.look.ui.lookEditors(app.treelune.core.variables.VariableLook.SPEC, look, s) { look = it }, pages = pages)
        // A page of the look open, the rest waits for its return
        if (!pages.atRoot) return@Column
        if (groups.isNotEmpty()) app.treelune.core.ui.components.GroupSelector(groups, group, { group = it }, s.shared("label_group"))

        // The terms its values share, each read once per instant for all of them
        UI.Text(text = s.shared("variable_terms"), type = TextType.SUBTITLE)
        terms.keys().asSequence().toList().forEach { termName ->
            TermEditor(
                name = termName,
                term = terms.getJSONObject(termName),
                s = s,
                onRename = { newName -> edit { val t = optJSONObject(Def.TERMS) ?: JSONObject().also { put(Def.TERMS, it) }; val v = t.remove(termName); t.put(newName, v) } },
                onChange = { term -> edit { (optJSONObject(Def.TERMS) ?: JSONObject().also { put(Def.TERMS, it) }).put(termName, term) } },
                onRemove = { edit { optJSONObject(Def.TERMS)?.remove(termName) } }
            )
        }
        UI.Button(type = ButtonType.SECONDARY, onClick = {
            edit {
                val t = optJSONObject(Def.TERMS) ?: JSONObject().also { put(Def.TERMS, it) }
                var n = 1
                while (t.has("t$n")) n++
                t.put("t$n", JSONObject().put("constant", 0))
            }
        }) { UI.Text(text = s.shared("variable_add_term"), type = TextType.LABEL) }

        // Its values, each in its card; with several, each its key, its label and whether the tile shows it
        UI.Text(text = s.shared("variable_values"), type = TextType.SUBTITLE)
        val several = values.length() > 1
        (0 until values.length()).forEach { index ->
            val value = values.getJSONObject(index)
            fun editValue(change: JSONObject.() -> Unit) = edit { getJSONArray(Def.VALUES).getJSONObject(index).apply(change) }
            UI.Card(type = CardType.DEFAULT) {
                Column(modifier = Modifier.padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    if (several) {
                        Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                                UI.FormField(label = s.shared("variable_value_label"), value = value.optString(Def.LABEL), onChange = { t -> editValue { put(Def.LABEL, t) } }, required = true)
                            }
                            UI.ActionButton(action = ButtonAction.DELETE, display = app.treelune.core.ui.ButtonDisplay.ICON, onClick = { edit { getJSONArray(Def.VALUES).remove(index) } })
                        }
                        UI.FormField(label = s.shared("variable_value_key"), value = value.optString(Def.KEY), onChange = { t -> editValue { put(Def.KEY, t.trim()) } }, required = true)
                    }
                    val isFormula = !value.has(Def.CONSTANT)
                    val kinds = listOf(true to s.shared("variable_kind_formula"), false to s.shared("variable_kind_constant"))
                    UI.FormSelection(
                        label = s.shared("variable_kind"),
                        options = kinds.map { it.second },
                        selected = kinds.first { it.first == isFormula }.second,
                        onSelect = { label ->
                            val formula = kinds.first { it.second == label }.first
                            editValue {
                                if (formula) { remove(Def.CONSTANT); remove(Def.FIELD); put(Def.FORMULA, "") }
                                else { remove(Def.FORMULA); put(Def.CONSTANT, 0); put(Def.FIELD, JSONObject().put("type", "NUMERIC").put("config", JSONObject().put("decimals", 0))) }
                            }
                        },
                        required = true
                    )
                    if (!isFormula) {
                        val field = fieldOf(name, JsonUtils.toMap(value.optJSONObject(Def.FIELD) ?: JSONObject()))
                        if (field != null) {
                            FieldInput(field.copy(displayName = s.shared("variable_value")), value.opt(Def.CONSTANT), { v -> editValue { put(Def.CONSTANT, v ?: 0) } }, context, required = true)
                        }
                    } else {
                        FormulaEditor(value.optString(Def.FORMULA), terms.keys().asSequence().toList(), variableTokens, s) { text -> editValue { put(Def.FORMULA, text) } }
                    }
                    ValueTypeEditor(value.optJSONObject(Def.FIELD), isFormula, s) { field -> editValue { if (field == null) remove(Def.FIELD) else put(Def.FIELD, field) } }
                    if (several) UI.BooleanField(label = s.shared("variable_value_on_tile"), value = value.optBoolean(Def.ON_TILE, true),
                        onValueChange = { on -> editValue { if (on == false) put(Def.ON_TILE, false) else remove(Def.ON_TILE) } }, required = true)
                }
            }
        }
        UI.Button(type = ButtonType.SECONDARY, onClick = {
            edit {
                val list = getJSONArray(Def.VALUES)
                val taken = (0 until list.length()).map { list.getJSONObject(it).optString(Def.KEY) }.toSet()
                var n = list.length() + 1
                while ("value_$n" in taken) n++
                // The first value gets a label once there are two, its own key kept
                list.optJSONObject(0)?.let { first -> if (first.optString(Def.LABEL).isBlank()) first.put(Def.LABEL, look.optString("label")) }
                list.put(JSONObject().put(Def.KEY, "value_$n").put(Def.LABEL, "").put(Def.FORMULA, ""))
            }
        }) { UI.Text(text = s.shared("variable_add_value"), type = TextType.LABEL) }
        // With several values, how its tile lays them
        if (several) {
            val widths = app.treelune.core.variables.VariableLook.ITEMS.widths.map { it.stored to s.shared("item_width_${it.stored}") }
            UI.FormSelection(label = s.shared("tools_config_label_item_width"), options = widths.map { it.second },
                selected = widths.first { it.first == itemWidth }.second, onSelect = { label -> itemWidth = widths.first { it.second == label }.first }, required = true)
        }

        SeriesEditor(json.optJSONObject(app.treelune.core.variables.VariableDefinition.SERIES)?.optString(app.treelune.core.variables.VariableDefinition.UNIT), s) { unit ->
            edit { if (unit == null) remove(app.treelune.core.variables.VariableDefinition.SERIES) else put(app.treelune.core.variables.VariableDefinition.SERIES, JSONObject().put(app.treelune.core.variables.VariableDefinition.UNIT, unit)) }
        }

        error?.let { UI.Text(text = it, type = TextType.ERROR) }

        UI.FormActions {
            UI.Button(type = ButtonType.PRIMARY, onClick = {
                scope.launch {
                    // Every setting of its look is sent: an absent one as null, which empties it
                    val params = mutableMapOf<String, Any>("name" to name, "definition" to JsonUtils.toMap(JSONObject(draft)),
                        app.treelune.core.look.ItemLayout.WIDTH to itemWidth)
                    app.treelune.core.look.Looks.keys(app.treelune.core.variables.VariableLook.SPEC).forEach { key -> params[key] = look.opt(key) ?: JSONObject.NULL }
                    val result = if (variableId == null) {
                        coordinator.processUserAction("variables.create", params + ("zone_id" to zoneId) + (group?.let { mapOf("group" to it) } ?: emptyMap()))
                    } else coordinator.processUserAction("variables.update", params + ("variable_id" to variableId) + ("group" to (group ?: JSONObject.NULL)))
                    if (result.isSuccess) onDone() else error = result.error
                }
            }) { UI.Text(text = s.shared("action_save"), type = TextType.LABEL) }
            if (variableId != null) {
                UI.ActionButton(action = ButtonAction.DELETE, onClick = { confirmDelete = true })
            }
        }
    }

    if (confirmDelete && variableId != null) {
        val dependents = app.treelune.core.ui.components.rememberDependents(mapOf("kind" to "VARIABLE", "id" to variableId))
        UI.Dialog(type = DialogType.DANGER, onCancel = { confirmDelete = false }, onConfirm = {
            scope.launch {
                val result = coordinator.processUserAction("variables.delete", mapOf("variable_id" to variableId))
                confirmDelete = false
                if (result.isSuccess) onDone() else error = result.error
            }
        }) {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(UI.Space.M)) {
                UI.Text(text = s.shared("variable_delete_confirm").format(name), type = TextType.BODY)
                dependents?.let { UI.Text(text = s.shared("dependents_will_fail").format(it), type = TextType.WARNING) }
            }
        }
    }
}

/**
 * Whether the variable has a value per step of a unit (docs/design/variable-series.md), and what
 * that means for its terms, said under the choice.
 */
@Composable
private fun SeriesEditor(unit: String?, s: StringsContext, onChange: (String?) -> Unit) {
    val units = app.treelune.core.selection.PeriodType.entries.map { it.name to s.shared("period_type_${it.name.lowercase()}") }
    val none = s.shared("variable_series_none")
    UI.FormSelection(
        label = s.shared("variable_series"),
        options = listOf(none) + units.map { it.second },
        selected = units.firstOrNull { it.first == unit }?.second ?: none,
        onSelect = { label -> onChange(units.firstOrNull { it.second == label }?.first) },
        required = false
    )
    units.firstOrNull { it.first == unit }?.let { (_, name) -> UI.Text(text = s.shared("variable_series_hint").format(name.lowercase()), type = TextType.CAPTION) }
}

/**
 * The formula in text, the names to insert where the cursor stands, and what stops it from
 * computing, said at each keystroke: where it does not read, or the names that are neither a term
 * nor a variable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormulaEditor(formula: String, terms: List<String>, variables: List<String>, s: StringsContext, onChange: (String) -> Unit) {
    // The cursor is kept here; a formula changed elsewhere puts it at the end
    var field by remember { mutableStateOf(TextFieldValue(formula, TextRange(formula.length))) }
    val shown = if (field.text == formula) field else TextFieldValue(formula, TextRange(formula.length))
    UI.FormField(label = s.shared("variable_formula"), value = shown, onChange = { next ->
        field = next
        if (next.text != formula) onChange(next.text)
    }, required = true, fieldType = app.treelune.core.ui.FieldType.TEXT_MEDIUM)
    val problem = when (val parsed = Formula.parse(formula, Formula.Function.localized { s.shared(it) })) {
        is Formula.Parsed.Unreadable -> if (formula.isBlank()) null
            else s.shared("variable_error_formula").format(parsed.position + 1, s.shared("formula_problem_${parsed.problem.name.lowercase()}"), parsed.detail)
        is Formula.Parsed.Read -> parsed.formula.names().filter { it !in terms && it !in variables }.distinct()
            .takeIf { it.isNotEmpty() }?.let { s.shared("variable_unknown_names").format(it.joinToString(", ")) }
    }
    problem?.let { UI.Text(text = it, type = TextType.ERROR) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UI.Space.XS), verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        (terms + variables + listOf("+", "-", "×", "÷", "(", ")") + Formula.Function.entries.map { s.shared("formula_function_${it.canonical}") + if (it == Formula.Function.NOW) "()" else "(" }).forEach { token ->
            UI.Button(type = ButtonType.SECONDARY, onClick = {
                val (text, cursor) = insertToken(shown.text, shown.selection.min, shown.selection.max, token)
                field = TextFieldValue(text, TextRange(cursor))
                onChange(text)
            }) {
                UI.Text(text = token, type = TextType.LABEL)
            }
        }
    }
}

/**
 * [text] with [token] in place of what lies between [start] and [end] (the cursor, or the
 * selection it replaces), and the cursor just after it. A space sets it apart from its
 * neighbours, except at an edge, inside parentheses, or after a function's opening one:
 * "a + |b" with "×" gives "a + × b".
 */
internal fun insertToken(text: String, start: Int, end: Int, token: String): Pair<String, Int> {
    val before = text.substring(0, start)
    val after = text.substring(end)
    val left = if (before.isEmpty() || before.endsWith(" ") || before.endsWith("(")) "" else " "
    val right = if (after.isEmpty() || after.startsWith(" ") || after.startsWith(")") || token.endsWith("(")) "" else " "
    val inserted = before + left + token
    return (inserted + right + after) to inserted.length
}

/** One term of the formula: its local name, and what it is (TermPicker), a number when a constant. */
@Composable
private fun TermEditor(
    name: String,
    term: JSONObject,
    s: StringsContext,
    onRename: (String) -> Unit,
    onChange: (JSONObject) -> Unit,
    onRemove: () -> Unit
) {
    var shownName by remember(name) { mutableStateOf(name) }
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(label = s.shared("variable_term_name"), value = shownName, onChange = { shownName = it }, required = true,
                        fieldModifier = app.treelune.core.ui.FieldModifier(onFocusChanged = { state -> if (!state.isFocused && shownName != name && shownName.isNotBlank()) onRename(shownName) }))
                }
                UI.ActionButton(action = ButtonAction.DELETE, display = app.treelune.core.ui.ButtonDisplay.ICON, onClick = onRemove)
            }
            TermPicker(term, remember { FieldDefinition(name, name, null, FieldType.NUMERIC, false, null) }, onChange, s, ReadingContext(s.shared("instant_reference_reading")))
        }
    }
}

/**
 * The type of the value: deduced from what the formula reads, or set — a number with its unit and
 * decimals, a scale with its bounds, a duration. A constant always says it.
 */
@Composable
private fun ValueTypeEditor(field: JSONObject?, deducible: Boolean, s: StringsContext, onChange: (JSONObject?) -> Unit) {
    val deduced = s.shared("variable_type_deduced")
    val types = listOf(FieldType.NUMERIC, FieldType.SCALE, FieldType.DURATION)
    val options = (if (deducible) listOf(deduced) else emptyList()) + types.map { s.shared("field_type_${it.name.lowercase()}_display_name") }
    val type = field?.optString("type")?.let { t -> types.firstOrNull { it.name == t } }
    UI.FormSelection(
        label = s.shared("variable_type"),
        options = options,
        selected = type?.let { s.shared("field_type_${it.name.lowercase()}_display_name") } ?: deduced,
        onSelect = { label ->
            val chosen = types.firstOrNull { s.shared("field_type_${it.name.lowercase()}_display_name") == label }
            onChange(when (chosen) {
                null -> null
                FieldType.NUMERIC -> JSONObject().put("type", "NUMERIC").put("config", JSONObject().put("decimals", 0))
                FieldType.SCALE -> JSONObject().put("type", "SCALE").put("config", JSONObject().put("min", 1).put("max", 10))
                else -> JSONObject().put("type", "DURATION")
            })
        },
        required = true
    )
    val config = field?.optJSONObject("config") ?: return
    fun edit(key: String, value: Any?) = onChange(JSONObject(field.toString()).put("config", JSONObject(config.toString()).apply { if (value == null) remove(key) else put(key, value) }))
    when (type) {
        FieldType.NUMERIC -> Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                UI.FormField(label = s.shared("field_config_unit"), value = config.optString("unit"), onChange = { edit("unit", it.takeIf { u -> u.isNotBlank() }) }, required = false)
            }
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                UI.FormField(label = s.shared("field_config_decimals"), value = config.optInt("decimals").toString(),
                    onChange = { t -> t.toIntOrNull()?.let { edit("decimals", it) } }, fieldType = app.treelune.core.ui.FieldType.NUMERIC, required = true)
            }
        }
        FieldType.SCALE -> Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            listOf("min" to "field_config_min", "max" to "field_config_max").forEach { (key, labelKey) ->
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(label = s.shared(labelKey), value = config.opt(key)?.toString() ?: "",
                        onChange = { t -> t.toDoubleOrNull()?.let { edit(key, it) } }, fieldType = app.treelune.core.ui.FieldType.NUMERIC, required = true)
                }
            }
        }
        else -> {}
    }
}
