package app.treelune.tools.chart

import app.treelune.core.ai.prompts.SchemaModelView
import app.treelune.core.ai.prompts.SchemaNotation
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.tools.ToolTypeManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * What the AI reads of a chart's config: a layer, the same in every composition, is written once
 * at the end, each composition naming it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChartSchemaNotationTest {

    @Test
    fun aLayerIsWrittenOnce() {
        val context = RuntimeEnvironment.getApplication()
        val content = ToolConfigSettings.schema(ToolTypeManager.getToolType("chart")!!, "chart_config", context).content
        val notation = SchemaNotation.render(SchemaModelView.forModel(JSONObject(content), ZoneId.of("Europe/Paris")))

        val compositions = ChartKeys.COMPOSITIONS.size
        assertEquals(notation, compositions, Regex("layer\\*: list of Layer \\(see Layer\\)").findAll(notation).count())
        assertEquals(notation, 1, Regex("(?m)^Layer: ").findAll(notation).count())
        assertEquals(notation, 1, Regex("(?m)^ *encoding: ").findAll(notation).count())
    }
}
