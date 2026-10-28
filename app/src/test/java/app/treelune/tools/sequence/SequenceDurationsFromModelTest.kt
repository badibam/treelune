package app.treelune.tools.sequence

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
 * A Sequence's durations the AI writes in ISO 8601 are stored in milliseconds: a timer's length
 * and a step's, each in a variant inside a variant of the config's generated schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SequenceDurationsFromModelTest {

    private val schema by lazy {
        JSONObject(ToolConfigSettings.schema(SequenceToolType, "sequence_config", RuntimeEnvironment.getApplication()).content)
    }

    private fun stored(config: String) = ModelValues.fromModel(JSONObject(config), schema, ZoneId.of("Europe/Paris")) as JSONObject

    @Test
    fun aTimersLengthAndAStepsLength_areStoredInMilliseconds() {
        assertEquals(900_000L, (stored("""{"name": "Rest", "variant": "timer", "timer_duration": "PT15M"}""").get("timer_duration") as Number).toLong())
        val steps = stored("""{"name": "Stretch", "variant": "steps", "steps": [{"kind": "step", "name": "Neck", "length": "fixed", "duration": "PT1M", "auto_next": true}]}""")
        assertEquals(60_000L, (steps.getJSONArray("steps").getJSONObject(0).get("duration") as Number).toLong())
    }
}
