package app.treelune.tools.goal

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolDataEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A goal's read criterion (GoalService.judged): a reading without a period of its own reads the
 * attempt's, so an entry outside the attempt's day counts for nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalReadCriterionTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var runsId: String
    private lateinit var goalId: String
    private val database get() = AppDatabase.getDatabase(context)

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Course"))
        assertTrue(zone.error, zone.isSuccess)
        val zoneId = zone.data!!["zone_id"] as String

        val runs = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zoneId, "tooltype" to "tracking",
            "config" to mapOf("name" to "Runs", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to emptyList<String>())
        ))
        assertTrue(runs.error, runs.isSuccess)
        runsId = runs.data!!["tool_instance_id"] as String

        val goal = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zoneId, "tooltype" to "goal",
            "config" to mapOf(
                "name" to "Course",
                "criteria" to listOf(mapOf(
                    "key" to "c_sum", "name" to "Sum",
                    "condition" to mapOf(
                        "left" to mapOf("reading" to mapOf(
                            "selection" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to runsId)),
                            "reduction" to "SUM", "field" to "data.value"
                        )),
                        "op" to "<=", "right" to mapOf("constant" to 15)
                    )
                ))
            )
        ))
        assertTrue(goal.error, goal.isSuccess)
        goalId = goal.data!!["tool_instance_id"] as String
    }

    @Test
    fun `a read criterion is judged over the attempt's day alone`() = runBlocking {
        val day = 86_400_000L
        val start = System.currentTimeMillis() - day
        // Inside the attempt's day: 10. Outside it: 100.
        coordinator.processUserAction("tool_data.create", mapOf(
            "tool_instance_id" to runsId, "name" to "In", "data" to mapOf("value" to 10), "timestamp" to start + day / 2
        ))
        coordinator.processUserAction("tool_data.create", mapOf(
            "tool_instance_id" to runsId, "name" to "Out", "data" to mapOf("value" to 100), "timestamp" to start - day
        ))

        val id = java.util.UUID.randomUUID().toString()
        database.toolDataDao().insert(ToolDataEntity(
            id = id, toolInstanceId = goalId, tooltype = "goal", timestamp = start,
            name = "Course", data = "{}", createdAt = 0, updatedAt = 0,
            state = """{"status": "active", "period_end": ${start + day}}"""
        ))

        val evaluated = coordinator.processUserAction("goal.evaluate", mapOf("id" to id))
        assertTrue(evaluated.error, evaluated.isSuccess)
        @Suppress("UNCHECKED_CAST")
        val criteria = evaluated.data!!["criteria"] as List<Map<*, *>>
        assertEquals(10.0, (criteria.single()["value"] as Number).toDouble(), 0.0)
    }
}
