package app.treelune.tools.sequence

import android.content.Context
import app.treelune.core.ai.prompts.ModelValues
import app.treelune.core.tools.ToolConfigSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * A Session's durations written by the AI in ISO 8601 reach its config in milliseconds: its
 * schema branches on its variant and again on its expiry, and the duration sits behind both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SequenceConfigFromModelTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val schema get() = JSONObject(ToolConfigSettings.schema(SequenceToolType, "sequence_config", context).content)

    @Test
    fun `a timer's length written in ISO 8601 is stored in milliseconds`() {
        val written = JSONObject().put("name", "Méditation").put("variant", "timer").put("timer_duration", "PT15M").put("expiry", "never")
        val stored = ModelValues.fromModel(written, schema, ZoneId.of("Europe/Paris")) as JSONObject
        assertEquals(900_000L, stored.get("timer_duration"))
    }

    @Test
    fun `a step's duration written in ISO 8601 is stored in milliseconds`() {
        val step = JSONObject().put("kind", "step").put("name", "Inspire").put("length", "fixed").put("duration", "PT30S").put("auto_next", true)
        val written = JSONObject().put("name", "Souffle").put("variant", "steps").put("steps", org.json.JSONArray().put(step)).put("expiry", "never")
        val stored = ModelValues.fromModel(written, schema, ZoneId.of("Europe/Paris")) as JSONObject
        assertEquals(30_000L, stored.getJSONArray("steps").getJSONObject(0).get("duration"))
    }
}
