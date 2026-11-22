package app.treelune.tools.sequence

import app.treelune.core.fields.FileField
import org.json.JSONObject
import kotlin.random.Random

/**
 * The background sound chosen for a session (docs/design/sequence-background.md): its linked
 * files, whether they play shuffled and in a loop, and its volume, a share of the phone's media
 * volume from 0 to 100.
 */
data class BackgroundChoice(val files: List<String>, val shuffle: Boolean, val loop: Boolean, val volume: Int) {

    /** What the session's entry keeps of it; nothing without a file. */
    fun toData(): Map<String, Any> = if (files.isEmpty()) emptyMap() else mapOf(
        SequenceToolType.BACKGROUND to files,
        SequenceToolType.BACKGROUND_SHUFFLE to shuffle,
        SequenceToolType.BACKGROUND_LOOP to loop,
        SequenceToolType.BACKGROUND_VOLUME to volume
    )

    /** Its values in the launch's window, under the keys of an entry. */
    fun toValues(): Map<String, Any?> = mapOf(
        SequenceToolType.BACKGROUND to files.takeIf { it.isNotEmpty() },
        SequenceToolType.BACKGROUND_SHUFFLE to shuffle,
        SequenceToolType.BACKGROUND_LOOP to loop,
        SequenceToolType.BACKGROUND_VOLUME to volume
    )

    companion object {
        /** What the config proposes at launch. */
        fun ofConfig(config: JSONObject) = BackgroundChoice(
            FileField.ids(config.opt(SequenceToolType.BACKGROUND_FILES)),
            config.optBoolean(SequenceToolType.BACKGROUND_SHUFFLE, false),
            config.optBoolean(SequenceToolType.BACKGROUND_LOOP, true),
            config.optInt(SequenceToolType.BACKGROUND_VOLUME, SequenceToolType.DEFAULT_BACKGROUND_VOLUME)
        )

        /** What a launch's window answered or an entry keeps, under the same keys; null without a file. */
        fun of(values: JSONObject): BackgroundChoice? {
            val files = FileField.ids(values.opt(SequenceToolType.BACKGROUND)).takeIf { it.isNotEmpty() } ?: return null
            return BackgroundChoice(files,
                values.optBoolean(SequenceToolType.BACKGROUND_SHUFFLE, false),
                values.optBoolean(SequenceToolType.BACKGROUND_LOOP, true),
                values.optInt(SequenceToolType.BACKGROUND_VOLUME, SequenceToolType.DEFAULT_BACKGROUND_VOLUME))
        }

        /**
         * One tour of [items]: in their order, or shuffled, never beginning with [last], the one
         * that just finished, when there is another to begin with.
         */
        fun <T> tour(items: List<T>, shuffle: Boolean, last: T?, random: Random = Random.Default): List<T> {
            if (!shuffle) return items
            val order = items.shuffled(random).toMutableList()
            if (order.size > 1 && order.first() == last) {
                val other = order.indexOfFirst { it != last }
                if (other > 0) { order[other] = order[0].also { order[0] = order[other] } }
            }
            return order
        }
    }
}
