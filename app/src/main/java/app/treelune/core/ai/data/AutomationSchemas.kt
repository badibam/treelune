package app.treelune.core.ai.data

import android.content.Context
import app.treelune.core.access.AccessGrant
import app.treelune.core.access.AccessLevel
import app.treelune.core.fields.FieldValueSchema
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the AI reads and writes of a routine (docs/AI.md), each described by a
 * schema: its config, read by AUTOMATION_CONFIG and written by CREATE_AUTOMATION and
 * UPDATE_AUTOMATION in the same shape, and its runs, read by AUTOMATION_RUNS. Their instants and
 * durations are marked (FieldValueSchema), and so reach the AI in ISO 8601 and come back in
 * milliseconds, by ModelValues as everything else's.
 */
object AutomationSchemas {

    const val CONFIG_ID = "automation_config"
    const val RUNS_ID = "automation_runs"

    /** A routine's config: its zone, its settings (AutomationSettings), its access (AccessMask) and its message (AutomationMessage). */
    fun config(context: Context): Schema {
        val s = Strings.`for`(context = context)
        val content = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("zone_id", JSONObject().put("type", "string"))
                .put("settings", SettingsSchemaGenerator.generate(AutomationSettings.nodes(context), s::shared))
                .put("access", access())
                .put("message", message(s::shared)))
            .put("additionalProperties", false)
        return Schema(CONFIG_ID, s.shared("automation_display_name"), "", SchemaCategory.AUTOMATION_CONFIG, content.toString())
    }

    /** A list of `{"target": {"kind", "id"}, "level"}`, empty for everything. */
    private fun access(): JSONObject = JSONObject()
        .put("type", "array")
        .put("items", JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("target", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()
                        .put("kind", JSONObject().put("enum", JSONArray(AccessGrant.KINDS.map { it.name })))
                        .put("id", JSONObject().put("type", "string")))
                    .put("required", JSONArray().put("kind").put("id"))
                    .put("additionalProperties", false))
                .put("level", JSONObject().put("enum", JSONArray(AccessLevel.entries.map { it.key }))))
            .put("required", JSONArray().put("target").put("level"))
            .put("additionalProperties", false))

    /**
     * The blocks of a message, text or pointer, told apart by their type. A pointer's period is the
     * one a pointer of the screen stores, relative to the scheduled time of the run.
     */
    internal fun message(text: (String) -> String): JSONObject {
        val period = SettingsSchemaGenerator.generate(listOf(SettingNode.Period("period", text("pointer_period"), text("instant_reference_scheduled_run"))), text)
            .getJSONObject("properties").getJSONObject("period")
        val text = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("type", JSONObject().put("const", AutomationMessage.TEXT))
                .put("text", JSONObject().put("type", "string")))
            .put("required", JSONArray().put("type").put("text"))
            .put("additionalProperties", false)
        val pointer = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("type", JSONObject().put("const", AutomationMessage.POINTER))
                .put("pointer", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()
                        .put("selection", JSONObject()
                            .put("type", "object")
                            .put("description", text("selection_schema_description"))
                            .put("properties", JSONObject()
                                .put("target", JSONObject().put("type", "object"))
                                .put("period", period)
                                .put("filters", JSONObject().put("type", "array").put("items", JSONObject().put("type", "object")))
                                .put("fields", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))))
                            .put("required", JSONArray().put("target"))
                            .put("additionalProperties", false))
                        .put("attach", JSONObject()
                            .put("type", "object")
                            .put("properties", JSONObject()
                                .put("config", JSONObject().put("type", "boolean"))
                                .put("entries", JSONObject().put("type", "boolean")))
                            .put("additionalProperties", false)))
                    .put("required", JSONArray().put("selection"))
                    .put("additionalProperties", false)))
            .put("required", JSONArray().put("type").put("pointer"))
            .put("additionalProperties", false)
        return JSONObject().put("type", "array").put("items", JSONObject().put("oneOf", JSONArray().put(text).put(pointer)))
    }

    /** The keys of a run kept for the AI, as the sessions service names them (list_sessions_for_automation). */
    val RUN_KEYS = listOf("id", "scheduled_execution_time", "created_at", "end_reason", "total_roundtrips", "total_cost", "cost_is_lower_bound")

    /**
     * A routine's runs, the most recent first: when each was scheduled for and when it started,
     * why it ended (SessionEndReason, none while it runs or after a crash), its round trips with
     * the AI, its cost in dollars, a lower bound when a call's price is unknown.
     */
    fun runs(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(RUNS_ID, s.shared("automation_runs_display_name"), "", SchemaCategory.AUTOMATION_CONFIG, runsContent().toString())
    }

    /** The content of the runs' schema, which needs no context. */
    internal fun runsContent(): JSONObject {
        fun instant() = JSONObject().put("type", "integer").put("format", FieldValueSchema.EPOCH_MILLIS)
        val run = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("id", JSONObject().put("type", "string"))
                .put("scheduled_execution_time", instant())
                .put("created_at", instant())
                .put("end_reason", JSONObject().put("type", JSONArray().put("string").put("null")).put("enum", JSONArray(SessionEndReason.entries.map { it.name } + JSONObject.NULL)))
                .put("total_roundtrips", JSONObject().put("type", "integer"))
                .put("total_cost", JSONObject().put("type", "number"))
                .put("cost_is_lower_bound", JSONObject().put("type", "boolean")))
            .put("additionalProperties", false)
        return JSONObject()
            .put("type", "object")
            .put("properties", JSONObject().put("runs", JSONObject().put("type", "array").put("items", run)))
    }

    /**
     * A routine of a list as the AI reads it, in the app's state and in AUTOMATIONS: its id, name,
     * zone and group, whether it is on, and its schedule in a sentence, the one its card shows.
     * Never its message, read with AUTOMATION_CONFIG.
     */
    fun listed(row: Map<*, *>, s: app.treelune.core.strings.StringsContext): Map<String, Any?> = linkedMapOf(
        "id" to row["id"],
        "name" to row["name"],
        "zone_id" to row["zone_id"],
        "group" to row["group"],
        "is_enabled" to row["is_enabled"],
        "schedule" to ((row["schedule"] as? Map<*, *>)?.let { schedule ->
            app.treelune.core.fields.settings.scheduleSummary(
                kotlinx.serialization.json.Json.decodeFromString(app.treelune.core.utils.ScheduleConfig.serializer(), JSONObject(schedule).toString()), s)
        } ?: s.shared("schedule_summary_none"))
    )
}
