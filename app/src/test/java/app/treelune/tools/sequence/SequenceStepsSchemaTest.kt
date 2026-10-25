package app.treelune.tools.sequence

import app.treelune.core.fields.settings.SettingsSchemaGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the agreement between the session's steps as its config declares them, from which the
 * form and the AI's schema come, and SequencePlan, which reads them: what the schema lets through
 * unrolls, and what SequencePlan could not read — a block too deep, a timer without its length, a
 * step skipped at a last round outside any block — is refused before it is stored.
 */
class SequenceStepsSchemaTest {

    private val mapper = ObjectMapper()

    private val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(
        SettingsSchemaGenerator.generate(listOf(SequenceToolType.steps { it })) { it }.toString()
    )

    private fun passes(json: String) = schema.validate(mapper.readTree(json)).isEmpty()

    private val intervals = """
        {"steps": [
          {"kind": "step", "name": "Warm-up", "length": "none", "auto_next": false},
          {"kind": "block", "name": "Intervals", "repeat": 8, "steps": [
            {"kind": "step", "name": "Run", "length": "fixed", "auto_next": true, "duration": 30000, "instruction": "5 km pace"},
            {"kind": "step", "name": "Rest", "length": "fixed", "auto_next": true, "duration": 90000, "skip_last_round": true}
          ]},
          {"kind": "step", "name": "Cool-down", "length": "fixed", "auto_next": false, "duration": 300000}
        ]}
    """

    @Test
    fun `a step without a time of its own that would pass by itself is found, nothing ending it`() {
        assertEquals("Wait", SequencePlan.endless(JSONObject("""{"steps": [{"kind": "block", "name": "B", "repeat": 2, "steps": [
            {"kind": "step", "name": "Wait", "length": "none", "auto_next": true}]}]}""")))
        assertEquals(null, SequencePlan.endless(JSONObject(intervals)))
    }

    @Test
    fun `a session the schema lets through unrolls`() {
        assertTrue(passes(intervals))
        assertEquals(1 + 8 + 7 + 1, SequencePlan.unroll(JSONObject(intervals)).size)
    }

    @Test
    fun `a block in a block holds steps stored without their kind`() {
        val nested = """
            {"steps": [{"kind": "block", "name": "Circuit", "repeat": 2, "steps": [
              {"kind": "block", "name": "Set", "repeat": 3, "steps": [{"name": "Squat", "length": "none", "auto_next": false}]}
            ]}]}
        """
        assertTrue(passes(nested))
        assertEquals(6, SequencePlan.unroll(JSONObject(nested)).size)
    }

    @Test
    fun `a block three deep is refused`() {
        assertFalse(passes("""
            {"steps": [{"kind": "block", "name": "A", "repeat": 2, "steps": [
              {"kind": "block", "name": "B", "repeat": 2, "steps": [
                {"kind": "block", "name": "C", "repeat": 2, "steps": [{"name": "X", "length": "none", "auto_next": false}]}
              ]}
            ]}]}
        """))
    }

    @Test
    fun `a timer without its length, an empty run, a block without rounds are refused`() {
        assertFalse(passes("""{"steps": [{"kind": "step", "name": "Run", "length": "fixed", "auto_next": true}]}"""))
        assertFalse(passes("""{"steps": []}"""))
        assertFalse(passes("""{"steps": [{"kind": "block", "name": "B", "steps": [{"kind": "step", "name": "X", "length": "none", "auto_next": false}]}]}"""))
    }

    @Test
    fun `a step outside a block is never skipped at a last round`() {
        assertFalse(passes("""{"steps": [{"kind": "step", "name": "X", "length": "none", "auto_next": false, "skip_last_round": true}]}"""))
    }
}
