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
 * What docs/DATA.md (« FILE ») promises of a FILE field outside its storage: a list of ids, its
 * max held by the schema, its kinds required, filtered on present and absent only, and the AI
 * reading each id with its kind and size, never the file, and writing back the ids it read.
 */
class FileFieldTest {

    private val field = FieldDefinition("photos", "Photos", null, FieldType.FILE, false, FileField.config(listOf(app.treelune.core.files.FileKind.IMAGE), max = 2))
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
    fun `a lower max no longer holds a value with more files`() {
        assertTrue(FieldType.FILE.permits(listOf("a", "b"), mapOf(FileField.MAX to 2)))
        assertFalse(FieldType.FILE.permits(listOf("a", "b", "c"), mapOf(FileField.MAX to 2)))
        assertTrue(FieldType.FILE.permits(listOf("a", "b", "c"), null))
    }

    @Test
    fun `it is filtered on present and absent only`() {
        assertEquals(setOf(FilterOperator.PRESENT, FilterOperator.ABSENT), EntryFilters.operatorsFor(FieldType.FILE))
    }

    @Test
    fun `the AI reads each id with its kind and size, an image's width and height, never the file`() {
        val files = mapOf("a" to FileEntity.image("a", null, "e", null, app.treelune.core.files.FileFormat.JPEG, 1, 1568, 1176, 0))
        val shown = ModelValues.withFileDescriptions(mapOf("photos" to listOf("a", "gone")), schema, files) as Map<*, *>
        assertEquals(
            listOf(mapOf("id" to "a", "kind" to "IMAGE", "size_bytes" to 1L, "width" to 1568, "height" to 1176), mapOf("id" to "gone", "missing" to true)),
            shown["photos"]
        )
        assertEquals(listOf("a", "gone"), ModelValues.fileIds(mapOf("photos" to listOf("a", "gone")), schema))
    }

    @Test
    fun `the ids come back from what the AI wrote, as read or alone`() {
        val written = JSONObject().put("photos", JSONArray().put(JSONObject().put("id", "a").put("width", 1)).put("b"))
        val stored = ModelValues.fromModel(written, schema, zone) as JSONObject
        assertEquals(listOf("a", "b"), FileField.ids(stored.get("photos")))
    }
}
