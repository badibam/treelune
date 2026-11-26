package app.treelune.core.ui.selectors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.conditions.ConditionPhrase
import app.treelune.core.conditions.PhrasePart
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FilterOperator
import app.treelune.core.strings.Strings
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import org.json.JSONObject

/**
 * A condition's phrase on a line (ConditionPhrase): « [label] : » then its words, wrapping as the
 * line does. With [onChange], each constant is touchable: a window writes it over with the input
 * of the field it is a value of (several options for « is one of »), or as text when that field is
 * not known, and the condition is written back with that one value changed.
 *
 * @param phrase How the phrase is written, its sources and where the condition stands
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionPhraseLine(label: String, condition: JSONObject, phrase: ConditionPhrase, onChange: ((JSONObject) -> Unit)?) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val parts by produceState<List<PhrasePart>?>(null, condition.toString(), phrase) {
        value = try { phrase.of(condition) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: IllegalStateException) { listOf(PhrasePart.Text(e.message ?: "")) }
    }
    var editing by remember { mutableStateOf<PhrasePart.Constant?>(null) }
    val shown = parts ?: return
    FlowRow {
        UI.Text("${s.shared("settings_count").format(label, "").trimEnd()} ", TextType.CAPTION)
        ConditionPhrase.tokens(shown).forEach { (text, constant) ->
            if (constant == null || onChange == null) UI.Text(text, if (constant == null) TextType.CAPTION else TextType.LABEL)
            else Box(Modifier.clickable { editing = constant }) { UI.Text(text, TextType.STRONG) }
        }
    }
    editing?.let { constant ->
        ConstantDialog(constant, onCancel = { editing = null }) { value ->
            editing = null
            onChange?.invoke(ConditionPhrase.write(condition, constant.path, value))
        }
    }
}

/** The window that writes one constant over: the input of its field, several options for « is one of », text otherwise. */
@Composable
private fun ConstantDialog(constant: PhrasePart.Constant, onCancel: () -> Unit, onDone: (Any?) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var value by remember { mutableStateOf(constant.value) }
    UI.Dialog(type = DialogType.EDIT, onConfirm = { onDone(value) }, onCancel = onCancel) {
        val field = constant.field
        if (field != null) {
            val shown = if (constant.op == FilterOperator.IN && field.type == FieldType.CHOICE)
                field.copy(displayName = s.shared("phrase_constant_title"), config = field.config.orEmpty() + ("multiple" to true))
                else field.copy(displayName = s.shared("phrase_constant_title"))
            FieldInput(shown, value, { value = it }, context, required = false)
        } else {
            // Unknown field: its text, read back as a number when it is one
            UI.FormField(label = s.shared("phrase_constant_title"), value = value?.toString() ?: "", onChange = { text ->
                value = text.toLongOrNull() ?: text.toDoubleOrNull() ?: text
            }, required = false)
        }
    }
}
