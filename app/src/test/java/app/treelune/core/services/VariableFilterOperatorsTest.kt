package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A variable's reading filters its entries as any config's filter does: an operator its field
 * does not take is refused at the write, as a trigger's filter is (ConditionCheck) — « = » on a
 * choice, which takes « in ».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VariableFilterOperatorsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun aReadingsFilter_isHeldToTheOperatorsItsFieldTakes() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Days")).data!!["zone_id"] as String
        val moods = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone, "tooltype" to "tracking", "config" to mapOf(
            "name" to "Mood", "type" to "choice", "value" to mapOf("options" to ChoiceSettings.storedOptions(listOf("calm", "tense")))
        )))
        assertTrue(moods.error, moods.isSuccess)
        val tool = moods.data!!["tool_instance_id"] as String

        suspend fun variable(name: String, op: String, right: String) = coordinator.processUserAction("variables.create", mapOf(
            "zone_id" to zone, "name" to name, "label" to name,
            "definition" to JsonUtils.toMap(JSONObject("""{"kind": "FORMULA", "formula": "t", "terms": {"t": {"reading": {"selection": {
                "target": {"kind": "TOOL_INSTANCE", "id": "$tool"},
                "filters": [{"left": {"field": "data.value"}, "op": "$op", "right": {"constant": $right}}]}, "reduction": "COUNT"}}}}"""))
        ))

        assertFalse(variable("calm_equal", "=", "\"calm\"").isSuccess)
        val taken = variable("calm_days", "in", "[\"calm\"]")
        assertTrue(taken.error, taken.isSuccess)
    }
}
