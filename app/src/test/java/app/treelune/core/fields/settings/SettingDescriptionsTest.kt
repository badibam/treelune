package app.treelune.core.fields.settings

import android.content.Context
import app.treelune.core.ai.data.AutomationSettings
import app.treelune.core.ai.data.CommunicationModules
import app.treelune.core.config.AppSettings
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

/**
 * Every value a settings declaration puts in a schema the AI reads carries what it is, at its own
 * level: a setting, a group, a list and one of its elements, each
 * option of a variant. A section, the screen's alone, has none; a brick (a condition, a term, a
 * selection, a period, instants) writes its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingDescriptionsTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val text: (String) -> String = Strings.`for`(context = context)::shared

    /** The declarations whose schema the AI reads, by the name a missing description is reported under. */
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

    /** The paths under [path] of the values among [nodes] without a description. */
    private fun missing(nodes: List<SettingNode>, path: String): List<String> = nodes.flatMap { node ->
        when (node) {
            is SettingNode.Field -> listOfNotNull("field $path.${node.definition.name}".takeIf { node.definition.description.isNullOrBlank() })
            is SettingNode.Group -> listOfNotNull("group $path.${node.name}".takeIf { node.description.isNullOrBlank() }) + missing(node.nodes, "$path.${node.name}")
            // Its element is a field definition, which the prompt describes once (see Fields)
            is SettingNode.ListOf -> listOfNotNull("list $path.${node.name}".takeIf { node.description.isNullOrBlank() }) + if (node.fieldDefinitions) emptyList() else when (val item = node.item) {
                is SettingNode.Item.Of -> listOfNotNull("element $path.${node.name}[]".takeIf { item.description.isNullOrBlank() }) + missing(item.nodes, "$path.${node.name}[]")
                is SettingNode.Item.Value -> listOfNotNull("value $path.${node.name}[]".takeIf { item.definition.description.isNullOrBlank() })
            }
            is SettingNode.Variant -> missing(listOf(node.selector), path) + node.cases.flatMap { (option, caseNodes) ->
                listOfNotNull("option $path.${node.selector.definition.name}=$option".takeIf { node.descriptions[option].isNullOrBlank() }) +
                    missing(caseNodes, "$path(${node.selector.definition.name}=$option)")
            }
            is SettingNode.Section -> missing(node.nodes, path)
            is SettingNode.Condition, is SettingNode.Term, is SettingNode.Selection, is SettingNode.Sort, is SettingNode.Period, is SettingNode.Instants -> emptyList()
        }
    }.distinct()

    /** The fields a tool type declares in its entries, its default config's, without a description, by where they are. */
    private fun entryGaps(): List<String> = ToolTypeManager.getAllToolTypes().flatMap { (id, type) ->
        if (!type.keepsEntries()) return@flatMap emptyList<String>()
        val declared = type.getEntryFields(ToolConfigSettings.defaults(type, context), context)
        val fields = declared.data.map { "data" to it.definition } + declared.state.map { "state" to it.definition }
        fields.filter { (_, field) -> field.description.isNullOrBlank() }.map { (container, field) -> "${id}_data.$container.${field.name}" }
    }

    @Test
    fun `every field of an entry says what it is`() {
        val gaps = entryGaps()
        assertTrue("${gaps.size} without a description:\n" + gaps.joinToString("\n"), gaps.isEmpty())
    }

    @Test
    fun `every value of a declaration says what it is`() {
        val gaps = declarations().flatMap { (name, nodes) -> missing(nodes, name) }.distinct()
        assertTrue("${gaps.size} without a description:\n" + gaps.joinToString("\n"), gaps.isEmpty())
    }
}
