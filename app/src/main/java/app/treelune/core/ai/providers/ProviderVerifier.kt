package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.ai.domain.AIState
import app.treelune.core.ai.data.SessionType
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager

/**
 * Which provider a session calls, checked before its prompt is built (docs/AI.md, « Provider abstraction »).
 *
 * A session calls its own provider: a run its routine's, given at its launch; a conversation the
 * one it took. A conversation that has none yet takes the default at its first call and keeps it;
 * without a default it is refused, saying to create a provider. A provider gone, or whose key does
 * not open on this phone, is refused before anything is sent. Nothing falls back on another.
 */
object ProviderVerifier {

    /** The provider to call ([providerId]), or why there is none ([errorMessage]). */
    data class VerificationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val providerId: String? = null
    )

    suspend fun verifyProvider(state: AIState, context: Context): VerificationResult {
        val coordinator = Coordinator(context)
        val s = Strings.`for`(context = context)
        val sessionId = state.sessionId
            ?: return VerificationResult(false, s.shared("ai_error_session_not_found")).also { LogManager.aiService("verifyProvider: No session ID in state", "ERROR") }

        try {
            val sessionResult = coordinator.processUserAction("ai_sessions.get_session", mapOf("session_id" to sessionId))
            val sessionData = sessionResult.data?.get("session") as? Map<*, *>
            if (!sessionResult.isSuccess || sessionData == null) {
                LogManager.aiService("verifyProvider: Failed to load session: ${sessionResult.error}", "ERROR")
                return VerificationResult(false, s.shared("ai_error_session_not_found"))
            }

            val providerId: String = (sessionData["provider_id"] as? String) ?: when (state.sessionType) {
                // Its first call: the default, kept from now on
                SessionType.CHAT -> {
                    val default = coordinator.processUserAction("ai_provider_config.get_default")
                    val defaultId = default.data?.get("provider_id") as? String
                    if (!default.isSuccess || defaultId == null) {
                        LogManager.aiService("verifyProvider: conversation without a provider, and no default", "WARN")
                        return VerificationResult(false, s.shared("ai_error_no_provider_configured"))
                    }
                    val taken = coordinator.processUserAction("ai_sessions.set_provider", mapOf("session_id" to sessionId, "provider_id" to defaultId))
                    if (!taken.isSuccess) {
                        LogManager.aiService("verifyProvider: the default not taken: ${taken.error}", "ERROR")
                        return VerificationResult(false, taken.error ?: s.shared("ai_error_no_provider_configured"))
                    }
                    LogManager.aiService("verifyProvider: conversation $sessionId takes the default provider $defaultId", "INFO")
                    defaultId
                }
                // Its routine had none at its launch, which refuses it: not run
                SessionType.ROUTINE_RUN -> {
                    LogManager.aiService("verifyProvider: ROUTINE_RUN session has no provider", "ERROR")
                    return VerificationResult(false, s.shared("routine_provider_none"))
                }
                SessionType.SEED, null -> {
                    LogManager.aiService("verifyProvider: session of type ${state.sessionType} cannot call a provider", "ERROR")
                    return VerificationResult(false, s.shared("ai_error_no_provider_configured"))
                }
            }

            val providerResult = coordinator.processUserAction("ai_provider_config.get", mapOf("provider_id" to providerId))
            if (!providerResult.isSuccess) {
                LogManager.aiService("verifyProvider: Provider '$providerId' not found", "ERROR")
                return VerificationResult(false, s.shared("ai_session_provider_gone"))
            }
            val kind = AIProviderKinds(context).of(providerResult.data?.get("kind") as String)
            if (kind == null) {
                LogManager.aiService("verifyProvider: Provider '$providerId' of an unknown kind", "ERROR")
                return VerificationResult(false, s.shared("ai_error_unknown_provider").format(providerResult.data?.get("kind")))
            }

            // A key sealed on another phone: said before a session starts on it
            if ((providerResult.data?.get(app.treelune.core.secrets.SecretSettings.UNREADABLE) as? List<*>).orEmpty().isNotEmpty()) {
                LogManager.aiService("verifyProvider: Provider '$providerId' has a secret unreadable on this phone", "ERROR")
                return VerificationResult(false, s.shared("ai_error_provider_secret_unreadable").format(providerResult.data?.get("name")))
            }

            LogManager.aiService("verifyProvider: Provider '$providerId' verified", "DEBUG")
            return VerificationResult(true, providerId = providerId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiService("verifyProvider: Exception during verification: ${e.message}", "ERROR", e)
            return VerificationResult(false, s.shared("ai_error_ai_query_failed").format(e.message ?: ""))
        }
    }
}
