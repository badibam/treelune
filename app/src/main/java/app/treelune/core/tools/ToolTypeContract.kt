package app.treelune.core.tools

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.room.migration.Migration
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.services.ExecutableService
import app.treelune.core.validation.ValidationResult
import app.treelune.core.validation.SchemaProvider
import org.json.JSONObject

/**
 * Contract for tool type implementations
 * Defines the mandatory static metadata that each tool type must provide
 * Includes data migration capabilities for autonomous data upgrades
 */
interface ToolTypeContract {

    /**
     * The label of [fieldName] as a screen shows it, for a validation error or the pointer
     * to name it.
     */
    fun getFormFieldName(fieldName: String, context: Context): String
    
    /**
     * Human-readable display name for this tool type
     * @param context Android context for string resource access
     */
    fun getDisplayName(context: Context): String

    /**
     * What this tool type is for, what it already does on its own and what it is not for: one
     * text, read by the AI in its prompt (always in English) and shown by the Guide.
     * @param context Android context for string resource access
     */
    fun getUsage(context: Context): String

    /** What only the AI needs besides the usage, written after it in the prompt; none for most types. */
    fun getAIDetail(context: Context): String? = null

    /**
     * The Guide's chapter on this type, beside its usage, in the Guide's markup (GuideMarkup):
     * every setting of its own config, and every button of its screen, one mention each
     * (docs/design/user-journey.md, « Prise en main »). None until written.
     */
    fun getGuideConfig(context: Context): String? = null
    fun getGuideScreen(context: Context): String? = null

    /** The display mode a new tool of this type is shown in, in its zone. */
    fun getDefaultDisplayMode(): String

    /**
     * Whether the user's fields show their names beside their values, until the tool's config
     * says otherwise (show_field_labels): where values speak for themselves, a list item's, no.
     */
    fun getDefaultShowFieldLabels(): Boolean
    
    // Schema Provider Implementation
    // SchemaProvider methods are inherited from SchemaProvider interface
    
    
    
    /**
     * Default icon for this tool type, a Lucide name: given to a tool created without one.
     */
    fun getDefaultIconName(): String
    
    /**
     * Suggested icon names for this tool type (shown first in icon selector)
     * @return List of icon IDs that make sense for this tool type
     */
    fun getSuggestedIcons(): List<String> = emptyList()
    
    /**
     * Create service instance for this tool type
     * Returns null if this tool type doesn't have an associated service
     * @param context Android context for service creation
     */
    fun getService(context: Context): ExecutableService?
    
    /**
     * Create DAO instance for this tool type
     * Returns null if this tool type doesn't have an associated DAO
     * @param context Android context for DAO creation
     */
    fun getDao(context: Context): Any?
    
    /**
     * Get database entities for this tool type
     * Used for Room database setup via discovery
     * @return List of entity classes for this tool type
     */
    fun getDatabaseEntities(): List<Class<*>>
    
    /**
     * Usage screen for this tool type
     * @param toolInstanceId ID of the tool instance
     * @param configJson Configuration JSON of the tool instance
     * @param onNavigateBack Called when user wants to navigate back
     * @param onLongClick Called when user long-clicks for configuration access
     * @param openEntry What to open at once (EntryToOpen): an entry touched on the tile, the
     *   oldest of those waiting for the user (getWaiting), or a new one; null for the screen alone
     */
    @Composable
    fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit,
        openEntry: EntryToOpen?
    )

    /**
     * The tile of [tool] (ToolTile): its summary and its body, placed by UI.ToolCard as its
     * display mode lays them out. Every tool type fills every mode: there is no tile by default.
     * [open] opens the tool on one of its entries, as this tool type understands opening one
     * (getUsageScreen's openEntry): a tile opens an entry it shows when touched, or a new one from
     * a button that writes one.
     *
     * A tile that shows something the tool's entries hold loads it once for both parts and
     * reloads on their change (DataChangeNotifier).
     */
    @Composable
    fun rememberTile(tool: app.treelune.core.database.entities.ToolInstance, open: (EntryToOpen) -> Unit): ToolTile

    /**
     * The fields of this tool type's entries, for a tool instance whose config is [config]:
     * how it uses name and timestamp, its fixed fields in data, and the fields of their state.
     * The user's fields come from the config's extra_fields and are not declared here.
     *
     * The data schema of the entries is generated from it (BaseSchemas.getEntrySchema), so a
     * tool type never writes that schema by hand.
     */
    fun getEntryFields(config: JSONObject, context: Context): app.treelune.core.fields.EntryFields

    /**
     * The settings of this tool type's config, beside the ones every tool has
     * (ToolConfigSettings): the config's schema and checking are generated from them.
     */
    fun getConfigSettings(context: Context): List<app.treelune.core.fields.settings.SettingNode>

    /**
     * The operations this tool type's service runs on its entries beside the generic writes
     * (ToolOperation). The AI receives them with a tool's entries schema and calls them with
     * TOOL_OPERATION; a type that declares none offers only the generic writes.
     */
    fun getOperations(context: Context): List<ToolOperation> = emptyList()

    /**
     * [config] once [added] have joined the options of the CHOICE field [field] this tool type
     * declares in data, for a field whose vocabulary is open. Only a tool type that declares an
     * open CHOICE in data keeps its options in its config and answers; the service asks no other.
     */
    fun configWithOptionsAdded(config: JSONObject, field: String, added: List<String>): JSONObject =
        throw IllegalStateException("${this::class.simpleName} declares no open choice in data")

    /**
     * Keep a rule that spans the entries of one tool instance, such as a manual order.
     *
     * ToolDataService calls it on every create, update and delete of an entry of this tool type,
     * whoever the caller — screen or AI — and stores its answer in the same transaction as the write.
     *
     * @param entries Every entry of the tool instance as it will stand once the write is done,
     *        read only when called: a tool type with no such rule never pays for reading them
     * @param writtenId The entry just created or updated; null after a delete
     * @return The entries whose content must change, the written one included if it must;
     *         empty when the rule already holds
     */
    suspend fun settleEntries(entries: suspend () -> List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> = emptyList()

    /**
     * The entries [config] removes among those recorded ([entries]), beyond what its fields
     * lose: a list set to remove what is checked removes the items already checked; a calendar
     * whose series changes its schedule, the occurrences to come, to be made again. They join
     * the entries a config change deletes (ToolInstanceService.planMigration), so nothing goes
     * before the user or the AI agrees to it (confirm_migration).
     *
     * @param previous The config as stored, before the change
     */
    fun entriesRemovedByConfig(previous: JSONObject, config: JSONObject, entries: List<ToolDataEntity>, context: Context): List<ToolDataEntity> = emptyList()

    /**
     * The entries whose content [config] changes, beyond what its fields do to them, written with
     * the config in one transaction (ToolInstanceService.planMigration): a calendar's series hands
     * its new duration to the occurrences to come that still had the former one. Nothing is lost
     * by such a change, which asks for no agreement; one that loses something is a removal.
     *
     * @param previous The config as stored, before the change
     * @param entries The entries as the change of fields leaves them, those it removes left out
     * @return The entries changed, each whole; empty when the config changes none
     */
    fun entriesUpdatedByConfig(previous: JSONObject, config: JSONObject, entries: List<ToolDataEntity>, context: Context): List<ToolDataEntity> = emptyList()

    /**
     * [config] completed with what only this tool type writes, on every create and update of an
     * instance, before it is checked: a goal gives each new criterion its key, fixed from then on.
     *
     * @param previous The config as stored, null on a create
     */
    fun completeConfig(config: JSONObject, previous: JSONObject?): JSONObject = config

    /**
     * What waits for the user among an instance's entries (the waiting, docs/BRICKS.md): the
     * conditions an entry passes when it does (Conditions.onField), a questionnaire to fill, an
     * attempt to validate. The core counts them on the instance's tile and its zone's; none,
     * nothing ever waits.
     */
    fun getWaiting(config: JSONObject): List<JSONObject> = emptyList()

    /**
     * Why [entry] may not be changed or deleted by an ordinary write, or null when it may: a
     * goal's validated attempt changes only by its own operation, reopening it. ToolDataService
     * asks it before every update and delete, whoever the caller.
     */
    fun refuseChange(entry: ToolDataEntity, context: Context): String? = null

    /**
     * Why an entry holding [data] and [state] may not be stored, beyond what each field says on
     * its own, or null when it may: a calendar's whole-day event has days, not a duration.
     * ToolDataService asks it on every create and update, the entry as it will be stored.
     */
    fun refuseEntry(data: Map<String, Any?>, state: Map<String, Any?>, context: Context): String? = null

    /**
     * Why [entry] may not be deleted by an ordinary write, though it may change, or null when it
     * may: a calendar's occurrence of a series is removed by its own operation, a deletion making
     * the series give it again. ToolDataService asks it before every delete, after refuseChange.
     */
    fun refuseDelete(entry: ToolDataEntity, context: Context): String? = null

    /**
     * Whether [entry] keeps the values it was judged or made with when the config changes
     * (docs/design/config-change.md): a change of config leaves it as it is. By default, an entry
     * no ordinary write may change; a goal's reopened attempt too, which keeps its copy.
     */
    fun keepsItsPast(entry: ToolDataEntity, context: Context): Boolean = refuseChange(entry, context) != null

    /**
     * Whether a tool of this type keeps entries of its own. One that shows others' (a chart) has
     * none: no fields of the user's in its config, and every write of an entry is refused.
     */
    fun keepsEntries(): Boolean = true

    /**
     * The fields of the rows this tool type's config describes, where a setting chooses among
     * them (SettingNode.Field.rowField): a chart's columns, per layer. Null for a config without
     * rows, which then declares no such setting.
     */
    fun getRowFields(): app.treelune.core.fields.settings.RowFields? = null

    /**
     * Why [config] may not be stored, beyond what its schema says, or null when it may: what only
     * reading the app tells, such as a chart's column that none of its sources gives.
     * ToolInstanceService asks it on every create and update, whoever the caller, once the schema
     * holds and the config is complete.
     */
    suspend fun refuseConfig(config: JSONObject, context: Context): String? = null

    /**
     * Get scheduler instance for this tool type.
     *
     * Discovery pattern: CoreScheduler discovers schedulers via ToolTypeManager,
     * tools with scheduling needs return a ToolScheduler instance.
     *
     * @return ToolScheduler instance if this tool requires periodic scheduling, null otherwise
     */
    fun getScheduler(): ToolScheduler? = null
}