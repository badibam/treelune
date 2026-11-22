package app.treelune.core.services

import app.treelune.core.database.AppDatabase
import app.treelune.core.strings.StringsContext

/**
 * What still names a thing about to be deleted -- an identity, an API -- for its deletion to be
 * refused while anything does, naming them (docs/AI.md, « Identities »).
 * One check for all: each says which of its kind's fields name it. An AI provider is not held
 * back: its routines are turned off instead (AIProviderConfigService). Nothing is deleted with it,
 * every zone and routine being changed by hand first. A past session naming it stays readable and
 * cannot continue (SessionUsable).
 */
object InUse {

    /** The routines and the Tasks naming identity [identityId], by name. */
    suspend fun identity(database: AppDatabase, identityId: String): Users = Users(
        routines = database.aiDao().getAllRoutines().filter { it.identityId == identityId }.map { it.name },
        // A Task that sends it to an API (an `assemble` step's identity)
        tasks = database.taskDao().getAll().filter { identityId in app.treelune.core.tasks.StepObject.identitiesOf(it.stepsJson) }.map { it.name }
    )

    /** The Tasks one of whose steps calls API [apiId], by name. */
    suspend fun api(database: AppDatabase, apiId: String): Users = Users(
        routines = emptyList(),
        tasks = database.taskDao().getAll().filter { task ->
            org.json.JSONArray(task.stepsJson).let { steps -> (0 until steps.length()).any { steps.getJSONObject(it).optString("api_id") == apiId } }
        }.map { it.name }
    )

    /** What names a thing, by kind. */
    data class Users(val routines: List<String>, val tasks: List<String> = emptyList()) {
        val none: Boolean get() = routines.isEmpty() && tasks.isEmpty()

        /** The refusal of [what]'s deletion, naming each; null when nothing names it. */
        fun refusal(what: String, s: StringsContext): String? {
            if (none) return null
            val named = listOfNotNull(
                routines.takeIf { it.isNotEmpty() }?.let { s.shared("in_use_routines").format(it.joinToString(", ")) },
                tasks.takeIf { it.isNotEmpty() }?.let { s.shared("in_use_tasks").format(it.joinToString(", ")) }
            )
            return s.shared("in_use_refused").format(what, named.joinToString(" ; "))
        }
    }
}
