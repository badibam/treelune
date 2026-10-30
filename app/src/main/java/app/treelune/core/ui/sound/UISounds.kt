package app.treelune.core.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.LogManager

/**
 * The player of the interface sounds: the signal a component sends, the sound the current theme
 * answers it with, played when the app's setting allows (AppSettings.UI_SOUNDS).
 *
 * One track only: a sound cuts the one still playing, so that quick touches never pile up.
 * The theme's sounds are loaded ahead (UISoundsLoader, at the app's root), as one loaded at its
 * first touch would play nothing then.
 *
 * A sound that stays silent is logged with what explains it (never loaded, refused by the
 * decoder, played before its decoding ended, refused by the pool): SoundPool says none of it on
 * its own, and a bug report from another phone has only the log to tell it.
 */
object UISounds {

    private val pool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .build()
            .apply {
                // Decoding happens in the background: a sound is playable only once it ends well
                setOnLoadCompleteListener { _, sample, status ->
                    if (status == 0) ready.add(sample)
                    else LogManager.ui("UISounds: ${names[sample]} could not be decoded (status $status)", "ERROR")
                }
            }
    }

    /** The sounds loaded, by the theme's raw resource. */
    private val loaded = mutableMapOf<Int, Int>()

    /** The pool's samples whose decoding ended well; written by the pool's callback thread. */
    private val ready = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    /** Each sample's resource name, for the log. */
    private val names = java.util.concurrent.ConcurrentHashMap<Int, String>()

    /** The themes whose sounds were loaded, in order, for the log of a sound never loaded. */
    private val themesLoaded = mutableListOf<String>()

    /** Loads every sound of the current theme not loaded yet. */
    fun load(context: Context) {
        val app = context.applicationContext
        themesLoaded.add(CurrentTheme.themeId)
        UISignal.entries.mapNotNull { CurrentTheme.current.sound(it) }.distinct().filter { it !in loaded }
            .forEach { resource ->
                val name = app.resources.getResourceEntryName(resource)
                val sample = pool.load(app, resource, 1)
                if (sample == 0) {
                    LogManager.ui("UISounds: $name could not be loaded", "ERROR")
                    return@forEach
                }
                names[sample] = name
                loaded[resource] = sample
            }
    }

    /** Plays the current theme's sound for [signal], if it has one and sounds are on. */
    fun play(signal: UISignal) {
        if (!AppConfigManager.getUISounds()) return
        val resource = CurrentTheme.current.sound(signal) ?: return
        val sound = loaded[resource]
        if (sound == null) {
            LogManager.ui("UISounds: the sound for $signal of the theme ${CurrentTheme.themeId} was never loaded (themes loaded: ${themesLoaded.joinToString().ifEmpty { "none" }})", "ERROR")
            return
        }
        if (sound !in ready) {
            LogManager.ui("UISounds: ${names[sound]} played before its decoding ended", "WARN")
            return
        }
        if (pool.play(sound, 1f, 1f, 1, 0, 1f) == 0) LogManager.ui("UISounds: ${names[sound]} refused by the player", "WARN")
    }
}

/** Loads the current theme's sounds, again whenever the theme changes. Placed once, at the app's root. */
@Composable
fun UISoundsLoader() {
    val context = LocalContext.current
    val theme = CurrentTheme.current
    LaunchedEffect(theme) { UISounds.load(context) }
}

/** What a composable sends a signal with. */
@Composable
fun rememberUISound(): (UISignal) -> Unit = remember { { signal: UISignal -> UISounds.play(signal) } }
