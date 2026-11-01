package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
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
 * What depends on a thing about to be deleted (docs/design/dependents.md): a tool, a field of it,
 * a variable named by its id or in a formula by its name, an entry other entries designate; the
 * AI refused while something depends, naming it, then going ahead with confirm_dependents; a
 * person's deletion going ahead, its confirmation having said it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DependentsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val coordinator get() = Coordinator(context)

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    private suspend fun names(target: Dependents.Target) = Dependents.of(context, target).map { it.kind to it.name }

    @Test
    fun `what reads a tool, its field, a variable and an entry is named, and holds back the AI alone`() = runBlocking<Unit> {
        val zone = ok("zones.create", mapOf("name" to "Kitchen"))["zone_id"] as String
        val meals = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to "tracking", "config" to mapOf(
            "name" to "Meals", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to emptyList<String>(),
            "extra_fields" to listOf(
                mapOf("display_name" to "kcal", "type" to "NUMERIC", "always_visible" to false, "config" to mapOf("decimals" to 0)),
                mapOf("display_name" to "note", "type" to "TEXT", "always_visible" to false, "config" to mapOf("length" to "SHORT")))
        )))["tool_instance_id"] as String
        val kcalPath = "extra." + (JSONObject(AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(meals)!!.config_json)
            .getJSONArray("extra_fields").getJSONObject(0).getString("name"))
        val total = ok("variables.create", mapOf("zone_id" to zone, "name" to "kcal_today", "label" to "Kcal today", "definition" to mapOf(
            "kind" to "FORMULA", "formula" to "eaten",
            "terms" to mapOf("eaten" to mapOf("reading" to mapOf(
                "selection" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to meals)),
                "field" to kcalPath, "reduction" to "SUM"))),
            "field" to mapOf("type" to "NUMERIC", "config" to mapOf("decimals" to 0))
        )))["variable_id"] as String
        ok("variables.create", mapOf("zone_id" to zone, "name" to "kcal_double", "label" to "Kcal doubled", "definition" to mapOf(
            "kind" to "FORMULA", "formula" to "kcal_today × 2", "terms" to emptyMap<String, Any>(),
            "field" to mapOf("type" to "NUMERIC", "config" to mapOf("decimals" to 0))
        )))

        // The tool and its field are read by the total; the total, by name, by the doubled one
        assertEquals(listOf("variable" to "Kcal today"), names(Dependents.Target.Tool(meals)))
        assertEquals(listOf("variable" to "Kcal today"), names(Dependents.Target.Field(meals, kcalPath)))
        assertEquals(emptyList<Any>(), names(Dependents.Target.Field(meals, "extra.note")))
        assertEquals(listOf("variable" to "Kcal doubled"), names(Dependents.Target.Variable(total)))

        // An entry that two others designate
        val apple = ok("tool_data.create", mapOf("tool_instance_id" to meals, "name" to "Apple", "data" to mapOf("value" to 1)))["id"] as String
        repeat(2) { ok("tool_data.create", mapOf("tool_instance_id" to meals, "name" to "Lunch", "data" to mapOf("value" to 1), "extra" to mapOf("note" to apple))) }
        val designating = Dependents.of(context, Dependents.Target.Entry(apple)).single()
        assertEquals("entries" to 2, designating.kind to designating.count)

        // The AI is held back, the refusal naming what reads; then it goes ahead when told to
        val refused = coordinator.processAICommand("variables.delete", mapOf("variable_id" to total))
        assertFalse(refused.isSuccess)
        assertTrue(refused.error, refused.error!!.contains("Kcal doubled"))
        val confirmed = coordinator.processAICommand("variables.delete", mapOf("variable_id" to total, Dependents.CONFIRM to true))
        assertTrue(confirmed.error, confirmed.isSuccess)

        // A person's deletion goes ahead: its confirmation said it
        ok("tool_data.delete", mapOf("id" to apple))
    }
}
