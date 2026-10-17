package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.fields.settings.SettingValues
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import org.json.JSONObject

/**
 * The config of an AI provider, declared with the fields by its kind (AIProviderKind.getConfigSettings): the
 * schema AIProviderConfigService checks every write against, and the single way code reads it.
 */
object AIProviderSettings {

    /** The schema a config of a provider of [provider]'s kind is held to. */
    fun schema(provider: AIProviderKind, context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "ai_provider_${provider.kind}_config",
            displayName = provider.kindName(context),
            description = provider.getConfigHelp(context),
            category = SchemaCategory.AI_PROVIDER,
            content = SettingsSchemaGenerator.generate(provider.getConfigSettings(context), s::shared).toString()
        )
    }

    /** [config] of [provider], read through its declaration (SettingValues). */
    fun read(provider: AIProviderKind, config: JSONObject, context: Context): SettingValues =
        SettingValues(provider.getConfigSettings(context), config)
}
