package app.treelune.core.ai.processing

import app.treelune.core.selection.TimePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * A period the model writes: a fixed bound in ISO 8601 reaches the app in milliseconds, which
 * is all TimeResolver reads (it refused « 2026-10-30T18:11:00+01:00 » given as text by TOOL_DATA);
 * a relative bound is kept as it is written.
 */
class ModelPeriodTest {

    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun aFixedBound_inIso_isMilliseconds() {
        val period = ModelPeriod.read(mapOf("start" to "2026-10-30T18:11:00+01:00"), paris) { it }
        assertEquals(TimePoint.Fixed(1_793_380_260_000L), period.start)
        assertEquals(null, period.end)
    }

    @Test
    fun aBoundWithoutOffset_isInTheAppsZone() {
        val stored = ModelPeriod.stored(mapOf("end" to "2026-10-30T18:11:00"), paris)
        assertEquals(1_793_380_260_000L, stored["end"])
    }

    @Test
    fun aRelativeBound_isKeptAsWritten() {
        val relative = mapOf("relative" to mapOf("unit" to "DAY", "offset" to 0, "edge" to "START"))
        val stored = ModelPeriod.stored(mapOf("start" to relative, "end" to mapOf("relative" to "NOW")), paris)
        assertEquals(relative, stored["start"])
        assertTrue(ModelPeriod.read(mapOf("end" to mapOf("relative" to "NOW")), paris) { it }.end is TimePoint.Now)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aBoundThatDoesNotRead_isRefused() {
        ModelPeriod.read(mapOf("start" to "yesterday"), paris) { it }
    }
}
