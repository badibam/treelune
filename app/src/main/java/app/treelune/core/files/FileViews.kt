package app.treelune.core.files

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.FullScreenDialog
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * The kept files on screen (docs/DATA.md, « FILE »), whatever their kind: an image as its
 * thumbnail, a video as a frame of it, an audio as its length with a button that plays it, a text
 * as its name and its lines. A touched image opens whole with the other images, a video plays
 * over the screen, a text opens to be read.
 */

/** What reading a kept file gave: where it is and what it is, or that it is missing. */
private sealed class FileLoad {
    object Loading : FileLoad()
    object Missing : FileLoad()
    class Loaded(val found: Files.Found) : FileLoad()
}

/** The file [id] found wherever it is (Files.inspect); a missing one is said, never hidden. */
@Composable
private fun rememberFile(id: String): FileLoad {
    val context = LocalContext.current
    val load by produceState<FileLoad>(initialValue = FileLoad.Loading, id) {
        value = withContext(Dispatchers.IO) {
            Files.inspect(context, id)?.let { FileLoad.Loaded(it) } ?: run {
                LogManager.service("File $id: missing", "ERROR")
                FileLoad.Missing
            }
        }
    }
    return load
}

/** A length as a clock reads it: 3:07, 1:02:45. */
fun clock(durationMs: Long): String {
    val seconds = durationMs / 1000
    val (h, m, s) = Triple(seconds / 3600, seconds / 60 % 60, seconds % 60)
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** What a file opened from a row shows over the screen. */
private sealed class Opened {
    class Images(val ids: List<String>, val start: Int) : Opened()
    class Video(val file: File) : Opened()
    class Text(val file: File, val name: String?) : Opened()
}

/**
 * File [id] at [height]: an image's thumbnail, a video's frame, an audio's length and its play
 * button, a text's name and lines; [onOpen] on a touch, but for an audio, which plays in place.
 */
@Composable
fun FileTile(id: String, height: Dp = IMAGE_THUMBNAIL_HEIGHT, onOpen: ((Files.Found) -> Unit)? = null) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    when (val load = rememberFile(id)) {
        FileLoad.Loading -> Box(Modifier.height(height))
        FileLoad.Missing -> UI.Text(s.shared("file_missing"), TextType.ERROR)
        is FileLoad.Loaded -> {
            val found = load.found
            val description = found.description
            val open = onOpen?.let { { it(found) } }
            when (description.kind) {
                FileKind.IMAGE -> ImageThumbnail(id, height = height, onClick = open)
                FileKind.VIDEO -> VideoFrame(found.file, description.durationMs, height, open)
                FileKind.AUDIO -> Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.XS), verticalAlignment = Alignment.CenterVertically) {
                    AudioButton(found.file)
                    UI.Icon("audio-lines", contentDescription = s.shared("file_kind_audio"))
                    UI.Text(listOfNotNull(description.name, description.durationMs?.let(::clock)).joinToString(" · "), TextType.CAPTION)
                }
                FileKind.TEXT -> Row(
                    modifier = if (open != null) Modifier.clickable(onClick = open) else Modifier,
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.XS),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UI.Icon("file-text", contentDescription = s.shared("file_kind_text"))
                    UI.Text(listOfNotNull(description.name, description.lineCount?.let { s.shared("file_line_count").format(it) }).joinToString(" · "), TextType.CAPTION)
                }
            }
        }
    }
}

/** A video's first frame at [height], its length beside it; [onClick] on a touch. */
@Composable
private fun VideoFrame(file: File, durationMs: Long?, height: Dp, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val frame by produceState<Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.path)
                retriever.frameAtTime
            } catch (e: RuntimeException) {
                LogManager.service("Video ${file.name}: no frame read: ${e.message}", "WARN")
                null
            } finally {
                retriever.release()
            }
        }
    }
    Row(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        horizontalArrangement = Arrangement.spacedBy(UI.Space.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        frame?.let { Image(it.asImageBitmap(), contentDescription = s.shared("file_kind_video"), contentScale = ContentScale.Fit, modifier = Modifier.height(height)) }
            ?: Box(Modifier.height(height)) { UI.Icon("video", contentDescription = s.shared("file_kind_video")) }
        UI.Icon("play", contentDescription = null)
        durationMs?.let { UI.Text(clock(it), TextType.CAPTION) }
    }
}

/** A button that plays the audio [file] and pauses it, its player let go when it leaves the screen. */
@Composable
fun AudioButton(file: File) {
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    DisposableEffect(file) { onDispose { player?.release() } }
    UI.ActionButton(action = if (playing) ButtonAction.PAUSE else ButtonAction.START, display = ButtonDisplay.ICON, size = Size.S) {
        val current = player ?: MediaPlayer().apply {
            setDataSource(file.path)
            prepare()
            setOnCompletionListener { playing = false; seekTo(0) }
        }.also { player = it }
        if (playing) current.pause() else current.start()
        playing = !playing
    }
}

/** The video [file] over the whole screen, playing, with the phone's controls; back closes. */
@Composable
fun VideoViewer(file: File, onDismiss: () -> Unit) {
    FullScreenDialog(onDismiss = onDismiss) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoPath(file.path)
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    setOnPreparedListener { start() }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** The lines shown of a text opened, the rest said by its count: enough to read, never a whole book at once. */
private const val TEXT_SHOWN_LINES = 2000

/** The text [file], named [name], over the whole screen to be read; back closes. */
@Composable
fun TextViewer(file: File, name: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val content by produceState<FileLines.Part?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { FileLines.window(file.readText(Charsets.UTF_8), 1, null) }
    }
    FullScreenDialog(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            name?.let { UI.Text(it, TextType.HEADING) }
            content?.let { part ->
                val lines = part.text.lines()
                UI.Text(lines.take(TEXT_SHOWN_LINES).joinToString("\n"), TextType.BODY)
                if (lines.size > TEXT_SHOWN_LINES) UI.Text(s.shared("file_more_lines").format(lines.size - TEXT_SHOWN_LINES), TextType.CAPTION)
            }
            UI.ActionButton(action = ButtonAction.BACK, onClick = onDismiss)
        }
    }
}

/**
 * Files [ids] in a row, scrolling sideways past the screen's width: a touched image opens whole,
 * the other images a swipe away; a video plays over the screen; a text opens; an audio plays in place.
 */
@Composable
fun FileRow(ids: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var opened by remember(ids) { mutableStateOf<Opened?>(null) }
    // The kind of each, for the images to open among themselves
    val kinds by produceState(emptyMap<String, FileKind>(), ids) {
        value = withContext(Dispatchers.IO) { ids.mapNotNull { id -> Files.inspect(context, id)?.let { id to it.description.kind } }.toMap() }
    }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.M),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ids.forEach { id -> FileTile(id, onOpen = { found -> opened = open(id, found, ids.filter { kinds[it] == FileKind.IMAGE }) }) }
    }
    Shown(opened) { opened = null }
}

/** What touching file [id], [found], opens: an image among [images], the row's, from itself. */
private fun open(id: String, found: Files.Found, images: List<String>): Opened? = when (found.description.kind) {
    FileKind.IMAGE -> images.indexOf(id).let { start -> if (start < 0) Opened.Images(listOf(id), 0) else Opened.Images(images, start) }
    FileKind.VIDEO -> Opened.Video(found.file)
    FileKind.TEXT -> Opened.Text(found.file, found.description.name)
    FileKind.AUDIO -> null
}

/** What is opened over the screen, closed by [onDismiss]. */
@Composable
private fun Shown(opened: Opened?, onDismiss: () -> Unit) {
    when (opened) {
        is Opened.Images -> ImageViewer(opened.ids, opened.start, onDismiss)
        is Opened.Video -> VideoViewer(opened.file, onDismiss)
        is Opened.Text -> TextViewer(opened.file, opened.name, onDismiss)
        null -> Unit
    }
}

/**
 * Files [ids] in a short form (a tile, a table's cell, a note's card): the first one small, and
 * how many others there are (+3). Nothing to touch: the entry opens where it is shown.
 */
@Composable
fun FileAbbreviation(ids: List<String>) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val first = ids.firstOrNull() ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.XS), verticalAlignment = Alignment.CenterVertically) {
        FileTile(first, height = IMAGE_SMALL_HEIGHT)
        if (ids.size > 1) UI.Text(s.shared("field_image_others").format(ids.size - 1), TextType.CAPTION)
    }
}
