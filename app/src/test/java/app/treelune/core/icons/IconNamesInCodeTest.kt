package app.treelune.core.icons

import app.treelune.core.ai.data.EnrichmentType
import app.treelune.core.notifications.NotificationService
import app.treelune.core.tools.ToolTypeScanner
import app.treelune.core.ui.ButtonAction
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every icon name the code asks for is in the vocabulary. A name that is not shows as two
 * letters, breaks an action button or refuses a notification, on the device only: this finds it before.
 */
class IconNamesInCodeTest {

    private val index = IconIndex.parse(File("src/main/assets/icons/index.json").readText())

    /** The names the code asks for, with where each comes from. */
    private fun namesInCode(): List<Pair<String, String>> {
        val names = mutableListOf<Pair<String, String>>()
        ButtonAction.entries.forEach { names.add("ButtonAction.$it" to it.iconName) }
        EnrichmentType.entries.forEach { names.add("EnrichmentType.$it" to it.iconName) }
        names.add("NotificationService.APP_ICON" to NotificationService.APP_ICON)
        // Every registered tool type, so a new one is checked without being listed here
        ToolTypeScanner.scanForToolTypes().values.forEach { toolType ->
            val owner = toolType::class.simpleName
            names.add("$owner default" to toolType.getDefaultIconName())
            toolType.getSuggestedIcons().forEach { names.add("$owner suggested" to it) }
        }
        // The names written in the screens and the themes: by the parameter's name anywhere
        // (iconName = "..."), and in the argument that names the icon of each call drawing one,
        // however it is written ("plus", if (open) "chevron-down" else "chevron-up")
        val named = Regex("""iconName = "([^"]+)"""")
        val literal = Regex(""""([^"\\]*)"""")
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            named.findAll(text).forEach { names.add(file.name to it.groupValues[1]) }
            ICON_CALLS.forEach { (call, argument) ->
                Regex(Regex.escape(call) + """\(""").findAll(text).forEach { match ->
                    val icon = argumentAt(text, match.range.last + 1, argument).removePrefix("iconName = ")
                    literal.findAll(icon).forEach { names.add("${file.name} $call" to it.groupValues[1]) }
                }
            }
        }
        return names
    }

    /**
     * The text of the argument at [index] (from 0) of the call whose arguments start at [start]:
     * up to its comma, or the call's closing parenthesis, at the call's own depth.
     */
    private fun argumentAt(text: String, start: Int, index: Int): String {
        var depth = 0
        var inString = false
        var argument = 0
        var from = start
        var i = start
        while (i < text.length) {
            val c = text[i]
            when {
                inString && c == '\\' -> i++
                c == '"' -> inString = !inString
                inString -> Unit
                c == '(' || c == '[' || c == '{' -> depth++
                (c == ')' || c == ']' || c == '}') && depth > 0 -> depth--
                c == ')' || (c == ',' && depth == 0) -> {
                    if (argument == index) return text.substring(from, i).trim()
                    if (c == ')') return ""
                    argument++
                    from = i + 1
                }
            }
            i++
        }
        return ""
    }

    private companion object {
        /** Each call drawing an icon by its name, with the argument that names it. */
        val ICON_CALLS = listOf("UI.Icon" to 0, "UI.ItemIcon" to 0, "NamedIcon" to 0, "Icons.drawable" to 1)
    }

    /** A current name, not a former one: the code writes the names it means today. */
    @Test
    fun everyIconNameInTheCodeIsACurrentLucideName() {
        val names = namesInCode()
        val wrong = names.filter { (_, name) -> index.resolve(name) != name }
        assertTrue(wrong.joinToString("\n") { (where, name) -> "$where: $name" }, wrong.isEmpty())
        assertTrue("the source scan found no name", names.any { it.first.endsWith(".kt") })
        assertTrue("the scan of the calls found no name", names.any { it.first.endsWith(".kt NamedIcon") })
    }
}
