package app.treelune.core.ai.services

import android.content.Context
import app.treelune.core.ai.data.AutomationMessage
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A scheduled automation's catch-up (AutomationSettings): given with the schedule and only with it,
 * a limited one saying its delay (AutomationService.withSettings). The editor's rules hold at the
 * write, whatever the screen that sent them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatchUpValidationTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var zoneId: String

    private val weekly = mapOf("pattern" to mapOf("type" to "WeeklySimple", "days_of_week" to listOf(2), "time" to "18:00"))

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Garden"))
        assertTrue(zone.error, zone.isSuccess)
        zoneId = zone.data!!["zone_id"] as String
    }

    private suspend fun create(schedule: Any? = null, catchUp: Any? = null): app.treelune.core.commands.CommandResult {
        val params = mutableMapOf<String, Any?>(
            "zone_id" to zoneId,
            "name" to "Routine",
            "identity_id" to null,
            "provider_id" to null,
            "is_enabled" to false,
            "message" to listOf(mapOf("type" to AutomationMessage.TEXT, "text" to "hi"))
        )
        if (schedule != null) params["schedule"] = schedule
        if (catchUp != null) params["catch_up"] = catchUp
        return coordinator.processUserAction("automations.create", params)
    }

    @Test
    fun `a schedule asks for its catch-up, a catch-up only with a schedule, a limited one its delay`() = runBlocking {
        assertFalse("a schedule without a catch-up is refused", create(schedule = weekly).isSuccess)
        assertFalse("a catch-up without a schedule is refused", create(catchUp = mapOf("limit" to "unlimited")).isSuccess)
        assertFalse("a limited catch-up without its delay is refused", create(schedule = weekly, catchUp = mapOf("limit" to "limited")).isSuccess)

        assertTrue("a routine without a schedule takes no catch-up", create().isSuccess)
        assertTrue("a limited catch-up with its delay passes", create(schedule = weekly, catchUp = mapOf("limit" to "limited", "window" to 10_800_000L)).isSuccess)
    }
}
