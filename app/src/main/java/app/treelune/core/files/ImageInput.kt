package app.treelune.core.files

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.ImageField
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import kotlinx.coroutines.launch

/**
 * The input of an IMAGE field (docs/DATA.md, « IMAGE »): its images in a row, each with
 * buttons to move it left or right and to take it off, and the phone's camera and photo picker,
 * as in the composer, while the field holds fewer than its max. A photo added is prepared at once
 * (files.prepare_image), its file set aside until the entry is written; an image taken off goes
 * from the value, and the entry's write deletes it.
 *
 * The value is the list of ids, null once empty: no image is no answer.
 */
@Composable
fun ImageInput(fieldDef: FieldDefinition, value: Any?, onChange: (Any?) -> Unit, context: Context) {
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    val ids = ImageField.ids(value)
    // The value as it is when a photo comes back prepared, which may be after other changes
    val current by rememberUpdatedState(ids)
    val max = ImageField.max(fieldDef.config)
    var open by rememberSaveable { mutableStateOf<Int?>(null) }

    fun changed(next: List<String>) = onChange(next.takeIf { it.isNotEmpty() })

    /** The photo at [uri] prepared and added after the others, then [afterwards]. */
    fun add(uri: android.net.Uri, afterwards: () -> Unit = {}) {
        scope.launch {
            val result = Coordinator(context).processUserAction("files.prepare_image", mapOf("uri" to uri.toString()))
            afterwards()
            if (result.isSuccess) {
                changed(current + (result.data!!["id"] as String))
                // An animated image keeps its first frame, which is said
                if (result.data!!["animated"] == true) UI.Toast(s.shared("image_animated_first_frame"), Duration.LONG)
            } else UI.Toast(result.error ?: "", Duration.LONG)
        }
    }

    // The camera writes into a file of the app's cache, handed to it by the FileProvider; the
    // file is kept until the photo is prepared, then deleted. Its path survives the app being
    // stopped while the camera is open.
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = capturePath?.let { java.io.File(it) } ?: return@rememberLauncherForActivityResult
        capturePath = null
        if (taken) add(android.net.Uri.fromFile(file)) { file.delete() } else file.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) add(uri)
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Text(
                text = max?.let { s.shared("field_image_input_label_max").format(fieldDef.displayName, ids.size, it) } ?: fieldDef.displayName,
                type = TextType.LABEL
            )
            if (max == null || ids.size < max) {
                UI.ActionButton(
                    action = ButtonAction.PHOTO,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = {
                        val file = java.io.File(context.cacheDir, "camera/${java.util.UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
                        capturePath = file.path
                        try {
                            camera.launch(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                        } catch (e: android.content.ActivityNotFoundException) {
                            capturePath = null
                            file.delete()
                            UI.Toast(s.shared("ai_image_no_camera"), Duration.LONG)
                        }
                    }
                )
                UI.ActionButton(
                    action = ButtonAction.GALLERY,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                )
            }
        }

        if (ids.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.M)
            ) {
                ids.forEachIndexed { index, id ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                        ImageThumbnail(id, onClick = { open = index })
                        Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                            UI.ActionButton(
                                action = ButtonAction.LEFT,
                                display = ButtonDisplay.ICON,
                                size = Size.S,
                                enabled = index > 0,
                                onClick = { changed(ids.toMutableList().apply { add(index - 1, removeAt(index)) }) }
                            )
                            UI.ActionButton(
                                action = ButtonAction.DELETE,
                                display = ButtonDisplay.ICON,
                                size = Size.S,
                                onClick = { changed(ids - id) }
                            )
                            UI.ActionButton(
                                action = ButtonAction.RIGHT,
                                display = ButtonDisplay.ICON,
                                size = Size.S,
                                enabled = index < ids.lastIndex,
                                onClick = { changed(ids.toMutableList().apply { add(index + 1, removeAt(index)) }) }
                            )
                        }
                    }
                }
            }
        }
    }

    open?.let { start -> ImageViewer(ids, start) { open = null } }
}
