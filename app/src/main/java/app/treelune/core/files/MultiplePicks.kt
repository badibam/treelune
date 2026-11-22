package app.treelune.core.files

import android.annotation.SuppressLint
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
    @SuppressLint("NewApi") // getPickImagesMaxLimit is read only where systemLimit says it exists
    fun visual(room: Int?): ActivityResultContracts.PickMultipleVisualMedia {
        val limit = if (systemLimit()) MediaStore.getPickImagesMaxLimit() else Int.MAX_VALUE
        return if (room == null || room >= limit) ActivityResultContracts.PickMultipleVisualMedia()
        else ActivityResultContracts.PickMultipleVisualMedia(room.coerceAtLeast(2))
    }

    /**
     * Whether MediaStore.getPickImagesMaxLimit exists here: Android 13 and up, or Android 11 and 12
     * with the R extension 2, as androidx reads its own limit. The photo picker may be there
     * without it: on Android 10, Google Play brings its own (isPhotoPickerAvailable true), and
     * reading the limit fails.
     */
    private fun systemLimit(): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU ||
            (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
                android.os.ext.SdkExtensions.getExtensionVersion(android.os.Build.VERSION_CODES.R) >= 2)
}
