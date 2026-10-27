package app.treelune.core.triggers

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A trigger the form is still filling reads as one that does not read, an IllegalArgumentException
 * the config screens catch: a JSONException closed the app on the phone as soon as « an entry is
 * written » was chosen in a Task's « Also starts when… ».
 */
@RunWith(RobolectricTestRunner::class)
class TriggersReadTest {

    private fun read(trigger: JSONObject) = Triggers.read(JSONArray().put(trigger))

    @Test
    fun `an entry trigger without its entries does not read`() {
        assertThrows(IllegalArgumentException::class.java) { read(JSONObject().put(Triggers.KIND, Triggers.ENTRY_WRITTEN)) }
    }

    @Test
    fun `an entry trigger without its tool does not read`() {
        val trigger = JSONObject().put(Triggers.KIND, Triggers.ENTRY_WRITTEN).put(Triggers.ENTRIES, JSONObject())
        assertThrows(IllegalArgumentException::class.java) { read(trigger) }
    }

    @Test
    fun `an entry trigger without its writes does not read`() {
        val trigger = JSONObject().put(Triggers.KIND, Triggers.ENTRY_WRITTEN)
            .put(Triggers.ENTRIES, JSONObject().put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", "t")))
        assertThrows(IllegalArgumentException::class.java) { read(trigger) }
    }

    @Test
    fun `an unknown write does not read`() {
        assertThrows(IllegalArgumentException::class.java) { Triggers.Write.of("renamed") }
    }
}
