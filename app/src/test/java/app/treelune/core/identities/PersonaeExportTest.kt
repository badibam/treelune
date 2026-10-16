package app.treelune.core.identities

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * What an import of a personae export promises (docs/design/incarnations.md, « L'import »): an
 * unknown format refused, a field expected and missing or of another type refused by its path, a
 * field more passed over, the bagage kept in its order with each piece's address.
 */
class PersonaeExportTest {

    private fun piece(text: String) = JSONObject().put("adresse", "mediatheque:lens:1/$text@X").put("contenu", text)

    private fun resource(key: String) = JSONObject().put("adresse", "mediatheque:lens:1/$key@X")
        .put("titre", "Title $key").put("description", "When $key").put("contenu", "Content $key")

    private fun section(vararg keys: String) = JSONObject().put("voix", piece("voix")).put("esprit", piece("esprit"))
        .put("portrait", piece("portrait")).put("bagage", JSONObject().apply { keys.forEach { put("1/$it@X", resource(it)) } })

    private fun export() = JSONObject().put("format", 1).put("id", "stan-prokopenko").put("nom", "Stan Prokopenko")
        .put("distribuable", false).put("publie_le", "2026-10-16")
        .put("langues", JSONObject().put("en", section("canon", "lexique")).put("fr", section("canon")))

    private fun refusal(text: String): PersonaeExport.Refused = try {
        PersonaeExport.read(text)
        fail("expected a refusal")
        throw IllegalStateException()
    } catch (e: PersonaeExport.Refused) {
        e
    }

    @Test
    fun `an export reads into the app's words, every language kept`() {
        val read = PersonaeExport.read(export().toString())
        assertEquals("stan-prokopenko", read.id)
        assertEquals("2026-10-16", read.publishedAt)
        assertEquals(setOf("en", "fr"), read.languages.keys)
        val en = read.languages.getValue("en")
        assertEquals("voix", en.voice?.content)
        assertEquals("mediatheque:lens:1/esprit@X", en.mind?.address)
        assertEquals(IdentityResource("Title lexique", "When lexique", "Content lexique", "mediatheque:lens:1/lexique@X"), en.resources["1/lexique@X"])
    }

    @Test
    fun `the bagage keeps the export's order`() {
        val keys = listOf("substance", "canon", "lexique", "florilege")
        // Written by hand: org.json on the JVM keeps no order of its keys
        val bagage = keys.joinToString(",") { "\"1/$it@X\": ${resource(it)}" }
        val text = """{"format": 1, "id": "x", "nom": "X", "distribuable": false, "publie_le": "2026-10-16",
            "langues": {"en": {"voix": ${piece("v")}, "esprit": ${piece("e")}, "portrait": ${piece("p")}, "bagage": {$bagage}}}}"""
        assertEquals(keys.map { "1/$it@X" }, PersonaeExport.read(text).languages.getValue("en").resources.keys.toList())
    }

    @Test
    fun `a field more is passed over`() {
        val text = export().put("poids", "complet").toString()
        assertEquals("Stan Prokopenko", PersonaeExport.read(text).name)
    }

    @Test
    fun `an unknown format is refused, saying both`() {
        val refused = refusal(export().put("format", 2).toString())
        assertEquals("identity_import_format", refused.key)
        assertEquals(listOf(2, 1), refused.args.toList())
    }

    @Test
    fun `a missing format is refused`() {
        val json = export(); json.remove("format")
        assertEquals("format", refusal(json.toString()).args.single())
    }

    @Test
    fun `a missing or empty piece is refused by its path`() {
        val json = export()
        json.getJSONObject("langues").getJSONObject("fr").getJSONObject("esprit").put("contenu", "")
        assertEquals("langues.fr.esprit.contenu", refusal(json.toString()).args.single())
    }

    @Test
    fun `a resource of another type is refused by its path`() {
        val json = export()
        json.getJSONObject("langues").getJSONObject("en").getJSONObject("bagage").put("1/canon@X", "text")
        assertEquals("langues.en.bagage.1/canon@X", refusal(json.toString()).args.single())
    }

    @Test
    fun `an id personae would not write is refused`() {
        assertEquals("id", refusal(export().put("id", "Stan P").toString()).args.single())
    }

    @Test
    fun `a file that is not JSON is refused`() {
        assertEquals("identity_import_not_json", refusal("voix: …").key)
    }

    @Test
    fun `an identity's languages survive their storage, the bagage's order with them`() {
        val read = PersonaeExport.read(export().toString())
        val stored = Identity.languagesToJson(read.languages).toString()
        assertEquals(read.languages, Identity.languagesFromJson(JSONObject(stored)))
    }

    @Test
    fun `an imported identity names its pieces retouched, one never imported none`() {
        val read = PersonaeExport.read(export().toString())
        val fr = read.languages.getValue("fr")
        val retouched = read.languages + ("fr" to fr.withPiece(IdentityLanguage.VOICE, IdentityPiece("mine"))
            .copy(resources = fr.resources + ("own" to IdentityResource("t", "d", "c"))))
        val identity = Identity("stan-prokopenko", "Stan", Identity.DEFAULT_ICON, null, "fr", retouched, "2026-10-16", 0, 0)
        assertEquals(listOf("fr · voice", "fr · own"), identity.retouched())
        assertEquals(emptyList<String>(), identity.copy(publishedAt = null).retouched())
        assertNull(IdentityLanguage().voice)
    }
}
