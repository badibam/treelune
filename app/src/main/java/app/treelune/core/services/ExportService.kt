package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.exports.AppFolder
import app.treelune.core.exports.EntriesCsv
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.selection.Reference
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.FileNames
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Files written out of the app for people to read (docs/DATA.md, « Export »), into the app's folder
 * chosen once in Settings › Data (AppSettings.DATA_FOLDER, the automatic backups' too), without
 * asking anything: the same from a screen, the AI or a routine.
 *
 * - csv: the entries of `tool_instance_id` as a CSV (EntriesCsv), those `filters` keep (as
 *   tool_data.get takes them, a period being filters on timestamp), with the fields `fields`
 *   names, all of them when absent. Answer: `file_name`, `uri` (the document, to share), `entries`.
 *
 * The file is named after the tool and the minute (`Meals_2026-10-20_0930.csv`), `_2`, `_3`…
 * when the name is taken: nothing is written over, and the caller does not choose the name. It is
 * written under a `.part` name first, then renamed: a file of the final name is complete.
 */
class ExportService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val coordinator = Coordinator(context)

    private companion object {
        val CSV_PARAMS = setOf("tool_instance_id", "filters", "fields")
        val MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
    }

    /** Reading a tool's entries whole, like a backup reads the base: never beside another. */
    override val longOperations = setOf("csv")

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return withContext(Dispatchers.IO) {
            when (operation) {
                "csv" -> csv(params, token)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        }
    }

    private suspend fun csv(params: JSONObject, token: CancellationToken): OperationResult {
        // A parameter this export does not take would be ignored, and the file widened in silence
        params.keys().asSequence().firstOrNull { it !in CSV_PARAMS }?.let { param ->
            return OperationResult.error(s.shared("service_error_param_unknown").format(param, CSV_PARAMS.joinToString(", ")))
        }
        val toolInstanceId = params.optString("tool_instance_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))

        // The folder first: no entry is read for a file that cannot be written
        val folder = when (val read = AppFolder.read(context)) {
            AppFolder.Read.NoFolder -> return OperationResult.error(s.shared("export_error_no_folder"))
            AppFolder.Read.Lost -> return OperationResult.error(s.shared("export_error_folder_lost"))
            is AppFolder.Read.Ready -> read.folder
        }

        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        val toolInstance = tool.data?.get("tool_instance") as? Map<*, *>
            ?: return OperationResult.error(tool.error ?: s.shared("service_error_tool_instance_not_found"))
        @Suppress("UNCHECKED_CAST")
        val config = JsonUtils.toJSONObject(toolInstance["config"] as Map<String, Any?>)
        val toolType = ToolTypeManager.getToolType(toolInstance["tooltype"] as String)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found"))
        val text: (String) -> String = { s.shared(it) }
        val all = EntriesCsv.columns(toolType.getEntryFields(config, context), config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList(), text)

        // The fields kept, each one the tool has: a field it does not have is refused, never left out
        val kept = params.optJSONArray("fields")?.let { array -> (0 until array.length()).map { array.getString(it) } }
        kept?.firstOrNull { path -> all.none { it.path == path } }?.let { path ->
            return OperationResult.error(s.shared("export_error_unknown_field").format(path, all.joinToString(", ") { it.path }))
        }
        val columns = kept?.let { paths -> all.filter { it.path in paths } } ?: all

        val read = coordinator.processUserAction("tool_data.get", buildMap {
            put("tool_instance_id", toolInstanceId)
            params.optJSONArray("filters")?.let { put("filters", JsonUtils.toList(it)) }
        })
        @Suppress("UNCHECKED_CAST")
        val entries = (read.data?.get("entries") as? List<Map<String, Any?>>)
            ?: return OperationResult.error(read.error ?: s.shared("export_error_read"))
        if (token.isCancelled) return OperationResult.cancelled()

        val names = names(EntriesCsv.references(columns, entries)) ?: return OperationResult.error(s.shared("export_error_read"))
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        val csv = EntriesCsv.write(columns, entries, names, zone, text)

        val toolName = config.optString("name")
        val base = try {
            "${FileNames.clean(toolName)}_${MINUTE.format(Instant.now().atZone(zone))}"
        } catch (e: IllegalArgumentException) {
            return OperationResult.error(s.shared("export_error_file_name").format(toolName))
        }
        val written = try {
            AppFolder.write(context, folder, base, "csv") { it.write(EntriesCsv.bytes(csv)) }
        } catch (e: Exception) {
            LogManager.service("Export: $base not written: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("export_error_write").format(base))
        }
        LogManager.service("Export: ${written.second} written, ${entries.size} entries of $toolInstanceId")
        return OperationResult.success(mapOf("file_name" to written.second, "uri" to written.first.toString(), "entries" to entries.size))
    }

    /** The name each reference shows, null for one that no longer exists; null when they cannot be read. */
    private suspend fun names(references: Set<Reference>): Map<Reference, String?>? {
        if (references.isEmpty()) return emptyMap()
        val result = coordinator.processUserAction("references.names", mapOf("references" to references.map { JsonUtils.toMap(it.toJson()) }))
        val read = result.data?.get("references") as? List<*> ?: return null
        return read.filterIsInstance<Map<*, *>>().associate { named ->
            requireNotNull(ReferenceTarget.referenceOf(named)) to (named["name"] as? String)
        }
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        if (operation != "csv") return s.shared("action_verbalize_unknown")
        val tool = Coordinator(context).processUserAction("tools.get", mapOf("tool_instance_id" to params.optString("tool_instance_id")))
        @Suppress("UNCHECKED_CAST")
        val toolName = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.get("name") as? String
            ?: return s.shared("action_verbalize_exports_csv")
        return s.shared("action_verbalize_exports_csv_tool").format(toolName)
    }
}
