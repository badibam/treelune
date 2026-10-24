package app.treelune.core.fields

import app.treelune.core.ai.prompts.ModelValues
import app.treelune.core.files.FileEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * What docs/DATA.md (« IMAGE ») promises of an IMAGE field outside its storage: a list of ids,
 * its max held by the schema, filtered on present and absent only, and the AI reading each id with
 * its size, never the image, and writing back the ids it read.
 */
class ImageFieldTest {

    private val field = FieldDefinition("photos", "Photos", null, FieldType.IMAGE, false, mapOf(ImageField.MAX to 2))
    private val schema = JSONObject().put("type", "object").put("properties", JSONObject().put("photos", FieldValueSchema.of(field)))
    private val zone = ZoneId.of("Europe/Paris")

    @Test
    fun `its value is a list of distinct ids, at most its max`() {
        val own = FieldValueSchema.of(field)
        assertEquals("array", own.getString("type"))
        assertEquals(2, own.getInt("maxItems"))
        assertTrue(own.getBoolean("uniqueItems"))
        assertFalse(FieldValueSchema.of(field.copy(config = null)).has("maxItems"))
    }

    @Test
    fun `a lower max no longer holds a value with more images`() {
        assertTrue(FieldType.IMAGE.permits(listOf("a", "b"), mapOf(ImageField.MAX to 2)))
        assertFalse(FieldType.IMAGE.permits(listOf("a", "b", "c"), mapOf(ImageField.MAX to 2)))
        assertTrue(FieldType.IMAGE.permits(listOf("a", "b", "c"), null))
    }

    @Test
    fun `it is filtered on present and absent only`() {
        assertEquals(setOf(FilterOperator.PRESENT, FilterOperator.ABSENT), EntryFilters.operatorsFor(FieldType.IMAGE))
    }

    @Test
    fun `the AI reads each id with its size, never the image`() {
        val images = mapOf("a" to FileEntity.image("a", null, "e", null, app.treelune.core.files.FileFormat.JPEG, 1, 1568, 1176, 0))
        val shown = ModelValues.withImageSizes(mapOf("photos" to listOf("a", "gone")), schema, images) as Map<*, *>
        assertEquals(
            listOf(mapOf("id" to "a", "width" to 1568, "height" to 1176), mapOf("id" to "gone", "missing" to true)),
            shown["photos"]
        )
        assertEquals(listOf("a", "gone"), ModelValues.imageIds(mapOf("photos" to listOf("a", "gone")), schema))
    }

    @Test
    fun `the ids come back from what the AI wrote, as read or alone`() {
        val written = JSONObject().put("photos", JSONArray().put(JSONObject().put("id", "a").put("width", 1)).put("b"))
        val stored = ModelValues.fromModel(written, schema, zone) as JSONObject
        assertEquals(listOf("a", "b"), ImageField.ids(stored.get("photos")))
    }
}
