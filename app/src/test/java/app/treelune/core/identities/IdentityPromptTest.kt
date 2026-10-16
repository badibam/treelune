package app.treelune.core.identities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a session speaking as an identity reads of it (docs/design/incarnations.md, « Ce que porte
 * le prompt »): its texts in the language it speaks, an absent one left out, its bagage listed by
 * key and size without its content; a resource read as written.
 */
class IdentityPromptTest {

    private val text: (String) -> String = { key ->
        when (key) {
            "ai_identity_block" -> "WHO %1\$s IN %2\$s"
            "ai_identity_bagage" -> "BAGAGE OF %1\$s, %2\$s:"
            else -> key
        }
    }

    private fun identity(fr: IdentityLanguage) = Identity("jung", "Jung", Identity.DEFAULT_ICON, null, "fr",
        mapOf("fr" to fr, "en" to IdentityLanguage(IdentityPiece("english voice"))), "2026-10-16", 0, 0)

    @Test
    fun `the block holds the spoken language's texts and lists the bagage without its content`() {
        val block = IdentityPrompt.block(identity(IdentityLanguage(
            voice = IdentityPiece("voix française\n"),
            portrait = IdentityPiece("Carl Gustav Jung"),
            resources = mapOf("1/lexique@Jung" to IdentityResource("Lexique", "Quand un mot revient", "anima, animus"))
        )), text)
        assertTrue(block.startsWith("WHO Jung IN fr"))
        assertTrue("voix française" in block)
        assertFalse("english voice" in block)
        // The mind is not written: no heading for it
        assertFalse("ai_identity_piece_mind" in block)
        assertTrue("BAGAGE OF Jung, jung:" in block)
        assertTrue("- `1/lexique@Jung` — Lexique (13 chars): Quand un mot revient" in block)
        assertFalse("anima" in block)
    }

    @Test
    fun `an identity without bagage says it has none`() {
        assertTrue(IdentityPrompt.block(identity(IdentityLanguage()), text).endsWith("ai_identity_bagage_none"))
    }

    @Test
    fun `a resource is read as written, not as escaped JSON`() {
        val read = IdentityPrompt.forModel("read_resource", mapOf("resource" to mapOf(
            "identity_id" to "jung", "language" to "fr", "key" to "k",
            "title" to "Lexique", "description" to "Quand", "content" to "ligne \"un\"\nligne deux\n")))
        assertEquals("## Lexique\n\nQuand\n\nligne \"un\"\nligne deux", read)
    }
}
