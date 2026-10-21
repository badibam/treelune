package app.treelune.core.identities

import android.content.Context
import app.treelune.core.ai.data.MessageSender
import app.treelune.core.ai.database.SessionMessageEntity
import app.treelune.core.ai.prompts.PromptManager
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.strings.Strings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * An identity deleted while a past conversation spoke as it (docs/AI.md, « Identities »): the
 * conversation stays whole, its messages read and still naming it, which its screen reads as the
 * reason it cannot go on; asked to go on, the prompt refuses with that reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdentityGoneTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a conversation outlives its identity, read whole, refusing to go on with the reason`() = runBlocking {
        val coordinator = Coordinator(context)
        val identity = coordinator.processUserAction("identities.create", mapOf("name" to "Teacher", "language" to "en"))
        assertTrue(identity.error, identity.isSuccess)
        val identityId = identity.data!!["identity_id"] as String
        val session = coordinator.processUserAction("ai_sessions.create_session", mapOf("name" to "Lesson", "type" to "CHAT", "identity_id" to identityId))
        assertTrue(session.error, session.isSuccess)
        val sessionId = session.data!!["session_id"] as String
        AppDatabase.getDatabase(context).aiDao().insertMessage(SessionMessageEntity(
            id = UUID.randomUUID().toString(), sessionId = sessionId, timestamp = 1L, sender = MessageSender.USER,
            richContentJson = null, textContent = "Hello", aiMessageJson = null, aiMessageParsedJson = null,
            systemMessageJson = null, executionMetadataJson = null
        ))

        val deleted = coordinator.processUserAction("identities.delete", mapOf("identity_id" to identityId))
        assertTrue(deleted.error, deleted.isSuccess)

        val read = coordinator.processUserAction("ai_sessions.get_session", mapOf("session_id" to sessionId))
        assertTrue(read.error, read.isSuccess)
        val kept = read.data!!["session"] as Map<*, *>
        assertEquals("the conversation still names it, for its screen to say why", identityId, kept["identity_id"])
        assertEquals(listOf("Hello"), (read.data!!["messages"] as List<*>).map { (it as Map<*, *>)["text_content"] })

        val reason = Strings.`for`(context = context).shared("identity_session_gone")
        try {
            PromptManager.buildPromptData(sessionId, context)
            fail("the conversation went on without its identity")
        } catch (e: IllegalStateException) {
            assertEquals(reason, e.message)
        }
    }
}
