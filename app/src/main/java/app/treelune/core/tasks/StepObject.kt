package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.ai.enrichments.PointerConfig
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceName
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.ToolFields
import app.treelune.core.fields.formatValue
import app.treelune.core.fields.loadReferenceNames
import app.treelune.core.reading.FieldReading
import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.StringsContext
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * The object an `assemble` step makes (docs/CORE.md, « Tasks »): keys the user names, each
 * holding one part, stored under `object` as `{"<key>": <part>}`, a part being one of
 *
 * - `{"text": "…"}`, a text as written;
 * - `{"pointer": {"selection", "attach": {"config", "entries"}, "rendering"}}`, entries of a tool
 *   (its order and its count included) or one entry, rendered Readable or Technical;
 * - `{"entry": <input>}`, an entry the run gives (the one a trigger gave, the one a step created),
 *   rendered as a pointer to it;
 * - `{"value": <input>}`, a value the run reads or was given, as it is;
 * - `{"object": {…}}`, more keys, the same rule at each level.
 *
 * The step gives the object under [OUTPUT], of type object (ApiSettings.OBJECT), to the steps after
 * it: an API's input of type object, a JSON field (TextLength.JSON). It writes nothing.
 *
 * Rendered **Readable**, a pointer's entries keep each field under its label, a value as the app
 * says it (a scale with its legend, a choice by its option, a reference by the name of what it
 * designates, a date as a person reads it), the empty fields left out, each entry's id always and
 * nothing else technical; its config attached, the tool comes down to its name, its description
 * and what each field kept means. Rendered **Technical**, each field goes under the last part of
 * its path with its stored value, as an API's list receives it; its config attached, whole.
 */
object StepObject {

    const val OBJECT = "object"
    const val TEXT = "text"
    const val POINTER = "pointer"
    const val ENTRY = "entry"
    const val VALUE = "value"
    const val RENDERING = "rendering"
    const val READABLE = "readable"
    const val TECHNICAL = "technical"

    /** The name the step's object goes under, for the steps after it (`{"result": {"step", "value": "object"}}`). */
    const val OUTPUT = "object"

    sealed interface Part {
        data class Text(val text: String) : Part
        data class Pointer(val pointer: PointerConfig, val readable: Boolean) : Part
        data class Entry(val input: JSONObject) : Part
        data class Value(val input: JSONObject) : Part
        data class Obj(val parts: Map<String, Part>) : Part
    }

    /**
     * The parts [json] stores, by key, in their order.
     *
     * @throws IllegalArgumentException naming the first key, by its path, that does not read
     */
    fun read(json: JSONObject, text: (String) -> String, path: String = ""): Map<String, Part> =
        json.keys().asSequence().toList().associateWith { key ->
            val at = if (path.isEmpty()) key else "$path › $key"
            if (key.isBlank()) throw IllegalArgumentException(text("task_error_object_key").format(at))
            val part = json.optJSONObject(key) ?: throw IllegalArgumentException(text("task_error_object_part").format(at))
            when {
                part.has(TEXT) -> Part.Text(part.optString(TEXT))
                part.has(POINTER) -> {
                    val pointer = part.optJSONObject(POINTER) ?: throw IllegalArgumentException(text("task_error_object_part").format(at))
                    Part.Pointer(PointerConfig.fromJson(pointer, text), pointer.optString(RENDERING, READABLE) != TECHNICAL)
                }
                part.has(ENTRY) -> Part.Entry(part.optJSONObject(ENTRY) ?: throw IllegalArgumentException(text("task_error_object_part").format(at)))
                part.has(VALUE) -> Part.Value(part.optJSONObject(VALUE) ?: throw IllegalArgumentException(text("task_error_object_part").format(at)))
                part.has(OBJECT) -> Part.Obj(read(part.optJSONObject(OBJECT) ?: throw IllegalArgumentException(text("task_error_object_part").format(at)), text, at))
                else -> throw IllegalArgumentException(text("task_error_object_part").format(at))
            }
        }

    /** Every part of [parts] with its path, down every object. */
    fun flat(parts: Map<String, Part>, path: String = ""): List<Pair<String, Part>> = parts.flatMap { (key, part) ->
        val at = if (path.isEmpty()) key else "$path › $key"
        listOf(at to part) + ((part as? Part.Obj)?.let { flat(it.parts, at) } ?: emptyList())
    }

    /**
     * Why a pointer of the object cannot be rendered; null when it can: entries of a tool, in
     * their order, or one entry; Readable, no two fields it keeps under one label — one key would
     * hide the other.
     */
    suspend fun pointerRefusal(path: String, part: Part.Pointer, context: Context, s: StringsContext): String? {
        val selection = part.pointer.selection
        if (selection.target.kind != ReferenceKind.TOOL_INSTANCE && selection.target.kind != ReferenceKind.ENTRY) {
            return s.shared("task_error_object_pointer_target").format(path)
        }
        (selection.problem { s.shared(it) } ?: selection.unordered { s.shared(it) })?.let { return "$path: $it" }
        if (!part.readable) return null
        val toolId = toolOf(selection.target, context) ?: return s.shared("service_error_entry_not_found").format(selection.target.id)
        val fields = try {
            shown(ToolFields.filterable(toolId, context, s), selection.fields)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            return "$path: ${e.message}"
        }
        return fields.values.groupBy { it.displayName.trim().lowercase() }.values.firstOrNull { it.size > 1 }
            ?.let { same -> s.shared("task_error_object_labels").format(path, same.first().displayName) }
    }

    /**
     * The object [parts] make at this run: [resolve] reads a value's input, [entryOf] gives the id
     * of the entry an entry's input designates.
     *
     * @throws IllegalStateException said in words, a source that cannot be read
     */
    suspend fun render(parts: Map<String, Part>, at: Long, context: Context, s: StringsContext,
                       resolve: suspend (JSONObject) -> Any?, entryOf: suspend (JSONObject) -> String): Map<String, Any?> =
        parts.mapValues { (_, part) ->
            when (part) {
                is Part.Text -> part.text
                is Part.Pointer -> pointer(part.pointer, part.readable, at, context, s)
                is Part.Entry -> pointer(PointerConfig(EntrySelection(Reference(ReferenceKind.ENTRY, entryOf(part.input))), entries = true), readable = true, at, context, s)
                is Part.Value -> resolve(part.input)
                is Part.Obj -> render(part.parts, at, context, s, resolve, entryOf)
            }
        }

    /** The tool of [target]: itself, or one entry's. */
    private suspend fun toolOf(target: Reference, context: Context): String? = when (target.kind) {
        ReferenceKind.ENTRY -> app.treelune.core.database.AppDatabase.getDatabase(context).toolDataDao().getById(target.id!!)?.toolInstanceId
        else -> target.id
    }

    /**
     * The fields a pointer shows, by path: those it keeps, or every field a person reads of an
     * entry (its name, its date, the tool type's and the user's), never its state nor when it was
     * created or changed unless kept.
     */
    private fun shown(fields: Map<String, FieldDefinition>, kept: List<String>?): Map<String, FieldDefinition> =
        if (kept != null) kept.mapNotNull { path -> fields[path]?.let { path to it } }.toMap()
        else fields.filterKeys { it == "name" || it == "timestamp" || it.startsWith("data.") || it.startsWith("extra.") }

    /** A pointer's entries rendered, with its tool when its config is attached. */
    private suspend fun pointer(pointer: PointerConfig, readable: Boolean, at: Long, context: Context, s: StringsContext): Any? {
        val selection = pointer.selection
        val toolId = toolOf(selection.target, context) ?: throw IllegalStateException(s.shared("service_error_entry_not_found").format(selection.target.id))
        val all = ToolFields.filterable(toolId, context, s)
        val fields = shown(all, selection.fields)
        val one = selection.target.kind == ReferenceKind.ENTRY
        val filters = if (one) org.json.JSONArray().put(app.treelune.core.conditions.Conditions.onField(app.treelune.core.fields.EntryFilters.ID, "=", selection.target.id))
            else selection.storedFilters(all, TimeResolver.at(at)) { s.shared(it) }
        val read = Coordinator(context).processTaskStep("tool_data.get", mapOf(
            "tool_instance_id" to toolId,
            "filters" to JsonUtils.toList(filters),
            "reference" to at
        ) + (if (one) emptyMap() else selection.orderParams()))
        if (!read.isSuccess) throw IllegalStateException(read.error ?: s.shared("error_unknown"))
        @Suppress("UNCHECKED_CAST")
        val rows = (read.data?.get("entries") as? List<Map<String, Any?>>).orEmpty()
        val names = if (readable) referenceNames(rows, fields, context, s) else emptyMap()
        val entries = rows.map { row -> if (readable) readableEntry(row, fields, names, context, s) else technicalEntry(row, fields) }
        val body: Any? = if (one) entries.firstOrNull() else entries
        if (!pointer.config) return body
        val tool = Coordinator(context).processTaskStep("tools.get", mapOf("tool_instance_id" to toolId)).data?.get("tool_instance") as? Map<*, *>
            ?: throw IllegalStateException(s.shared("service_error_tool_instance_not_found"))
        val config = tool["config"] as? Map<*, *> ?: emptyMap<String, Any?>()
        return if (readable) buildMap {
            put(s.shared("task_object_tool"), config["name"] ?: tool["name"])
            (config["description"] as? String)?.takeIf { it.isNotBlank() }?.let { put(s.shared("task_object_description"), it) }
            fields.values.mapNotNull { field -> meaning(field, s)?.let { field.displayName to it } }.toMap().takeIf { it.isNotEmpty() }
                ?.let { put(s.shared("task_object_fields"), it) }
            put(s.shared(if (one) "task_object_entry" else "task_object_entries"), body)
        } else mapOf("tool" to tool["name"], "config" to config, "entries" to body)
    }

    /** What a field says to whoever reads it: its description, or what its values mean (a scale's bounds, a choice's options). */
    private fun meaning(field: FieldDefinition, s: StringsContext): String? {
        val config = field.config.orEmpty()
        val values = when (field.type) {
            FieldType.SCALE -> s.shared("task_object_scale").format(
                config["min"]?.toString().orEmpty() + ((config["min_label"] as? String)?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                config["max"]?.toString().orEmpty() + ((config["max_label"] as? String)?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""))
            FieldType.CHOICE -> ChoiceSettings.fromConfig(config).let { choice -> choice.options.joinToString(", ") { choice.labelOf(it) } }.takeIf { it.isNotEmpty() }
            else -> null
        }
        return listOfNotNull(field.description?.takeIf { it.isNotBlank() }, values).joinToString(" ").takeIf { it.isNotEmpty() }
    }

    /** The names of what the references of [rows] designate, read once. */
    private suspend fun referenceNames(rows: List<Map<String, Any?>>, fields: Map<String, FieldDefinition>, context: Context, s: StringsContext): Map<Reference, String> {
        val paths = fields.filterValues { it.type == FieldType.REFERENCE }.keys
        val references = rows.flatMap { row -> paths.flatMap { ReferenceTarget.referencesOf(FieldReading.valueAt(row, it)) } }.toSet()
        if (references.isEmpty()) return emptyMap()
        return loadReferenceNames(references, context).mapValues { (_, name) -> (name as? ReferenceName.Named)?.name ?: s.shared("pointer_target_deleted") }
    }

    /** One entry, Readable: its id, then each field it fills under its label, as the app says it. */
    private fun readableEntry(row: Map<String, Any?>, fields: Map<String, FieldDefinition>, names: Map<Reference, String>, context: Context, s: StringsContext): Map<String, Any?> =
        buildMap {
            put("id", row["id"])
            fields.forEach { (path, field) ->
                val value = FieldReading.valueAt(row, path) ?: return@forEach
                put(field.displayName, when (field.type) {
                    // A text as it was written, its marks kept
                    FieldType.TEXT -> value.toString()
                    FieldType.REFERENCE -> ReferenceTarget.referencesOf(value).map { names[it] ?: s.shared("pointer_target_deleted") }
                        .let { list -> if (ReferenceTarget.isMultiple(field.config)) list else list.firstOrNull() }
                    else -> field.formatValue(value, context)
                })
            }
        }

    /** One entry, Technical: its id, then each field it fills under the last part of its path, its stored value. */
    private fun technicalEntry(row: Map<String, Any?>, fields: Map<String, FieldDefinition>): Map<String, Any?> = buildMap {
        put("id", row["id"])
        fields.keys.forEach { path -> FieldReading.valueAt(row, path)?.let { put(TaskSteps.keyOf(path), it) } }
    }
}
