package app.treelune.tools.path

import android.content.Context
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.SortKey
import app.treelune.core.strings.StringsContext
import app.treelune.core.views.ToolView
import app.treelune.core.views.View
import app.treelune.core.views.ViewPeriod
import org.json.JSONObject

/**
 * A Path's two views (« Les vues »), computed by its replay at the instant read, never stored:
 * [Stages], a row per stage of every run, its state and `since`; [Moves], a row per move, those
 * effects gave included. Read at an instant past, only the moves before it are replayed.
 */
object PathViews {

    /** `stages`: where each stage stands. Its rows have no date of their own: a period is refused. */
    object Stages : ToolView {
        override val name = PathRows.STAGES
        override val defaultSort = listOf(SortKey("data.${PathSelf.POSITION}", ascending = true))

        override suspend fun open(context: Context, toolInstanceId: String, config: JSONObject, s: StringsContext): View = object : View {
            private val grammar = PathGrammar.fromJson(config)
            override val fields = PathRows.stageFields(grammar)
            override val period = ViewPeriod.NONE
            override suspend fun rows(start: Long?, end: Long?, at: Long): List<ToolDataEntity> =
                PathRead.of(context, toolInstanceId, config).replay(at).rows.map { entity(it) }
        }

        override suspend fun fields(context: Context, config: JSONObject, s: StringsContext): Map<String, FieldDefinition> =
            PathRows.stageFields(PathGrammar.fromJson(config))
    }

    /** `moves`: what happened, and when, a period narrowing them by their instant. */
    object Moves : ToolView {
        override val name = PathRows.MOVES
        override val defaultSort = listOf(SortKey("timestamp", ascending = false))

        override suspend fun open(context: Context, toolInstanceId: String, config: JSONObject, s: StringsContext): View = object : View {
            override val fields = PathRows.moveFields(PathGrammar.fromJson(config))
            override val period = ViewPeriod.OPTIONAL
            override suspend fun rows(start: Long?, end: Long?, at: Long): List<ToolDataEntity> =
                PathRead.of(context, toolInstanceId, config).replay(at).moves
                    .filter { row -> (row["timestamp"] as Long).let { (start == null || it >= start) && (end == null || it <= end) } }
                    .map { entity(it) }
        }

        override suspend fun fields(context: Context, config: JSONObject, s: StringsContext): Map<String, FieldDefinition> = PathRows.moveFields(PathGrammar.fromJson(config))
    }

    /** A row as an entry, which the core's filters judge in SQL (ViewRows). */
    private fun entity(row: Map<String, Any?>): ToolDataEntity = ToolDataEntity(
        id = row["id"] as String,
        toolInstanceId = row["tool_instance_id"] as String,
        tooltype = PathRows.TOOLTYPE,
        timestamp = row["timestamp"] as Long?,
        name = row["name"] as String?,
        data = app.treelune.core.utils.JsonUtils.toJSONObject((row["data"] as Map<*, *>).entries.associate { it.key.toString() to it.value }).toString(),
        createdAt = 0L,
        updatedAt = 0L,
        extra = null,
        state = null
    )
}
