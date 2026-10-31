package app.treelune.core.readouts

import android.content.Context
import app.treelune.core.conditions.AppConditionSources
import app.treelune.core.conditions.Condition
import app.treelune.core.conditions.ConditionCheck
import app.treelune.core.conditions.ConditionJudge
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.transaction
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.formatValue
import app.treelune.core.grid.Groups
import app.treelune.core.grid.SectionItem
import app.treelune.core.grid.ToolPositions
import app.treelune.core.look.Looks
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.selection.TimeResolver
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.services.VariableService
import app.treelune.core.strings.Strings
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermFields
import app.treelune.core.terms.TermReader
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonNulls
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import app.treelune.core.validation.SchemaValidator
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The readouts (docs/design/readouts.md), for the screen and the AI alike.
 *
 * Operations:
 * - readouts.list (zone_id): its readouts; .list_all: every one; .get (readout_id): one — each
 *   with its settings as ReadoutSettings declares them, its place in its section, and the tools
 *   it reads (`tools_read`, VariableService.ANY_TOOL when one of its variables may read any)
 * - .create (zone_id, its settings at the top); .update (readout_id, what changes: a setting left
 *   out kept, one given null removed; `zone_id`); .delete (readout_id)
 * - .read (readout_id, `at`? — now when left out): its core and its sentence (`core`, `text`, the
 *   core alone without a sentence), each value written as its field shows it, the colour of the first condition that holds and whether that one signals,
 *   and the values that gave nothing, each with why
 *
 * A write is checked as the screen checks it: its settings against their schema, its look as
 * every look is, its group one of its zone's, its values each a name a sentence can write, unique,
 * and a term that reads, every `{name}` of its sentence one of them, its conditions comparing its
 * values (ConditionCheck, its values as a row's fields: ReadoutRows), its target a thing that is.
 */
class ReadoutService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val dao by lazy { database.readoutDao() }
    private val sections by lazy { app.treelune.core.grid.SectionGrid(context) }
    // Every write of a readout goes through the history's one door
    private val writes by lazy { app.treelune.core.history.Writes(context) }

    /** The keys of a readout's settings, beside its zone. */
    private val SETTING_KEYS = Looks.keys(ReadoutSettings.LOOK) + listOf("group") + ReadoutSettings.BODY_KEYS

    /** A write refused, with what to say. */
    private class Refused(message: String) : Exception(message)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult = try {
        when (operation) {
            "list" -> OperationResult.success(mapOf("readouts" to dao.getByZone(params.optString("zone_id")).map { describe(it) }))
            "list_all" -> OperationResult.success(mapOf("readouts" to dao.getAll().map { describe(it) }))
            "get" -> OperationResult.success(mapOf("readout" to describe(readout(params))))
            "create" -> create(params)
            "update" -> update(params)
            "delete" -> delete(params)
            "read" -> read(params)
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    } catch (e: Refused) {
        OperationResult.error(e.message ?: "")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        LogManager.service("ReadoutService.$operation failed: ${e.message}", "ERROR", e)
        OperationResult.error(s.shared("readout_error").format(e.message ?: ""))
    }

    private suspend fun readout(params: JSONObject): ReadoutEntity =
        params.optString("readout_id").ifEmpty { null }?.let { dao.get(it) } ?: throw Refused(s.shared("readout_error_not_found").format(params.optString("readout_id")))

    /** [readout] as a result gives it: what ReadoutRow reads. */
    private suspend fun describe(readout: ReadoutEntity): Map<String, Any?> = mapOf(
        "id" to readout.id,
        "zone_id" to readout.zoneId,
        "settings" to JsonUtils.toMap(ReadoutSettings.of(readout)),
        "grid_x" to readout.gridX,
        "grid_y" to readout.gridY,
        "updated_at" to readout.updatedAt,
        "tools_read" to toolsRead(JSONObject(readout.settingsJson)).toList(),
        // A tool it opens is reached through its zone
        "target_zone_id" to ReferenceTarget.referenceOf(JSONObject(readout.settingsJson).opt(ReadoutSettings.TARGET))?.let { target ->
            when (target.kind) {
                ReferenceKind.TOOL_INSTANCE -> database.toolInstanceDao().getToolInstanceById(target.id ?: "")?.zone_id
                ReferenceKind.ZONE -> target.id
                else -> null
            }
        }
    )

    private suspend fun create(params: JSONObject): OperationResult {
        val zoneId = params.optString("zone_id").ifEmpty { throw Refused(s.shared("error_param_zone_id_required")) }
        val zone = database.zoneDao().getZoneById(zoneId) ?: throw Refused(s.shared("service_error_zone_not_found"))
        // The app's own demo gives its ids; every other caller gets one made here
        val id = when (val given = app.treelune.core.coordinator.GivenId.read(params)) {
            app.treelune.core.coordinator.GivenId.Read.None -> UUID.randomUUID().toString()
            is app.treelune.core.coordinator.GivenId.Read.Accepted -> given.id
            is app.treelune.core.coordinator.GivenId.Read.Refused -> throw Refused(s.shared("service_error_id_not_given").format(given.id))
        }
        val now = System.currentTimeMillis()
        val base = ReadoutEntity(id, zoneId, "", null, ReadoutSettings.DEFAULT_ICON, null, null, ReadoutSettings.LOOK.defaultMode.name,
            null, 0, 0, "{}", now, now)
        val readout = checked(base, given(params, null), null)
        database.transaction {
            // At the end of its section's grid, among the tools, the variables, the routines and the Tasks
            val place = sections.arrive(SectionItem.of(readout), zone.tool_groups)
            writes.insertReadout(readout.copy(gridX = place.column, gridY = place.row))
        }
        DataChangeNotifier.notifyZonesChanged()
        return OperationResult.success(mapOf("readout_id" to id, "name" to readout.name))
    }

    private suspend fun update(params: JSONObject): OperationResult {
        val readout = readout(params)
        val moved = params.optString("zone_id").ifEmpty { null }?.takeIf { it != readout.zoneId }
        val settings = given(params, ReadoutSettings.of(readout))
        // A readout changing zone without a group given leaves its group behind
        if (moved != null && !params.has("group")) settings.remove("group")
        val updated = checked(readout.copy(zoneId = moved ?: readout.zoneId, updatedAt = System.currentTimeMillis()), settings, ReadoutSettings.of(readout))
        database.transaction {
            val place = sections.move(SectionItem.of(readout), SectionItem.of(updated),
                database.zoneDao().getZoneById(readout.zoneId)?.tool_groups, database.zoneDao().getZoneById(updated.zoneId)?.tool_groups)
            writes.updateReadout(updated.copy(gridX = place.column, gridY = place.row))
        }
        DataChangeNotifier.notifyZonesChanged()
        return OperationResult.success(mapOf("readout_id" to readout.id, "name" to updated.name))
    }

    private suspend fun delete(params: JSONObject): OperationResult {
        val readout = readout(params)
        database.transaction {
            sections.leave(SectionItem.of(readout), database.zoneDao().getZoneById(readout.zoneId)?.tool_groups)
            writes.deleteReadout(readout.id)
        }
        DataChangeNotifier.notifyZonesChanged()
        return OperationResult.success(mapOf("readout_id" to readout.id, "name" to readout.name))
    }

    /** [stored] with the settings [params] give: a setting left out keeps its value, one given null is removed. */
    private fun given(params: JSONObject, stored: JSONObject?): JSONObject {
        val settings = stored?.let { JSONObject(it.toString()) } ?: JSONObject()
        SETTING_KEYS.forEach { key ->
            if (!params.has(key)) return@forEach
            if (params.isNull(key)) settings.remove(key) else settings.put(key, params.get(key))
        }
        return settings
    }

    /** [readout] holding [sent], checked (see the class); refused with what to say. */
    private suspend fun checked(readout: ReadoutEntity, sent: JSONObject, before: JSONObject?): ReadoutEntity {
        val settings = JsonNulls.withoutNullKeys(sent)
        if (!settings.has(Looks.ICON)) settings.put(Looks.ICON, ReadoutSettings.DEFAULT_ICON)
        if (!settings.has(Looks.DISPLAY_MODE)) settings.put(Looks.DISPLAY_MODE, ReadoutSettings.LOOK.defaultMode.name)
        (Looks.check(ReadoutSettings.LOOK, settings, before, context) as? Looks.Checked.Refused)?.let { throw Refused(it.message) }
        val look = Looks.read(ReadoutSettings.LOOK, settings)
        val validation = SchemaValidator.validate(ReadoutSettings.schema(context), JsonUtils.toMap(settings), context)
        if (!validation.isValid) throw Refused(validation.errorMessage ?: s.shared("message_validation_error_simple"))
        val group = settings.optString("group").ifEmpty { null }
        val zone = database.zoneDao().getZoneById(readout.zoneId) ?: throw Refused(s.shared("service_error_zone_not_found"))
        Groups.refusal(group, ToolPositions.zoneGroups(zone.tool_groups), s)?.let { throw Refused(it) }

        val names = checkValues(settings)
        // Every name its core and its sentence write is one of its values; its core does not place itself
        val core = settings.optString(ReadoutSettings.CORE)
        if (ReadoutSettings.CORE_PLACE in core) throw Refused(s.shared("readout_error_core_place"))
        val written = (PLACEHOLDER.findAll(core) + PLACEHOLDER.findAll(settings.optString(ReadoutSettings.TEXT))).map { it.groupValues[1] }.toList()
        written.firstOrNull { it !in names }?.let { throw Refused(s.shared("readout_error_text_name").format(it, names.joinToString(", "))) }
        ConditionCheck(AppConditionSources(context, ReadoutRows)) { s.shared(it) }.refuse(ReadoutSettings.nodes(context), settings)?.let { throw Refused(it) }
        checkTarget(settings)

        val body = JSONObject().apply { ReadoutSettings.BODY_KEYS.forEach { key -> settings.opt(key)?.let { put(key, it) } } }
        return readout.copy(
            name = look.name,
            description = look.description,
            iconName = look.iconName!!,
            iconColor = look.iconColor,
            texture = look.texture,
            displayMode = look.displayMode.name,
            group = group,
            settingsJson = body.toString()
        )
    }

    /** Its values' names, each a name a sentence can write, given once, its term readable. */
    private suspend fun checkValues(settings: JSONObject): List<String> {
        val values = settings.optJSONArray(ReadoutSettings.VALUES) ?: JSONArray()
        val sources = AppConditionSources(context, null)
        val names = mutableListOf<String>()
        for (index in 0 until values.length()) {
            val value = values.getJSONObject(index)
            val name = value.optString(ReadoutSettings.NAME).trim()
            if (!VariableService.NAME.matches(name)) throw Refused(s.shared("readout_error_value_name").format(name))
            if (name in names) throw Refused(s.shared("readout_error_value_twice").format(name))
            val term = try {
                Term.fromJson(value.getJSONObject(ReadoutSettings.VALUE), name) { s.shared(it) }
            } catch (e: IllegalArgumentException) {
                throw Refused(e.message ?: "")
            }
            // A formula per entry is read in a variable only (TermReader)
            if (term is Term.Reading && term.perEntry != null) throw Refused(s.shared("readout_error_per_entry").format(name))
            try {
                TermFields.of(term, name, { s.shared(it) }, sources::targetFields, sources::variableField)
            } catch (e: IllegalArgumentException) {
                throw Refused(e.message ?: "")
            }
            value.put(ReadoutSettings.NAME, name)
            names += name
        }
        return names
    }

    /** Its target, a thing that is: the app, a zone or a tool. */
    private suspend fun checkTarget(settings: JSONObject) {
        val target = ReferenceTarget.referenceOf(settings.opt(ReadoutSettings.TARGET)) ?: throw Refused(s.shared("readout_error_target"))
        if (target.kind == ReferenceKind.APP) return
        val result = Coordinator(context).processUserAction("references.names", mapOf("references" to listOf(mapOf("kind" to target.kind.name, "id" to target.id))))
        if (!result.isSuccess) throw Refused(result.error ?: "")
        val gone = ((result.data?.get("references") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("deleted") == true
        if (gone) throw Refused(s.shared("readout_error_target_gone"))
    }

    /**
     * The tools [settings] reads, through its variables too: what, changing, changes what it
     * says. VariableService.ANY_TOOL when a variable may read any.
     */
    private suspend fun toolsRead(settings: JSONObject): Set<String> {
        val values = settings.optJSONArray(ReadoutSettings.VALUES) ?: return emptySet()
        val tools = mutableSetOf<String>()
        for (index in 0 until values.length()) {
            val term = values.optJSONObject(index)?.optJSONObject(ReadoutSettings.VALUE) ?: continue
            val target = term.optJSONObject("reading")?.optJSONObject("selection")?.optJSONObject("target")
            // A variable's series reads what its variable reads
            val seriesOf = target?.takeIf { it.optString("kind") == app.treelune.core.selection.ReferenceKind.VARIABLE.name }?.optString("id")
            if (seriesOf == null) target?.optString("id")?.takeIf { it.isNotEmpty() }?.let { tools += it }
            (seriesOf ?: term.optString("variable")).takeIf { it.isNotEmpty() }?.let { id ->
                val result = Coordinator(context).processUserAction("variables.get", mapOf("variable_id" to id))
                ((result.data?.get("variable") as? Map<*, *>)?.get("tools_read") as? List<*>)?.mapTo(tools) { it.toString() }
            }
        }
        return tools
    }

    // ========================================================================================
    // Reading
    // ========================================================================================

    private suspend fun read(params: JSONObject): OperationResult {
        val readout = readout(params)
        val at = if (params.has("at")) params.getLong("at") else System.currentTimeMillis()
        val settings = JSONObject(readout.settingsJson)
        val reader = TermReader(context)
        val values = settings.optJSONArray(ReadoutSettings.VALUES) ?: JSONArray()

        // Each value read once, kept with the field it is a value of
        val read = mutableMapOf<String, Pair<Any?, FieldDefinition?>>()
        val failures = mutableListOf<Map<String, Any?>>()
        for (index in 0 until values.length()) {
            val value = values.getJSONObject(index)
            val name = value.getString(ReadoutSettings.NAME)
            val term = Term.fromJson(value.getJSONObject(ReadoutSettings.VALUE), name) { s.shared(it) }
            when (val got = try { reader.read(term, at, null) } catch (e: IllegalStateException) { TermReader.Read.Failed(e.message ?: "", broken = true) }) {
                is TermReader.Read.Value -> read[name] = got.value to (got.field ?: ReadoutRows.constantField(term, name))
                is TermReader.Read.Failed -> {
                    read[name] = null to null
                    failures += mapOf("name" to name, "message" to got.message, "broken" to got.broken)
                }
            }
        }

        // Its core and its sentence, each value as its field shows it, one that gave nothing as « ? »;
        // the sentence with its core in its place, the core alone without one
        fun written(template: String) = PLACEHOLDER.replace(template) { match ->
            val (value, field) = read[match.groupValues[1]] ?: (null to null)
            if (value == null) s.shared("readout_value_missing") else field?.formatValue(value, context) ?: value.toString()
        }
        val core = written(settings.optString(ReadoutSettings.CORE))
        val text = settings.optString(ReadoutSettings.TEXT).takeIf { it.isNotBlank() }?.let { written(it).replace(ReadoutSettings.CORE_PLACE, core) } ?: core

        // The first colour whose condition holds; one not knowing is not holding
        val colors = settings.optJSONArray(ReadoutSettings.COLORS) ?: JSONArray()
        val resolver = TimeResolver.at(at)
        var color: String? = null
        var signal = false
        for (index in 0 until colors.length()) {
            val line = colors.getJSONObject(index)
            if (holds(Condition.fromJson(line.getJSONObject(ReadoutSettings.WHEN), readout.name) { s.shared(it) }, read, resolver) != true) continue
            color = line.getString(ReadoutSettings.COLOR)
            signal = line.optBoolean(ReadoutSettings.SIGNAL, false)
            break
        }
        return OperationResult.success(buildMap {
            put("readout_id", readout.id)
            put("core", core)
            put("text", text)
            color?.let { put("color", it) }
            put("signal", signal)
            put("failures", failures)
        })
    }

    /** Whether [condition] holds on the values [read], each side a value by its name or a written one; null when one is missing. */
    private fun holds(condition: Condition, read: Map<String, Pair<Any?, FieldDefinition?>>, resolver: TimeResolver): Boolean? {
        fun valueOf(side: Condition.Side): Any? = when (side) {
            is Condition.Side.Field -> read[side.path]?.first
            is Condition.Side.Of -> (side.term as? Term.Constant)?.value
        }
        val left = condition.left as? Condition.Side.Field ?: return null
        val field = read[left.path]?.second ?: return null
        return ConditionJudge.holds(field, condition.op, valueOf(left), condition.right.map { valueOf(it) }, resolver) { s.shared(it) }
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        val name = params.optString("name").ifEmpty { null } ?: params.optString("readout_id").ifEmpty { null }?.let { dao.get(it)?.name } ?: "?"
        return when (operation) {
            "create" -> s.shared("readout_verbalize_create").format(name)
            "update" -> s.shared("readout_verbalize_update").format(name)
            "delete" -> s.shared("readout_verbalize_delete").format(name)
            else -> s.shared("readout_verbalize_read")
        }
    }

    companion object {
        /** A value written in a sentence: `{name}`, a name a formula can write. */
        val PLACEHOLDER = Regex("\\{([\\p{L}_][\\p{L}\\p{N}_]*)\\}")
    }
}
