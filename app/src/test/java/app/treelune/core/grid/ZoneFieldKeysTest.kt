package app.treelune.core.grid

import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.StateField
import org.junit.Assert.assertEquals
import org.junit.Test

/** A tool's fields as the AI's context lists them: the type's and the user's alike, by where they stand. */
class ZoneFieldKeysTest {

    private fun field(name: String) = FieldDefinition(name, name, null, FieldType.TEXT, false, null)

    @Test
    fun `the type's keys and the user's come together, without the app's bookkeeping`() {
        val declared = EntryFields(
            data = listOf(FixedField(field("value"))),
            state = listOf(StateField(field("read"), filterable = true), StateField(field("reminded"), filterable = false, bookkeeping = true))
        )
        assertEquals(mapOf("data" to listOf("value"), "extra" to listOf("calories"), "state" to listOf("read")),
            ZoneContents.fieldKeys(declared, listOf("calories")))
    }

    @Test
    fun `the snapshot keeps what a zone holds, without its look or its tiles'`() {
        val tile = mapOf("kind" to "tool", "id" to "t", "name" to "Weight", "fields" to mapOf("data" to listOf("value")),
            "tile_size" to "2x1", "icon_name" to "scale", "icon_color" to "BLUE", "texture" to "DOTS")
        val zone = mapOf("id" to "z", "name" to "Health", "tile_size" to "2x2", "icon_name" to "heart", "icon_color" to null, "texture" to null,
            "tile_pieces" to emptyList<String>(), "sections" to listOf(mapOf("group" to null, "tiles" to listOf(tile))))
        assertEquals(mapOf("id" to "z", "name" to "Health", "sections" to listOf(mapOf("group" to null, "tiles" to listOf(
            mapOf("kind" to "tool", "id" to "t", "name" to "Weight", "fields" to mapOf("data" to listOf("value"))))))),
            ZoneContents.withoutLook(zone))
    }

    @Test
    fun `a place without a key is left out`() {
        assertEquals(mapOf("extra" to listOf("mood")), ZoneContents.fieldKeys(EntryFields(), listOf("mood")))
        assertEquals(emptyMap<String, List<String>>(), ZoneContents.fieldKeys(EntryFields(), emptyList()))
    }
}
