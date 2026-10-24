package app.treelune.core.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.treelune.core.strings.Strings
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.FullScreenDialog
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** The height of an image shown in place: a thumbnail, the whole image a touch away. */
val IMAGE_THUMBNAIL_HEIGHT: Dp = 96.dp

/** The height of an image shown large in place: a step's during a session. */
val IMAGE_LARGE_HEIGHT: Dp = 280.dp

/** The height of an image in a short form: a tile, a table's cell, a note's card. */
val IMAGE_SMALL_HEIGHT: Dp = 40.dp

/** What reading an image's file gave: its picture, or that its file is missing. */
private sealed class ImageLoad {
    object Loading : ImageLoad()
    object Missing : ImageLoad()
    class Loaded(val bitmap: Bitmap) : ImageLoad()
}

/**
 * The image [imageId] read from its file, wherever it is (Files.locate), decoded no larger than
 * [heightPx] needs (null: whole). A missing file is said as such: a row without its file is an
 * error, never hidden.
 */
@Composable
private fun rememberImage(imageId: String, heightPx: Int?): ImageLoad {
    val context = LocalContext.current
    val load by produceState<ImageLoad>(initialValue = ImageLoad.Loading, imageId, heightPx) {
        value = withContext(Dispatchers.IO) {
            val file = Files.locate(context, imageId)
            if (file == null) {
                LogManager.service("Image $imageId: its file is missing", "ERROR")
                return@withContext ImageLoad.Missing
            }
            val options = BitmapFactory.Options()
            if (heightPx != null) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outHeight / (sample * 2) >= heightPx) sample *= 2
                options.inSampleSize = max(1, sample)
            }
            BitmapFactory.decodeFile(file.path, options)?.let { ImageLoad.Loaded(it) } ?: ImageLoad.Missing
        }
    }
    return load
}

/**
 * Image [imageId] at [height], its width following; [onClick], if any, on a touch. A missing
 * file reads as such in its place.
 */
@Composable
fun ImageThumbnail(imageId: String, modifier: Modifier = Modifier, height: Dp = IMAGE_THUMBNAIL_HEIGHT, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val heightPx = with(LocalDensity.current) { height.roundToPx() }
    when (val load = rememberImage(imageId, heightPx)) {
        ImageLoad.Loading -> Box(modifier.height(height))
        ImageLoad.Missing -> UI.Text(s.shared("ai_image_missing"), TextType.ERROR)
        is ImageLoad.Loaded -> Image(
            bitmap = load.bitmap.asImageBitmap(),
            contentDescription = s.shared("ai_image_block"),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = modifier.height(height).let { if (onClick != null) it.clickable(onClick = onClick) else it }
        )
    }
}

/**
 * Images [ids] over the whole screen, from the one at [start]: a swipe goes from one to the
 * next, a touch or back closes.
 */
@Composable
fun ImageViewer(ids: List<String>, start: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (ids.size - 1).coerceAtLeast(0))) { ids.size }
    FullScreenDialog(onDismiss = onDismiss) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            Box(modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss).padding(UI.Space.M), contentAlignment = Alignment.Center) {
                when (val whole = rememberImage(ids[page], null)) {
                    ImageLoad.Loading -> {}
                    ImageLoad.Missing -> UI.Text(s.shared("ai_image_missing"), TextType.ERROR)
                    is ImageLoad.Loaded -> Image(
                        bitmap = whole.bitmap.asImageBitmap(),
                        contentDescription = s.shared("ai_image_block"),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Images [ids] in a row of thumbnails, scrolling sideways past the screen's width; a touched one
 * opens whole, the others a swipe away.
 */
@Composable
fun ImageRow(ids: List<String>, modifier: Modifier = Modifier) {
    var open by rememberSaveable(ids) { mutableStateOf<Int?>(null) }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S)
    ) {
        ids.forEachIndexed { index, id -> ImageThumbnail(id, onClick = { open = index }) }
    }
    open?.let { start -> ImageViewer(ids, start) { open = null } }
}

/**
 * Images [ids] in a short form (a tile, a table's cell, a note's card): the first one small, and
 * how many others there are (+3). Nothing to touch: the entry opens where it is shown.
 */
@Composable
fun ImageAbbreviation(ids: List<String>) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val first = ids.firstOrNull() ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.XS), verticalAlignment = Alignment.CenterVertically) {
        ImageThumbnail(first, height = IMAGE_SMALL_HEIGHT)
        if (ids.size > 1) UI.Text(s.shared("field_image_others").format(ids.size - 1), TextType.CAPTION)
    }
}
