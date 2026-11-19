package app.treelune.core.files

import android.annotation.SuppressLint
import android.content.Context
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts

/** The pickers that take several files of the phone at once. */
object MultiplePicks {

    /**
     * Android's photo and video picker taking up to [room] files, or as many as it allows when
     * [room] is null or more than that. The picker refuses a limit past its own
     * (MediaStore.getPickImagesMaxLimit, where it exists) and a limit under 2: one place left
     * takes the single picker instead.
     */
    @SuppressLint("NewApi") // getPickImagesMaxLimit exists wherever the photo picker does, which is checked first
    fun visual(context: Context, room: Int?): ActivityResultContracts.PickMultipleVisualMedia {
        val limit = if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) MediaStore.getPickImagesMaxLimit() else Int.MAX_VALUE
        return if (room == null || room >= limit) ActivityResultContracts.PickMultipleVisualMedia()
        else ActivityResultContracts.PickMultipleVisualMedia(room.coerceAtLeast(2))
    }
}
