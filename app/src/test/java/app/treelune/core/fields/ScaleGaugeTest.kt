package app.treelune.core.fields

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the gauge an entry copied draws for a scale: its marks follow the value, whatever the bounds. */
class ScaleGaugeTest {

    @Test
    fun marksFollowTheValue() {
        assertEquals("[=======|---]", scaleGauge(7.0, 0.0, 10.0))
        assertEquals("[|----------]", scaleGauge(0.0, 0.0, 10.0))
        assertEquals("[==========|]", scaleGauge(10.0, 0.0, 10.0))
    }

    /** Ten marks long on any scale: a scale from 1 to 5 puts 3 in the middle. */
    @Test
    fun tenMarksOnAnyScale() {
        assertEquals("[=====|-----]", scaleGauge(3.0, 1.0, 5.0))
        assertEquals("[=====|-----]", scaleGauge(50.0, 0.0, 100.0))
    }

    /** A value stored before the bounds narrowed stands at the nearest end, never past it. */
    @Test
    fun aValueOutsideTheBoundsStandsAtTheEnd() {
        assertEquals("[==========|]", scaleGauge(14.0, 0.0, 10.0))
        assertEquals("[|----------]", scaleGauge(-2.0, 0.0, 10.0))
    }
}
