package app.treelune.core.events

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.database.AppDatabase
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The events of the zones (docs/CORE.md, « Events »), for the screens and the AI alike.
 *
 * - list (`zone_id`, all of them without), get (`event_id`): each with its zone, its name, its values
 * - create: `zone_id`, `name` (unique in its zone), `description`?, `values` (EventValues), none when left out
 * - update: `event_id`, `name`, `description` (null empties it) and `values`, each kept when left out
 * - delete: `event_id`; refused while something listens to it or emits it, named
 * - emit: `event_id`, `values` (by name, each of its type): what listens to it starts, in the
 *   emitter's context for its chain
 *
 * Its rows go through the history's door: a change is undone as a variable's.
 */
class EventService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val dao = AppDatabase.getDatabase(context).eventDao()
    private val writes = app.treelune.core.history.Writes(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "list" -> OperationResult.success(mapOf("events" to
                (params.optString("zone_id").ifEmpty { null }?.let { dao.getByZone(it) } ?: dao.getAll()).map { describe(it) }))
            "get" -> event(params)?.let { OperationResult.success(mapOf("event" to describe(it))) } ?: notFound()
            "create" -> create(params)
            "update" -> update(params)
            "delete" -> delete(params)
            "emit" -> emit(params)
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun event(params: JSONObject) = params.optString("event_id").ifEmpty { null }?.let { dao.get(it) }

    private fun notFound() = OperationResult.error(s.shared("event_error_not_found"))

    private fun describe(event: EventEntity): Map<String, Any?> = mapOf(
        "id" to event.id,
        "zone_id" to event.zoneId,
        EventValues.NAME to event.name,
        EventValues.DESCRIPTION to event.description,
        EventValues.VALUES to JsonUtils.toList(event.valuesJson)
    )

    /** The schema an event's name and values are checked against, from their declaration. */
    private fun schema(): Schema = Schema("event", s.shared("event_display_name"), "", SchemaCategory.TASK_CONFIG,
        app.treelune.core.fields.settings.SettingsSchemaGenerator.generate(EventValues.nodes(s::shared), s::shared).toString())

    /** [event] holding what [params] give, checked: its name and values against their schema, its name unique in its zone. */
    private suspend fun checked(event: EventEntity, params: JSONObject): Result<EventEntity> {
        fun refused(message: String) = Result.failure<EventEntity>(IllegalArgumentException(message))
        val name = if (params.has(EventValues.NAME)) params.optString(EventValues.NAME).trim() else event.name
        val values = if (params.has(EventValues.VALUES)) params.optJSONArray(EventValues.VALUES) ?: return refused(s.shared("event_error_values")) else JSONArray(event.valuesJson)
        val description = if (params.has(EventValues.DESCRIPTION)) params.optString(EventValues.DESCRIPTION).trim().takeUnless { params.isNull(EventValues.DESCRIPTION) || it.isEmpty() } else event.description
        val settings = JSONObject().put(EventValues.NAME, name).put(EventValues.VALUES, values).apply { description?.let { put(EventValues.DESCRIPTION, it) } }
        val validation = app.treelune.core.validation.SchemaValidator.validate(schema(), JsonUtils.toMap(settings), context)
        if (!validation.isValid) return refused(validation.errorMessage ?: s.shared("message_validation_error_simple"))
        val declared = try {
            EventValues.read(values.toString())
        } catch (e: IllegalArgumentException) {
            return refused(e.message ?: s.shared("event_error_values"))
        }
        if (declared.map { it.name }.toSet().size != declared.size) return refused(s.shared("event_error_value_twice"))
        val tools = AppDatabase.getDatabase(context).toolInstanceDao()
        declared.mapNotNull { it.toolInstanceId }.forEach { tools.getToolInstanceById(it) ?: return refused(s.shared("trigger_error_tool").format(it)) }
        if (dao.getByZone(event.zoneId).any { it.id != event.id && it.name.equals(name, ignoreCase = true) }) return refused(s.shared("event_error_name_taken").format(name))
        return Result.success(event.copy(name = name, description = description, valuesJson = values.toString()))
    }

    private suspend fun create(params: JSONObject): OperationResult {
        val zoneId = params.optString("zone_id").ifEmpty { return OperationResult.error(s.shared("error_param_zone_id_required")) }
        AppDatabase.getDatabase(context).zoneDao().getZoneById(zoneId) ?: return OperationResult.error(s.shared("service_error_zone_not_found"))
        // The app's own demo gives its ids; every other caller gets one made here
        val eventId = when (val given = app.treelune.core.coordinator.GivenId.read(params)) {
            app.treelune.core.coordinator.GivenId.Read.None -> UUID.randomUUID().toString()
            is app.treelune.core.coordinator.GivenId.Read.Accepted -> given.id
            is app.treelune.core.coordinator.GivenId.Read.Refused -> return OperationResult.error(s.shared("service_error_id_not_given").format(given.id))
        }
        val now = System.currentTimeMillis()
        val event = checked(EventEntity(eventId, zoneId, "", "[]", now, now), params)
            .getOrElse { return OperationResult.error(it.message ?: s.shared("message_validation_error_simple")) }
        writes.insertEvent(event)
        DataChangeNotifier.notifyZonesChanged()
        return OperationResult.success(mapOf("event_id" to event.id, "name" to event.name))
    }

    private suspend fun update(params: JSONObject): OperationResult {
        val stored = event(params) ?: return notFound()
        val event = checked(stored.copy(updatedAt = System.currentTimeMillis()), params)
            .getOrElse { return OperationResult.error(it.message ?: s.shared("message_validation_error_simple")) }
        writes.updateEvent(event)
        DataChangeNotifier.notifyZonesChanged()
        return OperationResult.success(mapOf("event_id" to event.id, "name" to event.name))
    }

    private suspend fun delete(params: JSONObject): OperationResult {
        val event = event(params) ?: return notFound()
        EventUses.of(context, event.id).takeIf { it.isNotEmpty() }?.let { uses ->
            return OperationResult.error(s.shared("event_error_in_use").format(event.name, uses.joinToString(", ")))
        }
        writes.deleteEvent(event.id)
        DataChangeNotifier.notifyZonesChanged()
        return OperationResult.success(mapOf("event_id" to event.id, "name" to event.name))
    }

    private suspend fun emit(params: JSONObject): OperationResult {
        val event = event(params) ?: return notFound()
        val values = params.optJSONObject(EventValues.VALUES) ?: JSONObject()
        val declared = EventValues.read(event.valuesJson)
        EventValues.refusal(declared, values, s::shared)?.let { return OperationResult.error(it) }
        // What it carries is checked at the emitter's: an AI held to a mask carries only entries it
        // may read, or it would choose the target of a Task stronger than itself
        kotlin.coroutines.coroutineContext[app.treelune.core.access.AccessScope]?.let { scope ->
            val carried = declared.filter { it.type == EventValues.Type.ENTRY }.mapNotNull { values.optString(it.name).ifEmpty { null } }
                .map { app.treelune.core.access.Reach.Entry(it, app.treelune.core.access.AccessLevel.READ) }
            app.treelune.core.access.AccessRules.refusal(scope.mask, scope.holder, carried, app.treelune.core.access.DatabaseAccessLookups(context), s::shared)
                ?.let { return OperationResult.error(it) }
        }
        EventTriggers.dispatch(context, event, values)
        return OperationResult.success(mapOf("event_id" to event.id, "name" to event.name))
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        val name = params.optString("name").ifEmpty { null } ?: params.optString("event_id").ifEmpty { null }?.let { dao.get(it)?.name } ?: "?"
        return when (operation) {
            "create" -> s.shared("event_verbalize_create").format(name)
            "update" -> s.shared("event_verbalize_update").format(name)
            "delete" -> s.shared("event_verbalize_delete").format(name)
            "emit" -> s.shared("event_verbalize_emit").format(name)
            else -> s.shared("event_verbalize_list")
        }
    }
}
