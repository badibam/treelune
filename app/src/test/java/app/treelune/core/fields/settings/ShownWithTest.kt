package app.treelune.core.fields.settings

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a setting shown with an option is shown: on the shape of a reference field, whose list of
 * tools serves only the entries among what it can point to.
 */
class ShownWithTest {

    private val withEntry = SettingNode.ShownWith("kinds", "ENTRY")

    @Test
    fun `chosen among the values of a setting that takes several`() {
        assertTrue(withEntry.isChosenIn(JSONObject().put("kinds", JSONArray(listOf("ZONE", "ENTRY")))))
        assertFalse(withEntry.isChosenIn(JSONObject().put("kinds", JSONArray(listOf("ZONE", "TOOL_INSTANCE")))))
    }

    @Test
    fun `chosen as the value of a setting that takes one`() {
        assertTrue(withEntry.isChosenIn(JSONObject().put("kinds", "ENTRY")))
        assertFalse(withEntry.isChosenIn(JSONObject().put("kinds", "ZONE")))
    }

    @Test
    fun `not chosen while the setting is absent or empty`() {
        assertFalse(withEntry.isChosenIn(JSONObject()))
        assertFalse(withEntry.isChosenIn(JSONObject().put("kinds", JSONObject.NULL)))
        assertFalse(withEntry.isChosenIn(JSONObject().put("kinds", JSONArray())))
    }
}
