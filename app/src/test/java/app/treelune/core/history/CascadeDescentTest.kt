package app.treelune.core.history

import androidx.room.useReaderConnection
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What docs/DATA.md, « History of writes » promised of a deletion: the door reads, before it, every row
 * SQLite deletes along with it, so that an undo puts it back. The descent it walks
 * (CoveredTable.children) is the schema's cascades from a covered table, every one of them: a
 * table added with a cascade the descent does not know would be deleted unseen by the history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CascadeDescentTest {

    /** A cascade: SQLite deletes the rows of [child] whose [column] names a deleted row of [parent]. */
    private data class Cascade(val parent: String, val child: String, val column: String)

    private fun schemaCascades(): Set<Cascade> = runBlocking {
        val database = AppDatabase.getDatabase(RuntimeEnvironment.getApplication())
        database.useReaderConnection { connection ->
            val tables = connection.usePrepared("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'") { s ->
                buildList { while (s.step()) add(s.getText(0)) }
            }
            tables.flatMap { table ->
                // Columns of foreign_key_list: id, seq, table, from, to, on_update, on_delete, match
                connection.usePrepared("PRAGMA foreign_key_list(`$table`)") { s ->
                    buildList { while (s.step()) if (s.getText(6) == "CASCADE") add(Cascade(s.getText(2), table, s.getText(3))) }
                }
            }.toSet()
        }
    }

    @Test
    fun `the descent of a deletion is every cascade from a covered table, none but those`() {
        val covered = CoveredTable.entries.map { it.tableName }.toSet()
        val fromCovered = schemaCascades().filter { it.parent in covered }
            .filter { (it.parent to it.child) !in CoveredTable.CHILDREN_LEFT_OUT }
            .toSet()
        val descent = CoveredTable.entries.flatMap { table -> table.children.map { (child, column) -> Cascade(table.tableName, child, column) } }.toSet()
        assertEquals(fromCovered, descent)
    }

    @Test
    fun `the children left out are cascades of the schema`() {
        val cascades = schemaCascades().map { it.parent to it.child }.toSet()
        CoveredTable.CHILDREN_LEFT_OUT.forEach { assertEquals("$it", true, it in cascades) }
    }
}
