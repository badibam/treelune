package app.treelune.core.ai.providers

import org.json.JSONObject

import app.treelune.core.utils.JsonUtils
import android.content.Context
import app.treelune.core.ai.data.*
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Client - pure logic for interfacing with AI providers
 *
 * Responsibilities:
 * - Convert PromptResult to provider-specific format
 * - Handle provider errors (NO FALLBACKS without explicit configuration)
 * - Uses AIProviderConfigService for configurations (no direct DB access), calling the provider
 *   a session names, never another (ProviderVerifier)
 */
class AIClient(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val kinds = AIProviderKinds(context)
    private val s = Strings.`for`(context = context)

    /**
     * Send prompt data to AI provider and return raw response
     * Provider transforms PromptData to its specific format and handles prompt construction
     *
     * @param promptData The raw prompt data (L1-L3 + messages)
     * @param providerId The provider the session calls, checked before (ProviderVerifier)
     * @param reasoning The conversation's own reasoning, sent in place of the config's; checked
     *   before by the caller ([reasoningRefusal]); null, the config's
     * @return AIResponse with raw JSON string (not parsed)
     */
    suspend fun query(promptData: PromptData, providerId: String, reasoning: SessionReasoning? = null): AIResponse {
        LogManager.aiService("AIClient.query() called with PromptData: ${promptData.sessionMessages.size} messages, providerId: $providerId", "DEBUG")

        return withContext(Dispatchers.IO) {
            try {
                val (provider, config, data) = stored(providerId) ?: return@withContext AIResponse(
                    success = false,
                    content = "",
                    errorMessage = s.shared("ai_error_provider_not_found").format(providerId),
                    failure = AIFailure.CONFIG
                ).also { LogManager.aiService("Provider not found: $providerId", "ERROR") }
                val name = data["name"] as String
                LogManager.aiService("Using provider $providerId (${provider.kind})", "DEBUG")

                // A key sealed on another phone: said as such, never sent as an empty key
                if ((data[app.treelune.core.secrets.SecretSettings.UNREADABLE] as? List<*>).orEmpty().isNotEmpty()) {
                    LogManager.aiService("Provider secret unreadable on this phone: $providerId", "ERROR")
                    return@withContext AIResponse(
                        success = false,
                        content = "",
                        errorMessage = s.shared("ai_error_provider_secret_unreadable").format(name),
                        failure = AIFailure.CONFIG
                    )
                }

                // A history with images goes whole or not at all: refused, and said, when the
                // model does not read them, when nothing says it does, when there are too many,
                // or when an image's file is missing; never sent without them
                val images = promptData.sessionMessages.flatMap { it.promptParts.orEmpty() }
                    .filterIsInstance<app.treelune.core.ai.data.PromptPart.Image>()
                val imageRefusal = ImageInput.refusal(images.size, provider.readsImages(config, context)) { s.shared(it) }
                    ?: images.firstOrNull { !app.treelune.core.files.Files.sessionFile(context, it.imageId, it.format).exists() }
                        ?.let { s.shared("ai_image_error_missing") }
                if (imageRefusal != null) {
                    LogManager.aiService("Request with ${images.size} images refused: $imageRefusal", "WARN")
                    return@withContext AIResponse(success = false, content = "", errorMessage = imageRefusal, failure = AIFailure.CONFIG)
                }

                val sentConfig = (reasoning?.applyTo(JSONObject(config.toString())) ?: config).toString()
                val aiResponse = provider.query(promptData, sentConfig)
                LogManager.aiService("Received response from provider $providerId: success=${aiResponse.success}", "DEBUG")
                aiResponse

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogManager.aiService("AIClient query failed: ${e.message}", "ERROR", e)
                AIResponse(
                    success = false,
                    content = "",
                    errorMessage = s.shared("ai_error_ai_query_failed").format(e.message ?: ""),
                    failure = aiFailureOf(e)
                )
            }
        }
    }

    /**
     * Why [reasoning] cannot go with the model of [providerId], in the kind's words
     * (AIProviderKind.configError on its config with [reasoning] in place), or null when it can.
     * Null too without a provider to ask: a call then fails on that.
     */
    suspend fun reasoningRefusal(reasoning: SessionReasoning, providerId: String): String? {
        val (provider, config) = stored(providerId) ?: return null
        return provider.configError(reasoning.applyTo(config), context)
    }

    /**
     * What the model of [providerId] lets a conversation set of its reasoning, its provider listing
     * it (a network call, as the provider's config screen).
     */
    suspend fun modelReasoning(providerId: String): ModelReasoning {
        val (provider, config, data) = stored(providerId)
            ?: return ModelReasoning(null, null, null, s.shared("ai_session_provider_gone"))
        val ownSetting = SessionReasoning(config.optBoolean(SessionReasoning.KEY_THINKING_OFF), config.optString(SessionReasoning.KEY_EFFORT).ifBlank { null })
        val model = config.optString("model").ifBlank { null }
            ?: return ModelReasoning(null, null, ownSetting, s.shared("ai_error_provider_not_configured").format(data["name"]))
        // A key sealed on another phone, left out of the config: said as such, as a call says it
        if ((data[app.treelune.core.secrets.SecretSettings.UNREADABLE] as? List<*>).orEmpty().isNotEmpty())
            return ModelReasoning(model, null, ownSetting, s.shared("ai_error_provider_secret_unreadable").format(data["name"]))
        // What listing the models needs (its key), missing: the kind would not list them
        if (provider.modelListingSettings().any { config.optString(it).isBlank() })
            return ModelReasoning(model, null, ownSetting, s.shared("ai_error_provider_not_configured").format(data["name"]))
        val listed = provider.listModels(config)
        listed.error?.let { return ModelReasoning(model, null, ownSetting, s.shared("ai_provider_fetch_error").format(it)) }
        return ModelReasoning(model, listed.models.find { it.id == model }?.reasoning, ownSetting, null)
    }

    /** The kind of provider [providerId], its stored config, and what the service says of it; null when it is gone. */
    private suspend fun stored(providerId: String): Triple<AIProviderKind, JSONObject, Map<String, Any?>>? {
        val result = coordinator.processUserAction("ai_provider_config.get", mapOf("provider_id" to providerId))
        if (!result.isSuccess) return null
        val data = result.data ?: return null
        val provider = kinds.of(data["kind"] as String) ?: return null
        @Suppress("UNCHECKED_CAST")
        val config = (data["config"] as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) } ?: return null
        return Triple(provider, config, data)
    }
}

/**
 * What a conversation may set of its model's reasoning: the model, what it offers (null: nothing,
 * or not listed), the provider's own setting (null without a config), and why it could not be known.
 */
data class ModelReasoning(val model: String?, val reasoning: Reasoning?, val providerSetting: SessionReasoning?, val error: String?)

/**
 * AI provider response structure
 */
data class AIResponse(
    val success: Boolean,
    val content: String,
    val errorMessage: String? = null,
    // Why the call failed, when it did. Null on success. Read by AIEventProcessor to decide
    // between waiting for the network and stopping the session.
    val failure: AIFailure? = null,
    val tokensUsed: Int = 0,
    // Cache metrics (generic, supported by multiple providers, 0 if not supported)
    val cacheWriteTokens: Int = 0,  // Cache write/creation tokens (Claude, OpenAI, etc.)
    val cacheReadTokens: Int = 0,   // Cache read tokens (Claude, OpenAI, etc.)
    val inputTokens: Int = 0
)