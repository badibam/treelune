package app.treelune.core.services

import android.content.Context
import app.treelune.core.database.AppDatabase
import app.treelune.core.strings.StringsContext

/**
 * What depends on a thing about to be deleted (docs/design/dependents.md): the configs that name
 * it — tools, variables, routines (their triggers, access and starting message), Tasks, readouts,
 * events — and, for an entry, the entries of other tools that designate it. Nothing is cleaned:
 * what is named here fails visibly once the thing is gone, and comes back if the deletion is
 * undone. The search reads each config as JSON text, an id being a UUID no other value takes; it
 * leans towards naming too much rather than too little.
 */
object Dependents {

    /** What a search looks for. */
    sealed interface Target {
        data class Tool(val id: String) : Target
        data class Variable(val id: String) : Target
        data class Entry(val id: String) : Target
        /** A field of a tool, by its path (`extra.kcal`, `data.value`). */
        data class Field(val toolInstanceId: String, val path: String) : Target {
            val name: String get() = path.substringAfterLast('.')
        }
    }

    /** A thing that depends: its kind (`tool`, `variable`, `routine`, `task`, `readout`, `event`, `entries`), its id, its name, and for entries their count. */
    data class Dependent(val kind: String, val id: String, val name: String, val count: Int? = null) {
        fun toMap(): Map<String, Any> = buildMap {
            put("kind", kind); put("id", id); put("name", name); count?.let { put("count", it) }
        }
    }

    /** What depends on [target], configs first, then entries; the target itself left out. */
    suspend fun of(context: Context, target: Target): List<Dependent> {
        val database = AppDatabase.getDatabase(context)
        val self = when (target) {
            is Target.Tool -> target.id
            is Target.Variable -> target.id
            is Target.Entry -> target.id
            is Target.Field -> null
        }
        val variableName = (target as? Target.Variable)?.let { database.variableDao().getById(it.id)?.name }
        fun names(json: String?): Boolean = json != null && when (target) {
            is Target.Tool -> json.contains(target.id)
            is Target.Entry -> json.contains(target.id)
            is Target.Variable -> json.contains(target.id) || (variableName != null && formulas(json).any { word(it, variableName) })
            // The field's path in a config naming its tool; or, in a formula, read through a
            // reference (`aliment.kcal_100_g`), whatever tool it is written in, or by its name
            // in a config naming its tool
            is Target.Field -> formulas(json).any { it.contains(Regex("""\.${Regex.escape(target.name)}(?![\w])""")) } ||
                (json.contains(target.toolInstanceId) && (json.contains("\"${target.path}\"") || formulas(json).any { word(it, target.name) }))
        }

        val found = mutableListOf<Dependent>()
        database.toolInstanceDao().getAllToolInstances().filter { it.id != self && names(it.config_json) }
            .forEach { found += Dependent("tool", it.id, toolName(it.config_json)) }
        database.variableDao().getAll().filter { it.id != self && names(it.definitionJson) }
            .forEach { found += Dependent("variable", it.id, it.label) }
        val aiDao = database.aiDao()
        aiDao.getAllRoutines().filter { routine ->
            names(routine.triggersJson) || names(routine.accessJson) ||
                aiDao.getMessagesForSession(routine.seedSessionId).any { names(it.richContentJson) }
        }.forEach { found += Dependent("routine", it.id, it.name) }
        database.taskDao().getAll().filter { names(it.stepsJson) || names(it.triggersJson) }
            .forEach { found += Dependent("task", it.id, it.name) }
        database.readoutDao().getAll().filter { names(it.settingsJson) }
            .forEach { found += Dependent("readout", it.id, it.name) }
        database.eventDao().getAll().filter { names(it.valuesJson) }
            .forEach { found += Dependent("event", it.id, it.name) }
        if (target is Target.Entry) {
            database.toolDataDao().countDesignating(target.id).forEach { counted ->
                val tool = database.toolInstanceDao().getToolInstanceById(counted.toolInstanceId) ?: return@forEach
                found += Dependent("entries", tool.id, toolName(tool.config_json), counted.count)
            }
        }
        return found
    }

    /** A tool's name, from its config. */
    private fun toolName(config: String): String = org.json.JSONObject(config).optString("name")

    /** The formulas a config holds, under `formula` or `per_entry`. */
    private fun formulas(json: String): List<String> =
        Regex(""""(?:formula|per_entry)"\s*:\s*"((?:[^"\\]|\\.)*)"""").findAll(json).map { it.groupValues[1] }.toList()

    /** Whether [name] stands in [formula] as a word of its own. */
    private fun word(formula: String, name: String): Boolean = Regex("""(?<![\w.])${Regex.escape(name)}(?![\w])""").containsMatchIn(formula)

    /**
     * [dependents] in words, grouped by kind, each named, a long list cut: what a confirmation and
     * a refusal say. Empty when nothing depends.
     */
    fun said(dependents: List<Dependent>, s: StringsContext, most: Int = 8): String {
        if (dependents.isEmpty()) return ""
        return dependents.groupBy { it.kind }.entries.joinToString(" ; ") { (kind, items) ->
            val named = items.map { if (it.count != null) s.shared("dependents_entries_of").format(it.count, it.name) else it.name }
            val shown = named.take(most).joinToString(", ") + (if (named.size > most) s.shared("dependents_more").format(named.size - most) else "")
            s.shared("dependents_kind_$kind").format(shown)
        }
    }

    /**
     * The refusal of a deletion asked by someone else than a person while [dependents] depend on
     * [what], unless [confirmed]; null when it goes ahead (docs/design/dependents.md). The search
     * runs only when a refusal is possible: a person has seen its confirmation already.
     */
    suspend fun refusal(what: String, confirmed: Boolean, s: StringsContext, dependents: suspend () -> List<Dependent>): String? {
        if (confirmed || app.treelune.core.coordinator.Protections.byAPerson()) return null
        val found = dependents()
        return if (found.isEmpty()) null else s.shared("dependents_refused").format(what, said(found, s))
    }

    /** The parameter a caller other than a person gives to go ahead anyway. */
    const val CONFIRM = "confirm_dependents"
}
