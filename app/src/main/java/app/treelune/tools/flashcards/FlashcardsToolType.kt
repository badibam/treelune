package app.treelune.tools.flashcards

import android.content.Context
import androidx.compose.runtime.Composable
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.EntryStart
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.StateField
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.ToolFields
import app.treelune.core.fields.settings.RowFields
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.labelOf
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.services.ExecutableService
import app.treelune.core.strings.Strings
import app.treelune.core.tools.BaseSchemas
import app.treelune.core.tools.EntryToOpen
import app.treelune.core.tools.ToolOperation
import app.treelune.core.tools.ToolTile
import app.treelune.core.tools.ToolTypeContract
import app.treelune.core.utils.JsonUtils
import app.treelune.tools.flashcards.ui.FlashcardsScreen
import app.treelune.tools.flashcards.ui.rememberFlashcardsTile
import org.json.JSONObject
import java.text.Normalizer

/**
 * A deck of flashcards (docs/design/flashcards.md): the lines of another tool, its source, reviewed
 * by spaced repetition, each card coming back just before it is forgotten (Fsrs). It holds none of
 * the words: a card is a line of the source in one of the deck's directions, and its only data of
 * its own are its reviews, the deck's entries.
 *
 * A review is written once, by the deck's own operation (FlashcardsService.review), never by an
 * ordinary write: any other creation is refused (EntryStart without a status), and its fields are
 * the app's to write, dropped from a screen, the AI or an import. It never changes; the last review of a card is deleted to
 * undo it, the state before coming back by the calculation. The state of a card is that of its last
 * review (FlashcardsJournal).
 */
object FlashcardsToolType : ToolTypeContract {

    private fun s(context: Context) = Strings.`for`(tool = "flashcards", context = context)

    override fun getDisplayName(context: Context): String = s(context).tool("display_name")
    override fun getGuideConfig(context: Context): String = s(context).tool("guide_config")
    override fun getGuideScreen(context: Context): String = s(context).tool("guide_screen")
    override fun getUsage(context: Context): String = s(context).tool("usage")
    override fun getAIDetail(context: Context): String = s(context).tool("ai_detail")
    override fun getDefaultDisplayMode(): String = "LINE"
    override fun getDefaultShowFieldLabels(): Boolean = false
    override fun getDefaultIconName(): String = "layers"
    override fun getSuggestedIcons(): List<String> = listOf("layers", "languages", "brain", "graduation-cap", "book-open-check", "repeat")

    override fun getFormFieldName(fieldName: String, context: Context): String =
        getConfigSettings(context).labelOf(fieldName)
            ?: when (fieldName) {
                FlashcardsJournal.SOURCE_ENTRY, FlashcardsJournal.DIRECTION, FlashcardsJournal.RATING,
                FlashcardsJournal.STABILITY, FlashcardsJournal.DIFFICULTY, FlashcardsJournal.DUE, FlashcardsJournal.FSRS_VERSION -> s(context).tool("field_$fieldName")
                else -> null
            }
            ?: BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName

    /** Its reviews are written by the app alone, from what the user does in a session: no fields of the user's. */
    override fun takesUserFields(): Boolean = false

    // -----------------------------------------------------------------------------------------
    // The config
    // -----------------------------------------------------------------------------------------

    private fun field(name: String, label: String, type: FieldType, description: String?, config: Map<String, Any>? = null) =
        FieldDefinition(name, label, description, type, false, config)

    private fun short() = mapOf("length" to TextLength.SHORT.name)

    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = s(context)
        val face = { name: String, label: String, description: String ->
            SettingNode.ListOf(name, label, SettingNode.Item.Value(field(name, s.tool("field_face_field"), FieldType.TEXT, description, short()), rowField = true),
                required = true, minItems = 1, distinct = true)
        }
        return listOf(
            SettingNode.Section(s.tool("section_source"), listOf(
                SettingNode.Selection(FlashcardsDeck.SOURCE, s.tool("field_source"), s.tool("reference_review"), required = true),
                SettingNode.Field(field(FlashcardsDeck.GROUP_BY, s.tool("field_group_by"), FieldType.TEXT, s.tool("schema_group_by"), short()), rowField = true)
            )),
            SettingNode.Section(s.tool("section_directions"), listOf(
                SettingNode.ListOf(FlashcardsDeck.DIRECTIONS, s.tool("field_directions"), SettingNode.Item.Of(listOf(
                    SettingNode.Field(field(FlashcardsDeck.KEY, FlashcardsDeck.KEY, FieldType.TEXT, s.tool("schema_direction_key"), short()), systemWritten = true),
                    SettingNode.Field(field(FlashcardsDeck.NAME, s.tool("field_direction_name"), FieldType.TEXT, s.tool("schema_direction_name"), short()), required = true),
                    face(FlashcardsDeck.FRONT, s.tool("field_front"), s.tool("schema_front")),
                    face(FlashcardsDeck.BACK, s.tool("field_back"), s.tool("schema_back"))
                )), required = true, minItems = 1, summary = listOf(FlashcardsDeck.NAME)),
                SettingNode.Field(field(FlashcardsDeck.AFTER_ANOTHER, s.tool("field_after_another"), FieldType.BOOLEAN, s.tool("schema_after_another")), required = true, default = false)
            )),
            SettingNode.Section(s.tool("section_day"), listOf(
                SettingNode.Field(field(FlashcardsDeck.NEW_PER_DAY, s.tool("field_new_per_day"), FieldType.NUMERIC, s.tool("schema_new_per_day"), mapOf("min" to 0, "max" to 9999, "decimals" to 0)), required = true, default = 20),
                SettingNode.Field(field(FlashcardsDeck.REVIEWS_PER_DAY, s.tool("field_reviews_per_day"), FieldType.NUMERIC, s.tool("schema_reviews_per_day"), mapOf("min" to 0, "max" to 9999, "decimals" to 0)), required = true, default = 200),
                SettingNode.Field(field(FlashcardsDeck.DESIRED_RETENTION, s.tool("field_desired_retention"), FieldType.NUMERIC, s.tool("schema_desired_retention"), mapOf("min" to 0.7, "max" to 0.99, "decimals" to 2)), required = true, default = 0.9)
            ))
        )
    }

    override fun getRowFields(): RowFields = FlashcardsRowFields

    /** Each direction gets its key at its creation, kept from then on: renaming it keeps its reviews. */
    override fun completeConfig(config: JSONObject, previous: JSONObject?): JSONObject {
        val completed = JSONObject(config.toString())
        val all = FlashcardsDeck.objects(completed.optJSONArray(FlashcardsDeck.DIRECTIONS))
        val taken = all.mapNotNull { it.optString(FlashcardsDeck.KEY).takeIf { k -> k.isNotEmpty() } }.toMutableSet()
        all.filter { it.optString(FlashcardsDeck.KEY).isEmpty() }.forEach { direction ->
            val base = "d_" + Normalizer.normalize(direction.optString(FlashcardsDeck.NAME), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            var key = base
            var n = 2
            while (key in taken) key = "${base}_${n++}"
            taken.add(key)
            direction.put(FlashcardsDeck.KEY, key)
        }
        return completed
    }

    /**
     * What only reading the app tells: the source is one tool that keeps entries, not a deck; its
     * filters read; every field the deck names is one of its fields.
     */
    override suspend fun refuseConfig(config: JSONObject, context: Context): String? {
        val s = s(context)
        val deck = try { FlashcardsDeck.of(config) { s.shared(it) } } catch (e: IllegalArgumentException) {
            return s.tool("error_source_reads").format(e.message ?: "")
        }
        if (deck.source.target.kind != ReferenceKind.TOOL_INSTANCE || deck.source.target.id == null) return s.tool("error_source_tool")
        val coordinator = Coordinator(context)
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to deck.sourceId)).data?.get("tool_instance") as? Map<*, *>
            ?: return s.tool("error_source_gone")
        val type = app.treelune.core.tools.ToolTypeManager.getToolType(tool["tooltype"] as? String ?: "")
        if (type == null || type === this || !type.keepsEntries()) return s.tool("error_source_kind").format(tool["name"])
        val fields = try { ToolFields.filterable(deck.sourceId, context, s) } catch (e: IllegalStateException) { return e.message }
        deck.namedFields.firstOrNull { it !in fields }?.let { return s.tool("error_field_gone").format(it.substringAfter('.'), tool["name"]) }
        val names = deck.directions.map { it.name.trim().lowercase() }
        if (names.size != names.toSet().size) return s.tool("error_direction_names")
        // Its filters, read as the deck will read them
        val read = coordinator.processUserAction("tool_data.get", mapOf(
            "tool_instance_id" to deck.sourceId,
            "filters" to JsonUtils.toList(deck.source.storedFilters(fields, app.treelune.core.selection.TimeResolver.at(System.currentTimeMillis())) { s.shared(it) }),
            "limit" to 1
        ))
        return if (read.isSuccess) null else read.error
    }

    /**
     * A source changed removes every review; a direction removed, its own. Only after the user's or
     * the AI's agreement, as every entry a config change deletes (confirm_migration).
     */
    override fun entriesRemovedByConfig(previous: JSONObject, config: JSONObject, entries: List<ToolDataEntity>, context: Context): List<ToolDataEntity> {
        fun sourceOf(c: JSONObject) = c.optJSONObject(FlashcardsDeck.SOURCE)?.optJSONObject("target")?.optString("id")
        if (sourceOf(previous) != sourceOf(config)) return entries
        fun keysOf(c: JSONObject) = FlashcardsDeck.objects(c.optJSONArray(FlashcardsDeck.DIRECTIONS)).map { it.optString(FlashcardsDeck.KEY) }.toSet()
        val removed = keysOf(previous) - keysOf(config)
        return entries.filter { JSONObject(it.data).optString(FlashcardsJournal.DIRECTION) in removed }
    }

    // -----------------------------------------------------------------------------------------
    // The reviews
    // -----------------------------------------------------------------------------------------

    /**
     * A review: its instant, the line and direction of its card and its rating, written by the app
     * alone; in its state, the card's after it.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = s(context)
        val source = config.optJSONObject(FlashcardsDeck.SOURCE)?.optJSONObject("target")?.optString("id")?.takeIf { it.isNotEmpty() }
        val ratings = Fsrs.Rating.entries.map { it.name }
        return EntryFields(
            name = CoreFieldUsage.ABSENT,
            timestamp = CoreFieldUsage.REQUIRED,
            data = listOf(
                FixedField(field(FlashcardsJournal.SOURCE_ENTRY, s.tool("field_source_entry"), FieldType.REFERENCE, s.tool("schema_source_entry"),
                    mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.ENTRY.name), ReferenceTarget.TOOL_INSTANCES to listOfNotNull(source)))),
                    required = true, systemWritten = true),
                FixedField(field(FlashcardsJournal.DIRECTION, s.tool("field_direction"), FieldType.TEXT, s.tool("schema_direction"), short()), required = true, systemWritten = true),
                FixedField(field(FlashcardsJournal.RATING, s.tool("field_rating"), FieldType.CHOICE, s.tool("schema_rating"),
                    mapOf("options" to ChoiceSettings.storedOptions(ratings, ratings.associateWith { s.tool("rating_${it.lowercase()}") }, emptyMap()))),
                    required = true, systemWritten = true)
            ),
            state = listOf(
                StateField(field(FlashcardsJournal.STABILITY, s.tool("field_stability"), FieldType.NUMERIC, s.tool("schema_stability"), mapOf("decimals" to 2)), filterable = true),
                StateField(field(FlashcardsJournal.DIFFICULTY, s.tool("field_difficulty"), FieldType.NUMERIC, s.tool("schema_difficulty"), mapOf("decimals" to 2)), filterable = true),
                StateField(field(FlashcardsJournal.DUE, s.tool("field_due"), FieldType.DATETIME, s.tool("schema_due")), filterable = true),
                StateField(field(FlashcardsJournal.FSRS_VERSION, s.tool("field_fsrs_version"), FieldType.TEXT, s.tool("schema_fsrs_version"), short()), filterable = false)
            ),
            // Written by the deck alone, from a rating: any other creation is refused with what to do instead
            start = EntryStart(emptyList(), refusal = s.tool("start_refused"), withStatus = false)
        )
    }

    /** A review is written once: undoing it deletes it. */
    override fun refuseUpdate(entry: ToolDataEntity, context: Context): String = s(context).tool("error_review_changed")

    /** Only the last review of a card is undone, the state before it coming back by the calculation. */
    override suspend fun refuseDelete(entry: ToolDataEntity, entries: suspend () -> List<ToolDataEntity>, context: Context): String? {
        val key = FlashcardsJournal.key(entry)
        val last = entries().filter { FlashcardsJournal.key(it) == key }
            .maxWith(compareBy({ it.timestamp ?: error("a review ${it.id} without its instant") }, { it.createdAt }))
        return if (last.id == entry.id) null else s(context).tool("error_not_last_review")
    }

    /** Rating a card, and the cards to offer, for the screen and the AI alike. */
    override fun getOperations(context: Context): List<ToolOperation> {
        val s = s(context)
        val group = SettingNode.Field(field("group", s.tool("param_group"), FieldType.TEXT, s.tool("schema_param_group"), short()))
        val direction = SettingNode.Field(field("direction", s.tool("param_direction"), FieldType.TEXT, s.tool("schema_param_direction"), short()))
        val ratings = Fsrs.Rating.entries.map { it.name }
        return listOf(
            ToolOperation("due", s.tool("operation_due"), listOf(group, direction)),
            ToolOperation("counts", s.tool("operation_counts"), emptyList()),
            ToolOperation("review", s.tool("operation_review"), listOf(
                SettingNode.Field(field("source_entry", s.tool("param_source_entry"), FieldType.TEXT, s.tool("schema_param_source_entry"), short()), required = true),
                direction.copy(required = true),
                SettingNode.Field(field("rating", s.tool("field_rating"), FieldType.CHOICE, s.tool("schema_rating"),
                    mapOf("options" to ChoiceSettings.storedOptions(ratings, ratings.associateWith { s.tool("rating_${it.lowercase()}") }, emptyMap()))), required = true)
            ))
        )
    }

    /** The cards due today wait: the deck counts them itself, a due card being no entry. */
    override suspend fun countWaiting(tool: ToolInstance, context: Context): Int? {
        val deck = try { FlashcardsDeck.of(JSONObject(tool.config_json)) { s(context).shared(it) } } catch (e: IllegalArgumentException) {
            throw IllegalStateException(e.message)
        }
        return FlashcardsService.read(context, tool.id, deck, System.currentTimeMillis()).offer(null, null).due
    }

    override fun getService(context: Context): ExecutableService = FlashcardsService(context)

    override fun getDao(context: Context): Any {
        val baseDao = app.treelune.core.database.AppDatabase.getDatabase(context).toolDataDao()
        return app.treelune.core.database.dao.DefaultExtendedToolDataDao(baseDao, "flashcards")
    }

    override fun getDatabaseEntities(): List<Class<*>> = listOf(ToolDataEntity::class.java)

    /** A deck opens on its page; nothing of it is an entry to open. */
    @Composable
    override fun getUsageScreen(toolInstanceId: String, configJson: String, zoneName: String, onNavigateBack: () -> Unit, onLongClick: () -> Unit, openEntry: EntryToOpen?) {
        FlashcardsScreen(toolInstanceId = toolInstanceId, onNavigateBack = onNavigateBack, onConfigureClick = onLongClick)
    }

    @Composable
    override fun rememberTile(tool: ToolInstance, open: (EntryToOpen) -> Unit): ToolTile = rememberFlashcardsTile(tool)
}
