package app.treelune.core.backup

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The rolling of the automatic backups: four files in the folder chosen, one per [Slot], each
 * older than the one above it. A new copy takes the hour; the copy it replaces goes down to the
 * day when the day is older than a day, the day's to the week when the week is older than a week,
 * the week's to the month when the month is older than a month — or else is deleted. A month on,
 * the hour is under an hour old, the day an hour to a day, the week a day to eight, the month
 * eight to thirty-eight.
 *
 * Pure: the folder's files come in as names, the moves go out as steps, applied by AutoBackup.
 */
object AutoBackupRotation {

    /** A place of the rolling, by how old its copy may get before the one above comes down into it. */
    enum class Slot(val key: String, val period: Long) {
        HOUR("hour", HOUR_MS),
        DAY("day", 24 * HOUR_MS),
        WEEK("week", 7 * 24 * HOUR_MS),
        MONTH("month", 30 * 24 * HOUR_MS)
    }

    /** A move in the folder: a copy deleted, or renamed into the slot below with its date kept. */
    sealed interface Step {
        data class Delete(val slot: Slot) : Step
        data class MoveDown(val from: Slot, val to: Slot) : Step
    }

    private const val PREFIX = "treelune_auto_"
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH'h'mm")
    // Android adds " (1)" to a file created under a name already taken: a copy made twice in a minute
    private val NAME = Regex("^${PREFIX}(hour|day|week|month)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}h\\d{2})( \\(\\d+\\))?\\.zip$")

    /** The name of the copy of [slot] made at [at]: "treelune_auto_day_2026-10-12_14h05.zip", in [zone]. */
    fun name(slot: Slot, at: Long, zone: ZoneId): String =
        "$PREFIX${slot.key}_${DATE.format(Instant.ofEpochMilli(at).atZone(zone))}.zip"

    /** The slot and the moment of a file of the rolling, or null for any other file of the folder. */
    fun parse(name: String, zone: ZoneId): Pair<Slot, Long>? {
        val match = NAME.matchEntire(name) ?: return null
        val slot = Slot.entries.first { it.key == match.groupValues[1] }
        val at = LocalDateTime.parse(match.groupValues[2], DATE).atZone(zone).toInstant().toEpochMilli()
        return slot to at
    }

    /** Whether a new copy is due at [now]: no hour copy, or one an hour old. */
    fun due(now: Long, existing: Map<Slot, Long>): Boolean =
        existing[Slot.HOUR]?.let { now - it >= Slot.HOUR.period } ?: true

    /**
     * What to do in the folder before a new copy takes the hour at [now], [existing] the moment
     * of each slot's copy: from the bottom up, so a slot is emptied before the one above comes down.
     */
    fun plan(now: Long, existing: Map<Slot, Long>): List<Step> {
        val steps = mutableListOf<Step>()
        /** Whether the copy of [slot], if any, gives way to the one above it. */
        fun givesWay(slot: Slot): Boolean = existing[slot]?.let { now - it >= slot.period } ?: true

        // The copy of [slot] leaves its place: it goes down into [slot]'s next if that one gives way, or is deleted
        fun leave(slot: Slot) {
            if (slot !in existing) return
            val below = Slot.entries.getOrNull(slot.ordinal + 1)
            if (below != null && givesWay(below)) {
                leave(below)
                steps += Step.MoveDown(slot, below)
            } else steps += Step.Delete(slot)
        }
        leave(Slot.HOUR)
        return steps
    }
}

private const val HOUR_MS = 60 * 60 * 1000L
