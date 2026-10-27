package app.treelune.tools.chart

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A reference in a chart shows the name of what it designates, given to each reference of a
 * multiple one too, so that a flatten leaves a named thing on each row.
 */
class ChartReferenceNamesTest {

    private val anna = mapOf("kind" to "ENTRY", "id" to "anna")
    private val ben = mapOf("kind" to "ENTRY", "id" to "ben")
    private val names = mapOf(Reference(ReferenceKind.ENTRY, "anna") to "Anna", Reference(ReferenceKind.ENTRY, "ben") to "Ben")

    @Test
    fun `a reference takes its name, each of a list too, anything else left as is`() {
        assertEquals(anna + ("name" to "Anna"), ChartSources.named(anna, names))
        assertEquals(listOf(anna + ("name" to "Anna"), ben + ("name" to "Ben")), ChartSources.named(listOf(anna, ben), names))
        assertEquals(null, ChartSources.named(null, names))
    }

    @Test
    fun `a multiple reference flattened leaves a named thing on each row`() {
        val people = FieldDefinition("extra.people", "People", null, FieldType.REFERENCE, false,
            mapOf("target" to mapOf("kinds" to listOf("ENTRY")), "multiple" to true))
        val table = ChartTable(mapOf("extra.people" to people), listOf(Row(mapOf("extra.people" to Cell.Value(ChartSources.named(listOf(anna, ben), names))))))
        val flat = table.transformed(Transform.Flatten(listOf("extra.people")))
        assertEquals(listOf("Anna", "Ben"), flat.rows.map { (it.value("extra.people") as Map<*, *>)["name"] })
    }
}
