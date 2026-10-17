package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.ai.data.PromptData
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.strings.Strings
import org.json.JSONObject

/**
 * The Claude kind: Anthropic's Messages API, all of it in ClaudeProviderCore.
 *
 * Configuration: the API key (secret), the model chosen among those the key lists, the longest
 * answer and the reasoning the model allows.
 */
class ClaudeProvider(private val context: Context) : AIProviderKind {

    private val core = ClaudeProviderCore(context, KIND, MessagesApi.ANTHROPIC)

    override val kind: String = KIND

    override fun kindName(context: Context): String = Strings.`for`(context = context).shared("ai_provider_kind_claude")

    override fun kindDescription(context: Context): String = Strings.`for`(context = context).shared("ai_provider_kind_claude_description")

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    override fun factsProvider(): String = core.api.factsProvider

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override fun configError(config: JSONObject, context: Context): String? = core.configError(config, context)

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config.getString("api_key"))

    override suspend fun query(promptData: PromptData, config: String): AIResponse = core.query(promptData, config)

    companion object {
        const val KIND = "claude"
    }
}
