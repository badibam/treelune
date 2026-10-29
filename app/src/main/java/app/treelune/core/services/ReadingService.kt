package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.ToolFields
import app.treelune.core.fields.toJson
import app.treelune.core.reading.FailureReason
import app.treelune.core.conditions.Condition
import app.treelune.core.conditions.ConditionJudge
import app.treelune.core.reading.FieldReading
import app.treelune.core.reading.GroupedReading
import app.treelune.core.reading.ReadingGroup
import app.treelune.core.terms.TermReader
import app.treelune.core.reading.ReadingResult
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.EntryPeriod
import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.Strings
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * The core's reading: one value from the entries of one tool instance, or from one entry
 * (docs/DATA.md, « Reading of the core »), the door every reader goes through -- a goal's
 * criterion, a variable's term, a step's file.
 *
 * - read, a variable: `variable` (its name) and `at`, the instants to read it at (milliseconds);
 *   a value or a failure per instant, in the same order (the variables service computes it).
 * - read, a field: `selection` (a tool instance, its period and filters, relative dates resolved against
 *   `reference`, milliseconds, which the reader gives: a goal's attempt end, the instant a
 *   variable is read at), `field` (a path, absent to count) and `reduction`. It gives `value` with
 *   `field`, the type and settings of the result, or `failure` with its causes as data: a reading
 *   without a value is an answer, which each reader decides what to do with.
 * - read, a field at several instants: `at` in place of `reference`, the instants a reader reads a
 *   range at (a chart's grid, a step each); `field` once, and `values`, a value or a failure per
 *   instant in the same order, `{"at", "value" | "failure"}`. One read of the entries serves them
 *   all when the filters say no relative date: only the period then moves with the instant.
 * - grouped, `group` beside (ReadingGroup): each group of entries reduced by `field` and
 *   `reduction`, the groups kept, their values reduced again; the units of a period are its
 *   groups even without entries, counted from its start (required) to its end, or to the instant
 *   read without one; the value the groups are compared with is read once at that instant.
 */
class ReadingService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "read" -> read(params)
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun read(params: JSONObject): OperationResult {
        // A variable: computed at each instant asked, by the variables service
        params.optString("variable").takeIf { it.isNotEmpty() }?.let { name ->
            val result = Coordinator(context).processUserAction("variables.evaluate", mapOf(
                "name" to name,
                "at" to (params.optJSONArray("at")?.let { JsonUtils.toList(it) }
                    ?: return OperationResult.error(s.shared("service_error_reading_param").format("at")))
            ))
            return if (result.isSuccess) OperationResult.success(result.data ?: emptyMap()) else OperationResult.error(result.error ?: "")
        }
        val selection = try {
            EntrySelection.fromJson(params.optJSONObject("selection")
                ?: return OperationResult.error(s.shared("service_error_reading_param").format("selection"))) { s.shared(it) }
        } catch (e: IllegalArgumentException) {
            return OperationResult.error(e.message ?: s.shared("service_error_reading_param").format("selection"))
        }
        selection.problem { s.shared(it) }?.let { return OperationResult.error(it) }
        if (selection.target.kind != ReferenceKind.TOOL_INSTANCE && selection.target.kind != ReferenceKind.ENTRY) {
            return OperationResult.error(s.shared("service_error_reading_source").format(selection.target.kind.name))
        }
        // One instant, or several read in one go
        val instants = params.optJSONArray("at")?.let { array ->
            (0 until array.length()).map { (array.opt(it) as? Number)?.toLong() ?: return OperationResult.error(s.shared("service_error_reading_param").format("at")) }
        }
        val reference = (params.opt("reference") as? Number)?.toLong()
        if (instants == null && reference == null) return OperationResult.error(s.shared("service_error_reading_param").format("reference"))
        val reduction = Reduction.entries.firstOrNull { it.name == params.optString("reduction") }
            ?: return OperationResult.error(s.shared("service_error_reading_reduction").format(
                params.optString("reduction"), Reduction.entries.joinToString(", ") { it.name }))
        // A source deleted since is an answer, not an error: whoever reads decides what it means.
        // A read that fails is an error, never taken for a deletion
        when (sourceGone(selection.target)) {
            null -> return OperationResult.error(s.shared("service_error_reading_source_unread"))
            true -> {
                val gone = failure(ReadingResult.Failure(FailureReason.SOURCE_NOT_FOUND, null))
                return OperationResult.success(if (instants == null) gone else mapOf("values" to instants.map { mapOf("at" to it) + gone }))
            }
            false -> {}
        }
        // One entry read is the only entry of its reading, in its tool; a tool's are those its filters keep
        val entry = if (selection.target.kind == ReferenceKind.ENTRY) {
            app.treelune.core.database.AppDatabase.getDatabase(context).toolDataDao().getById(selection.target.id!!)
                ?.let { app.treelune.core.services.ToolDataEntries.toMap(it) }
                ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(selection.target.id))
        } else null
        val toolInstanceId = entry?.get("tool_instance_id") as? String ?: selection.target.id!!
        suspend fun entries(toolInstanceId: String, filters: org.json.JSONArray): List<Map<String, Any?>> =
            if (entry != null) listOf(entry) else readEntries(toolInstanceId, filters)

        val fields = ToolFields.filterable(toolInstanceId, context, s)
        val path = params.optString("field").takeIf { it.isNotEmpty() }
        val field = path?.let { fields[it] ?: return OperationResult.error(
            s.shared("service_error_filter_unknown_field").format(it, fields.keys.joinToString(", "))) }
        if (reduction !in Reduction.forField(field)) {
            return OperationResult.error(s.shared("service_error_reading_reduction_type").format(
                reduction.name, field?.type?.name ?: "-", Reduction.forField(field).joinToString(", ") { it.name }))
        }
        val group = try {
            params.optJSONObject("group")?.let { ReadingGroup.fromJson(it) { key -> s.shared(key) } }
        } catch (e: IllegalArgumentException) {
            return OperationResult.error(e.message ?: "")
        }
        val value = field.takeIf { reduction != Reduction.COUNT } ?: FieldReading.COUNT_FIELD
        group?.problem(fields, value) { s.shared(it) }?.let { return OperationResult.error(it) }
        if (group != null && GroupedReading.needsStart(group) && selection.period.start == null) {
            return OperationResult.error(s.shared("reading_error_group_start"))
        }
        val keep = group?.keepCondition()?.let { Condition.fromJson(it, "", { key -> s.shared(key) }) }

        /** The entries of one instant's period reduced: grouped, or all at once. */
        suspend fun reduced(rows: List<Map<String, Any?>>, at: Long): ReadingResult {
            group ?: return FieldReading.reduce(rows, path, field, reduction, at)
            val calendar = TimeResolver.at(at)
            val (start, end) = selection.period.instants(calendar)
            val right = keep?.right?.map { side ->
                when (val read = TermReader(context).read((side as Condition.Side.Of).term, at, null)) {
                    is TermReader.Read.Value -> read.value
                    is TermReader.Read.Failed -> throw IllegalStateException(s.shared("reading_error_group_keep").format(read.message))
                }
            }
            return GroupedReading.reduce(rows, path, field, reduction, group, fields.getValue(group.field),
                GroupedReading.listed(group, fields.getValue(group.field), start, end ?: at, calendar), calendar,
                { v -> ConditionJudge.holds(value, keep!!.op, v, right!!, calendar) { s.shared(it) } == true }, at)
        }

        if (instants == null) {
            val rows = try { entries(toolInstanceId, selection.storedFilters(fields, TimeResolver.at(reference!!)) { s.shared(it) }) }
                catch (e: IllegalArgumentException) { return OperationResult.error(e.message ?: "") }
                catch (e: IllegalStateException) { return OperationResult.error(e.message ?: "") }
            return try {
                OperationResult.success(result(reduced(rows, reference)))
            } catch (e: IllegalStateException) {
                OperationResult.error(e.message ?: "")
            }
        }

        // Several instants: the filters on values are the same at each when none holds a relative
        // date, and the entries of every period are then read at once, each instant keeping those
        // its own period takes. Otherwise each instant reads its own
        val values = try {
            if (selection.filtersMove) {
                instants.map { at -> at to reduced(entries(toolInstanceId, selection.storedFilters(fields, TimeResolver.at(at)) { s.shared(it) }), at) }
            } else {
                val ranges = instants.map { at -> at to selection.period.instants(TimeResolver.at(at)) }
                // Every period at once: from the earliest start to the latest end, a side without
                // limit at one instant being without limit for all
                val union = EntryPeriod(
                    start = ranges.map { it.second.first }.takeIf { starts -> starts.none { it == null } }?.minOfOrNull { it!! }?.let { TimePoint.Fixed(it) },
                    end = ranges.map { it.second.second }.takeIf { ends -> ends.none { it == null } }?.maxOfOrNull { it!! }?.let { TimePoint.Fixed(it) }
                )
                val rows = entries(toolInstanceId, selection.copy(period = union).storedFilters(fields, TimeResolver.at(instants.first())) { s.shared(it) })
                ranges.map { (at, range) ->
                    val (start, end) = range
                    at to reduced(rows.filter { row ->
                        val timestamp = (row["timestamp"] as? Number)?.toLong() ?: return@filter false
                        (start == null || timestamp >= start) && (end == null || timestamp <= end)
                    }, at)
                }
            }
        } catch (e: IllegalArgumentException) {
            return OperationResult.error(e.message ?: "")
        } catch (e: IllegalStateException) {
            return OperationResult.error(e.message ?: "")
        }
        return OperationResult.success(mapOf(
            "field" to JsonUtils.toMap((group?.let { ReadingGroup.resultField(value, it.reduction) } ?: value).toJson()),
            "values" to values.map { (at, result) -> mapOf("at" to at) + result(result).filterKeys { it != "field" } }
        ))
    }

    /**
     * The entries of [toolInstanceId] the stored [filters] keep, newest first.
     *
     * @throws IllegalStateException when they cannot be read
     */
    private suspend fun readEntries(toolInstanceId: String, filters: org.json.JSONArray): List<Map<String, Any?>> {
        val entries = Coordinator(context).processUserAction("tool_data.get", mapOf(
            "tool_instance_id" to toolInstanceId,
            "filters" to JsonUtils.toList(filters)
        ))
        if (!entries.isSuccess) throw IllegalStateException(entries.error ?: "")
        return (entries.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<String, Any?>>()
    }

    /** A result as data: its value with its field, or its failure. */
    private fun result(result: ReadingResult): Map<String, Any> = when (result) {
        is ReadingResult.Value -> mapOf("value" to result.value, "field" to JsonUtils.toMap(result.field.toJson()))
        is ReadingResult.Failure -> failure(result)
    }

    /** Whether the tool instance or the entry read was deleted; null when that cannot be read. */
    private suspend fun sourceGone(source: app.treelune.core.selection.Reference): Boolean? {
        val result = Coordinator(context).processUserAction("references.names", mapOf("references" to listOf(JsonUtils.toMap(source.toJson()))))
        if (!result.isSuccess) return null
        return ((result.data?.get("references") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("deleted") as? Boolean
    }

    /** A failure as data, with its words for whoever shows it. */
    private fun failure(failure: ReadingResult.Failure): Map<String, Any> = mapOf("failure" to mapOf(
        "reason" to failure.reason.name,
        "field" to failure.field,
        "entries" to failure.entries,
        "message" to when (failure.reason) {
            FailureReason.NO_ENTRY -> s.shared("reading_failure_no_entry").format(failure.field ?: "")
            FailureReason.MISSING_VALUE -> s.shared("reading_failure_missing_value").format(failure.entries.size, failure.field ?: "")
            FailureReason.SOURCE_NOT_FOUND -> s.shared("reading_failure_source_not_found")
        }
    ))

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "read" -> s.shared("action_verbalize_readings_read")
            else -> s.shared("action_verbalize_unknown")
        }
    }
}
