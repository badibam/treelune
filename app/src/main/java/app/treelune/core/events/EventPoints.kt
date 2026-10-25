package app.treelune.core.events

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.StringsContext
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * The places where the app tells the user something (docs/CORE.md, « Events »): an Agenda's
 * reminder, a List's due date passed, a Goal's attempt to validate, a Questionnaire's
 * invitation, a Sequence's planned session, a Message sent. Each may also emit an event, chosen in
 * its config under `emit_event` (`{"kind": "EVENT", "id"}`), which carries nothing or the entry
 * concerned: one value, an entry of that very tool. The budget's alert threshold may too, an event
 * carrying nothing (AILimitsConfig.budgetAlertEvent).
 */
object EventPoints {

    const val KEY = "emit_event"

    /** The setting's stored value for the event [id], as a column keeps it; null for none. */
    fun stored(id: String?): JSONObject? = id?.let { JSONObject().put("kind", ReferenceKind.EVENT.name).put("id", it) }

    /** The event's id the setting in [settings] holds, as a column keeps it; null for none. */
    fun idIn(settings: JSONObject): String? = ReferenceTarget.referenceOf(settings.opt(KEY))?.id

    /** The setting, beside what the place tells. */
    fun node(text: (String) -> String): SettingNode = SettingNode.Field(FieldDefinition(KEY, text("event_point"), text("event_point_schema"), FieldType.REFERENCE, false,
        mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.EVENT.name)))))

    /**
     * Why the event [config] names cannot be emitted by the tool [toolInstanceId] (null while it is
     * created); null when it can, or names none: it exists, and carries nothing or one entry of
     * that tool.
     */
    suspend fun refusal(config: JSONObject, toolInstanceId: String?, context: Context, s: StringsContext): String? {
        val reference = ReferenceTarget.referenceOf(config.opt(KEY)) ?: return null
        val event = AppDatabase.getDatabase(context).eventDao().get(reference.id ?: "") ?: return s.shared("trigger_error_event").format(reference.id)
        val values = EventValues.read(event.valuesJson)
        val fits = values.isEmpty() || (values.size == 1 && values[0].type == EventValues.Type.ENTRY && values[0].toolInstanceId == toolInstanceId)
        return if (fits) null else s.shared("event_point_error").format(event.name)
    }

    /**
     * Why the event [chosen] designates (the stored value of a setting) cannot be emitted where no
     * entry is concerned, as the budget's alert; null when it can, or none is chosen: it exists, and
     * carries nothing.
     */
    suspend fun refusalCarryingNothing(chosen: Any?, context: Context, s: StringsContext): String? {
        val reference = ReferenceTarget.referenceOf(chosen) ?: return null
        val event = AppDatabase.getDatabase(context).eventDao().get(reference.id ?: "") ?: return s.shared("trigger_error_event").format(reference.id)
        return if (EventValues.read(event.valuesJson).isEmpty()) null else s.shared("event_point_error_values").format(event.name)
    }

    /**
     * The event [config] names emitted, carrying [entryId] when it declares an entry; nothing when
     * it names none. A failure is logged: what the place tells goes on all the same.
     */
    suspend fun emit(context: Context, config: JSONObject, entryId: String?) = emit(context, config.opt(KEY), entryId)

    /** The event [chosen] designates (the stored value of the setting) emitted, as [emit] says. */
    suspend fun emit(context: Context, chosen: Any?, entryId: String?) {
        val reference = ReferenceTarget.referenceOf(chosen) ?: return
        val event = AppDatabase.getDatabase(context).eventDao().get(reference.id ?: "") ?: run {
            LogManager.service("EventPoints: event ${reference.id} gone, not emitted", "WARN")
            return
        }
        val values = EventValues.read(event.valuesJson).firstOrNull()?.let { mapOf(it.name to entryId) } ?: emptyMap()
        val emitted = Coordinator(context).processScheduledTask("events.emit", mapOf("event_id" to event.id, "values" to values))
        if (!emitted.isSuccess) LogManager.service("EventPoints: ${event.name} not emitted: ${emitted.error}", "ERROR")
    }
}
