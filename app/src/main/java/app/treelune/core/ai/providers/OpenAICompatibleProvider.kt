package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.ai.data.PromptData
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.strings.Strings
import org.json.JSONObject

/**
 * The Other server kind: any server speaking Chat Completions at an https address of the user's,
 * Ollama, llama.cpp, vLLM, LM Studio, OpenRouter. All of it is OpenAICompatibleProviderCore.
 */
class OpenAICompatibleProvider(private val context: Context) : AIProviderKind {

    private val core = OpenAICompatibleProviderCore(context, KIND)

    override val kind: String = KIND

    override fun kindName(context: Context): String = Strings.`for`(context = context).shared("ai_provider_kind_compatible")

    override fun kindDescription(context: Context): String = Strings.`for`(context = context).shared("ai_provider_kind_compatible_description")

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    // A server of the user's: no fact names its models, only its own list may say
    override fun factsProvider(): String? = null

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    // The address is the user's, so listing needs it; the key only if the server asks for one
    override fun modelListingSettings(): List<String> = listOf("base_url")

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config)

    override fun configError(config: JSONObject, context: Context): String? = core.configError(config, context)

    override suspend fun query(promptData: PromptData, config: String): AIResponse = core.query(promptData, config)

    /** Its own prices when its config sets them by hand, rather than LiteLLM's list's (OpenAICompatibleProviderCore.ownPrices). */
    override fun ownPrices(config: JSONObject, context: Context): ManualPrices? = core.ownPrices(config, context)

    companion object {
        const val KIND = "compatible"
    }
}
