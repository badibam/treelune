package app.treelune.tools.sequence.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldType
import app.treelune.core.files.FileInput
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.UI
import app.treelune.tools.sequence.SequenceLive
import app.treelune.tools.sequence.SequenceToolType

/** The background's files, as a field: a linked field of sounds, in the config, at launch and in the history. */
internal fun backgroundFilesField(s: StringsContext) =
    FieldDefinition(SequenceToolType.BACKGROUND, s.tool("field_background"), null, FieldType.FILE, false, SequenceToolType.BACKGROUND_FILE_CONFIG)

private fun volumeField(s: StringsContext) =
    FieldDefinition(SequenceToolType.BACKGROUND_VOLUME, s.tool("field_background_volume"), null, FieldType.SCALE, false, SequenceToolType.BACKGROUND_VOLUME_CONFIG)

/**
 * The background of the session about to start (docs/design/sequence-background.md): its files,
 * the browser opening at the folder of the first one the phone has, and right under them
 * « Shuffle », « Loop » and the volume. [values] are for this session alone.
 */
@Composable
internal fun BackgroundInput(values: Map<String, Any?>, onChange: (Map<String, Any?>) -> Unit, s: StringsContext) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(s.tool("field_background"), app.treelune.core.ui.TextType.LABEL)
        FileInput(backgroundFilesField(s), values[SequenceToolType.BACKGROUND], { onChange(values + (SequenceToolType.BACKGROUND to it)) }, context, browseFromFirst = true)
        UI.BooleanField(s.tool("field_background_shuffle"), values[SequenceToolType.BACKGROUND_SHUFFLE] == true, { onChange(values + (SequenceToolType.BACKGROUND_SHUFFLE to it)) })
        UI.BooleanField(s.tool("field_background_loop"), values[SequenceToolType.BACKGROUND_LOOP] == true, { onChange(values + (SequenceToolType.BACKGROUND_LOOP to it)) })
        FieldInput(volumeField(s), values[SequenceToolType.BACKGROUND_VOLUME], { onChange(values + (SequenceToolType.BACKGROUND_VOLUME to it)) }, context, required = true)
    }
}

/** The background's volume on the session's screen: heard at once, for this session, nothing written. */
@Composable
internal fun BackgroundVolume(entry: SequenceEntry, s: StringsContext) {
    val context = LocalContext.current
    val live by SequenceLive.backgroundVolume.collectAsState()
    FieldInput(volumeField(s), live ?: entry.backgroundVolume ?: SequenceToolType.DEFAULT_BACKGROUND_VOLUME,
        { value -> (value as? Number)?.let { SequenceLive.setBackgroundVolume(it.toInt()) } }, context, required = true)
}
