package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The routines' stored names say routine where they said automation, wherever they are stored. */
class RoutinesAtV107Test {

    @Test
    fun aRunsSession_namesItsRoutineAndIsARoutineRun() {
        val session = RoutinesAtV107.session(JSONObject().put("id", "s").put("type", "AUTOMATION").put("automation_id", "r"))

        assertEquals("ROUTINE_RUN", session.getString("type"))
        assertEquals("r", session.getString("routine_id"))
        assertFalse(session.has("automation_id"))
    }

    /** A conversation and a routine's seed session keep their type; a null routine stays null. */
    @Test
    fun aConversationAndASeed_keepTheirType() {
        val chat = RoutinesAtV107.session(JSONObject().put("type", "CHAT").put("automation_id", JSONObject.NULL))
        val seed = RoutinesAtV107.session(JSONObject().put("type", "SEED").put("automation_id", JSONObject.NULL))

        assertEquals("CHAT", chat.getString("type"))
        assertEquals("SEED", seed.getString("type"))
        assertTrue(chat.isNull("routine_id"))
    }

    @Test
    fun theReminderOfARunWithoutCommand() {
        val json = RoutinesAtV107.systemMessage("""{"type":"AUTOMATION_NO_COMMANDS","summary":"s"}""")!!

        assertEquals("ROUTINE_NO_COMMANDS", JSONObject(json).getString("type"))
        assertNull(RoutinesAtV107.systemMessage("""{"type":"NETWORK_ERROR","summary":"s"}"""))
    }

    @Test
    fun aTaskStepRunningARoutine() {
        val steps = """[{"kind":"run_routine","automation_id":"r","data":"d"},{"kind":"emit"}]"""
        val step = JSONArray(RoutinesAtV107.steps(steps)!!).getJSONObject(0)

        assertEquals("r", step.getString("routine_id"))
        assertFalse(step.has("automation_id"))
        assertNull(RoutinesAtV107.steps("""[{"kind":"emit"}]"""))
    }

    @Test
    fun theAiLimitsAndTheGuide() {
        val limits = JSONObject(RoutinesAtV107.settings("ai_limits", """{"automation_max_autonomous_roundtrips":20,"automation_max_data_chars":100000,"chain_max_runs":5}""")!!)
        assertEquals(20, limits.getInt("routine_max_autonomous_roundtrips"))
        assertEquals(100000, limits.getInt("routine_max_data_chars"))
        assertEquals(5, limits.getInt("chain_max_runs"))
        assertFalse(limits.has("automation_max_data_chars"))

        val guide = JSONObject(RoutinesAtV107.settings("guide", """{"chapters":[{"id":"automate","step":2,"done":false,"kept":[{"name":"automation","value":"r"},{"name":"zone","value":"z"}]}]}""")!!)
        val kept = guide.getJSONArray("chapters").getJSONObject(0).getJSONArray("kept")
        assertEquals("routine", kept.getJSONObject(0).getString("name"))
        assertEquals("zone", kept.getJSONObject(1).getString("name"))

        assertNull(RoutinesAtV107.settings("ui", """{"automation_max_data_chars":1}"""))
    }

    @Test
    fun aBackup_itsRoutinesUnderTheirKeyAndWhatNamesThem() {
        val data = JSONObject()
            .put("automations", JSONArray().put(JSONObject().put("id", "r")))
            .put("ai_sessions", JSONArray().put(JSONObject().put("type", "AUTOMATION").put("automation_id", "r")))
            .put("session_messages", JSONArray().put(JSONObject().put("system_message_json", """{"type":"AUTOMATION_NO_COMMANDS"}""")))
            .put("tasks", JSONArray().put(JSONObject().put("steps_json", """[{"automation_id":"r"}]""")))
        RoutinesAtV107.backup(data)

        assertFalse(data.has("automations"))
        assertEquals("r", data.getJSONArray("routines").getJSONObject(0).getString("id"))
        assertEquals("r", data.getJSONArray("ai_sessions").getJSONObject(0).getString("routine_id"))
        assertEquals("ROUTINE_RUN", data.getJSONArray("ai_sessions").getJSONObject(0).getString("type"))
        assertEquals("ROUTINE_NO_COMMANDS", JSONObject(data.getJSONArray("session_messages").getJSONObject(0).getString("system_message_json")).getString("type"))
        assertEquals("r", JSONArray(data.getJSONArray("tasks").getJSONObject(0).getString("steps_json")).getJSONObject(0).getString("routine_id"))
    }

    /** A backup's settings go through the app config's chain, after the versions before them: the limits of v31 reach v107 whole. */
    @Test
    fun aBackupsAiLimits_throughTheAppConfigsChain() {
        val v31 = """{ "chat_max_action_retries": 3, "automation_max_autonomous_roundtrips": 30 }"""
        val current = JSONObject(JsonTransformers.transformAppConfig(v31, "ai_limits", 31, 107))

        assertEquals(30, current.getInt("routine_max_autonomous_roundtrips"))
        assertEquals(100_000, current.getInt("routine_max_data_chars"))
        assertFalse(current.has("automation_max_autonomous_roundtrips") || current.has("automation_max_data_chars"))
    }

    /** The history's copies, so that an undo puts back a row the renamed tables can read. */
    @Test
    fun theHistorysCopies() {
        assertEquals("r", RoutinesAtV107.historyCopy("ai_sessions", JSONObject().put("type", "AUTOMATION").put("automation_id", "r")).getString("routine_id"))
        assertEquals("r", JSONArray(RoutinesAtV107.historyCopy("tasks", JSONObject().put("steps_json", """[{"automation_id":"r"}]""")).getString("steps_json")).getJSONObject(0).getString("routine_id"))
        val zone = JSONObject().put("name", "automation")
        assertEquals(zone.toString(), RoutinesAtV107.historyCopy("zones", JSONObject(zone.toString())).toString())
    }
}
