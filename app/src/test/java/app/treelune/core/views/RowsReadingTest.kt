package app.treelune.core.views

import app.treelune.core.conditions.Conditions
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.reading.ReadingResult
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.ZoneId

/**
 * Rows in memory read as entries are: each written filter judged row by row, a list of options
 * on either side, a count or a reduction over what is kept; a filter whose value is still to read
 * is refused rather than left out.
 */
class RowsReadingTest {

    private val resolver = app.treelune.core.selection.TimeResolver(0L, ZoneId.of("Europe/Paris"), 4, "MONDAY")

    private val fields = mapOf(
        "id" to FieldDefinition("id", "id", null, FieldType.TEXT, false, null),
        "data.state" to FieldDefinition("state", "state", null, FieldType.CHOICE, false, mapOf("options" to listOf("done", "available", "locked"))),
        "data.parent" to FieldDefinition("parent", "parent", null, FieldType.TEXT, false, null),
        "data.ancestors" to FieldDefinition("ancestors", "ancestors", null, FieldType.CHOICE, false, mapOf("options" to listOf("root", "week"), "multiple" to true)),
        "data.xp" to FieldDefinition("xp", "xp", null, FieldType.NUMERIC, false, null)
    )

    private fun row(id: String, state: String, parent: String, xp: Int) =
        mapOf("id" to id, "timestamp" to null, "data" to mapOf("state" to state, "parent" to parent, "ancestors" to listOf("root", parent), "xp" to xp))

    private val rows = listOf(row("a", "done", "week", 10), row("b", "available", "week", 20), row("c", "done", "other", 5))

    private fun selection(vararg filters: JSONObject) = EntrySelection(Reference(ReferenceKind.TOOL_INSTANCE, "path"), filters = JSONArray(filters.toList()))

    @Test
    fun `each written filter keeps its rows, a list on the right or on the left`() {
        val kept = RowsReading.selected(rows, selection(
            Conditions.onField("data.state", "in", listOf("done")),
            Conditions.onField("data.ancestors", "in", listOf("week"))
        ), fields, resolver) { it }
        assertEquals(listOf("a"), kept.map { it["id"] })
        assertEquals(listOf("a", "b"), RowsReading.selected(rows, selection(Conditions.onField("id", "in", listOf("a", "b"))), fields, resolver) { it }.map { it["id"] })
    }

    @Test
    fun `a count and a sum read what the filters keep`() {
        val done = selection(Conditions.onField("data.state", "in", listOf("done")))
        assertEquals(2, (RowsReading.read(rows, done, null, Reduction.COUNT, fields, resolver) { it } as ReadingResult.Value).value)
        assertEquals(15.0, ((RowsReading.read(rows, done, "data.xp", Reduction.SUM, fields, resolver) { it } as ReadingResult.Value).value as Number).toDouble(), 0.0)
        assertEquals(0, (RowsReading.read(emptyList(), done, null, Reduction.COUNT, fields, resolver) { it } as ReadingResult.Value).value)
    }

    @Test
    fun `a filter whose value is still to read, or on a field the rows lack, is refused`() {
        val toRead = Conditions.onField("data.xp", ">", null).put(Conditions.RIGHT, JSONObject().put("variable", "goal"))
        assertThrows(IllegalStateException::class.java) { RowsReading.selected(rows, selection(toRead), fields, resolver) { it } }
        assertThrows(IllegalStateException::class.java) { RowsReading.selected(rows, selection(Conditions.onField("data.missing", "=", 1)), fields, resolver) { it } }
    }
}
