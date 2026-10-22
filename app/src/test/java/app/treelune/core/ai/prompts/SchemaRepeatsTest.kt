package app.treelune.core.ai.prompts

import android.content.Context
import app.treelune.core.ai.data.AutomationSettings
import app.treelune.core.ai.data.CommunicationModules
import app.treelune.core.config.AppSettings
import app.treelune.core.fields.settings.FieldTypeSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.schemas.ZoneSettings
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.tools.ToolTypeManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * What the AI reads of a schema writes no block of settings twice: a block that stands in
 * several places is declared with a name (SettingNode.Group.definition, Item.Of.definition) and
 * written once at the end (SchemaNotation). Measured on the notation itself, not on the JSON
 * schema, where a variant repeats in every option what the notation writes once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchemaRepeatsTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val text: (String) -> String = Strings.`for`(context = context)::shared

    /** The declarations whose schema the AI reads, by name. */
    private fun declarations(): Map<String, List<SettingNode>> = buildMap {
        ToolTypeManager.getAllToolTypes().forEach { (id, type) ->
            put("${id}_config", ToolConfigSettings.nodes(type, context))
            type.getOperations(context).forEach { put("$id.${it.name}", it.params) }
        }
        put("zone_config", ZoneSettings.nodes(context))
        AppSettings.CATEGORIES.forEach { put("app_config_$it", AppSettings.nodes(it, context)) }
        put("automation_config", AutomationSettings.nodes(context))
        put("communication_module", CommunicationModules.declarationNodes(text))
        put("field_definitions", FieldTypeSettings.definitionNodes(text))
    }

    /**
     * The blocks of [lines] written more than once: what a line holds indented under it, with
     * its indentation removed, by its text, when it is at least [MIN_LINES] long. A block lying
     * inside a block repeated as a whole is not counted again.
     */
    private fun repeats(lines: List<String>): Map<String, Int> {
        fun depth(line: String) = line.length - line.trimStart().length
        // Each block: its text and the lines it spans
        val blocks = lines.indices.mapNotNull { i ->
            val inner = lines.drop(i + 1).takeWhile { it.isNotBlank() && depth(it) > depth(lines[i]) }
            if (inner.size < MIN_LINES) null else inner.joinToString("\n") { it.drop(depth(lines[i])) } to (i + 1..i + inner.size)
        }
        val repeated = blocks.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
        val spans = repeated.values.flatten()
        return repeated.filterValues { own -> own.any { span -> spans.none { other -> other != span && span.first >= other.first && span.last <= other.last } } }
            .mapValues { it.value.size }
    }

    @Test
    fun `no block of settings is written twice`() {
        val found = declarations().flatMap { (name, nodes) ->
            val notation = SchemaNotation.render(SchemaModelView.forModel(SettingsSchemaGenerator.generate(nodes, text), ZoneId.of("Europe/Paris")))
            repeats(notation.lines()).map { (block, count) -> "$name: ${count}x, ${block.lines().size} lines\n$block" }
        }
        assertTrue("${found.size} blocks written more than once:\n\n" + found.joinToString("\n\n"), found.isEmpty())
    }

    private companion object {
        /** The fewest lines a block repeated is worth a name for: a shorter one saves little more than its reference costs. */
        const val MIN_LINES = 6
    }
}
