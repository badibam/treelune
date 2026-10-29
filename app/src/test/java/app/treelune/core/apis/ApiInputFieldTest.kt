package app.treelune.core.apis

import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FieldValueSchema
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An API's input is filled through a field, in a Task's step on screen and in the schema the AI
 * reads: a number takes a decimal one (a latitude), and each type gives a schema.
 */
class ApiInputFieldTest {

    private fun input(type: String) = JSONObject().put(ApiSettings.NAME, "latitude").put(ApiSettings.TYPE, type)

    @Test
    fun aNumberInput_takesDecimals_andGivesItsSchema() {
        val field = ApiSettings.inputField(input(ApiSettings.NUMBER))
        assertEquals(FieldType.NUMERIC, field.type)
        assertTrue((field.config?.get("decimals") as Number).toInt() > 0)
        assertEquals("number", FieldValueSchema.of(field).getString("type"))
    }

    @Test
    fun eachInputType_givesItsField_andItsSchema() {
        mapOf(ApiSettings.NUMBER to FieldType.NUMERIC, ApiSettings.BOOLEAN to FieldType.BOOLEAN, ApiSettings.TEXT to FieldType.TEXT)
            .forEach { (type, fieldType) ->
                val field = ApiSettings.inputField(input(type))
                assertEquals(fieldType, field.type)
                FieldValueSchema.of(field)
            }
    }

    @Test
    fun theInputsOfAConfig_areItsFields_inOrder() {
        val config = JSONObject().put(ApiSettings.INPUTS, org.json.JSONArray().put(input(ApiSettings.NUMBER)).put(JSONObject().put(ApiSettings.NAME, "city").put(ApiSettings.TYPE, ApiSettings.TEXT)))
        assertEquals(listOf("latitude", "city"), ApiSettings.inputFields(config).map { it.name })
    }
}
