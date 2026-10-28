package app.treelune.core.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The catalog of the AI's commands against the texts the model reads (ai_prompt_chunks.xml):
 * each command has its text, and the parameters a text documents are exactly the ones the
 * command takes, with the same type and the same required mark. A parameter the prompt promised
 * and the app refused cost 54 plays of 190 in the first bench campaign; it can no longer happen.
 */
class AICommandsTest {

    private val texts: Map<String, String> = Regex("""<string name="(ai_command_\w+)"><!\[CDATA\[(.*?)]]></string>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(File("src/main/java/app/treelune/core/strings/sources/ai_prompt_chunks.xml").readText())
        .associate { it.groupValues[1] to it.groupValues[2] }

    /** The parameters a text lists, one "- `name` (type, optional|**required**)" line each. */
    private fun documented(text: String): List<CommandParam> =
        Regex("""^- `(\w+)` \((\w+), (optional|\*\*required\*\*)\)""", RegexOption.MULTILINE).findAll(text)
            .map { CommandParam(it.groupValues[1], it.groupValues[2], it.groupValues[3] != "optional") }
            .toList()

    @Test
    fun everyCommandHasItsText() {
        for (command in AICommands.ALL) {
            val text = texts[command.docKey]
            assertTrue("${command.type} has no text ${command.docKey}", text != null)
            assertTrue("${command.docKey} is not headed by ${command.type}", text!!.lineSequence().first().startsWith("### ") && command.type in text.lineSequence().first())
        }
    }

    @Test
    fun everyTextBelongsToACommand() {
        assertEquals(texts.keys, AICommands.ALL.map { it.docKey }.toSet())
    }

    @Test
    fun aTextDocumentsExactlyTheParametersItsCommandTakes() {
        for (command in AICommands.ALL) {
            assertEquals("${command.type}, as ${command.docKey} documents it", command.params.sortedBy { it.name }, documented(texts.getValue(command.docKey)).sortedBy { it.name })
        }
    }

    @Test
    fun aCommandRefusesWhatItDoesNotTake() {
        val toolData = AICommands.find("TOOL_DATA")!!
        val message: (String, Array<Any>) -> String = { key, args -> "$key ${args.joinToString(" ")}" }

        assertEquals(emptyList<String>(), toolData.problems(mapOf("id" to "t", "fields" to listOf("id"), "period" to mapOf("start" to null), "limit" to 10.0), message))
        assertEquals(listOf("ai_error_param_missing fields"), toolData.problems(mapOf("id" to "t"), message))
        assertEquals(listOf("ai_error_param_type limit integer"), toolData.problems(mapOf("id" to "t", "fields" to listOf("id"), "limit" to 2.5), message))
        assertTrue(toolData.problems(mapOf("id" to "t", "fields" to listOf("id"), "offset" to 3), message).single().startsWith("service_error_param_unknown offset"))
    }

    @Test
    fun theTypesAreUnique() {
        assertEquals(AICommands.ALL.size, AICommands.ALL.map { it.type }.toSet().size)
    }

    /**
     * The body of the function declared by [declaration] in [path]: from its first brace to the
     * one that closes it.
     */
    private fun body(path: String, declaration: String): String {
        val text = File(path).readText()
        val start = text.indexOf(declaration).also { assertTrue("$declaration not found in $path", it >= 0) }
        val open = text.indexOf('{', start)
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(open, i)
            }
        }
        error("$declaration has no closing brace in $path")
    }

    /** The command types [body] switches on: the quoted names heading a `when` branch, or compared with `command.type ==`. */
    private fun handled(body: String): Set<String> {
        val branches = Regex("""^\s*((?:"[A-Z_]+"\s*,\s*)*"[A-Z_]+")\s*->""", RegexOption.MULTILINE).findAll(body)
            .flatMap { Regex("\"([A-Z_]+)\"").findAll(it.groupValues[1]).map { name -> name.groupValues[1] } }
        val compared = Regex("""command\.type == "([A-Z_]+)"""").findAll(body).map { it.groupValues[1] }
        return (branches + compared).toSet()
    }

    /**
     * The places that switch on a command's type, written as text the compiler does not check,
     * each holding exactly the catalog's commands of its kind. A command missing from one falls
     * into its unknown case (an action never run, a reason « unknown » to approve), a name the
     * catalog no longer has is left behind by a rename.
     */
    @Test
    fun everySwitchOnTheTypeHoldsExactlyTheCatalogsCommands() {
        val root = "src/main/java/app/treelune/core/ai"
        val queries = AICommands.ALL.filter { it.kind == CommandKind.QUERY }.map { it.type }.toSet()
        val actions = AICommands.ALL.filter { it.kind == CommandKind.ACTION }.map { it.type }.toSet()
        val switches = listOf(
            Triple("$root/processing/CommandTransformer.kt", "suspend fun transformToExecutable(", queries),
            Triple("$root/processing/AICommandProcessor.kt", "private suspend fun transformActionCommand(", actions),
            Triple("$root/processing/AICommandProcessor.kt", "suspend fun transformActionForVerbalization(", actions),
            Triple("$root/validation/ValidationResolver.kt", "private suspend fun reason(", actions)
        )
        for ((path, declaration, expected) in switches) {
            assertEquals("${path.substringAfterLast('/')}, $declaration", expected.sorted(), handled(body(path, declaration)).sorted())
        }
    }
}
