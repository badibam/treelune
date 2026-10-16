package app.treelune.core.ai.prompts

import app.treelune.core.ai.data.CommandResult
import app.treelune.core.ai.data.CommandStatus
import app.treelune.core.ai.data.MessageSender
import app.treelune.core.ai.data.SessionMessage
import app.treelune.core.ai.data.SystemMessage
import app.treelune.core.ai.data.SystemMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The reads a session sends once (docs/design/incarnations.md, « Le bagage »): a text the model
 * holds is not sent again, one that changed since is, and what it holds is read back from the
 * session's stored results.
 */
class SessionReadsTest {

    private fun read(target: String, text: String, status: CommandStatus = CommandStatus.SUCCESS) = CommandResult(
        command = "schemas.get",
        status = status,
        details = null,
        data = mapOf(SessionReads.TARGET to target, SessionReads.FINGERPRINT to SessionReads.fingerprint(text)),
        error = null
    )

    private fun message(
        vararg results: CommandResult,
        type: SystemMessageType = SystemMessageType.DATA_ADDED,
        excluded: Boolean = false
    ) = SessionMessage(
        id = "m",
        timestamp = 0,
        sender = MessageSender.SYSTEM,
        richContent = null,
        textContent = null,
        aiMessage = null,
        aiMessageJson = null,
        systemMessage = SystemMessage(type = type, commandResults = results.toList(), summary = ""),
        excludeFromPrompt = excluded
    )

    @Test
    fun `a tool's entries schema is one per tool, any other schema is told by its name`() {
        assertEquals("entries:t1", SessionReads.target("schemas", "get", mapOf("tool_instance_id" to "t1")))
        assertEquals("tracking_config", SessionReads.target("schemas", "get", mapOf("tooltype" to "tracking")))
        assertEquals("zone_config", SessionReads.target("schemas", "get", mapOf("id" to "zone_config")))
    }

    @Test
    fun `an identity read is told by its identity, its language and its resource`() {
        assertEquals("identity:i1:", SessionReads.target("identities", "read", mapOf("identity_id" to "i1")))
        assertEquals("identity:i1:fr", SessionReads.target("identities", "read", mapOf("identity_id" to "i1", "language" to "fr")))
        assertEquals(
            "identity_resource:i1::1/lexique",
            SessionReads.target("identities", "read_resource", mapOf("identity_id" to "i1", "key" to "1/lexique"))
        )
    }

    @Test
    fun `any other command is sent every time`() {
        assertNull(SessionReads.target("tool_data", "get", mapOf("tool_instance_id" to "t1")))
        assertNull(SessionReads.target("identities", "list", emptyMap()))
        assertNull(SessionReads.target("identities", "read_resource", mapOf("identity_id" to "i1")))
    }

    @Test
    fun `the same text is not sent again, a changed one is, saying so`() {
        val sent = SessionReads.fingerprint("schema v1")
        assertEquals(SessionReads.Outcome.NEW, SessionReads.outcome(null, sent))
        assertEquals(SessionReads.Outcome.SAME, SessionReads.outcome(sent, SessionReads.fingerprint("schema v1")))
        assertEquals(SessionReads.Outcome.CHANGED, SessionReads.outcome(sent, SessionReads.fingerprint("schema v2")))
    }

    @Test
    fun `the model holds the last text it received for a target`() {
        val sent = SessionReads.sent(listOf(
            message(read("entries:t1", "v1"), read("zone_config", "z")),
            message(read("entries:t1", "v2"), type = SystemMessageType.SCHEMA_REQUIRED)
        ))
        assertEquals(mapOf("entries:t1" to SessionReads.fingerprint("v2"), "zone_config" to SessionReads.fingerprint("z")), sent)
    }

    @Test
    fun `a read it never received does not count`() {
        val sent = SessionReads.sent(listOf(
            message(read("entries:t1", "v1"), excluded = true),
            message(read("entries:t2", "v1", status = CommandStatus.FAILED)),
            message(read("entries:t3", "v1"), type = SystemMessageType.ACTIONS_EXECUTED)
        ))
        assertEquals(emptyMap<String, String>(), sent)
    }

    @Test
    fun `what the model holds survives the session's storage`() {
        val stored = message(read("entries:t1", "v1"))
        val reloaded = stored.copy(systemMessage = SystemMessage.fromJson(stored.systemMessage!!.toJson()))
        assertEquals(mapOf("entries:t1" to SessionReads.fingerprint("v1")), SessionReads.sent(listOf(reloaded)))
    }

    @Test
    fun `two texts share a fingerprint only when they are the same`() {
        assertEquals(SessionReads.fingerprint("a"), SessionReads.fingerprint("a"))
        assertNotEquals(SessionReads.fingerprint("a"), SessionReads.fingerprint("b"))
    }
}
