package app.treelune.core.ai.prompts

import app.treelune.core.ai.data.SessionType
import app.treelune.core.guide.Pieces
import app.treelune.core.strings.Strings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The AI reads what the user does on screen: each piece of the app with its usage and its screen,
 * each tool type with its screen as the Guide shows it, the icons in words: a `{name}` the AI
 * cannot see never reaches it, in a French app as in an English one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr")
class PiecesPromptTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `every piece and every tool type's screen reach the AI, their buttons in words`() = runBlocking {
        val prompt = PromptChunks.buildLevel1StaticDoc(context, SessionType.CHAT)
        val english = Strings.`for`(context = AiLanguage.english(context))

        for (piece in Pieces.ALL) {
            assertTrue(piece.nameKey, prompt.contains("### ${english.shared(piece.nameKey)}\n${english.shared(piece.usageKey)}"))
        }
        assertTrue(prompt.contains("- The “message-circle” button: Opens a conversation with its message"))
        assertTrue(prompt.contains("#### On screen"))
        assertFalse(Regex("""(?m)^- \{[a-z0-9-]+\}""").containsMatchIn(prompt))
    }
}
