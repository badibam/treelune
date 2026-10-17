package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.ai.data.PromptData
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.strings.Strings
import org.json.JSONObject

/**
 * The OpenAI kind: OpenAI's Responses API, all of it in OpenAIProviderCore.
 *
 * Configuration: the API key, the model (e.g. "gpt-5.5"), the temperature (0.0-2.0, 1.0 by
 * default), the longest answer and the reasoning the model allows.
 */
class OpenAIProvider(private val context: Context) : AIProviderKind {

    private val core = OpenAIProviderCore(context, KIND)

    override val kind: String = KIND

    override fun kindName(context: Context): String = Strings.`for`(context = context).shared("ai_provider_kind_openai")

    override fun kindDescription(context: Context): String = Strings.`for`(context = context).shared("ai_provider_kind_openai_description")

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    override fun factsProvider(): String = OpenAIProviderCore.FACTS_PROVIDER

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override fun configError(config: JSONObject, context: Context): String? = core.configError(config, context)

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config.getString("api_key"))

    override suspend fun query(promptData: PromptData, config: String): AIResponse = core.query(promptData, config)

    companion object {
        const val KIND = "openai"
    }
}
