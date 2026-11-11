package app.treelune.core.terms

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A constant keeps the value it was written with, whatever its type: a number as before, a text,
 * several options; the terms stored as bare numbers read as they did.
 */
class TermTest {

    private fun read(json: String) = Term.fromJson(JSONObject(json), "t") { it }

    @Test
    fun aBareNumber_readsAsBefore() {
        assertEquals(2100, (read("""{"constant": 2100}""") as Term.Constant).value)
    }

    @Test
    fun aConstantOfAnyType_writesAndReadsBack() {
        for (value in listOf<Any>(21_600_000, 2.5, "good", true, listOf("a", "b"))) {
            val written = Term.Constant(value).toJson().toString()
            assertEquals(value, (read(written) as Term.Constant).value)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun aConstantWithoutValue_isRefused() {
        read("""{"constant": null}""")
    }

    private val instant = app.treelune.core.fields.FieldDefinition("timestamp", "Date", null, app.treelune.core.fields.FieldType.DATETIME, false, null)
    private val number = app.treelune.core.fields.FieldDefinition("kcal", "kcal", null, app.treelune.core.fields.FieldType.NUMERIC, false, null)

    @Test
    fun whatNoEntryIsWorth_writesAndReadsBack_andIsAbsentUnlessSet() {
        val reading = """{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "meals"}}, "field": "timestamp", "reduction": "MIN", "if_none": "NOW"}}"""
        val term = read(reading) as Term.Reading
        assertEquals(Term.IfNone.NOW, term.ifNone)
        assertEquals("NOW", term.params()[Term.IF_NONE])
        assertEquals(term.toJson().toString(), read(term.toJson().toString()).toJson().toString())
        assertEquals(null, (read(reading.replace(""", "if_none": "NOW"""", "")) as Term.Reading).ifNone)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anUnknownValueOfWhatNoEntryIsWorth_isRefused() {
        read("""{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "meals"}}, "field": "timestamp", "reduction": "MAX", "if_none": "ZERO"}}""")
    }

    @Test
    fun whatNoEntryIsWorth_holdsOnlyForADateAndTimeKeptFromOneEntry_ungrouped() {
        val now = Term.IfNone.NOW
        for (reduction in listOf(app.treelune.core.reading.Reduction.MIN, app.treelune.core.reading.Reduction.MAX, app.treelune.core.reading.Reduction.FIRST)) {
            assertEquals(null, Term.ifNoneProblem(now, instant, reduction, grouped = false) { it })
        }
        assertEquals("reading_error_if_none", Term.ifNoneProblem(now, instant, app.treelune.core.reading.Reduction.MAX, grouped = true) { it })
        assertEquals("reading_error_if_none", Term.ifNoneProblem(now, number, app.treelune.core.reading.Reduction.FIRST, grouped = false) { it })
        assertEquals("reading_error_if_none", Term.ifNoneProblem(now, null, app.treelune.core.reading.Reduction.COUNT, grouped = false) { it })
        assertEquals(null, Term.ifNoneProblem(null, number, app.treelune.core.reading.Reduction.SUM, grouped = true) { it })
    }
}
