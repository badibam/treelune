package app.treelune.core.fields

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermReader
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A filter compared with a variable or a reading (« kcal > objectif_calorique »): the term read
 * once, then the filter in SQL; taken in a variable's reading as in a query; a variable whose
 * reading filters by itself refused when written, as it would read itself without end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilterTermsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val coordinator get() = Coordinator(context)

    private class Meals(val zone: String, val tool: String, val goal: String)

    /** A name of the app's not taken yet: a variable's is unique there, and the base outlives a test. */
    private fun unique(name: String) = "${name}_${java.util.UUID.randomUUID().toString().take(8).replace("-", "")}"

    /** Meals of 1800, 2200 and 2600 kcal, and a goal of 2100. */
    private suspend fun meals(): Meals {
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Food")).data!!["zone_id"] as String
        val tool = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone, "tooltype" to "tracking", "config" to mapOf(
            "name" to "Kcal", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to emptyList<String>()
        ))).data!!["tool_instance_id"] as String
        for (kcal in listOf(1800, 2200, 2600)) {
            val written = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to tool, "name" to "Day", "data" to mapOf("value" to kcal)))
            assertTrue(written.error, written.isSuccess)
        }
        val goal = coordinator.processUserAction("variables.create", mapOf("zone_id" to zone, "name" to unique("kcal_goal"), "label" to "Kcal goal",
            "definition" to mapOf("kind" to "CONSTANT", "value" to 2100, "field" to mapOf("type" to "NUMERIC"))))
        assertTrue(goal.error, goal.isSuccess)
        return Meals(zone, tool, goal.data!!["variable_id"] as String)
    }

    private fun overGoal(meals: Meals) = listOf(mapOf("left" to mapOf("field" to "data.value"), "op" to ">", "right" to mapOf("variable" to meals.goal)))

    @Test
    fun `a query keeps the entries over the variable`() = runBlocking {
        val meals = meals()
        val read = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to meals.tool, "filters" to overGoal(meals)))
        assertTrue(read.error, read.isSuccess)
        assertEquals(2, (read.data!!["entries"] as List<*>).size)
    }

    @Test
    fun `a variable counts the days over another, its filter taken when it is written`() = runBlocking {
        val meals = meals()
        val over = coordinator.processUserAction("variables.create", mapOf("zone_id" to meals.zone, "name" to unique("days_over"), "label" to "Days over",
            "definition" to JsonUtils.toMap(JSONObject().put("kind", "FORMULA").put("formula", "t").put("terms", JSONObject().put("t", JSONObject().put("reading",
                JSONObject().put("selection", JSONObject().put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", meals.tool))
                    .put("filters", JsonUtils.toJSONArray(overGoal(meals)))).put("reduction", "COUNT")))))))
        assertTrue(over.error, over.isSuccess)
        val read = TermReader(context).read(Term.Variable(over.data!!["variable_id"] as String), System.currentTimeMillis(), null)
        assertEquals(2, ((read as TermReader.Read.Value).value as Number).toInt())
    }

    @Test
    fun `a filter compared with a text is refused when written`() = runBlocking {
        val meals = meals()
        val refused = coordinator.processUserAction("variables.create", mapOf("zone_id" to meals.zone, "name" to unique("named"), "label" to "Named",
            "definition" to JsonUtils.toMap(JSONObject().put("kind", "FORMULA").put("formula", "t").put("terms", JSONObject().put("t", JSONObject().put("reading",
                JSONObject().put("selection", JSONObject().put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", meals.tool))
                    .put("filters", JsonUtils.toJSONArray(listOf(mapOf("left" to mapOf("field" to "name"), "op" to "=", "right" to mapOf("variable" to meals.goal))))))
                    .put("reduction", "COUNT")))))))
        assertFalse(refused.isSuccess)
    }

    @Test
    fun `a variable whose reading filters by itself is refused when written`() = runBlocking {
        val meals = meals()
        val created = coordinator.processUserAction("variables.create", mapOf("zone_id" to meals.zone, "name" to unique("loop"), "label" to "Loop",
            "definition" to mapOf("kind" to "CONSTANT", "value" to 1, "field" to mapOf("type" to "NUMERIC"))))
        assertTrue(created.error, created.isSuccess)
        val id = created.data!!["variable_id"] as String
        val itself = listOf(mapOf("left" to mapOf("field" to "data.value"), "op" to ">", "right" to mapOf("variable" to id)))
        val changed = coordinator.processUserAction("variables.update", mapOf("variable_id" to id,
            "definition" to JsonUtils.toMap(JSONObject().put("kind", "FORMULA").put("formula", "t").put("terms", JSONObject().put("t", JSONObject().put("reading",
                JSONObject().put("selection", JSONObject().put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", meals.tool))
                    .put("filters", JsonUtils.toJSONArray(itself))).put("reduction", "COUNT")))))))
        // Refused when written: read, it would read itself without end
        assertFalse(changed.isSuccess)
    }
}
