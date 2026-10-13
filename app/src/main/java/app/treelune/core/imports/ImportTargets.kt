package app.treelune.core.imports

import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.toFieldDefinition
import app.treelune.core.utils.JsonUtils

/**
 * What the columns of an import into a tool may fill, as tool_data says it writes them
 * (tool_data.writable): [fields], by path, and [notWritten], the fields the app writes itself
 * (created_at, the state…), by path with their display name — a column naming one is left out.
 */
data class ImportTargets(val fields: Map<String, FieldDefinition>, val notWritten: Map<String, String>) {

    companion object {
        /**
         * tool_data's answer for [toolInstanceId].
         *
         * @throws IllegalStateException with the service's error when it cannot answer
         */
        suspend fun read(coordinator: Coordinator, toolInstanceId: String): ImportTargets {
            val result = coordinator.processUserAction("tool_data.writable", mapOf("tool_instance_id" to toolInstanceId))
            if (!result.isSuccess) throw IllegalStateException(result.error ?: "")
            val written = result.data?.get("written") as? Map<*, *> ?: emptyMap<String, Any>()
            val notWritten = result.data?.get("not_written") as? Map<*, *> ?: emptyMap<String, Any>()
            return ImportTargets(
                fields = written.entries.associate { (path, field) ->
                    @Suppress("UNCHECKED_CAST")
                    path as String to JsonUtils.toJSONObject(field as Map<String, Any?>).toFieldDefinition()
                },
                notWritten = notWritten.entries.associate { (path, name) -> path as String to name as String }
            )
        }
    }
}
