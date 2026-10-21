package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.utils.LogManager

/**
 * The kinds of AI provider the app knows (docs/AI.md, « Provider abstraction »): the code that speaks to
 * one API. The providers themselves are the user's, created of a kind and stored with it
 * (AIProviderConfigService).
 */
class AIProviderKinds(private val context: Context) {

    // A kind that fails to build is logged and left out; the others stay available
    private val kinds = listOfNotNull(
        build("Claude") { ClaudeProvider(context) },
        build("OpenAI") { OpenAIProvider(context) },
        build("DeepSeek") { DeepSeekProvider(context) },
        build("OpenAICompatible") { OpenAICompatibleProvider(context) }
    )

    private fun build(name: String, make: () -> AIProviderKind): AIProviderKind? = try {
        make()
    } catch (e: Exception) {
        LogManager.aiService("Failed to initialize the $name provider kind: ${e.message}", "ERROR", e)
        null
    }

    /** The kind named [kind], null when the app knows none of that name. */
    fun of(kind: String): AIProviderKind? = kinds.find { it.kind == kind }

    /** Every kind, in the order a new provider's kind is chosen. */
    fun all(): List<AIProviderKind> = kinds
}
