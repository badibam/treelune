package app.treelune.core.conditions

import app.treelune.core.fields.migration.EntryMigration
import app.treelune.core.fields.migration.FieldChange
import app.treelune.core.fields.migration.FieldConfigComparator
import org.json.JSONArray
import org.json.JSONObject

/**
 * The filters a config change leaves aiming at nothing: an option taken out of a CHOICE field,
 * or renamed (its value changes, so the former one goes), that a filter of another tool compares
 * the field with ("moment = lunch" once "lunch" is gone). Such a filter finds no entry and says
 * nothing; the change's confirmation names the tools that hold one (docs/DATA.md, « Config
 * change »).
 *
 * A field taken out entirely is not counted: the filter naming it is refused when read
 * (EntryFilters), which shows.
 */
object FiltersOnOptions {

    /** The options [old] has and [new] no longer has, by the path a filter names their field with (`data.moment`). */
    fun removed(old: EntryMigration.Fields, new: EntryMigration.Fields): Map<String, Set<String>> {
        fun options(prefix: String, changes: List<FieldChange>) = changes.filterIsInstance<FieldChange.ChoiceOptionsRemoved>()
            .associate { "$prefix.${it.name}" to it.removedOptions.toSet() }
        return options("data", FieldConfigComparator.compare(old.data.map { it.definition }, new.data.map { it.definition })) +
            options("extra", FieldConfigComparator.compare(old.extra, new.extra))
    }

    /**
     * Whether the config [config], declared by [nodes], holds a filter on the entries of [tool]
     * that compares a field with one of its [removed] options.
     */
    suspend fun holds(nodes: List<app.treelune.core.fields.settings.SettingNode>, config: JSONObject, tool: String, removed: Map<String, Set<String>>): Boolean {
        var found = false
        ConfigConditions(unread).each(nodes, config) { condition ->
            if (condition.kind == ConfigConditions.Kind.ON_ENTRY && condition.tool == tool && aims(condition.condition, removed)) found = true
        }
        return found
    }

    /** What a filter names is read from the stored condition alone: no tool, variable or row is looked up. */
    private val unread = object : ConfigConditions.Sources {
        override suspend fun toolFields(toolInstanceId: String): Map<String, app.treelune.core.fields.FieldDefinition> = emptyMap()
        override suspend fun variableField(id: String): app.treelune.core.fields.FieldDefinition? = null
        override suspend fun rowFields(root: JSONObject, path: List<Any>): Map<String, app.treelune.core.fields.FieldDefinition> = emptyMap()
    }

    /** Whether the filter [condition] compares a field with one of its [removed] options. */
    fun aims(condition: JSONObject, removed: Map<String, Set<String>>): Boolean {
        val gone = Conditions.fieldOf(condition)?.let { removed[it] } ?: return false
        val written = Conditions.right(condition) as? Conditions.Right.Written ?: return false
        val values = when (val value = written.value) {
            is JSONArray -> (0 until value.length()).map { value.opt(it)?.toString() }
            else -> listOf(value.toString())
        }
        return values.any { it in gone }
    }
}
