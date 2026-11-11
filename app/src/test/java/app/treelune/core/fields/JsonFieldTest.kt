package app.treelune.core.fields

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What a JSON field promises (TextLength.JSON): a text that reads whole as a JSON object or list
 * is kept, anything else refused at the writing, never stored to be guessed at later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JsonFieldTest {

    private val context = RuntimeEnvironment.getApplication()
    private val field = FieldDefinition("context", "Context", null, FieldType.TEXT, false, mapOf("length" to TextLength.JSON.name))

    private fun valid(value: Any) = FieldValueValidator.validate(field, value, context).isValid

    @Test
    fun anObjectOrAList_isKept() {
        assertTrue(valid("""{"programme": "…", "escales": [{"id": "e1", "Titre": "La rivière"}]}"""))
        assertTrue(valid("""[1, 2]"""))
    }

    @Test
    fun aTextThatIsNotAnObjectOrAList_isRefused() {
        assertFalse(valid("le programme"))
        assertFalse(valid("\"a text\""))
        assertFalse(valid("42"))
        // Cut, or followed by more
        assertFalse(valid("""{"programme": """))
        assertFalse(valid("""{"a": 1} {"b": 2}"""))
    }

    @Test
    fun anUnlimitedText_staysFree() {
        val free = field.copy(config = mapOf("length" to TextLength.UNLIMITED.name))
        assertTrue(FieldValueValidator.validate(free, "le programme", context).isValid)
    }
}
