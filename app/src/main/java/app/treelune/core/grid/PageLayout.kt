package app.treelune.core.grid

import android.content.Context
import app.treelune.core.ai.data.RoutineSettings
import app.treelune.core.config.AppSettings
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.transaction
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.database.entities.Zone
import app.treelune.core.history.Writes
import app.treelune.core.look.ItemLayout
import app.treelune.core.look.ItemWidth
import app.treelune.core.look.LookSpec
import app.treelune.core.look.Looks
import app.treelune.core.look.TilePieces
import app.treelune.core.look.TileSize
import app.treelune.core.readouts.ReadoutSettings
import app.treelune.core.schemas.ZoneSettings
import app.treelune.core.services.AppConfigService
import app.treelune.core.strings.Strings
import app.treelune.core.tasks.TaskSettings
import app.treelune.core.themes.IconColor
import app.treelune.core.themes.Texture
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.ui.components.IconStrips
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import app.treelune.core.validation.SchemaValidator
import app.treelune.core.variables.VariableLook
import org.json.JSONArray
import org.json.JSONObject

/**
 * A page read for the AI's plan and arranged as it asks (docs/design/ai-page-layout.md): every
 * write checked first, then made in one transaction, through [Writes], so that the command is one
 * step of the history.
 */
class PageLayout(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val writes = Writes(context)
    private val s = Strings.`for`(context = context)

    /** A write refused while it is prepared, before anything is written. */
    private class Refusal(override val message: String) : Exception(message)

    /** The page of zone [zoneId], or the home screen for null; null for a zone that does not exist. */
    suspend fun read(zoneId: String?): Page? {
        val oneColumn = database.appSettingsCategoryDao().getSettingsJsonForCategory(AppSettingCategories.UI)
            ?.let { JSONObject(it).getBoolean(AppSettings.UI_ONE_COLUMN) } ?: false
        if (zoneId == null) return home(oneColumn)
        val zone = database.zoneDao().getZoneById(zoneId) ?: return null
        val tiles = zoneTiles(zone.id)
        val groups = ToolPositions.zoneGroups(zone.tool_groups)
        return Page(zone.id, sections(groups, tiles) { it.first }, PageZoneTile(TileSize.ofStored(zone.tile_size), zone.icon_name, zone.icon_color, zone.texture,
            TilePieces.read(zone.tile_pieces), zone.tile_pieces_icons), oneColumn)
    }

    private suspend fun home(oneColumn: Boolean): Page {
        val groups = AppConfigService(context).getZoneGroups()
        val tiles = database.zoneDao().getAllZones().map { zone ->
            val size = TileSize.ofStored(zone.tile_size)
            // A long row of icons folds by its pieces in one column; a piece with its summary counts its two cells
            val folded = if (size.inIcons && !size.vertical && size.length > IconStrips.ONE_COLUMN_ROW)
                IconStrips.oneColumn(size, listOf(1) + TilePieces.read(zone.tile_pieces).map { if (it.summary) 2 else 1 }) else null
            ZonePositions.section(zone.group, groups) to PageTile(zone.id, "zone", zone.name, size, zone.icon_name, zone.icon_color, zone.texture, zone.grid_x, zone.grid_y, inOneColumn = folded)
        }
        return Page(null, sections(groups, tiles) { it.first }, null, oneColumn)
    }

    /** [groups] then the ungrouped section when it holds a tile, each with its tiles in reading order. */
    private fun sections(groups: List<String>, tiles: List<Pair<String?, PageTile>>, section: (Pair<String?, PageTile>) -> String?): List<PageSection> =
        (groups.map { it as String? } + null).mapNotNull { name ->
            val held = tiles.filter { section(it) == name }.map { it.second }.sortedWith(compareBy({ it.row }, { it.column }))
            if (name == null && held.isEmpty()) null else PageSection(name, held)
        }

    private suspend fun zoneTiles(zoneId: String): List<Pair<String?, PageTile>> {
        val groups = ToolPositions.zoneGroups(database.zoneDao().getZoneById(zoneId)?.tool_groups)
        fun at(group: String?) = ToolPositions.section(group, groups)
        fun size(stored: String) = TileSize.ofStored(stored)
        val tools = database.toolInstanceDao().getToolInstancesByZone(zoneId).map { tool ->
            val config = JSONObject(tool.config_json)
            val type = ToolTypeManager.getToolType(tool.tooltype) ?: throw IllegalStateException("Tool ${tool.id}: unknown type ${tool.tooltype}")
            val items = ToolConfigSettings.itemLayout(type, config)
            at(config.text("group")) to PageTile(tool.id, "tool", config.optString("name"), size(config.getString(Looks.TILE_SIZE)), config.text(Looks.ICON), config.text(IconColor.KEY), config.text(Texture.KEY),
                tool.grid_x, tool.grid_y, items?.width?.stored, items?.lines)
        }
        val variables = database.variableDao().getByZone(zoneId).map {
            at(it.group) to PageTile(it.id, "variable", it.label, size(it.tileSize), it.iconName, it.iconColor, it.texture, it.gridX, it.gridY)
        }
        val routines = database.aiDao().getRoutinesByZone(zoneId).map {
            at(it.group) to PageTile(it.id, "routine", it.name, size(it.tileSize), it.iconName, it.iconColor, it.texture, it.gridX, it.gridY)
        }
        val tasks = database.taskDao().getByZone(zoneId).map {
            at(it.group) to PageTile(it.id, "task", it.name, size(it.tileSize), it.iconName, it.iconColor, it.texture, it.gridX, it.gridY)
        }
        val readouts = database.readoutDao().getByZone(zoneId).map {
            at(it.group) to PageTile(it.id, "readout", it.name, size(it.tileSize), it.iconName, it.iconColor, it.texture, it.gridX, it.gridY)
        }
        return tools + variables + routines + tasks + readouts
    }

    /** [arranged] written on [page], [checkZone] saying why a zone cannot be stored; the refusal, or null once written. */
    suspend fun apply(page: Page, arranged: PageArrangement.Arranged, checkZone: (Zone) -> String?): String? {
        val toWrite = mutableListOf<suspend () -> Unit>()
        try {
            if (page.zoneId != null) prepareZone(page.zoneId, arranged, checkZone, toWrite) else prepareHome(arranged, checkZone, toWrite)
        } catch (refusal: Refusal) {
            return refusal.message
        }
        database.transaction { for (write in toWrite) write() }
        DataChangeNotifier.notifyZonesChanged()
        if (page.zoneId != null) {
            DataChangeNotifier.notifyToolsChanged(page.zoneId)
            DataChangeNotifier.notifyVariablesChanged()
        } else {
            AppConfigManager.refresh(context)
            DataChangeNotifier.notifyAppConfigChanged()
        }
        return null
    }

    /** A look once its [changes] are applied and checked against its owner's [spec]. */
    private class Looked(val icon: String?, val color: String?, val texture: String?, val size: String)

    private fun looked(spec: LookSpec, icon: String?, color: String?, texture: String?, size: String, changes: Map<String, Any?>): Looked {
        val before = JSONObject().put(Looks.TILE_SIZE, size)
        icon?.let { before.put(Looks.ICON, it) }
        color?.let { before.put(IconColor.KEY, it) }
        texture?.let { before.put(Texture.KEY, it) }
        val values = JSONObject(before.toString())
        for (key in listOf(Looks.TILE_SIZE, Looks.ICON, IconColor.KEY, Texture.KEY)) if (changes.containsKey(key)) values.put(key, changes[key] ?: JSONObject.NULL)
        (Looks.check(spec, values, before, context) as? Looks.Checked.Refused)?.let { throw Refusal(it.message) }
        return Looked(values.text(Looks.ICON), values.text(IconColor.KEY), values.text(Texture.KEY), values.getString(Looks.TILE_SIZE))
    }

    /** Refuses the settings of a tile's elements on a tile that lists none. */
    private fun noItems(name: String, changes: Map<String, Any?>) {
        if (changes.containsKey(ItemLayout.WIDTH) || changes.containsKey(ItemLayout.LINES)) throw Refusal(s.shared("page_error_no_items").format(name))
    }

    private suspend fun prepareZone(zoneId: String, arranged: PageArrangement.Arranged, checkZone: (Zone) -> String?, toWrite: MutableList<suspend () -> Unit>) {
        val zone = database.zoneDao().getZoneById(zoneId) ?: throw Refusal(s.shared("service_error_zone_not_found"))
        val tools = database.toolInstanceDao().getToolInstancesByZone(zoneId).associateBy { it.id }
        val variables = database.variableDao().getByZone(zoneId).associateBy { it.id }
        val routines = database.aiDao().getRoutinesByZone(zoneId).associateBy { it.id }
        val tasks = database.taskDao().getByZone(zoneId).associateBy { it.id }
        val readouts = database.readoutDao().getByZone(zoneId).associateBy { it.id }
        val now = System.currentTimeMillis()

        for (tile in arranged.placed) {
            val changes = tile.changes
            tools[tile.id]?.let { tool ->
                val type = ToolTypeManager.getToolType(tool.tooltype) ?: throw IllegalStateException("Tool ${tool.id}: unknown type ${tool.tooltype}")
                val config = JSONObject(tool.config_json)
                val look = looked(ToolConfigSettings.look(type), config.text(Looks.ICON), config.text(IconColor.KEY), config.text(Texture.KEY), config.getString(Looks.TILE_SIZE), changes)
                if (tile.section == null) config.remove("group") else config.put("group", tile.section)
                config.putOrRemove(Looks.ICON, look.icon).putOrRemove(IconColor.KEY, look.color).putOrRemove(Texture.KEY, look.texture).put(Looks.TILE_SIZE, look.size)
                if (changes.containsKey(ItemLayout.WIDTH) || changes.containsKey(ItemLayout.LINES)) {
                    val spec = type.getItemSpec() ?: throw Refusal(s.shared("page_error_no_items").format(config.optString("name")))
                    (changes[ItemLayout.WIDTH] as? String)?.let { width ->
                        if (ItemWidth.of(width) !in spec.widths) throw Refusal(s.shared("page_error_value").format(ItemLayout.WIDTH, width))
                        config.put(ItemLayout.WIDTH, width)
                    }
                    (changes[ItemLayout.LINES] as? Int)?.let { lines ->
                        if (lines !in spec.lines) throw Refusal(s.shared("page_error_value").format(ItemLayout.LINES, lines))
                        config.put(ItemLayout.LINES, lines)
                    }
                }
                type.refuseConfig(config, tool.id, context)?.let { throw Refusal(it) }
                val updated = tool.copy(config_json = config.toString(), grid_x = tile.column, grid_y = tile.row)
                if (JsonUtils.toMap(JSONObject(tool.config_json)) != JsonUtils.toMap(config) || updated.grid_x != tool.grid_x || updated.grid_y != tool.grid_y) {
                    toWrite += { writes.updateTool(updated.copy(updated_at = now)) }
                }
            } ?: variables[tile.id]?.let { variable ->
                noItems(variable.label, changes)
                val look = looked(VariableLook.SPEC, variable.iconName, variable.iconColor, variable.texture, variable.tileSize, changes)
                val icon = look.icon ?: throw Refusal(s.shared("page_error_value").format(Looks.ICON, "null"))
                val updated = variable.copy(group = tile.section, iconName = icon, iconColor = look.color, texture = look.texture, tileSize = look.size, gridX = tile.column, gridY = tile.row)
                if (updated != variable) toWrite += { writes.updateVariable(updated.copy(updatedAt = now)) }
            } ?: routines[tile.id]?.let { routine ->
                noItems(routine.name, changes)
                val look = looked(RoutineSettings.LOOK, routine.iconName, routine.iconColor, routine.texture, routine.tileSize, changes)
                val icon = look.icon ?: throw Refusal(s.shared("page_error_value").format(Looks.ICON, "null"))
                val updated = routine.copy(group = tile.section, iconName = icon, iconColor = look.color, texture = look.texture, tileSize = look.size, gridX = tile.column, gridY = tile.row)
                if (updated != routine) toWrite += { writes.updateRoutine(updated.copy(updatedAt = now)) }
            } ?: tasks[tile.id]?.let { task ->
                noItems(task.name, changes)
                val look = looked(TaskSettings.LOOK, task.iconName, task.iconColor, task.texture, task.tileSize, changes)
                val icon = look.icon ?: throw Refusal(s.shared("page_error_value").format(Looks.ICON, "null"))
                val updated = task.copy(group = tile.section, iconName = icon, iconColor = look.color, texture = look.texture, tileSize = look.size, gridX = tile.column, gridY = tile.row)
                if (updated != task) toWrite += { writes.updateTask(updated.copy(updatedAt = now)) }
            } ?: readouts[tile.id]?.let { readout ->
                noItems(readout.name, changes)
                val look = looked(ReadoutSettings.LOOK, readout.iconName, readout.iconColor, readout.texture, readout.tileSize, changes)
                val icon = look.icon ?: throw Refusal(s.shared("page_error_value").format(Looks.ICON, "null"))
                val updated = readout.copy(group = tile.section, iconName = icon, iconColor = look.color, texture = look.texture, tileSize = look.size, gridX = tile.column, gridY = tile.row)
                if (updated != readout) toWrite += { writes.updateReadout(updated.copy(updatedAt = now)) }
            } ?: throw IllegalStateException("tile ${tile.id} is not of zone $zoneId")
        }

        val change = arranged.zoneTile
        val look = looked(ZoneSettings.LOOK, zone.icon_name, zone.icon_color, zone.texture, zone.tile_size, change?.changes.orEmpty())
        val updated = zone.copy(
            tool_groups = if (arranged.groups.isEmpty()) null else JSONArray(arranged.groups).toString(),
            icon_name = look.icon, icon_color = look.color, texture = look.texture, tile_size = look.size,
            tile_pieces = change?.pieces?.let { TilePieces.write(it) } ?: zone.tile_pieces,
            tile_pieces_icons = change?.piecesInIcons ?: zone.tile_pieces_icons
        )
        checkZone(updated)?.let { throw Refusal(it) }
        if (ToolPositions.zoneGroups(updated.tool_groups) == ToolPositions.zoneGroups(zone.tool_groups) && updated == zone) return

        toWrite += {
            // A size that changes takes its place on the home screen, the zones it grows over moving down
            var placed = updated
            if (updated.tile_size != zone.tile_size) {
                val homeGroups = AppConfigService(context).getZoneGroups()
                val zones = database.zoneDao().getAllZones()
                val resized = Grid.resize(ZonePositions.tiles(zones, homeGroups, ZonePositions.section(zone.group, homeGroups)), zone.id, ZonePositions.size(updated))
                ZonePositions.moved(zones.filter { it.id != zone.id }, resized).forEach { writes.moveZone(it.id, it.grid_x, it.grid_y) }
                resized.single { it.id == zone.id }.let { placed = updated.copy(grid_x = it.column, grid_y = it.row) }
            }
            writes.updateZone(placed.copy(updated_at = now))
        }
    }

    private suspend fun prepareHome(arranged: PageArrangement.Arranged, checkZone: (Zone) -> String?, toWrite: MutableList<suspend () -> Unit>) {
        // Read first: a category never written gets its row here
        val before = AppConfigService(context).getZoneGroups()
        val zones = database.zoneDao().getAllZones().associateBy { it.id }
        val now = System.currentTimeMillis()
        for (tile in arranged.placed) {
            val zone = zones[tile.id] ?: throw IllegalStateException("zone ${tile.id}")
            noItems(zone.name, tile.changes)
            val look = looked(ZoneSettings.LOOK, zone.icon_name, zone.icon_color, zone.texture, zone.tile_size, tile.changes)
            val updated = zone.copy(group = tile.section, icon_name = look.icon, icon_color = look.color, texture = look.texture, tile_size = look.size, grid_x = tile.column, grid_y = tile.row)
            if (updated.group != null && updated.group !in arranged.groups) throw IllegalStateException("zone ${tile.id}: group ${updated.group}")
            checkZone(updated)?.let { throw Refusal(it) }
            if (updated != zone) toWrite += { writes.updateZone(updated.copy(updated_at = now)) }
        }
        if (arranged.groups == before) return
        val stored = database.appSettingsCategoryDao().getSettingsJsonForCategory(AppSettingCategories.MAIN_SCREEN)
            ?: throw IllegalStateException("No ${AppSettingCategories.MAIN_SCREEN} settings")
        val settings = JSONObject(stored).put("zone_groups", JSONArray(arranged.groups))
        val checked = SchemaValidator.validate(AppSettings.schema(AppSettingCategories.MAIN_SCREEN, context), JsonUtils.toMap(settings), context)
        if (!checked.isValid) throw Refusal(checked.errorMessage ?: s.shared("message_validation_error_simple"))
        toWrite += { writes.updateSettings(AppSettingCategories.MAIN_SCREEN, settings.toString()) }
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

    private fun JSONObject.putOrRemove(key: String, value: String?): JSONObject = apply { if (value == null) remove(key) else put(key, value) }
}
