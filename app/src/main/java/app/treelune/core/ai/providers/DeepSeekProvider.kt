package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.ai.data.PromptData
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.strings.Strings
import org.json.JSONObject

/**
 * The DeepSeek kind: DeepSeek through its Anthropic-compatible endpoint, so all of it is
 * ClaudeProviderCore with MessagesApi.DEEPSEEK (endpoint, model listing, effort levels).
 *
 * Configuration: the API key, the model (e.g. "deepseek-v4-pro"), the effort, required (low, high,
 * max), and the longest answer, reasoning included.
 */
class DeepSeekProvider(private val context: Context) : AIProviderKind {

    private val core = ClaudeProviderCore(context, KIND, MessagesApi.DEEPSEEK)

    override val kind: String = KIND

    override fun kindName(context: Context): String = Strings.`for`(context = context).shared("ai_provider_kind_deepseek")

    override fun kindDescription(context: Context): String = Strings.`for`(context = context).shared("ai_provider_kind_deepseek_description")

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    override fun factsProvider(): String = core.api.factsProvider

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override fun configError(config: JSONObject, context: Context): String? = core.configError(config, context)

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config.getString("api_key"))

    override suspend fun query(promptData: PromptData, config: String): AIResponse = core.query(promptData, config)

    companion object {
        const val KIND = "deepseek"
    }
}
