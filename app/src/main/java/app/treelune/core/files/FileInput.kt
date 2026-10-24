package app.treelune.core.files

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
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
import androidx.compose.runtime.produceState
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
import app.treelune.core.fields.FileField
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The input of a FILE field (docs/DATA.md, « FILE »): its files in a row, each with buttons to
 * move it left or right and to take it off, and, while the field holds fewer than its max, a
 * button per way to add what it takes — a photo taken (IMAGE), a video filmed (VIDEO), a sound
 * recorded (AUDIO), each by the phone's own app, and a file chosen on the phone among the kinds
 * it takes. An image is reduced, unless « Keep the original » is ticked: the images added then are
 * kept byte for byte. A file added is set aside at once (files.prepare), until the entry is written; a file
 * taken off goes from the value, and the entry's write deletes it.
 *
 * The value is the list of ids, null once empty: no file is no answer.
 */
@Composable
fun FileInput(fieldDef: FieldDefinition, value: Any?, onChange: (Any?) -> Unit, context: Context) {
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    val ids = FileField.ids(value)
    // The value as it is when a file comes back set aside, which may be after other changes
    val current by rememberUpdatedState(ids)
    val max = FileField.max(fieldDef.config)
    val kinds = FileField.kinds(fieldDef.config)
    var open by remember { mutableStateOf<Int?>(null) }
    // The images added next kept whole, their metadata with them, rather than reduced
    var original by rememberSaveable { mutableStateOf(false) }

    fun changed(next: List<String>) = onChange(next.takeIf { it.isNotEmpty() })

    /** The file at [uri] set aside and added after the others, then [afterwards]. */
    fun add(uri: android.net.Uri, afterwards: () -> Unit = {}) {
        scope.launch {
            val result = Coordinator(context).processUserAction("files.prepare", mapOf("uri" to uri.toString(), "kinds" to kinds.map { it.name }, "original" to original))
            afterwards()
            if (result.isSuccess) {
                changed(current + (result.data!!["id"] as String))
                // An animated image keeps its first frame, which is said
                if (result.data!!["animated"] == true) UI.Toast(s.shared("image_animated_first_frame"), Duration.LONG)
            } else UI.Toast(result.error ?: "", Duration.LONG)
        }
    }

    // The camera writes into a file of the app's cache, handed to it by the FileProvider; the
    // file is kept until it is set aside, then deleted. Its path survives the app being stopped
    // while the camera is open.
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    fun captured(taken: Boolean) {
        val file = capturePath?.let { java.io.File(it) } ?: return
        capturePath = null
        if (taken) add(android.net.Uri.fromFile(file)) { file.delete() } else file.delete()
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured(it) }
    val videoCamera = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { captured(it) }
    val recorder = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { add(it) }
    }
    val visual = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) add(uri) }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) add(uri) }

    /** The phone's camera into a file of the cache ending with [extension], by [launch]. */
    fun capture(extension: String, launch: (android.net.Uri) -> Unit) {
        val file = java.io.File(context.cacheDir, "camera/${java.util.UUID.randomUUID()}.$extension").apply { parentFile?.mkdirs() }
        capturePath = file.path
        try {
            launch(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: android.content.ActivityNotFoundException) {
            capturePath = null
            file.delete()
            UI.Toast(s.shared("ai_image_no_camera"), Duration.LONG)
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Text(
                text = max?.let { s.shared("field_file_input_label_max").format(fieldDef.displayName, ids.size, it) } ?: fieldDef.displayName,
                type = TextType.LABEL
            )
            if (max == null || ids.size < max) {
                if (FileKind.IMAGE in kinds) UI.ActionButton(action = ButtonAction.PHOTO, display = ButtonDisplay.ICON, size = Size.S, onClick = {
                    capture("jpg") { camera.launch(it) }
                })
                if (FileKind.VIDEO in kinds) UI.ActionButton(action = ButtonAction.VIDEO, display = ButtonDisplay.ICON, size = Size.S, onClick = {
                    capture("mp4") { videoCamera.launch(it) }
                })
                if (FileKind.AUDIO in kinds) UI.ActionButton(action = ButtonAction.RECORD, display = ButtonDisplay.ICON, size = Size.S, onClick = {
                    try {
                        recorder.launch(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION))
                    } catch (e: android.content.ActivityNotFoundException) {
                        UI.Toast(s.shared("file_no_recorder"), Duration.LONG)
                    }
                })
                UI.ActionButton(action = ButtonAction.DEVICE, display = ButtonDisplay.ICON, size = Size.S, onClick = {
                    // The photo and video picker when the field takes nothing else, the phone's files otherwise
                    val media = when {
                        kinds.all { it == FileKind.IMAGE } -> ActivityResultContracts.PickVisualMedia.ImageOnly
                        kinds.all { it == FileKind.VIDEO } -> ActivityResultContracts.PickVisualMedia.VideoOnly
                        kinds.all { it == FileKind.IMAGE || it == FileKind.VIDEO } -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        else -> null
                    }
                    if (media != null) visual.launch(PickVisualMediaRequest(media)) else document.launch(FilePreparation.pickerTypes(kinds))
                })
            }
        }

        if (FileKind.IMAGE in kinds && (max == null || ids.size < max)) {
            UI.Checkbox(checked = original, onCheckedChange = { original = it }, label = s.shared("file_keep_original"))
        }

        if (ids.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.M)
            ) {
                ids.forEachIndexed { index, id ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                        FileTile(id, onOpen = { open = index })
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

    open?.let { index -> Opened(ids, index) { open = null } }
}

/** File [index] of [ids] opened over the screen: an image among the images, a video playing, a text to read. */
@Composable
private fun Opened(ids: List<String>, index: Int, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val found by produceState<List<Files.Found?>?>(initialValue = null, ids) {
        value = withContext(Dispatchers.IO) { ids.map { Files.inspect(context, it) } }
    }
    val all = found ?: return
    val touched = all.getOrNull(index) ?: run { androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }; return }
    when (touched.description.kind) {
        FileKind.IMAGE -> {
            val images = ids.filterIndexed { i, _ -> all[i]?.description?.kind == FileKind.IMAGE }
            ImageViewer(images, images.indexOf(ids[index]), onDismiss)
        }
        FileKind.VIDEO -> VideoViewer(touched.file, onDismiss)
        FileKind.TEXT -> TextViewer(touched.file, touched.description.name, onDismiss)
        // An audio plays in place, from its tile
        FileKind.AUDIO -> androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }
    }
}
