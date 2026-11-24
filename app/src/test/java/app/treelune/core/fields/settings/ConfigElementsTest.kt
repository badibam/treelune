package app.treelune.core.fields.settings

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One element of a config's list written alone by its id, the rest of the config left as it is. */
class ConfigElementsTest {

    private fun field(name: String) = SettingNode.Field(FieldDefinition(name, name, null, FieldType.TEXT, false, null))

    /** A list keyed by id inside a variant's case, as a session's programs are, and one without ids. */
    private val nodes = listOf(
        SettingNode.Section("s", listOf(
            SettingNode.Variant(selector = field("variant"), cases = mapOf(
                "programs" to listOf(SettingNode.ListOf("programs", "Programs", SettingNode.Item.Of(listOf(field("id"), field("name"))), summary = listOf("name")))
            )),
            SettingNode.ListOf("tags", "Tags", SettingNode.Item.Of(listOf(field("name"))), summary = listOf("name"))
        ))
    )

    private val config = JSONObject("""{"variant": "programs", "other": 1, "programs": [{"id": "a", "name": "A"}, {"id": "b", "name": "B"}]}""")

    /** The key of a refusal, with its values, in place of its text. */
    private val shared: (String) -> String = { "$it %1\$s %2\$s" }

    private fun write(element: String): ConfigElements.Written = ConfigElements.write(nodes, config, JSONObject(element), shared)

    private fun written(element: String): JSONObject = (write(element) as ConfigElements.Written.Config).config

    private fun names(config: JSONObject) = ElementKeys.objects(config.getJSONArray("programs")).map { it.optString("id") + "=" + it.optString("name") }

    @Test
    fun `an element is replaced in its place, added at the end, or taken out, the rest of the config kept`() {
        val replaced = written("""{"list": "programs", "id": "a", "value": {"name": "A2"}}""")
        assertEquals(listOf("a=A2", "b=B"), names(replaced))
        assertEquals(1, replaced.getInt("other"))
        assertEquals(listOf("a=A", "b=B", "c=C"), names(written("""{"list": "programs", "id": "c", "value": {"name": "C"}}""")))
        // Without an id, added without one: the tool gives it its own (completeConfig)
        assertEquals(listOf("a=A", "b=B", "=N"), names(written("""{"list": "programs", "value": {"name": "N", "id": "ignored"}}""")))
        assertEquals(listOf("b=B"), names(written("""{"list": "programs", "id": "a", "value": null}""")))
        assertEquals(listOf("b=B"), names(written("""{"list": "programs", "id": "a"}""")))
    }

    @Test
    fun `a list without ids, an element missing or a value that is no object is refused`() {
        assertTrue((write("""{"list": "tags", "value": {"name": "x"}}""") as ConfigElements.Written.Refused).message.startsWith("service_error_config_element_list tags programs"))
        assertTrue((write("""{"list": "programs", "id": "z", "value": null}""") as ConfigElements.Written.Refused).message.startsWith("service_error_config_element_missing z programs"))
        assertTrue((write("""{"list": "programs", "id": "a", "value": 3}""") as ConfigElements.Written.Refused).message.startsWith("service_error_config_element_value"))
    }
}
