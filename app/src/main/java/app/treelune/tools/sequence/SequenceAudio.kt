package app.treelune.tools.sequence

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import app.treelune.core.files.Files
import app.treelune.core.utils.LogManager

/**
 * The sound of the current step, played by the service that runs the session, screen on or off
 * (docs/TOOLS.md, « Session (Sequence) »). It follows the run as the service reads it: it plays
 * from where the step's clock stands once the step has begun, pauses with the session, starts
 * again from the right place after a `back` or a `restart`, and stops when the step is left. A
 * video is the session's screen's to play.
 */
class SequenceAudio(private val context: Context) {

    private var player: MediaPlayer? = null
    /** The step the player was made for: its place in the run and its start, which `back` and `restart` change */
    private var playing: Pair<Int, Long>? = null

    /** The sound [run] calls for at [now], played or paused as it says. */
    suspend fun follow(run: SequenceRun, now: Long) {
        val media = run.current.media?.takeIf { !it.isVideo }
        val elapsed = run.elapsed(now)
        val key = run.position to run.stepStart
        if (media == null || elapsed < 0 || run.held || elapsed >= media.durationMs - END_MARGIN) {
            if (media == null || key != playing) stop()
            else player?.takeIf { it.isPlaying }?.pause()
            return
        }
        if (key != playing) {
            stop()
            val file = Files.locate(context, media.id) ?: run {
                LogManager.service("Sequence: the sound ${media.id} of step ${run.position} is missing", "ERROR")
                return
            }
            player = try {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(file.path)
                    prepare()
                    seekTo(elapsed.toInt())
                }
            } catch (e: Exception) {
                LogManager.service("Sequence: the sound ${media.id} not played: ${e.message}", "ERROR", e)
                null
            }
            playing = key
        }
        val current = player ?: return
        if (run.isPaused) {
            if (current.isPlaying) current.pause()
        } else if (!current.isPlaying) {
            // Back from a pause, from where the step's clock stands
            current.seekTo(elapsed.toInt())
            current.start()
        }
    }

    private companion object {
        /** So near its end, a sound that stopped is let be, rather than played again for its last moment */
        const val END_MARGIN = 500L
    }

    /** The sound stopped and let go. */
    fun stop() {
        player?.release()
        player = null
        playing = null
    }
}
