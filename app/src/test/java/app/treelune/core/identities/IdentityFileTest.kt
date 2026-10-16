package app.treelune.core.identities

import app.treelune.core.versioning.IdentitiesAtV78
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * What docs/IDENTITY_FILE.md promises any producer: the example it gives reads, and its JSON
 * Schema agrees with the reader on it; an unknown format refused; a field required and missing
 * or of another type refused by its path; a field more passed over; a language without a text
 * refused; the resources kept in their order; every text read marked imported.
 */
class IdentityFileTest {

    private val doc = File("../docs/IDENTITY_FILE.md").readText()
    private val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
        .getSchema(File("../docs/schemas/identity-file.schema.json").readText())
    private val mapper = ObjectMapper()

    /** The example of the doc, the first JSON block. */
    private val example = Regex("```json\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).find(doc)!!.groupValues[1]

    private fun valid(text: String) = schema.validate(mapper.readTree(text)).isEmpty()

    private fun refusal(text: String): IdentityFile.Refused = try {
        IdentityFile.read(text)
        fail("expected a refusal")
        throw IllegalStateException()
    } catch (e: IdentityFile.Refused) {
        e
    }

    private fun file(languages: JSONObject = JSONObject().put("en", JSONObject().put("voice", JSONObject().put("content", "Short."))))
        = JSONObject().put("format", 2).put("id", "x").put("name", "X").put("languages", languages)

    @Test
    fun `the example of the doc reads, and its schema takes it`() {
        assertTrue(valid(example))
        val read = IdentityFile.read(example)
        assertEquals("drawing-teacher", read.id)
        assertEquals("2026-10-16", read.publishedAt)
        assertEquals(setOf("en", "fr"), read.languages.keys)
        val en = read.languages.getValue("en")
        assertTrue(en.voice!!.imported && en.mind!!.imported && en.resources.getValue("vocabulary").imported)
        assertNull(read.languages.getValue("fr").mind)
    }

    @Test
    fun `the least a file holds reads, its schema agreeing`() {
        val text = file().toString()
        assertTrue(valid(text))
        assertNull(IdentityFile.read(text).publishedAt)
    }

    @Test
    fun `a field required and missing is refused by its path, as by the schema`() {
        for ((text, path) in listOf(
            file().apply { remove("name") }.toString() to "name",
            file().put("id", "Drawing Teacher").toString() to "id",
            file().put("published_at", "16/10/2026").toString() to "published_at",
            file(JSONObject().put("en", JSONObject().put("voice", JSONObject().put("content", "")))).toString() to "languages.en.voice.content",
            file(JSONObject().put("english", JSONObject().put("voice", JSONObject().put("content", "a")))).toString() to "languages.english"
        )) {
            assertFalse(text, valid(text))
            val refused = refusal(text)
            assertEquals("identity_import_field", refused.key)
            assertEquals(path, refused.args[0])
        }
    }

    @Test
    fun `another format is refused, naming it`() {
        val refused = refusal(file().put("format", 1).toString())
        assertEquals("identity_import_format", refused.key)
        assertEquals(1, refused.args[0])
    }

    @Test
    fun `a language without a text is refused, as by the schema`() {
        val text = file(JSONObject().put("en", JSONObject().put("resources", JSONArray()))).toString()
        assertFalse(valid(text))
        assertEquals("identity_import_no_text", refusal(text).key)
    }

    @Test
    fun `the resources keep their order, a key twice refused`() {
        fun resource(key: String) = JSONObject().put("key", key).put("title", "T").put("description", "D").put("content", "C")
        val keys = listOf("substance", "canon", "lexique", "florilege")
        val section = JSONObject().put("voice", JSONObject().put("content", "v")).put("resources", JSONArray(keys.map(::resource)))
        assertEquals(keys, IdentityFile.read(file(JSONObject().put("en", section)).toString()).languages.getValue("en").resources.keys.toList())
        section.put("resources", JSONArray(listOf(resource("canon"), resource("canon"))))
        assertEquals("languages.en.resources.1.key", refusal(file(JSONObject().put("en", section)).toString()).args[0])
    }

    @Test
    fun `a field more is passed over`() {
        val text = file().put("licence", "CC BY").toString()
        assertTrue(valid(text))
        IdentityFile.read(text)
    }

    @Test
    fun `an identity stored before marks its texts imported by their former address`() {
        val stored = """{"en": {"voice": {"content": "v", "address": "somewhere"}, "mind": {"content": "m", "address": null},
            "resources": [{"key": "k", "title": "t", "description": "d", "content": "c", "address": "elsewhere"}]}}"""
        val en = Identity.languagesFromJson(JSONObject(IdentitiesAtV78.languages(stored))).getValue("en")
        assertTrue(en.voice!!.imported)
        assertFalse(en.mind!!.imported)
        assertTrue(en.resources.getValue("k").imported)
        assertFalse(IdentitiesAtV78.languages(stored).contains("address"))
        assertEquals(5L, IdentitiesAtV78.importedAt("2026-10-16", 5L))
        assertNull(IdentitiesAtV78.importedAt(null, 5L))
    }
}
