package app.treelune.core.validation

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A refusal names the field by the label the user reads, the title its schema carries, never by
 * the library's path: a note's title too long once showed "$.name: must be at most 60 characters".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ValidationFieldNamesTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val schema = Schema(
        id = "test_entry",
        displayName = "Test",
        description = "Test",
        category = SchemaCategory.TOOL_DATA,
        content = """
            {"type": "object", "properties": {
                "name": {"type": "string", "title": "Title", "maxLength": 5},
                "extra": {"type": "object", "properties": {
                    "kcal": {"type": "number", "title": "Calories", "minimum": 0}
                }},
                "items": {"type": "array", "items": {"type": "object", "properties": {
                    "quantity": {"type": "number", "title": "Quantity"}
                }}},
                "untitled": {"type": "string", "maxLength": 1}
            }}
        """.trimIndent()
    )

    private fun refusal(data: Map<String, Any?>): String {
        val result = SchemaValidator.validate(schema, data, context)
        assertFalse("refused", result.isValid)
        val message = result.errorMessage ?: ""
        assertFalse(message, message.contains("$"))
        return message
    }

    @Test
    fun aRootFieldIsNamedByItsTitle() {
        assertTrue(refusal(mapOf("name" to "too long")).startsWith("Title"))
    }

    @Test
    fun aNestedFieldIsNamedByItsTitle() {
        assertTrue(refusal(mapOf("extra" to mapOf("kcal" to -1))).startsWith("Calories"))
    }

    @Test
    fun aFieldInAListIsNamedByItsTitle() {
        assertTrue(refusal(mapOf("items" to listOf(mapOf("quantity" to "x")))).startsWith("Quantity"))
    }

    @Test
    fun aFieldWithoutTitleIsNamedByItsKey() {
        assertTrue(refusal(mapOf("untitled" to "ab")).startsWith("untitled"))
    }
}
