package app.treelune.core.history

import android.content.Context
import app.treelune.core.ai.data.RoutineSettings
import app.treelune.core.config.AppSettings
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.CoreFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.labelOf
import app.treelune.core.fields.settings.storedField
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.schemas.ZoneSettings
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.utils.JsonUtils
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * What a step changed in a row, as the history's screen shows it to the user: each field by the
 * label of the form that edits it, its values as the app shows them elsewhere (FieldValue), a
 * composite setting (a list, a group, a schedule) by its label alone, and what no form shows — a
 * tile's place, what the app writes on an entry — counted, not shown. A field the screen cannot
 * name is no help in deciding to undo; what it is stays the AI's to read (HISTORY).
 *
 * The labels come from the forms: a zone's (ZoneSettings), a tool's config (ToolConfigSettings),
 * an entry's fields (the core's, its type's, its tool's own), the app's settings (AppSettings), a
 * routine's (RoutineSettings); another row shows its name alone.
 */
internal class ReadableChanges(private val context: Context, private val database: AppDatabase) {

    sealed interface Line {
        /** A simple field, its values before and after as stored, null for none. */
        data class Value(val field: FieldDefinition, val old: Any?, val new: Any?) : Line

        /** A composite setting changed, named by its label. */
        data class Composite(val label: String) : Line
    }

    /** A row's lines, and how many of its changes no form shows. */
    data class Readable(val lines: List<Line>, val internal: Int)

    private val s = Strings.`for`(context = context)

    /** What the field at a place of a row is: a column, or a key of the JSON object it holds. */
    private fun interface Describe {
        operator fun invoke(column: String, key: String?): Described?
    }

    /** What a field of a row is to the user: a simple field, a composite setting, or nothing shown. */
    private sealed interface Described {
        data class Field(val definition: FieldDefinition) : Described
        data class Composite(val label: String) : Described
    }

    /**
     * The change of a row of [table] from [before] to [after], copies as the history keeps them
     * (null for a row created, or deleted); [stepRows] the other rows of its step, where an entry
     * finds its tool when they were deleted together.
     */
    suspend fun of(table: CoveredTable, before: JSONObject?, after: JSONObject?, stepRows: List<Pair<CoveredTable, JSONObject>>): Readable {
        val row = after ?: before ?: return Readable(emptyList(), 0)
        val describe = describer(table, row, stepRows)
        val lines = mutableListOf<Line>()
        var internal = 0
        val composites = mutableSetOf<String>()
        for ((place, values) in changes(before, after)) {
            when (val described = describe(place.first, place.second)) {
                is Described.Field -> {
                    val field = described.definition
                    lines += Line.Value(field, shown(field, values.first), shown(field, values.second))
                }
                is Described.Composite -> if (composites.add(described.label)) lines += Line.Composite(described.label)
                null -> internal++
            }
        }
        // A row created or deleted is what it holds: what no form shows says nothing of it
        return Readable(lines, if (before != null && after != null) internal else 0)
    }

    /**
     * The fields that differ between [before] and [after], each at its place — a column, or a key
     * of the JSON object a column holds — with its values; for a row created or deleted, each
     * field it holds. The row's id and its times are left out.
     */
    private fun changes(before: JSONObject?, after: JSONObject?): List<Pair<Pair<String, String?>, Pair<Any?, Any?>>> {
        fun keys(o: JSONObject?) = o?.keys()?.asSequence()?.toList().orEmpty()
        val columns = (keys(before) + keys(after)).distinct().filter { it !in LEFT_OUT }
        return columns.flatMap { column ->
            val old = before?.opt(column)?.takeIf { it != JSONObject.NULL }
            val new = after?.opt(column)?.takeIf { it != JSONObject.NULL }
            val oldObject = (old as? String)?.let(::objectOf)
            val newObject = (new as? String)?.let(::objectOf)
            if (oldObject != null || newObject != null) {
                (keys(oldObject) + keys(newObject)).distinct().mapNotNull { key ->
                    val o = oldObject?.opt(key)?.takeIf { it != JSONObject.NULL }
                    val n = newObject?.opt(key)?.takeIf { it != JSONObject.NULL }
                    if (o?.toString() == n?.toString()) null else (column to key) to (o to n)
                }
            } else if (old?.toString() == new?.toString()) emptyList()
            else listOf((column to null) to (old to new))
        }
    }

    /** What each place of a row of [table] is, read from the form that edits it. */
    private suspend fun describer(table: CoveredTable, row: JSONObject, stepRows: List<Pair<CoveredTable, JSONObject>>): Describe =
        when (table) {
            CoveredTable.ZONES -> ZoneSettings.nodes(context).let { nodes -> Describe { column, key -> if (key == null) nodes.describe(column) else null } }
            CoveredTable.TOOL_INSTANCES -> {
                val nodes = ToolTypeManager.getToolType(row.getString("tooltype"))?.let { ToolConfigSettings.nodes(it, context) }.orEmpty()
                Describe { column, key ->
                    when {
                        column == "config_json" && key != null -> nodes.describe(key)
                        column == "zone_id" -> Described.Field(idField("zone_id", s.shared("label_zone")))
                        else -> null
                    }
                }
            }
            CoveredTable.TOOL_DATA -> entryDescriber(row, stepRows)
            CoveredTable.APP_SETTINGS -> AppSettings.nodes(row.getString("category"), context).let { nodes ->
                Describe { column, key -> if (column == "settings" && key != null) nodes.describe(key) else null }
            }
            // A routine's setting stored as JSON lives in its column x_json
            CoveredTable.ROUTINES -> RoutineSettings.nodes(context).let { nodes ->
                Describe { column, key -> if (key == null) nodes.describe(column.removeSuffix("_json")) else null }
            }
            // A Task's as a routine's; its steps, which no setting describes, as a whole
            CoveredTable.TASKS -> app.treelune.core.tasks.TaskSettings.nodes(context).let { nodes ->
                Describe { column, key ->
                    when {
                        key != null -> null
                        column == "steps_json" -> Described.Composite(s.shared("task_steps"))
                        else -> nodes.describe(column.removeSuffix("_json"))
                    }
                }
            }
            // An API's settings in its config, as a tool's; its key is never copied
            CoveredTable.APIS -> app.treelune.core.apis.ApiSettings.nodes(context).let { nodes ->
                Describe { column, key ->
                    when {
                        column == "config_json" && key != null -> nodes.describe(key)
                        column == "name" && key == null -> Described.Field(nameField())
                        else -> null
                    }
                }
            }
            else -> Describe { column, key -> if (column == "name" && key == null) Described.Field(nameField()) else null }
        }

    /** An entry's places: the core's name and date, its type's fields in data and state, its tool's own fields in extra. */
    private suspend fun entryDescriber(row: JSONObject, stepRows: List<Pair<CoveredTable, JSONObject>>): Describe {
        val toolId = row.getString("tool_instance_id")
        // Its tool as it is, or as it was when deleted along with it; gone otherwise, its fields unknown
        val tool = Rows.read(database, CoveredTable.TOOL_INSTANCES, toolId)
            ?: stepRows.firstOrNull { (t, r) -> t == CoveredTable.TOOL_INSTANCES && r.optString("id") == toolId }?.second
        val toolType = ToolTypeManager.getToolType(row.getString("tooltype"))
        val config = tool?.let { JSONObject(it.getString("config_json")) }
        val declared = if (config != null && toolType != null) toolType.getEntryFields(config, context) else null
        val extra = config?.optJSONArray("extra_fields")?.toFieldDefinitions().orEmpty()
        return Describe { column, key ->
            val field = when {
                declared == null -> null
                column == "name" && key == null && declared.name != CoreFieldUsage.ABSENT -> CoreFields.name(s::shared)
                column == "timestamp" && key == null && declared.timestamp != CoreFieldUsage.ABSENT -> CoreFields.timestamp(s::shared)
                column == "data" -> declared.data.firstOrNull { it.definition.name == key && !it.systemWritten }?.definition
                column == "state" -> declared.state.firstOrNull { it.definition.name == key && !it.bookkeeping }?.definition
                column == "extra" -> extra.firstOrNull { it.name == key }
                else -> null
            }
            field?.let { Described.Field(it) }
        }
    }

    /** [name] in these nodes: a simple field stored flat, or a composite setting by its label. */
    private fun List<SettingNode>.describe(name: String): Described? {
        if (name in IDS) return labelOf(name)?.let { Described.Field(idField(name, it)) }
        return storedField(name)?.let { Described.Field(it) } ?: labelOf(name)?.let { Described.Composite(it) }
    }

    /** A field holding the id of a thing, shown by that thing's name. */
    private fun idField(name: String, label: String) = FieldDefinition(name, label, null, FieldType.TEXT, false, null)

    private fun nameField() = FieldDefinition("name", s.shared("label_name"), null, FieldType.TEXT, false, null)

    /** [value] as FieldValue reads it: a JSON object or list as a map or a list, an id by its thing's name. */
    private suspend fun shown(field: FieldDefinition, value: Any?): Any? {
        if (value == null) return null
        val id = IDS[field.name]
        if (id != null) return id(database, value.toString()) ?: s.shared("pointer_target_deleted")
        return JsonUtils.toValue(value)
    }

    /** [text] read as a JSON object; null when it is none. */
    private fun objectOf(text: String): JSONObject? {
        if (!text.trimStart().startsWith("{")) return null
        return try { JSONTokener(text).nextValue() as? JSONObject } catch (e: JSONException) { null }
    }

    private companion object {
        val LEFT_OUT = setOf("id", "created_at", "updated_at")

        /** The fields that hold the id of a thing, each with how to read that thing's name. */
        val IDS: Map<String, suspend (AppDatabase, String) -> String?> = mapOf(
            "zone_id" to { db, id -> db.zoneDao().getZoneById(id)?.name },
            "identity_id" to { db, id -> db.identityDao().getById(id)?.name },
            RoutineSettings.PROVIDER_ID to { db, id -> db.aiDao().getProviderConfig(id)?.name }
        )
    }
}
