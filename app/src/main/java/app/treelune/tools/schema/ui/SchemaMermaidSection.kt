package app.treelune.tools.schema.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.Reference
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.FieldType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.tools.schema.MermaidError
import app.treelune.tools.schema.MermaidImport
import app.treelune.tools.schema.SchemaKeys
import app.treelune.tools.schema.SchemaMermaid
import app.treelune.tools.schema.SchemaSpec
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The head of a schema's config (docs/design/schema.md, « La saisie »): a Mermaid text pasted or
 * typed, converted once, what it gives shown before it replaces anything; and the schema copied as
 * Mermaid. Accepting changes the config being edited: nothing is stored before the form is saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchemaMermaidSection(config: JSONObject, onChange: (JSONObject) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = remember { Strings.`for`(tool = "schema", context = context) }
    var importing by rememberSaveable { mutableStateOf(false) }
    val hasContent = (config.optJSONArray(SchemaKeys.NODES)?.length() ?: 0) > 0

    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(s.tool("section_mermaid"), TextType.SUBTITLE)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Button(type = ButtonType.DEFAULT, onClick = { importing = true }) { UI.Text(s.tool("mermaid_import"), TextType.LABEL) }
                UI.Button(
                    type = ButtonType.DEFAULT,
                    state = if (hasContent) ComponentState.NORMAL else ComponentState.DISABLED,
                    onClick = {
                        scope.launch {
                            val spec = try {
                                SchemaSpec.of(config) { s.tool(it) }
                            } catch (e: IllegalArgumentException) {
                                UI.Toast(e.message.orEmpty())
                                return@launch
                            }
                            val names = thingNames(spec.nodes.mapNotNull { it.thing }, context)
                            if (names == null) UI.Toast(s.tool("mermaid_names_failed"))
                            else UI.copy(context, SchemaMermaid { s.tool(it) }.write(spec) { names[it] })
                        }
                    }
                ) { UI.Text(s.tool("mermaid_copy"), TextType.LABEL) }
            }
        }
    }

    if (importing) ImportDialog(config, s, onAccept = { accepted ->
        onChange(accepted.applyTo(config))
        importing = false
    }, onCancel = { importing = false })
}

/** What the text typed so far gives. */
private sealed interface Preview {
    data class Read(val result: MermaidImport) : Preview
    data class Refused(val message: String) : Preview
}

@Composable
private fun ImportDialog(config: JSONObject, s: StringsContext, onAccept: (MermaidImport) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    // The current names of the boxes' links, without which a box written by its name cannot be told
    var names by remember { mutableStateOf<Map<Reference, String?>?>(null) }
    var namesFailed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val array = config.optJSONArray(SchemaKeys.NODES)
        val things = (0 until (array?.length() ?: 0)).mapNotNull { i -> array?.optJSONObject(i)?.opt(SchemaKeys.THING)?.let { ReferenceTarget.referenceOf(it) } }
        names = thingNames(things, context)
        namesFailed = names == null
    }
    val known = names
    val preview = remember(text, known, config.toString()) {
        if (text.isBlank() || known == null) null else try {
            Preview.Read(SchemaMermaid { s.tool(it) }.read(text, config) { known[it] })
        } catch (e: MermaidError) {
            Preview.Refused(e.message.orEmpty())
        }
    }
    UI.Dialog(
        type = DialogType.CONFIGURE,
        onConfirm = { (preview as? Preview.Read)?.let { onAccept(it.result) } },
        onCancel = onCancel,
        confirmEnabled = preview is Preview.Read
    ) {
        Column(
            modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.M)
        ) {
            // What the text gives comes first: a long text pasted must not push it out of sight
            when (preview) {
                null -> UI.Text(s.tool(if (namesFailed) "mermaid_names_failed" else "mermaid_hint"), if (namesFailed) TextType.ERROR else TextType.CAPTION)
                is Preview.Refused -> UI.Text(preview.message, TextType.ERROR)
                is Preview.Read -> {
                    UI.Text(summary(preview.result, s), TextType.BODY)
                    preview.result.notes.forEach { UI.Text(it, TextType.CAPTION) }
                    UI.Text(s.tool("mermaid_hint"), TextType.CAPTION)
                }
            }
            Box(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                UI.FormField(label = s.tool("mermaid_field"), value = text, onChange = { text = it }, fieldType = FieldType.TEXT_UNLIMITED, required = true)
            }
        }
    }
}

/** « 12 boîtes, 15 flèches, 2 groupes », a group being a box that holds others, counted apart. */
private fun summary(result: MermaidImport, s: StringsContext): String {
    fun count(n: Int, one: String, many: String) = if (n == 1) s.tool(one) else s.tool(many).format(n)
    return s.tool("mermaid_summary").format(
        count(result.nodes.length() - result.holders, "count_box_one", "count_boxes"),
        count(result.edges.length(), "count_arrow_one", "count_arrows"),
        count(result.holders, "count_group_one", "count_groups")
    )
}
