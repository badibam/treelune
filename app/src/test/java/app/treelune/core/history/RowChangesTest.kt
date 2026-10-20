package app.treelune.core.history

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the history's screen shows of a row a step changed: each field that differs, a column or
 * a key of the JSON a column holds, before and after; the id and the times left out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RowChangesTest {

    @Test
    fun `a column and a key of a JSON column, each by its name`() {
        val before = mapOf("id" to "z", "name" to "Garden", "data" to """{"content":"Fern","kcal":12}""", "updated_at" to 1L)
        val after = mapOf("id" to "z", "name" to "Orchard", "data" to """{"kcal":12.0,"content":"Moss"}""", "updated_at" to 2L)
        assertEquals(
            listOf(RowChanges.Change("name", "Garden", "Orchard"), RowChanges.Change("content", "Fern", "Moss")),
            RowChanges.of(before, after)
        )
    }

    @Test
    fun `a key given or taken away reads as nothing on its other side`() {
        val before = mapOf("settings" to """{"auto_backup":true}""")
        val after = mapOf("settings" to """{"auto_backup":true,"folder":"content://x"}""")
        assertEquals(listOf(RowChanges.Change("folder", null, "content://x")), RowChanges.of(before, after))
    }
}
