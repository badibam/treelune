package app.treelune.core.conditions

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.SettingNode
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A config's conditions are found through its declaration and checked when it is written
 * (docs/BRICKS.md, « Condition »): each side's type known without reading an entry, the
 * operator its type takes, a written value that reads in the type it faces, two read sides of one
 * type, a choice's values among its options. A refusal is the key of its text here.
 */
class ConditionCheckTest {

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) = FieldDefinition(name, name, null, type, false, config)
    private val moments = mapOf("options" to ChoiceSettings.storedOptions(listOf("breakfast", "lunch", "dinner")), "open" to true)

    /** Meals (a moment, kcal, the timestamp), Sleep (a duration), the variable kcal_goal, a row's columns. */
    private val sources = object : ConfigConditions.Sources {
        override suspend fun toolFields(toolInstanceId: String) = when (toolInstanceId) {
            "meals" -> mapOf("data.moment" to field("moment", FieldType.CHOICE, moments), "data.kcal" to field("kcal", FieldType.NUMERIC), "timestamp" to field("timestamp", FieldType.DATETIME))
            "sleep" -> mapOf("data.duration" to field("duration", FieldType.DURATION))
            else -> throw IllegalStateException("no tool $toolInstanceId")
        }
        override suspend fun variableField(id: String) = if (id == "kcal_goal") field("kcal_goal", FieldType.NUMERIC) else null
        override suspend fun rowFields(root: JSONObject, path: List<Any>) = mapOf("kcal" to field("kcal", FieldType.NUMERIC), "planned" to field("planned", FieldType.NUMERIC), "day" to field("day", FieldType.DATE))
    }

    /** A goal's criteria (a condition judged once, its left the value entered when one is declared), a chart's selection, a row's condition. */
    private val nodes = listOf(
        SettingNode.ListOf("criteria", "Criteria", SettingNode.Item.Of(listOf(
            SettingNode.Field(field("name", FieldType.TEXT)),
            SettingNode.Group("field", "Field", emptyList()),
            SettingNode.Condition("condition", "Condition", reference = "", emptyPeriod = null, enteredField = "field")
        )), summary = listOf("name")),
        SettingNode.Selection("selection", "Selection", reference = ""),
        SettingNode.Group("row", "Row", listOf(SettingNode.Condition("test", "Test", reference = "", emptyPeriod = null, onRow = true)))
    )

    private fun refuse(config: JSONObject): String? = runBlocking { ConditionCheck(sources) { it }.refuse(nodes, config) }

    private fun side(json: String) = JSONObject(json)
    private fun condition(left: String, op: String, vararg right: String) = JSONObject().put("left", side(left)).put("op", op).apply {
        when (right.size) {
            0 -> {}
            1 -> put("right", side(right[0]))
            else -> put("right", JSONArray(right.map { side(it) }))
        }
    }
    private fun criterion(condition: JSONObject, entered: JSONObject? = null) = JSONObject().put("name", "c").put("condition", condition).apply { entered?.let { put("field", it) } }
    private fun criteria(vararg criteria: JSONObject) = JSONObject().put("criteria", JSONArray(criteria.toList()))
    private fun filters(vararg filters: JSONObject) = JSONObject().put("selection", JSONObject()
        .put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", "meals"))
        .put("filters", JSONArray(filters.toList())))
    private fun row(condition: JSONObject) = JSONObject().put("row", JSONObject().put("test", condition))

    private val sleepAverage = """{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "sleep"}}, "field": "data.duration", "reduction": "AVERAGE"}}"""

    @Test
    fun anOperatorItsTypeDoesNotTake_isRefused() {
        // "Repas par moment": = on a choice
        assertEquals("condition_error_operator", refuse(filters(condition("""{"field": "data.moment"}""", "=", """{"constant": "lunch"}"""))))
        assertNull(refuse(filters(condition("""{"field": "data.moment"}""", "in", """{"constant": ["lunch"]}"""))))
    }

    @Test
    fun aValueOutsideAChoicesOptions_isRefused_openChoiceIncluded() {
        assertEquals("condition_error_option", refuse(filters(condition("""{"field": "data.moment"}""", "in", """{"constant": ["lunhc"]}"""))))
    }

    @Test
    fun aWrittenValue_readsInTheTypeItFaces() {
        // "Sommeil moyen ≥ 7 h": the AI's form never reaches the service
        assertEquals("condition_error_value", refuse(criteria(criterion(condition(sleepAverage, ">=", """{"constant": "PT7H"}""")))))
        assertNull(refuse(criteria(criterion(condition(sleepAverage, ">=", """{"constant": 25200000}""")))))
        // A relative date is resolved when judged: it only has to read
        assertNull(refuse(filters(condition("""{"field": "timestamp"}""", ">=", """{"constant": {"relative": {"unit": "DAY", "offset": -7, "edge": "START"}}}"""))))
        assertNull(refuse(filters(condition("""{"field": "data.kcal"}""", "between", """{"constant": 100}""", """{"constant": 900}"""))))
    }

    @Test
    fun twoReadSidesOfDifferentTypes_areRefused() {
        assertEquals("condition_error_types", refuse(criteria(criterion(condition("""{"variable": "kcal_goal"}""", "<", sleepAverage)))))
        assertNull(refuse(criteria(criterion(condition("""{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "meals"}}, "field": "data.kcal", "reduction": "SUM"}}""", "<=", """{"variable": "kcal_goal"}""")))))
    }

    @Test
    fun twoWrittenValues_sayNothingOfWhatIsCompared() {
        assertEquals("condition_error_untyped", refuse(criteria(criterion(condition("""{"constant": 1}""", "<", """{"constant": 2}""")))))
    }

    @Test
    fun anEnteredValue_standsOnTheLeft_whateverIsStoredThere() {
        val duration = JSONObject().put("type", "DURATION")
        assertNull(refuse(criteria(criterion(condition("""{"field": "data.c_sleep"}""", ">=", """{"constant": 25200000}"""), duration))))
        assertEquals("condition_error_value", refuse(criteria(criterion(condition("""{"field": "data.c_sleep"}""", ">=", """{"constant": "seven"}"""), duration))))
        // Without one, a field has no entry to be read in
        assertEquals("condition_error_field_side", refuse(criteria(criterion(condition("""{"field": "data.c_sleep"}""", ">=", """{"constant": 1}""")))))
    }

    @Test
    fun theFiltersOfAReadingOnASide_areCheckedToo() {
        val filtered = """{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "meals"}, "filters": [{"left": {"field": "data.moment"}, "op": "=", "right": {"constant": "lunch"}}]}, "reduction": "COUNT"}}"""
        assertEquals("condition_error_operator", refuse(criteria(criterion(condition(filtered, ">", """{"constant": 0}""")))))
    }

    @Test
    fun aRowsCondition_comparesItsColumnsOrWrittenValues() {
        assertNull(refuse(row(condition("""{"field": "kcal"}""", ">", """{"field": "planned"}"""))))
        assertEquals("condition_error_types", refuse(row(condition("""{"field": "kcal"}""", ">", """{"field": "day"}"""))))
        assertEquals("condition_error_field", refuse(row(condition("""{"field": "protein"}""", ">", """{"constant": 1}"""))))
        assertEquals("condition_error_row_written", refuse(row(condition("""{"field": "kcal"}""", ">", """{"variable": "kcal_goal"}"""))))
    }

    @Test
    fun aFilter_takesWrittenValuesOnItsRight() {
        assertEquals("service_error_filter_not_written", refuse(filters(condition("""{"field": "data.kcal"}""", "<", """{"variable": "kcal_goal"}"""))))
        assertEquals("condition_error_field", refuse(filters(condition("""{"field": "data.protein"}""", "<", """{"constant": 1}"""))))
    }
}
