package app.treelune.core.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the ids of a part promise when they are replaced (docs/design/share.md, « Les liens »): an
 * id is replaced wherever it stands, and a text that only looks like JSON is kept whole.
 */
class IdWordsTest {

    private val ids = mapOf("a1" to "n1", "b2" to "n2")

    @Test
    fun `a formula reading two variables keeps its whole text, both ids replaced`() {
        val definition = JSONObject().put("formula", "{var:a1} - {var:b2}")
        IdWords.replace(definition, ids)
        assertEquals("{var:n1} - {var:n2}", definition.getString("formula"))
    }

    @Test
    fun `a formula reading one variable alone stays a formula`() {
        val definition = JSONObject().put("formula", "{var:a1}")
        IdWords.replace(definition, ids)
        assertEquals("{var:n1}", definition.getString("formula"))
    }

    @Test
    fun `a config kept as a JSON text is walked, its ids replaced`() {
        val row = JSONObject().put("config_json", JSONObject().put("source", "a1").toString())
        IdWords.replace(row, ids)
        assertEquals("n1", JSONObject(row.getString("config_json")).getString("source"))
    }
}
