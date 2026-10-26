package app.treelune.core.tools

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.tools.questionnaire.QuestionnaireToolType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * When an item left waiting expires (WaitingExpiry): never by default, a config made before the
 * setting included; at the next one made, all but it; past a delay, those it ran out for. And a
 * questionnaire set to the next one, invited twice: the first expired, the second to fill.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WaitingExpiryTest {

    private val day = 86_400_000L
    private val plain: (Long, Long) -> Long = { start, delay -> start + delay }
    private val waiting = listOf("old" to 0L, "recent" to 3 * day, "made" to 5 * day)

    @Test
    fun `never expires, a config without the setting neither`() {
        assertEquals(WaitingExpiry.Rule.Never, WaitingExpiry.of(JSONObject()))
        assertTrue(WaitingExpiry.expired(WaitingExpiry.Rule.Never, waiting, "made", 10 * day, plain).isEmpty())
    }

    @Test
    fun `at the next one, all but the one just made, and nothing when nothing was made`() {
        assertEquals(listOf("old", "recent"), WaitingExpiry.expired(WaitingExpiry.Rule.Next, waiting, "made", 5 * day, plain))
        assertTrue(WaitingExpiry.expired(WaitingExpiry.Rule.Next, waiting, null, 5 * day, plain).isEmpty())
    }

    @Test
    fun `after a delay, those it ran out for, never the one just made`() {
        val rule = WaitingExpiry.of(JSONObject().put(WaitingExpiry.KEY, WaitingExpiry.AFTER).put(WaitingExpiry.DELAY, 2 * day))
        assertEquals(WaitingExpiry.Rule.After(2 * day), rule)
        assertEquals(listOf("old"), WaitingExpiry.expired(rule, waiting, "made", 4 * day, plain))
        assertEquals(listOf("old", "recent"), WaitingExpiry.expired(rule, waiting, null, 5 * day, plain))
    }

    @Test
    fun `a questionnaire set to the next one, invited twice, leaves the second alone to fill`() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val coordinator = Coordinator(context)
        suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
            val result = coordinator.processUserAction(action, params)
            assertTrue("$action: ${result.error}", result.isSuccess)
            return result.data.orEmpty()
        }
        val zoneId = ok("zones.create", mapOf("name" to "Body"))["zone_id"] as String
        val toolId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "questionnaire",
            "config" to mapOf("name" to "Feeling", "notify" to false, WaitingExpiry.KEY to WaitingExpiry.NEXT)))["tool_instance_id"] as String

        val first = ok("questionnaire.invite", mapOf("tool_instance_id" to toolId))["id"] as String
        val second = ok("questionnaire.invite", mapOf("tool_instance_id" to toolId))["id"] as String

        fun status(id: String) = JSONObject(runBlocking { AppDatabase.getDatabase(context).toolDataDao().getById(id)!!.state!! }).getString(QuestionnaireToolType.STATUS)
        assertEquals(QuestionnaireToolType.Status.EXPIRED, status(first))
        assertEquals(QuestionnaireToolType.Status.TO_FILL, status(second))

        // A config stored before the setting, saved again without naming it: it never expires
        val tools = AppDatabase.getDatabase(context).toolInstanceDao()
        val stored = tools.getToolInstanceById(toolId)!!
        val former = JSONObject(stored.config_json).apply { remove(WaitingExpiry.KEY) }
        tools.updateToolInstance(stored.copy(config_json = former.toString()))
        ok("tools.update", mapOf("tool_instance_id" to toolId, "config" to app.treelune.core.utils.JsonUtils.toMap(former)))
        assertEquals(WaitingExpiry.Rule.Never, WaitingExpiry.of(JSONObject(tools.getToolInstanceById(toolId)!!.config_json)))
    }
}
