package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings a routine's usage to its v118 form (docs/design/icon-tiles.md, « L'usage d'une routine »):
 * four usages instead of three. `both` becomes `alone_or_conversation`, the behaviour it had — its
 * schedule and its triggers run it, its tile opens a conversation too; `alone` and `conversation`
 * stay. The fourth, `conversation_or_alone`, is new: no routine has it before v118.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object RoutineUsagesAtV118 {

    const val TABLE = "routines"
    const val KEY = "usage"
    const val FORMER = "both"
    const val CURRENT = "alone_or_conversation"

    /** [values] (a routine's row, column -> value) at v118, in place; one already there is left as it is. */
    fun values(values: JSONObject): JSONObject {
        if (values.optString(KEY) == FORMER) values.put(KEY, CURRENT)
        return values
    }

    /** Rewrites the backup document's routines in place. */
    fun backup(data: JSONObject) {
        val rows = data.optJSONArray(TABLE) ?: return
        for (i in 0 until rows.length()) values(rows.getJSONObject(i))
    }
}
