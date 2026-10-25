package app.treelune.core.access

import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * An automation's access mask (docs/AI.md, « Levels »): what each level gives, what a zone
 * gives its tools, what each operation needs, and the refusal of what goes beyond.
 */
class AccessTest {

    private fun zone(id: String, level: AccessLevel) = AccessGrant(Reference(ReferenceKind.ZONE, id), level)
    private fun tool(id: String, level: AccessLevel) = AccessGrant(Reference(ReferenceKind.TOOL_INSTANCE, id), level)

    /**
     * Health holds weight and meals, the variable kcal and the routine weekly, whose run is r1;
     * garden is elsewhere, with seeds. The file scan is an entry's of weight, plot one of seeds,
     * joined a conversation's.
     */
    private val lookups = object : AccessLookups {
        override suspend fun toolZone(toolId: String) = mapOf("weight" to "health", "meals" to "health", "seeds" to "garden")[toolId]
        override suspend fun variableZone(idOrName: String, byName: Boolean) = if (idOrName in setOf("kcal", "v-kcal")) "health" else "garden"
        override suspend fun automationZone(automationId: String) = mapOf("weekly" to "health", "watering" to "garden")[automationId]
        override suspend fun taskZone(taskId: String) = mapOf("nightly" to "health")[taskId]
        override suspend fun eventZone(eventId: String) = mapOf("ready" to "health")[eventId]
        override suspend fun runAutomation(runId: String) = mapOf("r1" to "weekly")[runId]
        override suspend fun entryTool(entryId: String) = mapOf("w1" to "weight", "s1" to "seeds")[entryId]
        override suspend fun fileHolder(fileId: String) = mapOf("scan" to FileHolder.Tool("weight"), "plot" to FileHolder.Tool("seeds"), "joined" to FileHolder.Conversation)[fileId]
        override suspend fun zoneName(zoneId: String) = zoneId
        override suspend fun toolName(toolId: String) = toolId
        override fun isToolType(resource: String) = resource == "goal"
    }

    /** The texts a refusal is made of, each with the arguments its format takes. */
    private fun text(key: String) = when (key) {
        "access_refused" -> "%1\$s %2\$s %3\$s"
        "access_refused_unknown" -> "%1\$s %2\$s"
        "access_target_zone", "access_target_tool" -> "%1\$s"
        "access_widening" -> "%1\$s %2\$s %3\$s"
        "access_widening_open" -> "%1\$s everything"
        else -> key
    }

    private fun refusal(mask: AccessMask, resource: String, operation: String, params: Map<String, Any?>) = runBlocking {
        AccessRules.refusal(mask, "Weekly", AccessRules.reach(resource, operation, params, lookups::isToolType), lookups, ::text)
    }

    @Test
    fun anEmptyMaskReachesEverything() {
        val open = AccessMask()
        assertEquals(AccessLevel.FULL, open.zone("anything"))
        assertNull(refusal(open, "zones", "create", mapOf("name" to "New")))
        assertNull(refusal(open, "app_config", "set", mapOf("category" to "main_screen")))
    }

    @Test
    fun aZoneReadGivesItsToolsToReadAndAZoneUsedGivesThemFull() {
        assertEquals(AccessLevel.READ, AccessMask(listOf(zone("health", AccessLevel.READ))).tool("weight", "health"))
        assertEquals(AccessLevel.FULL, AccessMask(listOf(zone("health", AccessLevel.USE))).tool("weight", "health"))
        // A tool's own grant counts when it is higher
        assertEquals(AccessLevel.USE, AccessMask(listOf(zone("health", AccessLevel.READ), tool("weight", AccessLevel.USE))).tool("weight", "health"))
        assertNull(AccessMask(listOf(zone("health", AccessLevel.FULL))).tool("seeds", "garden"))
    }

    @Test
    fun aToolReadMayBeReadButNotWritten() {
        val mask = AccessMask(listOf(tool("weight", AccessLevel.READ)))
        assertNull(refusal(mask, "tool_data", "get", mapOf("id" to "weight")))
        assertNotNull(refusal(mask, "tool_data", "batch_create", mapOf("tool_instance_id" to "weight")))
        assertNotNull(refusal(mask, "tool_data", "get", mapOf("id" to "meals")))
        // A tool type's own operation writes the tool's entries
        assertNotNull(refusal(mask, "goal", "validate", mapOf("tool_instance_id" to "weight")))
    }

    @Test
    fun aKeptFileIsReadAsTheToolHoldingItAJoinedOneFreely() {
        val mask = AccessMask(listOf(tool("weight", AccessLevel.READ)))
        assertNull(refusal(mask, "files", "read", mapOf("id" to "scan")))
        assertNotNull(refusal(mask, "files", "read", mapOf("id" to "plot")))
        assertNull(refusal(mask, "files", "read", mapOf("id" to "joined")))
    }

    @Test
    fun aZoneUsedTakesNewToolsAndChangesItsToolsButNotItsOwnSettings() {
        val mask = AccessMask(listOf(zone("health", AccessLevel.USE)))
        assertNull(refusal(mask, "tools", "create", mapOf("zone_id" to "health")))
        assertNull(refusal(mask, "tools", "update", mapOf("tool_instance_id" to "weight")))
        assertNull(refusal(mask, "variables", "update", mapOf("variable_id" to "v-kcal")))
        assertNull(refusal(mask, "readings", "read", mapOf("variable" to "kcal")))
        assertNotNull(refusal(mask, "zones", "update", mapOf("zone_id" to "health")))
        // A tool moved into a zone out of reach
        assertNotNull(refusal(mask, "tools", "update", mapOf("tool_instance_id" to "weight", "zone_id" to "garden")))
    }

    @Test
    fun aFilledMaskKeepsTheAppAndWhatItCannotPlaceOutOfReach() {
        val mask = AccessMask(listOf(zone("health", AccessLevel.FULL)))
        assertNotNull(refusal(mask, "zones", "create", mapOf("name" to "New")))
        assertNotNull(refusal(mask, "app_config", "set", mapOf("category" to "main_screen")))
        assertNotNull(refusal(mask, "automations", "create", emptyMap()))
        // The lists, the schemas and the date stay free
        assertNull(refusal(mask, "zones", "list", emptyMap()))
        assertNull(refusal(mask, "schemas", "get", mapOf("id" to "x")))
        assertNull(refusal(mask, "app_config", "get_current_datetime", emptyMap()))
    }

    @Test
    fun aZoneReadGivesItsRoutinesAndTheirRunsToReadAndAZoneFullGivesThemToWrite() {
        val read = AccessMask(listOf(zone("health", AccessLevel.READ)))
        assertNull(refusal(read, "automations", "get", mapOf("automation_id" to "weekly")))
        assertNull(refusal(read, "automations", "config", mapOf("automation_id" to "weekly")))
        assertNotNull(refusal(read, "automations", "config", mapOf("automation_id" to "watering")))
        assertNull(refusal(read, "ai_sessions", "run_history", mapOf("session_id" to "r1")))
        assertNull(refusal(read, "automations", "list_all", emptyMap()))
        assertNotNull(refusal(read, "ai_sessions", "list_sessions_for_automation", mapOf("automation_id" to "watering")))
        // Any other session
        assertNotNull(refusal(read, "ai_sessions", "get_session", mapOf("session_id" to "r1")))
        // A zone used writes its tools, not its routines
        val used = AccessMask(listOf(zone("health", AccessLevel.USE)))
        assertNotNull(refusal(used, "automations", "create", mapOf("zone_id" to "health")))
        assertNotNull(refusal(used, "automations", "update", mapOf("automation_id" to "weekly")))
        val full = AccessMask(listOf(zone("health", AccessLevel.FULL)))
        assertNull(refusal(full, "automations", "create", mapOf("zone_id" to "health")))
        assertNull(refusal(full, "automations", "delete", mapOf("automation_id" to "weekly")))
        // A routine moved into a zone out of reach, a run of an unknown routine
        assertNotNull(refusal(full, "automations", "update", mapOf("automation_id" to "weekly", "zone_id" to "garden")))
        assertNotNull(refusal(full, "ai_sessions", "run_history", mapOf("session_id" to "r9")))
        // What the screens alone do
        assertNotNull(refusal(full, "automations", "execute_manual", mapOf("automation_id" to "weekly")))
    }

    private fun widening(given: AccessMask, holder: AccessMask) = runBlocking { AccessRules.widening(given, holder, "Weekly", lookups, ::text) }

    @Test
    fun anAccessWrittenForARoutineIsNoWiderThanTheWritersOwn() {
        val holder = AccessMask(listOf(zone("health", AccessLevel.USE), tool("seeds", AccessLevel.READ)))
        assertNull(widening(AccessMask(listOf(zone("health", AccessLevel.READ), tool("seeds", AccessLevel.READ))), holder))
        // A tool of a zone used is reached in full
        assertNull(widening(AccessMask(listOf(tool("weight", AccessLevel.FULL))), holder))
        assertNotNull(widening(AccessMask(listOf(zone("health", AccessLevel.FULL))), holder))
        assertNotNull(widening(AccessMask(listOf(tool("seeds", AccessLevel.USE))), holder))
        assertNotNull(widening(AccessMask(listOf(zone("garden", AccessLevel.READ))), holder))
        // Listing nothing reaches everything
        assertNotNull(widening(AccessMask(), holder))
        // Without limits, anything goes
        assertNull(widening(AccessMask(), AccessMask()))
    }

    @Test
    fun aMaskThatReachesNothingRefusesEverythingButWhatIsFree() {
        assertNotNull(refusal(AccessMask.NOTHING, "tool_data", "get", mapOf("id" to "weight")))
        assertNull(refusal(AccessMask.NOTHING, "zones", "list", emptyMap()))
    }

    @Test
    fun theStoredFormReadsBack() {
        val mask = AccessMask(listOf(zone("health", AccessLevel.USE), tool("seeds", AccessLevel.READ)))
        assertEquals(mask, AccessMask.fromJson(mask.toJson().toString()))
        val stored = AccessMask(listOf(zone("health", AccessLevel.USE))).toJson().getJSONObject(0)
        assertEquals("use", stored.getString("level"))
        assertEquals("ZONE", stored.getJSONObject("target").getString("kind"))
        assertEquals("health", stored.getJSONObject("target").getString("id"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun anAccessIsGivenOnAZoneOrAToolAlone() {
        AccessGrant(Reference(ReferenceKind.VARIABLE, "kcal"), AccessLevel.READ)
    }

    @Test
    fun aReadingOfASelection_isHeldAsItsTarget() {
        val mask = AccessMask(listOf(zone("health", AccessLevel.READ)))
        fun reading(kind: String, id: String) = refusal(mask, "readings", "read", mapOf("selection" to mapOf("target" to mapOf("kind" to kind, "id" to id))))
        assertNull(reading("TOOL_INSTANCE", "weight"))
        assertNull(reading("ENTRY", "w1"))
        assertNotNull(reading("ENTRY", "s1"))
        assertNotNull(reading("ZONE", "garden"))
    }

    @Test
    fun aTask_isRunAsAUseOfItsZone() {
        assertNull(refusal(AccessMask(listOf(zone("health", AccessLevel.USE))), "tasks", "run", mapOf("task_id" to "nightly")))
        assertNotNull(refusal(AccessMask(listOf(zone("health", AccessLevel.READ))), "tasks", "run", mapOf("task_id" to "nightly")))
    }

    @Test
    fun aTaskWrittenUnderAMask_reachesNoFurtherThanIt() {
        val mask = AccessMask(listOf(zone("health", AccessLevel.USE)))
        fun step(json: String) = org.json.JSONObject(json)
        fun reaches(json: String) = runBlocking {
            AccessRules.refusal(mask, "Weekly", app.treelune.core.tasks.TaskSteps.reaches(step(json), { null }, lookups::isToolType), lookups, ::text)
        }
        assertNull(reaches("""{"kind": "create_entry", "tool_instance_id": "weight", "values": {"data.kg": {"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "meals"}}, "reduction": "LAST"}}}}"""))
        assertNotNull(reaches("""{"kind": "create_entry", "tool_instance_id": "seeds"}"""))
        assertNotNull(reaches("""{"kind": "call_api", "api_id": "a", "inputs": {"q": {"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "seeds"}}, "reduction": "LAST"}}}}"""))
        assertNull(reaches("""{"kind": "call_api", "api_id": "a", "inputs": {"q": {"constant": "x"}}}"""))
    }
}
