package app.treelune.core.services

import app.treelune.core.themes.IconColor
import app.treelune.core.themes.Texture
import app.treelune.core.utils.JsonUtils
import android.content.Context
import app.treelune.core.look.Looks
import app.treelune.core.schemas.ZoneSettings
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.Zone
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.commands.CommandStatus
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.DataChangeNotifier
import org.json.JSONObject
import org.json.JSONArray
import app.treelune.core.utils.LogManager
import app.treelune.core.grid.ToolPositions
import app.treelune.core.grid.Grid
import app.treelune.core.grid.Groups
import app.treelune.core.grid.ZonePositions
import app.treelune.core.database.entities.Zone as ZoneEntity
import app.treelune.core.database.transaction

/**
 * Zone Service - Core service for zone operations
 * Implements the standard service pattern with cancellation token
 */
class ZoneService(private val context: Context) : ExecutableService {
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val zoneDao by lazy { database.zoneDao() }
    private val writes by lazy { app.treelune.core.history.Writes(context) }
    private val s = Strings.`for`(context = context)
    
    /**
     * Execute zone operation with cancellation support
     */
    override suspend fun execute(
        operation: String, 
        params: JSONObject, 
        token: CancellationToken
    ): OperationResult {
        return try {
            when (operation) {
                "create" -> handleCreate(params, token)
                "update" -> handleUpdate(params, token)
                "delete" -> handleDelete(params, token)
                "get" -> handleGet(params, token)
                "list" -> handleList(params, token)
                "place" -> handlePlace(params)
                "place_section" -> handlePlaceSection(params)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            OperationResult.error(s.shared("service_error_zone_service").format(e.message ?: ""))
        }
    }
    
    /**
     * Create a new zone
     */
    private suspend fun handleCreate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val name = params.givenText("name")
            ?: return OperationResult.error(s.shared("service_error_zone_name_required"))
        // The app's own demo gives its ids; every other caller gets one made here
        val zoneId = when (val given = app.treelune.core.coordinator.GivenId.read(params)) {
            app.treelune.core.coordinator.GivenId.Read.None -> java.util.UUID.randomUUID().toString()
            is app.treelune.core.coordinator.GivenId.Read.Accepted -> given.id
            is app.treelune.core.coordinator.GivenId.Read.Refused -> return OperationResult.error(s.shared("service_error_id_not_given").format(given.id))
        }

        // Its name, description, icon, colour, texture and mode (Looks)
        val lookValues = JSONObject().apply { Looks.keys(ZoneSettings.LOOK).forEach { key -> if (params.has(key)) put(key, params.get(key)) } }
        val icon = when (val checked = Looks.check(ZoneSettings.LOOK, lookValues, null, context)) {
            is Looks.Checked.Refused -> return OperationResult.error(checked.message)
            is Looks.Checked.Kept -> checked
        }
        val look = Looks.read(ZoneSettings.LOOK, lookValues)

        // Parse tool_groups if provided (checked with the rest, checkZone)
        val toolGroupsJson = if (params.has("tool_groups")) {
            params.optJSONArray("tool_groups")?.toString()
        } else {
            null
        }

        // One of the home screen's groups, or none
        val group = Groups.held(params)
        Groups.refusal(group, zoneGroups(), s)?.let { return OperationResult.error(it) }

        val validate = params.optBoolean("validate", false)
        app.treelune.core.coordinator.Protections.refusal(s.shared("label_zone_validate"), false, validate, s)?.let { return OperationResult.error(it) }

        val identityId = params.givenText("identity_id")
        identityRefusal(identityId)?.let { return OperationResult.error(it) }

        if (token.isCancelled) return OperationResult.cancelled()

        val placing = Zone(
            id = zoneId,
            name = name,
            description = look.description,
            icon_name = look.iconName,
            icon_color = look.iconColor,
            texture = look.texture,
            display_mode = look.displayMode.name,
            grid_x = 0,
            grid_y = 0,
            tool_groups = toolGroupsJson,
            group = group,
            validate = validate,
            identity_id = identityId
        )
        checkZone(placing)?.let { return OperationResult.error(it) }


        if (token.isCancelled) return OperationResult.cancelled()

        // At the bottom of its section's grid on the home screen
        val newZone = database.transaction {
            val groups = zoneGroups()
            val tile = Grid.arrive(ZonePositions.tiles(zoneDao.getAllZones(), groups, ZonePositions.section(group, groups)), placing.id, ZonePositions.size(placing)).last()
            placing.copy(grid_x = tile.column, grid_y = tile.row).also { writes.insertZone(it) }
        }

        // Notify UI of zones change
        DataChangeNotifier.notifyZonesChanged()

        return OperationResult.success(mapOf(
            "zone_id" to newZone.id,
            "name" to newZone.name,
            "created_at" to newZone.created_at
        ) + icon.report())
    }
    
    /** A zone's look as its columns keep it, for Looks. */
    private fun lookOf(zone: Zone): JSONObject = JSONObject().apply {
        put("name", zone.name)
        zone.description?.let { put(Looks.DESCRIPTION, it) }
        zone.icon_name?.let { put(Looks.ICON, it) }
        zone.icon_color?.let { put(IconColor.KEY, it) }
        zone.texture?.let { put(Texture.KEY, it) }
        put(Looks.DISPLAY_MODE, zone.display_mode)
    }

    /** A zone's identity is one that exists, or none. */
    private suspend fun identityRefusal(identityId: String?): String? =
        if (identityId == null || database.identityDao().getById(identityId) != null) null
        else s.shared("identity_error_not_found").format(identityId)

    /** The identity [zone] names, by id and name, for the screen and the AI. */
    private suspend fun identityOf(zone: Zone, into: MutableMap<String, Any?>) {
        val id = zone.identity_id ?: return
        into["identity_id"] = id
        // One deleted in a way the service refuses would read here as named by its id alone
        into["identity_name"] = database.identityDao().getById(id)?.name
    }

    /**
     * Check a zone's settings exactly as they are about to be stored, against the schema
     * generated from their declaration (ZoneSettings). Every write goes through here, whoever
     * makes it -- the zone screen or the AI.
     *
     * @return The error to hand back, or null when the zone is valid
     */
    private fun checkZone(zone: Zone): String? {
        val settings = buildMap<String, Any?> {
            put("name", zone.name)
            zone.description?.let { put("description", it) }
            zone.icon_name?.let { put("icon_name", it) }
            zone.icon_color?.let { put(IconColor.KEY, it) }
            zone.texture?.let { put(Texture.KEY, it) }
            zone.group?.let { put("group", it) }
            zone.identity_id?.let { put("identity_id", it) }
            put("display_mode", zone.display_mode)
            zone.tool_groups?.let { put("tool_groups", JsonUtils.toList(it)) }
            put("validate", zone.validate)
        }
        val result = app.treelune.core.validation.SchemaValidator.validate(app.treelune.core.schemas.ZoneSettings.schema(context), settings, context)
        return if (result.isValid) null else result.errorMessage ?: s.shared("message_validation_error_simple")
    }

    /**
     * A text parameter as given: null when it is JSON null or blank, which is how a command
     * empties a field. optString alone would read JSON null as the text "null".
     */
    private fun JSONObject.givenText(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

    /** The home screen's zone groups. */
    private suspend fun zoneGroups(): List<String> = AppConfigService(context).getZoneGroups()

    private suspend fun write(moved: List<ZoneEntity>) {
        moved.forEach { writes.moveZone(it.id, it.grid_x, it.grid_y) }
    }

    /**
     * [before] becoming [after], placed on the home screen: a zone that changes section leaves its
     * grid and arrives in the other one; one that changes mode takes its new size where it is, the
     * zones it grows over moving down. The other zones that move are written here, [after] is
     * returned at its place for the caller to write.
     */
    private suspend fun move(before: ZoneEntity, after: ZoneEntity): ZoneEntity {
        val groups = zoneGroups()
        val zones = zoneDao.getAllZones()
        val from = ZonePositions.section(before.group, groups)
        val to = ZonePositions.section(after.group, groups)
        if (from != to) {
            write(ZonePositions.moved(zones, Grid.leave(ZonePositions.tiles(zones, groups, from), before.id)))
            val tile = Grid.arrive(ZonePositions.tiles(zoneDao.getAllZones().filter { it.id != after.id }, groups, to), after.id, ZonePositions.size(after)).last()
            return after.copy(grid_x = tile.column, grid_y = tile.row)
        }
        val size = ZonePositions.size(after)
        if (size == ZonePositions.size(before)) return after
        val tiles = Grid.resize(ZonePositions.tiles(zones, groups, to), after.id, size)
        write(ZonePositions.moved(zones.filter { it.id != after.id }, tiles))
        val tile = tiles.single { it.id == after.id }
        return after.copy(grid_x = tile.column, grid_y = tile.row)
    }

    /**
     * The places of the zones of one zone group section, moved in the home screen's edit mode:
     * `group` (absent for the ungrouped one) and `places`, `{zone_id: {"grid_x", "grid_y"}}` for
     * every zone of the section. Written in one go, or refused when they do not lay the section
     * out (Grid.isLaidOut). The screen alone moves tiles: the AI has no command for it.
     */
    private suspend fun handlePlace(params: JSONObject): OperationResult {
        val groups = zoneGroups()
        val section = ZonePositions.section(params.optString("group").takeIf { it.isNotEmpty() }, groups)
        val places = params.optJSONObject("places") ?: return OperationResult.error(s.shared("service_error_grid_places"))
        val zones = zoneDao.getAllZones().filter { ZonePositions.section(it.group, groups) == section }
        if (places.keys().asSequence().toSet() != zones.map { it.id }.toSet()) return OperationResult.error(s.shared("service_error_grid_places"))
        val placed = zones.map { zone ->
            val place = places.getJSONObject(zone.id)
            ZonePositions.tile(zone).copy(column = place.getInt("grid_x"), row = place.getInt("grid_y"))
        }
        if (!Grid.isLaidOut(placed)) return OperationResult.error(s.shared("service_error_grid_places"))
        database.transaction { write(ZonePositions.moved(zones, placed)) }
        DataChangeNotifier.notifyZonesChanged()
        return OperationResult.success(mapOf("count" to placed.size))
    }

    /**
     * The tiles of one of a zone's sections put where the grid editor leaves them, tools and
     * variables together (SectionGrid): `zone_id`, `group` (none: the ungrouped section),
     * `places` (each tile's id -> `grid_x`, `grid_y`), every tile of the section named once.
     */
    private suspend fun handlePlaceSection(params: JSONObject): OperationResult {
        val zone = zoneDao.getZoneById(params.optString("zone_id"))
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))
        val groups = ToolPositions.zoneGroups(zone.tool_groups)
        val section = ToolPositions.section(params.optString("group").takeIf { it.isNotEmpty() }, groups)
        val places = params.optJSONObject("places") ?: return OperationResult.error(s.shared("service_error_grid_places"))
        val given = places.keys().asSequence().associateWith { id -> places.getJSONObject(id).let { it.getInt("grid_x") to it.getInt("grid_y") } }
        val placed = database.transaction { app.treelune.core.grid.SectionGrid(context).place(zone.id, groups, section, given) }
        if (!placed) return OperationResult.error(s.shared("service_error_grid_places"))
        DataChangeNotifier.notifyToolsChanged(zone.id)
        DataChangeNotifier.notifyVariablesChanged()
        return OperationResult.success(mapOf("zone_id" to zone.id, "count" to given.size))
    }

    /**
     * Update existing zone
     */
    private suspend fun handleUpdate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id")
        if (zoneId.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_required"))
        }

        val existingZone = zoneDao.getZoneById(zoneId)
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))

        if (token.isCancelled) return OperationResult.cancelled()

        // Parse tool_groups if provided (checked with the rest, checkZone)
        val toolGroupsJson = if (params.has("tool_groups")) {
            // Allow explicit null to clear tool_groups
            val toolGroupsValue = params.opt("tool_groups")
            when {
                toolGroupsValue == null || toolGroupsValue == JSONObject.NULL -> null
                toolGroupsValue is JSONArray -> toolGroupsValue.toString()
                else -> existingZone.tool_groups
            }
        } else {
            existingZone.tool_groups // Keep existing value if not provided
        }

        // One of the home screen's groups, or none: null or empty removes it
        val group = if (params.has("group")) Groups.held(params) else existingZone.group
        Groups.refusal(group, zoneGroups(), s)?.let { return OperationResult.error(it) }

        // A partial update: a field left out keeps its value, a field given replaces it, and a
        // field given as null or empty is emptied -- except the name, which a zone must have.
        val name = if (params.has("name")) {
            params.givenText("name") ?: return OperationResult.error(s.shared("service_error_zone_name_required"))
        } else existingZone.name
        // Its look as it is, each setting given replacing its own (Looks): a texture checked
        // against the icon the zone will have, removing the icon of an ICON texture refused too
        val lookBefore = lookOf(existingZone)
        val lookValues = JSONObject(lookBefore.toString()).apply { Looks.keys(ZoneSettings.LOOK).forEach { key -> if (params.has(key)) put(key, params.get(key)) } }
        val icon = when (val checked = Looks.check(ZoneSettings.LOOK, lookValues, lookBefore, context)) {
            is Looks.Checked.Refused -> return OperationResult.error(checked.message)
            is Looks.Checked.Kept -> checked
        }
        // A mode given as null keeps the zone's
        if (!lookValues.has(Looks.DISPLAY_MODE) || lookValues.isNull(Looks.DISPLAY_MODE)) lookValues.put(Looks.DISPLAY_MODE, existingZone.display_mode)
        val look = Looks.read(ZoneSettings.LOOK, lookValues)
        val validate = if (params.has("validate")) params.optBoolean("validate") else existingZone.validate
        app.treelune.core.coordinator.Protections.refusal(s.shared("label_zone_validate"), existingZone.validate, validate, s)?.let { return OperationResult.error(it) }
        val identityId = if (params.has("identity_id")) params.givenText("identity_id") else existingZone.identity_id
        // Only an identity that changes is checked: a zone saved again keeps the one it has
        if (identityId != existingZone.identity_id) identityRefusal(identityId)?.let { return OperationResult.error(it) }
        val updatedZone = existingZone.copy(
            name = name,
            description = look.description,
            icon_name = look.iconName,
            icon_color = look.iconColor,
            texture = look.texture,
            display_mode = look.displayMode.name,
            tool_groups = toolGroupsJson,
            group = group,
            validate = validate,
            identity_id = identityId,
            updated_at = System.currentTimeMillis()
        )

        LogManager.service("ZoneService.handleUpdate - Updated zone with group: '${updatedZone.group}'", "DEBUG")

        checkZone(updatedZone)?.let { return OperationResult.error(it) }

        // The zone's tool groups are held by its tools, automations and variables: a group
        // renamed is renamed in them, a group removed while one holds it is refused
        val toolDao = database.toolInstanceDao()
        val automationDao = database.aiDao()
        val variableDao = database.variableDao()
        val change = Groups.Change(ToolPositions.zoneGroups(existingZone.tool_groups), ToolPositions.zoneGroups(updatedZone.tool_groups), Groups.renames(params, "tool_groups"))
        val tools = toolDao.getToolInstancesByZone(zoneId)
        val automations = automationDao.getAutomationsByZone(zoneId)
        val variables = variableDao.getByZone(zoneId)
        val tasks = database.taskDao().getByZone(zoneId)
        Groups.changeRefusal(change,
            tools.map { JSONObject(it.config_json).optString("name") to Groups.held(JSONObject(it.config_json)) } +
                automations.map { it.name to it.group } + variables.map { it.name to it.group } + tasks.map { it.name to it.group },
            s
        )?.let { return OperationResult.error(it) }

        // A tool or a variable whose group the zone gains or loses changes section, and so grid:
        // the zone, the names it changes and the places it changes are one write
        val regrouped = database.transaction {
            val renamedTools = tools.map { tool ->
                val config = JSONObject(tool.config_json)
                val group = change.held(Groups.held(config))
                if (group == Groups.held(config)) tool
                else tool.copy(config_json = config.put("group", group).toString()).also { writes.updateTool(it) }
            }
            automations.filter { change.held(it.group) != it.group }.forEach { writes.updateAutomation(it.copy(group = change.held(it.group))) }
            // A Task is outside the history of writes: its group is written as it is
            tasks.filter { change.held(it.group) != it.group }.forEach { database.taskDao().update(it.copy(group = change.held(it.group))) }
            val renamedVariables = variables.map { variable ->
                if (change.held(variable.group) == variable.group) variable
                else variable.copy(group = change.held(variable.group)).also { writes.updateVariable(it) }
            }
            val items = renamedTools.map { app.treelune.core.grid.SectionItem.of(it) } + renamedVariables.map { app.treelune.core.grid.SectionItem.of(it) }
            val moved = if (change.beforeRenamed == change.after) emptyList() else app.treelune.core.grid.SectionGrid(context).regroup(items, change.beforeRenamed, change.after)
            writes.updateZone(move(existingZone, updatedZone))
            moved
        }

        // Notify UI of zones change
        DataChangeNotifier.notifyZonesChanged()
        if (regrouped.isNotEmpty() || change.renames.isNotEmpty()) DataChangeNotifier.notifyToolsChanged(zoneId)
        if (regrouped.isNotEmpty() || change.renames.isNotEmpty()) DataChangeNotifier.notifyVariablesChanged()

        return OperationResult.success(mapOf(
            "zone_id" to updatedZone.id,
            "updated_at" to updatedZone.updated_at
        ) + icon.report())
    }
    
    /**
     * Delete zone
     */
    private suspend fun handleDelete(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val zoneId = params.optString("zone_id")
        if (zoneId.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_required"))
        }
        
        // Verify zone exists
        val existingZone = zoneDao.getZoneById(zoneId)
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        // The zone, its routines (which no foreign key ties to it) and the rows its leaving closes
        // on the home screen are one write
        database.transaction {
            val groups = zoneGroups()
            val zones = zoneDao.getAllZones()
            write(ZonePositions.moved(zones, Grid.leave(ZonePositions.tiles(zones, groups, ZonePositions.section(existingZone.group, groups)), zoneId)))
            database.aiDao().getAutomationsByZone(zoneId).forEach { writes.deleteAutomation(it.id) }
            writes.deleteZone(zoneId)
        }

        // Notify UI of zones change
        DataChangeNotifier.notifyZonesChanged()

        return OperationResult.success(mapOf(
            "zone_id" to zoneId,
            "name" to existingZone.name, // Include name for verbalization
            "deleted_at" to System.currentTimeMillis()
        ))
    }
    
    /**
     * Get single zone
     */
    private suspend fun handleGet(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val zoneId = params.optString("zone_id")
        if (zoneId.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_required"))
        }
        
        val zone = zoneDao.getZoneById(zoneId)
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))

        val zoneMap = mutableMapOf<String, Any?>(
            "id" to zone.id,
            "name" to zone.name,
            "description" to zone.description,
            "icon_name" to zone.icon_name,
            IconColor.KEY to zone.icon_color,
            Texture.KEY to zone.texture,
            "display_mode" to zone.display_mode,
            "validate" to zone.validate,
            "created_at" to zone.created_at,
            "updated_at" to zone.updated_at
        )

        // Add tool_groups if present
        if (zone.tool_groups != null) {
            zoneMap["tool_groups"] = JsonUtils.toList(zone.tool_groups)
        }

        // Add group if present
        if (zone.group != null) {
            zoneMap["group"] = zone.group
        }
        identityOf(zone, zoneMap)

        return OperationResult.success(mapOf("zone" to zoneMap))
    }
    
    
    /**
     * List all zones
     */
    private suspend fun handleList(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val zones = zoneDao.getAllZones()
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneData = zones.map { zone ->
            val zoneMap = mutableMapOf<String, Any?>(
                "id" to zone.id,
                "name" to zone.name,
                "description" to zone.description,
                "icon_name" to zone.icon_name,
                IconColor.KEY to zone.icon_color,
                Texture.KEY to zone.texture,
                "display_mode" to zone.display_mode,
                "validate" to zone.validate,
                "created_at" to zone.created_at,
                "updated_at" to zone.updated_at
            )

            // Add tool_groups if present
            if (zone.tool_groups != null) {
                zoneMap["tool_groups"] = JsonUtils.toList(zone.tool_groups)
            }

            // Add group if present
            if (zone.group != null) {
                zoneMap["group"] = zone.group
            }
            identityOf(zone, zoneMap)

            // Its place on the home screen, for the screen: the AI neither sees nor changes places
            if (params.optBoolean("include_position", false)) {
                zoneMap["grid_x"] = zone.grid_x
                zoneMap["grid_y"] = zone.grid_y
            }

            zoneMap
        }

        return OperationResult.success(mapOf(
            "zones" to zoneData,
            "count" to zoneData.size
        ))
    }

    /**
     * Generates human-readable description of zone action
     * Format: substantive form (e.g., "Création de la zone « Santé »")
     * Usage: (a) UI validation display, (b) SystemMessage feedback
     */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "create" -> {
                val name = params.optString("name", s.shared("content_unnamed"))
                s.shared("action_verbalize_create_zone").format(name)
            }
            "update" -> {
                val zoneId = params.optString("zone_id")
                val zoneName = getZoneName(zoneId, context) ?: s.shared("content_unnamed")
                s.shared("action_verbalize_update_zone").format(zoneName)
            }
            "delete" -> {
                val zoneId = params.optString("zone_id")
                // Try to get name from params first (enriched by CommandExecutor after delete),
                // otherwise fallback to DB lookup (which will fail if already deleted)
                val zoneName = params.optString("name").takeIf { it.isNotBlank() }
                    ?: getZoneName(zoneId, context)
                    ?: s.shared("content_unnamed")
                s.shared("action_verbalize_delete_zone").format(zoneName)
            }
            "place" -> s.shared("action_verbalize_zones_place")
            "place_section" -> s.shared("action_verbalize_zones_place_section")
            else -> s.shared("action_verbalize_unknown")
        }
    }

    /**
     * Helper to retrieve zone name by ID
     */
    private suspend fun getZoneName(zoneId: String, context: Context): String? {
        if (zoneId.isBlank()) return null
        val coordinator = Coordinator(context)
        val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to zoneId))
        return if (result.status == CommandStatus.SUCCESS) {
            val zone = result.data?.get("zone") as? Map<*, *>
            zone?.get("name") as? String
        } else null
    }
}
