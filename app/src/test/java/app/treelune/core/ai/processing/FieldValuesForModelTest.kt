package app.treelune.core.ai.processing

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.SettingNode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * The values of the user's fields a config holds are written by the AI as an entry's are: a
 * duration and an instant in ISO 8601, stored in milliseconds, by the field each is given for,
 * which the config itself defines; read back in ISO 8601. A key of no field is left as it is.
 */
class FieldValuesForModelTest {

    private val zone = ZoneId.of("Europe/Paris")

    private val nodes = listOf(
        SettingNode.ListOf("items", "Shortcuts", SettingNode.Item.Of(listOf(
            SettingNode.Field(FieldDefinition("name", "Name", null, FieldType.TEXT, false, null)),
            SettingNode.FieldValues("extra", "Its fields", "extra_fields")
        )), summary = listOf("name"))
    )

    /** A config whose fields are a duration and an instant, with one shortcut holding [extra]. */
    private fun config(extra: JSONObject) = JSONObject()
        .put("extra_fields", JSONArray()
            .put(JSONObject().put("name", "cold").put("display_name", "Cold").put("type", "DURATION"))
            .put(JSONObject().put("name", "since").put("display_name", "Since").put("type", "DATETIME")))
        .put("items", JSONArray().put(JSONObject().put("name", "Cold shower").put("extra", extra)))

    private fun extraOf(config: JSONObject) = config.getJSONArray("items").getJSONObject(0).getJSONObject("extra")

    @Test
    fun aDurationAndAnInstantFromTheAi_areStoredInMilliseconds_andReadBackInIso() {
        val written = JSONObject().put("cold", "PT3M").put("since", "2025-09-15T08:00:00+02:00").put("nothing", "PT1H")
        val stored = extraOf(FieldValuesForModel.toStored(nodes, config(written), zone))
        assertEquals(180_000L, (stored.get("cold") as Number).toLong())
        assertEquals(1_757_916_000_000L, (stored.get("since") as Number).toLong())
        // Of no field: left as it is, for the service to name it
        assertEquals("PT1H", stored.get("nothing"))

        val read = extraOf(FieldValuesForModel.toModel(nodes, config(JSONObject().put("cold", 180_000L)), zone))
        assertEquals("PT3M", read.get("cold"))
    }
}
