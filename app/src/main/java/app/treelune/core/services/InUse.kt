package app.treelune.core.services

import app.treelune.core.database.AppDatabase
import app.treelune.core.strings.StringsContext

/**
 * What still names a thing about to be deleted -- an identity, an AI provider -- for its deletion
 * to be refused while anything does, naming them (docs/design/incarnations.md, « Suppression »).
 * One check for both: each says which of its kind's fields name it. Nothing is deleted with it,
 * every zone and routine being changed by hand first. A past session naming it stays readable and
 * cannot continue (SessionUsable).
 */
object InUse {

    /** The zones and the routines naming identity [identityId], by name. */
    suspend fun identity(database: AppDatabase, identityId: String): Users = Users(
        zones = database.zoneDao().getAllZones().filter { it.identity_id == identityId }.map { it.name },
        routines = database.aiDao().getAllAutomations().filter { it.identityId == identityId }.map { it.name }
    )

    /** The routines running on provider [providerId], by name; a zone has no provider. */
    suspend fun provider(database: AppDatabase, providerId: String): Users = Users(
        zones = emptyList(),
        routines = database.aiDao().getAllAutomations().filter { it.providerId == providerId }.map { it.name }
    )

    /** What names a thing, by kind. */
    data class Users(val zones: List<String>, val routines: List<String>) {
        val none: Boolean get() = zones.isEmpty() && routines.isEmpty()

        /** The refusal of [what]'s deletion, naming each; null when nothing names it. */
        fun refusal(what: String, s: StringsContext): String? {
            if (none) return null
            val named = listOfNotNull(
                zones.takeIf { it.isNotEmpty() }?.let { s.shared("in_use_zones").format(it.joinToString(", ")) },
                routines.takeIf { it.isNotEmpty() }?.let { s.shared("in_use_routines").format(it.joinToString(", ")) }
            )
            return s.shared("in_use_refused").format(what, named.joinToString(" ; "))
        }
    }
}
