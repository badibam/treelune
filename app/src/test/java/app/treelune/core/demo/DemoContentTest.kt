package app.treelune.core.demo

import app.treelune.core.coordinator.GivenId
import app.treelune.core.coordinator.Source
import app.treelune.core.fields.FieldNameGenerator
import app.treelune.core.grid.Grid
import app.treelune.core.grid.ZonePositions
import app.treelune.core.ui.DisplayMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers the demo the app ships, read from its assets as the service reads
 * them: the same text keys in both languages, every key the structure asks for given, the prefix on every id and the suffix on every variable, ids unique, the zones and
 * each section of tools laid out on their grid, the user's field names kept as given, and the
 * given ids accepted from the app alone.
 */
class DemoContentTest {

    private fun asset(name: String) = JSONObject(File("src/main/assets/demo/$name").readText(Charsets.UTF_8))

    private val structure = asset("structure.json")
    private val languages = listOf("texts-en.json", "texts-fr.json")

    private fun read(language: String = "texts-en.json") = DemoContent.read(structure, asset(language))

    @Test
    fun `both languages give the same text keys`() {
        val (en, fr) = languages.map { asset(it).keys().asSequence().toSet() }
        assertEquals(en, fr)
    }

    @Test
    fun `the demo reads whole in each language, every id prefixed and unique, every variable suffixed`() {
        for (language in languages) {
            val content = read(language)
            val ids = (content.zones + content.tools + content.variables).map { it.getString("id") }
            assertEquals(ids.size, ids.toSet().size)
            content.zones.forEach { assertEquals(content.group, it.getString("group")) }
        }
    }

    @Test
    fun `a text missing refuses the demo`() {
        val texts = asset("texts-en.json").apply { remove("course_name") }
        assertTrue(runCatching { DemoContent.read(structure, texts) }.isFailure)
    }

    @Test
    fun `the zones take a zone's modes, all four of them, and lie on the grid without overlapping`() {
        val zones = read().zones
        val modes = zones.map { DisplayMode.valueOf(it.getString("display_mode")) }
        assertEquals(ZonePositions.MODES.toSet(), modes.toSet())
        assertTrue(Grid.isLaidOut(zones.map { tile(it, it.getString("display_mode")) }))
    }

    @Test
    fun `each section of tools lies on its grid, in a group its zone has`() {
        val content = read()
        val groups = content.zones.associate { it.getString("id") to it.getJSONArray("tool_groups").strings() }
        content.tools.groupBy { it.getString("zone_id") to it.getJSONObject("config").optString("group") }.forEach { (section, tools) ->
            if (section.second.isNotEmpty()) assertTrue(section.toString(), section.second in groups.getValue(section.first))
            assertTrue(section.toString(), Grid.isLaidOut(tools.map { tile(it, it.getJSONObject("config").getString("display_mode")) }))
        }
    }

    @Test
    fun `the user's fields are named as given, the generator keeping each name`() {
        read().tools.forEach { tool ->
            val fields = tool.getJSONObject("config").optJSONArray("extra_fields") ?: return@forEach
            (0 until fields.length()).map { fields.getJSONObject(it).getString("name") }.forEach { name ->
                assertEquals(name, FieldNameGenerator.generateName(name, emptyList()))
            }
        }
    }

    @Test
    fun `every id a config names exists, and a tool another one reads is created before it`() {
        val content = read()
        val tools = content.tools.map { it.getString("id") }
        val variables = content.variables.map { it.getString("id") }
        val created = mutableSetOf<String>()
        // As the service creates them: the tools reading no variable, the variables, the others
        val (readingVariables, others) = content.tools.partition { DemoContent.readsVariable(it) }
        val order = others.map { it.getString("id") to it.getJSONObject("config") } +
            content.variables.map { it.getString("id") to it.getJSONObject("definition") } +
            readingVariables.map { it.getString("id") to it.getJSONObject("config") }
        for ((id, body) in order) {
            Regex("\"(demo-[a-z0-9-]+)\"").findAll(body.toString()).map { it.groupValues[1] }.forEach { named ->
                assertTrue("$id names $named, which the demo does not create", named in tools || named in variables)
                assertTrue("$id names $named, not created before it", named in created)
            }
            created.add(id)
        }
    }

    @Test
    fun `the demo shows every tool type, tracking type and tile mode, every field type among the user's fields, a tool in a group and one outside`() {
        val content = read()
        assertEquals(app.treelune.core.tools.ToolTypeScanner.scanForToolTypes().keys,
            content.tools.map { it.getString("tooltype") }.toSet())
        assertEquals(setOf("numeric", "scale", "choice", "boolean", "text", "timer", "occurrence"),
            content.tools.filter { it.getString("tooltype") == "tracking" }.map { it.getJSONObject("config").getString("type") }.toSet())
        assertEquals(DisplayMode.entries.map { it.name }.toSet(),
            content.tools.map { it.getJSONObject("config").getString("display_mode") }.toSet())
        val fieldTypes = content.tools.flatMap { tool ->
            tool.getJSONObject("config").optJSONArray("extra_fields")?.let { f -> (0 until f.length()).map { f.getJSONObject(it).getString("type") } } ?: emptyList()
        }.toSet()
        assertEquals(app.treelune.core.fields.FieldType.entries.map { it.name }.toSet(), fieldTypes)
        // In a zone that has groups, a tool in a group and one outside any
        val grouped = content.zones.filter { it.getJSONArray("tool_groups").length() > 0 }.map { it.getString("id") }.toSet()
        val inGrouped = content.tools.filter { it.getString("zone_id") in grouped }.map { it.getJSONObject("config").optString("group") }
        assertTrue(inGrouped.any { it.isNotEmpty() })
        assertTrue(inGrouped.any { it.isEmpty() })
    }

    @Test
    fun `the automations are off, each with its instruction, a catch-up with a schedule only, in a group its zone has`() {
        for (language in languages) {
            val content = read(language)
            assertEquals(4, content.automations.size)
            val groups = content.zones.associate { it.getString("id") to it.getJSONArray("tool_groups").strings() }
            content.automations.forEach { automation ->
                assertFalse(automation.getBoolean("is_enabled"))
                assertTrue(automation.getString("seed").isNotBlank())
                // Its pointers read as the routine's screen reads one
                automation.optJSONArray("pointers")?.let { pointers ->
                    (0 until pointers.length()).forEach {
                        val pointer = app.treelune.core.ai.enrichments.PointerConfig.fromJson(pointers.getJSONObject(it)) { key -> key }
                        assertEquals(null, pointer.selection.problem { key -> key })
                    }
                }
                assertEquals(automation.has("schedule"), automation.has("catch_up"))
                automation.optString("group").takeIf { it.isNotEmpty() }?.let { assertTrue(it in groups.getValue(automation.getString("zone_id"))) }
            }
        }
    }

    @Test
    fun `the demo shows what a zone, a tool, a field and a routine can carry beyond their type`() {
        val content = read()
        fun configs() = content.tools.map { it.getJSONObject("config") }
        fun fields() = configs().flatMap { c -> c.optJSONArray("extra_fields")?.let { f -> (0 until f.length()).map { f.getJSONObject(it) } } ?: emptyList() }
        // Textures, on a zone and on a tool, the icon as a motif among them
        assertTrue(content.zones.any { it.has("texture") })
        assertTrue(configs().any { it.optString("texture") == "ICON" } && configs().any { it.has("texture") && it.optString("texture") != "ICON" })
        // The three protections; no tool always sent, which would put the demo's data in every
        // prompt of whoever installs it (docs/design/user-journey.md, La démo)
        assertTrue(content.zones.any { it.optBoolean("validate") })
        assertTrue(configs().any { it.optBoolean("validate_data") })
        assertTrue(configs().none { it.optBoolean("always_send") })
        // A field read-only for the AI, and a choice of each kind: coloured, multiple and open, ordered
        assertTrue(fields().any { it.optBoolean("ai_read_only") })
        val choices = fields().filter { it.getString("type") == "CHOICE" }.map { it.getJSONObject("config") }
        assertTrue(choices.any { it.optBoolean("multiple") && it.optBoolean("open") })
        assertTrue(choices.any { it.optBoolean("ordered") })
        assertTrue(choices.any { c -> c.getJSONArray("options").let { o -> (0 until o.length()).any { o.getJSONObject(it).has("color") } } })
        // A goal judged once, one recurring, one whose validation is closed to the AI, one with sub-goals
        val goals = content.tools.filter { it.getString("tooltype") == "goal" }.map { it.getJSONObject("config") }
        assertEquals(setOf("once", "recurring"), goals.map { it.getString("recurrence") }.toSet())
        assertTrue(goals.any { it.optBoolean("ai_no_validation") } && goals.any { it.has("sub_goals") })
        // A chart of each mark the demo stands for: a pie among them
        assertTrue(content.tools.toString().contains("\"arc\""))
        // An identity, named by a zone and by a routine
        val identities = content.identities.map { it.getString("id") }.toSet()
        assertTrue(identities.isNotEmpty())
        assertTrue(content.zones.any { it.optString("identity_id") in identities })
        assertTrue(content.automations.any { it.optString("identity_id") in identities })
        // Routines held to an access, each end told as its routine says
        assertTrue(content.automations.all { it.has("access") })
        assertTrue(content.automations.map { it.optString("notify_end") }.toSet().containsAll(setOf("always", "on_problem")))
    }

    @Test
    fun `a moment of a config lands in the demo's weeks`() {
        val zone = java.time.ZoneId.of("Europe/Paris")
        val now = java.time.ZonedDateTime.of(2026, 10, 14, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        val data = DemoData(app.treelune.core.selection.TimeResolver(now, zone, 4, "MONDAY"), JSONObject())
        val race = read().tools.single { it.getString("id") == "demo-course-race" }
        val config = (DemoContent.dated(race, data::place) as JSONObject).getJSONObject("config")
        // Started eleven weeks back on a Monday, run on a Sunday six weeks ahead
        assertEquals(java.time.ZonedDateTime.of(2026, 7, 27, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), config.getLong("start"))
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 29, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), config.getLong("deadline"))
        assertFalse(config.toString().contains(DemoContent.AT))
    }

    @Test
    fun `a chart's columns are named as the chart takes them, in each language, and each one folded is one of its layer`() {
        for (language in languages) {
            fun layers(value: Any?): List<JSONObject> = when (value) {
                is JSONObject -> (if (value.has("columns") && value.has("mark")) listOf(value) else emptyList()) + value.keys().asSequence().flatMap { layers(value.get(it)) }
                is JSONArray -> (0 until value.length()).flatMap { layers(value.get(it)) }
                else -> emptyList()
            }
            read(language).tools.filter { it.getString("tooltype") == "chart" }.flatMap { layers(it.getJSONObject("config")) }.forEach { layer ->
                val names = layer.getJSONArray("columns").let { c -> (0 until c.length()).map { c.getJSONObject(it).getString("name") } }
                names.forEach { assertTrue("$language: $it", app.treelune.core.services.VariableService.NAME.matches(it)) }
                layer.optJSONArray("transform")?.let { t -> (0 until t.length()).mapNotNull { t.getJSONObject(it).optJSONArray("fold") } }
                    ?.forEach { fold -> (0 until fold.length()).forEach { assertTrue("$language: ${fold.getString(it)}", fold.getString(it) in names) } }
            }
        }
    }

    @Test
    fun `a filter on a choice of the user's takes the operators a choice takes`() {
        val content = read()
        val choices = content.tools.associate { tool ->
            tool.getString("id") to (tool.getJSONObject("config").optJSONArray("extra_fields")?.let { f ->
                (0 until f.length()).map { f.getJSONObject(it) }.filter { it.getString("type") == "CHOICE" }.map { "extra." + it.getString("name") }
            } ?: emptyList()).toSet()
        }
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val target = value.optJSONObject("target")?.optString("id")
                    value.optJSONArray("filters")?.takeIf { target != null }?.let { filters ->
                        (0 until filters.length()).map { filters.getJSONObject(it) }.forEach { filter ->
                            val field = filter.optJSONObject("left")?.optString("field")
                            if (field in choices[target].orEmpty()) assertTrue("$target $field ${filter.getString("op")}", filter.getString("op") in setOf("in", "absent", "present"))
                        }
                    }
                    value.keys().forEach { walk(value.get(it)) }
                }
                is JSONArray -> (0 until value.length()).forEach { walk(value.get(it)) }
            }
        }
        (content.tools + content.variables).forEach(::walk)
    }

    @Test
    fun `a field whose type has no settings carries no config`() {
        read().tools.forEach { tool ->
            val fields = tool.getJSONObject("config").optJSONArray("extra_fields") ?: return@forEach
            (0 until fields.length()).map { fields.getJSONObject(it) }.forEach { field ->
                val type = app.treelune.core.fields.FieldType.valueOf(field.getString("type"))
                if (app.treelune.core.fields.settings.FieldTypeSettings.configNodes(type) { it }.isEmpty())
                    assertFalse("${tool.getString("id")} ${field.getString("name")}", field.has("config"))
            }
        }
    }

    @Test
    fun `names and descriptions keep within their lengths, in each language`() {
        val short = app.treelune.core.validation.FieldLimits.SHORT_LENGTH
        val medium = app.treelune.core.validation.FieldLimits.MEDIUM_LENGTH
        for (language in languages) {
            val content = read(language)
            fun check(what: String, text: String, limit: Int) = assertTrue("$language $what: ${text.length} > $limit", text.length <= limit)
            content.zones.forEach { check(it.getString("id"), it.getString("name"), short); check(it.getString("id"), it.getString("description"), medium) }
            content.tools.forEach { tool ->
                val config = tool.getJSONObject("config")
                check(tool.getString("id"), config.getString("name"), short)
                check(tool.getString("id"), config.getString("description"), medium)
                config.optJSONArray("extra_fields")?.let { f -> (0 until f.length()).forEach { check(tool.getString("id"), f.getJSONObject(it).getString("display_name"), short) } }
            }
            content.automations.forEach { check(it.getString("id"), it.getString("name"), short) }
        }
    }

    @Test
    fun `an id is given by the app alone, with the demo's prefix`() {
        assertTrue(GivenId.isAccepted("demo-course", Source.SYSTEM))
        assertFalse(GivenId.isAccepted("demo-course", Source.AI))
        assertFalse(GivenId.isAccepted("demo-course", Source.USER))
        assertFalse(GivenId.isAccepted("course", Source.SYSTEM))
    }

    private fun tile(item: JSONObject, mode: String): Grid.Tile {
        val size = Grid.size(DisplayMode.valueOf(mode))
        return Grid.Tile(item.getString("id"), item.getInt("grid_x"), item.getInt("grid_y"), size.width, size.height)
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    @Test
    fun `every photo the entries name is shipped, every one shipped is named, each with its source`() {
        val named = mutableSetOf<String>()
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> if (value.has(DemoContent.IMAGE)) named += value.getString(DemoContent.IMAGE) else value.keys().forEach { walk(value.get(it)) }
                is JSONArray -> (0 until value.length()).forEach { walk(value.get(it)) }
            }
        }
        walk(asset("entries.json"))
        val shipped = File("src/main/assets/demo/images").list()!!.toSet()
        val sourced = JSONObject(File("src/main/java/app/treelune/core/demo/image-sources.json").readText(Charsets.UTF_8)).getJSONObject("images")
        assertTrue(named.isNotEmpty())
        assertEquals(shipped, named)
        assertEquals(shipped, sourced.keys().asSequence().toSet())
        sourced.keys().forEach { assertEquals("CC0", sourced.getJSONObject(it).getString("licence")) }
    }
}
