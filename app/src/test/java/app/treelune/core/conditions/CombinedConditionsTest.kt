package app.treelune.core.conditions

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the Condition brick promises of a combination (docs/BRICKS.md, « Condition »): all holds
 * when each part does, any when one does, not knowing kept apart from failing, nestable; a
 * malformed one named.
 */
class CombinedConditionsTest {

    /** A comparison judged by its op alone: "yes" holds, "no" does not, "?" is not known. */
    private fun c(answer: String) = JSONObject().put("op", answer)
    private val judge: (JSONObject) -> Boolean? = { when (it.getString("op")) { "yes" -> true; "no" -> false; else -> null } }
    private fun all(vararg parts: JSONObject) = JSONObject().put("all", org.json.JSONArray(parts.toList()))
    private fun any(vararg parts: JSONObject) = JSONObject().put("any", org.json.JSONArray(parts.toList()))

    @Test
    fun `all holds when each part does, fails as soon as one fails, else does not know`() {
        assertEquals(true, Conditions.holdsNow(all(c("yes"), c("yes")), judge))
        assertEquals(false, Conditions.holdsNow(all(c("yes"), c("?"), c("no")), judge))
        assertNull(Conditions.holdsNow(all(c("yes"), c("?")), judge))
        assertEquals(true, runBlocking { Conditions.holds(all(c("yes"), c("yes"))) { judge(it) } })
    }

    @Test
    fun `any holds as soon as one part does, fails when none does, else does not know`() {
        assertEquals(true, Conditions.holdsNow(any(c("no"), c("?"), c("yes")), judge))
        assertEquals(false, Conditions.holdsNow(any(c("no"), c("no")), judge))
        assertNull(Conditions.holdsNow(any(c("no"), c("?")), judge))
        assertEquals(false, runBlocking { Conditions.holds(any(c("no"), c("no"))) { judge(it) } })
    }

    @Test
    fun `combinations nest, their comparisons found and rewritten in place`() {
        val nested = all(c("yes"), any(c("no"), c("yes")))
        assertEquals(true, Conditions.holdsNow(nested, judge))
        assertEquals(listOf("yes", "no", "yes"), Conditions.comparisons(nested).map { it.getString("op") })
        val turned = runBlocking { Conditions.mapComparisons(nested) { JSONObject().put("op", "no") } }
        assertEquals(false, Conditions.holdsNow(turned, judge))
        assertEquals(3, Conditions.comparisons(turned).size)
    }

    @Test
    fun `a malformed combination is named, a comparison and a good one are not`() {
        val text: (String) -> String = { "$it %1\$s" }
        assertNull(Conditions.combinationProblem(c("yes"), "x", text))
        assertNull(Conditions.combinationProblem(all(c("yes"), any(c("no"))), "x", text))
        assertNotNull(Conditions.combinationProblem(JSONObject().put("all", org.json.JSONArray()), "x", text))
        assertNotNull(Conditions.combinationProblem(all(c("yes")).put("any", org.json.JSONArray().put(c("no"))), "x", text))
        assertNotNull(Conditions.combinationProblem(all(c("yes"), JSONObject().put("any", org.json.JSONArray())), "x", text))
    }
}
