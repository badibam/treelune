package app.treelune.core.conditions

import app.treelune.tools.path.PathConditionSources
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A condition in words (ConditionPhrase, docs/design/progression.md, « Les écrans »): each nested
 * piece between brackets, the thing judged named, a constant written as its field shows it and
 * found again where it stands, to be written over alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class ConditionPhraseTest {

    private val context = RuntimeEnvironment.getApplication()

    private val config = JSONObject("""{"root_kind": "tree", "link_types": [{"key": "required", "name": "Required"}], "kinds": [
        {"key": "tree", "name": "Tree", "contains": ["skill"], "states": [{"key": "open", "name": "Open"}]},
        {"key": "skill", "name": "Skill", "states": [{"key": "done", "name": "Done", "manual": true}, {"key": "available", "name": "Available"}, {"key": "locked", "name": "Locked"}]}]}""")

    private fun count(filters: String) = """{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "self"}, "view": "stages", "filters": [$filters]}, "reduction": "COUNT"}}"""
    private val required = """{"left": {"field": "id"}, "op": "in", "right": {"self": "links", "type": "required"}}"""
    private val done = """{"left": {"field": "data.state"}, "op": "in", "right": {"constant": ["done"]}}"""

    private val rule = JSONObject("""{"all": [
        {"left": ${count("$required, $done")}, "op": "=", "right": ${count(required)}},
        {"left": ${count(required)}, "op": ">", "right": {"constant": 0}}]}""")

    private val phrase = ConditionPhrase(context, PathConditionSources(AppConditionSources(context, null), config) { key ->
        app.treelune.core.strings.Strings.`for`(tool = "path", context = context).tool(key)
    }, config, listOf("kinds", 1, "states", 1, "when"), "path")

    @Test
    fun `nested pieces are bracketed, the thing judged named, a constant read as its field`() = runBlocking {
        val text = ConditionPhrase.text(phrase.of(rule))
        val stages = "this tool › Its stages where id is one of links “Required” of the stage judged"
        assertEquals("all of: count of [$stages, and state is one of Done] = count of [$stages]; count of [$stages] > 0", text)
    }

    /** French puts a no-break space before its semicolon, which the phrase keeps. */
    @Test
    @Config(qualifiers = "fr")
    fun `in French, the semicolon keeps its space`() = runBlocking {
        val text = ConditionPhrase.text(phrase.of(rule))
        assertEquals(true, text.startsWith("toutes : compte de [cet outil › Ses étapes dont"))
        assertEquals(true, text.contains("]\u00a0; compte de ["))
    }

    @Test
    fun `a constant is found where it stands and written over alone`() = runBlocking {
        val constants = phrase.of(rule).filterIsInstance<PhrasePart.Constant>()
        assertEquals(listOf(listOf("done"), 0L), constants.map { it.value }.map { if (it is Number) it.toLong() else it })
        val threshold = constants.last()
        val written = ConditionPhrase.write(rule, threshold.path, 2)
        assertEquals(2, written.getJSONArray("all").getJSONObject(1).getJSONObject("right").getInt("constant"))
        assertEquals(rule.getJSONArray("all").getJSONObject(0).toString(), written.getJSONArray("all").getJSONObject(0).toString())
    }
}
