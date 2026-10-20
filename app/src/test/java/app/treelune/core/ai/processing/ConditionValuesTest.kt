package app.treelune.core.ai.processing

import app.treelune.core.conditions.ConfigConditions
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.SettingNode
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * The written values of the conditions in a config the AI writes are stored by the type they
 * face: a duration and a date-time from ISO 8601 into milliseconds, a relative date kept, a bare
 * number refused, anything else as it is.
 */
class ConditionValuesTest {

    private fun field(name: String, type: FieldType) = FieldDefinition(name, name, null, type, false, null)

    private val sources = object : ConfigConditions.Sources {
        override suspend fun toolFields(toolInstanceId: String) = when (toolInstanceId) {
            "sleep" -> mapOf("data.duration" to field("duration", FieldType.DURATION), "timestamp" to field("timestamp", FieldType.DATETIME))
            else -> mapOf("data.kcal" to field("kcal", FieldType.NUMERIC))
        }
        override suspend fun variableField(id: String): FieldDefinition? = null
        override suspend fun rowFields(root: JSONObject, path: List<Any>): Map<String, FieldDefinition>? = null
    }

    private val nodes = listOf(
        SettingNode.ListOf("criteria", "Criteria", SettingNode.Item.Of(listOf(
            SettingNode.Field(field("name", FieldType.TEXT)),
            SettingNode.Group("field", "Field", emptyList()),
            SettingNode.Condition("condition", "Condition", reference = "", emptyPeriod = null, enteredField = "field")
        )), summary = listOf("name")),
        SettingNode.Selection("selection", "Selection", reference = "")
    )

    private fun stored(config: JSONObject) = runBlocking { ConditionValues(sources, ZoneId.of("Europe/Paris")) { it }.toStored(nodes, config) }

    private val sleepAverage = JSONObject("""{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "sleep"}}, "field": "data.duration", "reduction": "AVERAGE"}}""")
    private fun criterion(left: JSONObject, right: Any, entered: JSONObject? = null) = JSONObject().put("criteria", JSONArray().put(JSONObject()
        .put("name", "Sleep")
        .put("condition", JSONObject().put("left", left).put("op", ">=").put("right", JSONObject().put("constant", right)))
        .apply { entered?.let { put("field", it) } }))
    private fun rightOf(config: JSONObject) = config.getJSONArray("criteria").getJSONObject(0).getJSONObject("condition").getJSONObject("right").get("constant")
    private fun filter(tool: String, path: String, op: String, right: Any) = JSONObject().put("selection", JSONObject()
        .put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", tool))
        .put("filters", JSONArray().put(JSONObject().put("left", JSONObject().put("field", path)).put("op", op).put("right", JSONObject().put("constant", right)))))
    private fun filterValue(config: JSONObject) = config.getJSONObject("selection").getJSONArray("filters").getJSONObject(0).getJSONObject("right").get("constant")

    @Test
    fun aDurationFromTheAi_isStoredInMilliseconds_byTheTypeItFaces() {
        assertEquals(25_200_000L, (rightOf(stored(criterion(sleepAverage, "PT7H"))) as Number).toLong())
        // The value entered in each attempt stands on the left
        assertEquals(28_800_000L, (rightOf(stored(criterion(JSONObject().put("field", "data.c_sleep"), "PT8H", JSONObject().put("type", "DURATION")))) as Number).toLong())
    }

    @Test
    fun aBareNumberFacingADurationOrAnInstant_isRefused() {
        val refused = runCatching { stored(criterion(sleepAverage, 25_200_000)) }.exceptionOrNull()
        assertTrue(refused is IllegalArgumentException && refused.message == "ai_error_condition_bare_number")
        assertTrue(runCatching { stored(filter("sleep", "timestamp", ">=", 1_758_000_000_000L)) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun aDateTime_isStoredInMilliseconds_aRelativeDateKept() {
        assertEquals(1_757_916_000_000L, (filterValue(stored(filter("sleep", "timestamp", ">=", "2025-09-15T08:00:00+02:00"))) as Number).toLong())
        val relative = JSONObject("""{"relative": {"unit": "DAY", "offset": -7, "edge": "START"}}""")
        assertEquals(relative.toString(), filterValue(stored(filter("sleep", "timestamp", ">=", relative))).toString())
    }

    @Test
    fun anythingElse_isWrittenAsItIsStored() {
        assertEquals(2100, filterValue(stored(filter("meals", "data.kcal", ">", 2100))))
    }

    private fun forModel(config: JSONObject) = runBlocking { ConditionValues(sources, ZoneId.of("Europe/Paris")) { it }.toModel(nodes, config) }

    @Test
    fun aStoredValue_isReadByTheModelAsItWritesIt_theWayBack() {
        // What the AI reads of a config it wrote with PT7H is PT7H, not 25200000 it would take for a bare number
        assertEquals("PT7H", rightOf(forModel(stored(criterion(sleepAverage, "PT7H")))))
        assertEquals("2025-09-15T08:00:00+02:00", filterValue(forModel(filter("sleep", "timestamp", ">=", 1_757_916_000_000L))))
        assertEquals(2100, filterValue(forModel(filter("meals", "data.kcal", ">", 2100))))
    }
}
