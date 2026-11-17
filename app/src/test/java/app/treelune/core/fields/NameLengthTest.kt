package app.treelune.core.fields

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the length of an entry's name in the entry schema, which the form, the AI, an import and
 * a Task's steps are all checked against: a few words unless the tool type says otherwise (a
 * Records tool's « Length of the names »), so that no existing tool changes.
 */
class NameLengthTest {

    private fun nameMaxLength(declared: EntryFields): Int =
        JSONObject(EntrySchemaGenerator.generate(declared, emptyList()) { it })
            .getJSONObject("properties").getJSONObject("name").getInt("maxLength")

    @Test
    fun aNameIsShortByDefault() {
        assertEquals(60, nameMaxLength(EntryFields(nameUnique = true)))
    }

    @Test
    fun aNameTakesTheLengthItsToolTypeDeclares() {
        assertEquals(250, nameMaxLength(EntryFields(nameUnique = true, nameLength = TextLength.MEDIUM)))
        assertEquals(1500, nameMaxLength(EntryFields(nameUnique = true, nameLength = TextLength.LONG)))
    }
}
