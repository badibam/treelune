package app.treelune.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The time zones and languages the format settings offer come from Java, not from a list of examples. */
class FormatChoicesTest {

    @Test
    fun `every zone of a place is offered with its offset, Lisbon among them, and no old alias`() {
        val (zones, labels) = AppSettings.timezones()
        assertTrue("Europe/Lisbon" in zones)
        assertTrue("UTC" in zones)
        assertFalse("US/Pacific" in zones)
        assertFalse("EST" in zones)
        assertTrue(labels.getValue("Europe/Lisbon").matches(Regex("""Europe/Lisbon \(UTC\+0[01]:00\)|Europe/Lisbon \(UTC\)""")))
        assertEquals("UTC (UTC)", labels.getValue("UTC"))
    }

    @Test
    fun `every language of a country is offered, Portuguese of Portugal among them, the ones offered before kept`() {
        val (locales, _) = AppSettings.locales()
        assertTrue("pt-PT" in locales)
        listOf("fr-FR", "en-US", "en-GB", "de-DE", "es-ES", "it-IT", "ja-JP", "zh-CN").forEach { assertTrue(it, it in locales) }
    }
}
