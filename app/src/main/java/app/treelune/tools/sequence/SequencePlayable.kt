package app.treelune.tools.sequence

import android.content.Context
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.SortKey
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.StringsContext
import app.treelune.core.views.ToolView
import app.treelune.core.views.View
import app.treelune.core.views.ViewPeriod
import org.json.JSONObject

/**
 * What a session can play, as a view (core/views, its name [NAME]): one row per thing « Start »
 * offers, whatever its variant — each program, by its id, its values of the program fields as its
 * columns (`data.level`); each entry a medium is chosen among, by the entry's id, designating it;
 * a session of steps or a timer, a single row. A row has no date: the view takes no period. A
 * reference to a row (ROW) names what a session plays, a brick's séance, by its row's name.
 */
object SequencePlayable : ToolView {

    const val NAME = "playable"

    /** A media row's column: the entry it plays, designated */
    const val ENTRY = "entry"

    /** The id of the single row of a session of steps or of a timer */
    const val ONLY = "session"

    override val name = NAME

    /** In the order the variant offers its rows: no key. */
    override val defaultSort: List<SortKey> = emptyList()

    override suspend fun fields(context: Context, config: JSONObject, s: StringsContext): Map<String, FieldDefinition> = when (SequenceVariants.of(config)) {
        // A program's name, then its fields as its columns, by their names
        SequenceVariants.PROGRAMS -> mapOf("name" to FieldDefinition("name", s.shared("label_name"), null, app.treelune.core.fields.FieldType.TEXT, false, null)) +
            config.optJSONArray(SequenceVariants.PROGRAM_FIELDS)?.toFieldDefinitions().orEmpty().associateBy { "data.${it.name}" }
        SequenceVariants.MEDIA -> mapOf("data.$ENTRY" to FieldDefinition(ENTRY, s.tool("playable_entry"), null, app.treelune.core.fields.FieldType.REFERENCE, false,
            mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.ENTRY.name)))))
        else -> emptyMap()
    }

    override suspend fun open(context: Context, toolInstanceId: String, config: JSONObject, s: StringsContext): View {
        val fields = fields(context, config, s)
        return object : View {
            override val fields = fields
            override val period = ViewPeriod.NONE
            override suspend fun rows(start: Long?, end: Long?, at: Long): List<ToolDataEntity> = rows(context, toolInstanceId, config, s)
        }
    }

    /**
     * The rows of a session whose config is [config], in the order its variant offers them: the
     * programs in the list's, the media in their selection's.
     *
     * @throws IllegalStateException when a medium's selection cannot be read
     */
    suspend fun rows(context: Context, toolInstanceId: String, config: JSONObject, s: StringsContext): List<ToolDataEntity> {
        fun row(id: String, name: String, data: JSONObject) = ToolDataEntity(
            id = id, toolInstanceId = toolInstanceId, tooltype = "sequence", timestamp = null, name = name,
            data = data.toString(), createdAt = 0L, updatedAt = 0L, extra = null, state = null
        )
        return when (SequenceVariants.of(config)) {
            SequenceVariants.PROGRAMS -> SequenceVariants.programs(config).map { program ->
                row(program.optString(SequenceVariants.PROGRAM_ID), program.optString(SequenceVariants.PROGRAM_NAME),
                    program.optJSONObject(SequenceVariants.PROGRAM_VALUES)?.let { JSONObject(it.toString()) } ?: JSONObject())
            }
            SequenceVariants.MEDIA -> SequenceVariants.offered(context, config).map { entry ->
                val id = entry["id"] as String
                row(id, (entry["name"] as? String)?.takeIf { it.isNotBlank() } ?: "", JSONObject().put(ENTRY, JSONObject().put("kind", ReferenceKind.ENTRY.name).put("id", id)))
            }
            else -> listOf(row(ONLY, config.optString("name").ifEmpty { s.tool("display_name") }, JSONObject()))
        }
    }
}
