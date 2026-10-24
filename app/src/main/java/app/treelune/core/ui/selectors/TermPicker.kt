package app.treelune.core.ui.selectors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.StringsContext
import app.treelune.core.terms.Term
import app.treelune.core.ui.UI
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * The Terme brick's selector (docs/BRICKS.md): a constant, a variable, or a reading, [term] in its
 * stored form (Term). The constant is entered as [constantField], the type the context reads it as
 * (a number in a formula, the field on the other side of a condition); a variable is designated
 * as a thing, reached in its zone. A file's term (a FILE [constantField]) is a fixed value, the
 * files added or copied once from another entry, or a field of one entry followed, read again at
 * each use: its reading is that field, reached through the browser.
 *
 * @param kinds The kinds the context takes (a chart's grid reads, and has no constant); a term
 *   of none yet starts as the first of them
 */
@Composable
fun TermPicker(term: JSONObject, constantField: FieldDefinition, onChange: (JSONObject) -> Unit, s: StringsContext, where: ReadingContext,
               kinds: Set<Term.Kind> = Term.Kind.entries.toSet()) {
    val context = LocalContext.current
    val file = constantField.type == FieldType.FILE
    val offered = listOf(READING to s.shared(if (file) "term_follow_field" else "variable_term_reading"), CONSTANT to s.shared("variable_term_constant"), VARIABLE to s.shared("variable_term_variable"))
        .filter { (key, _) -> kinds.any { it.key == key } }
    val kind = offered.firstOrNull { term.has(it.first) }?.first ?: if (kinds.contains(Term.Kind.CONSTANT)) CONSTANT else offered.first().first
    if (offered.size > 1) UI.FormSelection(label = s.shared("variable_term_kind"), options = offered.map { it.second }, selected = offered.first { it.first == kind }.second, onSelect = { label ->
        onChange(fresh(offered.first { it.second == label }.first))
    }, required = true)
    // A term of none of the kinds offered yet shows as the one it starts as, written once chosen
    val shown = if (term.has(kind)) term else fresh(kind)
    when (kind) {
        CONSTANT -> FieldInput(
            constantField.copy(displayName = s.shared("variable_value")),
            JsonUtils.toValue(shown.opt(CONSTANT)?.takeIf { it != JSONObject.NULL }),
            { value -> onChange(if (value == null) JSONObject().put(CONSTANT, JSONObject.NULL) else Term.Constant(value).toJson()) },
            context, required = true
        )
        VARIABLE -> FieldInput(
            FieldDefinition(VARIABLE, s.shared("variable_term_variable"), null, FieldType.REFERENCE, false,
                mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.VARIABLE.name)))),
            shown.optString(VARIABLE).takeIf { it.isNotEmpty() }?.let { mapOf("kind" to ReferenceKind.VARIABLE.name, "id" to it) },
            { value -> onChange(JSONObject().put(VARIABLE, ReferenceTarget.referenceOf(value)?.id ?: "")) },
            context, required = true
        )
        else -> if (file) FollowedField(shown.getJSONObject(READING), constantField, s) { onChange(JSONObject().put(READING, it)) }
            else ReadingPicker(shown.getJSONObject(READING), s, where) { onChange(JSONObject().put(READING, it)) }
    }
}

/**
 * A field of one entry a file's term follows, [reading] in its stored form: what it follows,
 * named as it is now or said deleted (references.names), and the button that picks another among
 * the file fields of a single file of the kinds [value] takes.
 */
@Composable
private fun FollowedField(reading: JSONObject, value: FieldDefinition, s: StringsContext, onChange: (JSONObject) -> Unit) {
    val context = LocalContext.current
    var picking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val followed = reading.optJSONObject("selection")?.optJSONObject("target")?.let { target ->
        app.treelune.core.selection.Reference.fromJson(target).takeIf { it.kind == ReferenceKind.ENTRY }
            ?.let { it.copy(field = reading.optString("field").takeIf { f -> f.isNotEmpty() }) }
    }
    var name by androidx.compose.runtime.remember(followed) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(followed) {
        name = followed?.takeIf { it.field != null }?.let { reference ->
            try {
                when (val named = app.treelune.core.fields.loadReferenceNames(listOf(reference), context)[reference]) {
                    is app.treelune.core.fields.ReferenceName.Named -> s.shared("term_follows").format(named.name)
                    else -> s.shared("term_source_deleted")
                }
            } catch (e: IllegalStateException) {
                app.treelune.core.utils.LogManager.ui("FollowedField: ${e.message}", "ERROR")
                null
            }
        }
    }
    name?.let { UI.Text(it, app.treelune.core.ui.TextType.BODY) }
    UI.ActionButton(action = app.treelune.core.ui.ButtonAction.SELECT, onClick = { picking = true })
    if (picking) {
        val takes = app.treelune.core.fields.FileField.kinds(value.config)
        EntryFieldPicker(
            title = s.shared("term_follow_pick"),
            accepts = { it.type == FieldType.FILE && Reduction.forField(it).isNotEmpty() && app.treelune.core.fields.FileField.kinds(it.config).all { kind -> kind in takes } },
            onDismiss = { picking = false },
            onPick = { picked ->
                picking = false
                onChange(JSONObject()
                    .put("selection", JSONObject().put("target", app.treelune.core.selection.Reference(ReferenceKind.ENTRY, picked.id).toJson()))
                    .put("field", picked.field)
                    .put("reduction", Reduction.LAST.name))
            }
        )
    }
}

/** A term of [kind] with nothing chosen yet. */
private fun fresh(kind: String): JSONObject = when (kind) {
    READING -> JSONObject().put(READING, JSONObject().put("selection", JSONObject()).put("reduction", Reduction.COUNT.name))
    VARIABLE -> JSONObject().put(VARIABLE, "")
    else -> JSONObject().put(CONSTANT, JSONObject.NULL)
}

private const val READING = "reading"
private const val CONSTANT = "constant"
private const val VARIABLE = "variable"
