package app.treelune.tools.sequence

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import app.treelune.core.files.Files
import app.treelune.core.utils.LogManager

/**
 * The background sound of a running session, beside the step's sound and followed by the same
 * service: its own position, through the changes of step, paused with the session, screen on or
 * off, in by a fade and out by one. A file that does not play is skipped; none playing, the
 * session goes on without it. Everything but [open] runs on the main thread, where a player's
 * callbacks come, and a file is prepared without blocking it.
 */
class SequenceBackground private constructor(
    private val context: Context,
    private val sources: List<Uri>,
    private val choice: BackgroundChoice
) {
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var prepared = false
    private var order = emptyList<Uri>()
    private var place = 0
    /** The files failed in a row: all of them, and it stops */
    private var failed = 0
    private var paused = false
    private var volume = choice.volume
    /** The fade's share of the volume now, and where it is going */
    private var gain = 0f
    private var aim = 0f
    private var closing = false
    private var released = false

    private val fade = object : Runnable {
        override fun run() {
            val step = FADE_TICK.toFloat() / FADE_MS
            gain = if (gain < aim) (gain + step).coerceAtMost(aim) else (gain - step).coerceAtLeast(aim)
            applyVolume()
            if (gain != aim) main.postDelayed(this, FADE_TICK)
            else if (closing) releaseNow()
        }
    }

    private fun begin() {
        order = BackgroundChoice.tour(sources, choice.shuffle, null)
        place = 0
        play()
    }

    private fun play() {
        if (released) return
        player?.release()
        player = null
        prepared = false
        val uri = order[place]
        val made = MediaPlayer()
        try {
            made.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            made.setDataSource(context, uri)
            // A single file loops on itself; several start again once all are played (next)
            made.isLooping = choice.loop && sources.size == 1
            made.setOnPreparedListener { ready ->
                if (ready !== player) return@setOnPreparedListener
                prepared = true
                failed = 0
                applyVolume()
                if (!paused) ready.start()
                if (!closing && aim == 0f) fadeTo(1f)
            }
            made.setOnCompletionListener { done -> if (done === player) next() }
            made.setOnErrorListener { broken, what, extra ->
                if (broken === player) {
                    LogManager.service("Sequence: a background file stopped playing ($what, $extra)", "WARN")
                    skip()
                }
                true
            }
            player = made
            made.prepareAsync()
        } catch (e: Exception) {
            // Gone since the session began, or a format this phone does not read
            LogManager.service("Sequence: a background file not played: ${e.message}", "WARN")
            if (player === made) player = null
            made.release()
            main.post { skip() }
        }
    }

    /** The next file, a new tour after the last when it loops; without a loop, the sound ends there. */
    private fun next() {
        if (place + 1 < order.size) place++
        else if (choice.loop) {
            order = BackgroundChoice.tour(sources, choice.shuffle, order.last())
            place = 0
        } else {
            player?.release()
            player = null
            prepared = false
            return
        }
        play()
    }

    private fun skip() {
        if (++failed >= sources.size) {
            LogManager.service("Sequence: no background file plays, the session goes on without", "WARN")
            player?.release()
            player = null
            prepared = false
            return
        }
        next()
    }

    private fun fadeTo(target: Float) {
        aim = target
        main.removeCallbacks(fade)
        main.post(fade)
    }

    /** Squared: a slider's middle then sounds about half as loud, which a linear gain does not. */
    private fun applyVolume() {
        val level = volume / 100f * gain
        player?.takeIf { prepared }?.setVolume(level * level, level * level)
    }

    private fun releaseNow() {
        released = true
        main.removeCallbacks(fade)
        player?.release()
        player = null
        prepared = false
    }

    /** Paused with the session, or playing again from where it stood. */
    fun follow(sessionPaused: Boolean) = main.post {
        if (sessionPaused == paused || closing) return@post
        paused = sessionPaused
        val current = player?.takeIf { prepared } ?: return@post
        if (paused) { if (current.isPlaying) current.pause() } else current.start()
    }

    /** Its volume changed from the session's screen, for this session. */
    fun setVolume(value: Int) = main.post {
        volume = value
        applyVolume()
    }

    /** The session is over: out by its fade, at once if it is not sounding. */
    fun finish() = main.post {
        if (released || closing) return@post
        closing = true
        if (player?.takeIf { prepared }?.isPlaying == true) fadeTo(0f) else releaseNow()
    }

    /** Stopped and let go. */
    fun release() = main.post { releaseNow() }

    companion object {
        /** How long it takes to come in and go out. */
        private const val FADE_MS = 3_000L
        private const val FADE_TICK = 100L

        /**
         * The sound of [choice] started, from the first of a new order, on those of its files the
         * phone has; null when it has none of them.
         */
        suspend fun open(context: Context, choice: BackgroundChoice): SequenceBackground? {
            val sources = choice.files.mapNotNull { id ->
                Files.locate(context, id)?.uri ?: run {
                    LogManager.service("Sequence: the background file $id is not on this phone", "WARN")
                    null
                }
            }
            if (sources.isEmpty()) return null
            return SequenceBackground(context.applicationContext, sources, choice).also { sound -> sound.main.post { sound.begin() } }
        }
    }
}
