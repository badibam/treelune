package app.treelune.core.versioning

import app.treelune.core.database.MigrationDatabase
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * The copies the history of writes keeps of a table's rows, brought to the form a migration gives
 * the table (docs/design/undo-history.md, « Migrations »): an undo puts a copy back as it is, so a
 * copy left in an older form would write it into the newer table.
 *
 * A migration that renames or removes a column of a covered table, or rewrites the JSON it holds,
 * calls [rewrite] for that table with the same transformation it applies to the rows. A column
 * added asks nothing: a copy without it puts back the value the row holds, or the column's default
 * for a row put back whole.
 */
object HistoryCopies {

    /**
     * Each copy of [table]'s rows, before and after, rewritten by [transform], which receives a
     * copy (column -> value) and returns it in the new form. A copy [transform] cannot read stays as
     * it was and is logged: its undo then refuses, the row no longer matching it.
     */
    fun rewrite(database: MigrationDatabase, table: String, transform: (JSONObject) -> JSONObject) {
        var rewritten = 0
        database.query("SELECT id, before, after FROM history_rows WHERE table_name = ?", arrayOf<Any?>(table)).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                try {
                    val before = if (cursor.isNull(1)) null else transform(JSONObject(cursor.getString(1))).toString()
                    val after = if (cursor.isNull(2)) null else transform(JSONObject(cursor.getString(2))).toString()
                    database.execSQL("UPDATE history_rows SET before = ?, after = ? WHERE id = ?", arrayOf<Any?>(before, after, id))
                    rewritten++
                } catch (e: Exception) {
                    LogManager.database("History copy $id of $table left as it was: ${e.message}", "ERROR", e)
                }
            }
        }
        LogManager.database("History: $rewritten copies of $table rewritten", "INFO")
    }
}
