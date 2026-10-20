package app.treelune.core.backup

import app.treelune.core.backup.AutoBackupRotation.Slot
import app.treelune.core.backup.AutoBackupRotation.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** The rolling keeps four copies, each older than the one above, at the ages promised. */
class AutoBackupRotationTest {

    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour
    private val zone = ZoneId.of("Europe/Lisbon")

    /** The folder after a copy at [now]: [plan]'s steps applied, then the new hour. */
    private fun copy(now: Long, folder: MutableMap<Slot, Long>) {
        AutoBackupRotation.plan(now, folder).forEach { step ->
            when (step) {
                is Step.Delete -> folder.remove(step.slot)
                is Step.MoveDown -> {
                    assertFalse("${step.to} is taken when ${step.from} comes down", step.to in folder)
                    folder[step.to] = folder.remove(step.from)!!
                }
            }
        }
        folder[Slot.HOUR] = now
    }

    @Test
    fun `a copy every hour for two months keeps each slot older than the one above, within its ages`() {
        val folder = mutableMapOf<Slot, Long>()
        var now = 0L
        repeat(60 * 24) {
            now += hour
            copy(now, folder)
            if (now > 40 * day) {
                val age = folder.mapValues { now - it.value }
                assertTrue(age.getValue(Slot.HOUR) < hour)
                assertTrue(age.getValue(Slot.DAY) in hour..(day + hour))
                assertTrue(age.getValue(Slot.WEEK) in day..(8 * day + hour))
                assertTrue(age.getValue(Slot.MONTH) in (7 * day)..(38 * day + hour))
                assertTrue(age.getValue(Slot.HOUR) < age.getValue(Slot.DAY))
                assertTrue(age.getValue(Slot.DAY) < age.getValue(Slot.WEEK))
                assertTrue(age.getValue(Slot.WEEK) < age.getValue(Slot.MONTH))
            }
        }
    }

    @Test
    fun `a copy after a quiet week still finds the older ones`() {
        val folder = mutableMapOf(Slot.HOUR to 0L, Slot.DAY to -2 * day)
        copy(9 * day, folder)
        assertEquals(0L, folder[Slot.DAY])
        assertEquals(-2 * day, folder[Slot.WEEK])
        assertNull(folder[Slot.MONTH])
    }

    @Test
    fun `a copy is due without an hour copy, or with one an hour old`() {
        assertTrue(AutoBackupRotation.due(0L, emptyMap()))
        assertFalse(AutoBackupRotation.due(hour - 1, mapOf(Slot.HOUR to 0L)))
        assertTrue(AutoBackupRotation.due(hour, mapOf(Slot.HOUR to 0L)))
    }

    @Test
    fun `a name says its slot and its minute and reads back, another file of the folder being none of them`() {
        val at = java.time.LocalDateTime.of(2026, 10, 12, 14, 5).atZone(zone).toInstant().toEpochMilli()
        val name = AutoBackupRotation.name(Slot.DAY, at, zone)
        assertEquals("treelune_auto_day_2026-10-12_14h05.zip", name)
        assertEquals(Slot.DAY to at, AutoBackupRotation.parse(name, zone))
        assertNull(AutoBackupRotation.parse("treelune_backup_2026-10-12_v80.zip", zone))
        // A copy made twice in a minute, renamed by Android, is of the rolling all the same
        assertEquals(Slot.DAY to at, AutoBackupRotation.parse("treelune_auto_day_2026-10-12_14h05 (1).zip", zone))
    }
}
