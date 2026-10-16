package app.treelune.core.ai.prompts

import app.treelune.core.ai.data.CommandStatus
import app.treelune.core.ai.data.SessionMessage
import app.treelune.core.ai.data.SystemMessageType
import java.security.MessageDigest

/**
 * The reads a session sends once: a schema, an identity, a resource of its bagage. A text the
 * model already holds for the same target is not sent again; one that changed since — by the AI
 * or on screen — is sent whole, saying it replaces the earlier read.
 *
 * A read is known by its target, what it reads ("entries:<tool id>", "tracking_config",
 * "identity_resource:<id>:<language>:<key>"), and its text by a fingerprint. Both are kept in
 * the data of the stored result, which is what the session's history is read back from.
 */
object SessionReads {

    /** The keys of a stored result's data that hold its target and its text's fingerprint. */
    const val TARGET = "read_target"
    const val FINGERPRINT = "read_fingerprint"

    /** The system messages whose results the model received as data. */
    private val SENT_TYPES = setOf(SystemMessageType.DATA_ADDED, SystemMessageType.SCHEMA_REQUIRED)

    /** What a command on [resource].[operation] with [params] reads, when it is sent once in a session; null for any other command. */
    fun target(resource: String, operation: String, params: Map<String, Any?>): String? = when {
        resource == "schemas" && operation == "get" -> schemaTarget(params)
        resource == "identities" && operation in setOf("read", "read_resource") -> identityTarget(operation, params)
        else -> null
    }

    /**
     * The target of the entries schema of the tool [toolInstanceId]: one schema per tool, since
     * its user's fields are in it.
     */
    fun entriesSchemaTarget(toolInstanceId: String): String = "entries:$toolInstanceId"

    /** A schema asked for: a tool's entries by its id, any other schema by its name ("tracking_config", "zone_config"). */
    private fun schemaTarget(params: Map<String, Any?>): String? {
        (params["tool_instance_id"] as? String)?.let { return entriesSchemaTarget(it) }
        return params["id"] as? String ?: (params["tooltype"] as? String)?.let { "${it}_config" }
    }

    /** An identity read: the identity, the language asked (none for its own) and, for a resource, its key. */
    private fun identityTarget(operation: String, params: Map<String, Any?>): String? {
        val id = params["identity_id"] as? String ?: return null
        val language = params["language"] as? String ?: ""
        return if (operation == "read") "identity:$id:$language"
        else "identity_resource:$id:$language:${params["key"] as? String ?: return null}"
    }

    /** The fingerprint of [text]: two texts share it only when they are the same. */
    fun fingerprint(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * The fingerprint last sent for each target in [messages], in their order: a later read of a
     * target replaces an earlier one. A message left out of the prompt was never sent.
     */
    fun sent(messages: List<SessionMessage>): Map<String, String> {
        val sent = mutableMapOf<String, String>()
        for (message in messages) {
            if (message.excludeFromPrompt) continue
            val systemMessage = message.systemMessage?.takeIf { it.type in SENT_TYPES } ?: continue
            for (result in systemMessage.commandResults) {
                if (result.status != CommandStatus.SUCCESS) continue
                val target = result.data?.get(TARGET) as? String ?: continue
                val fingerprint = result.data[FINGERPRINT] as? String ?: continue
                sent[target] = fingerprint
            }
        }
        return sent
    }

    /** What a text read now is to the model, given the fingerprint it last received for the same target. */
    enum class Outcome {
        /** Never sent in the session: sent. */
        NEW,
        /** The same text as the one sent: not sent again. */
        SAME,
        /** A different text: sent, saying it replaces the earlier read. */
        CHANGED
    }

    fun outcome(previous: String?, current: String): Outcome = when (previous) {
        null -> Outcome.NEW
        current -> Outcome.SAME
        else -> Outcome.CHANGED
    }
}
