package app.treelune.core.ai.services

import app.treelune.core.utils.JsonUtils
import android.content.Context
import app.treelune.core.ai.database.AIProviderConfigEntity
import app.treelune.core.ai.providers.AIProviderKind
import app.treelune.core.ai.providers.AIProviderKinds
import app.treelune.core.ai.providers.AIProviderSettings
import app.treelune.core.secrets.SecretBox
import app.treelune.core.secrets.SecretSettings
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.database.AppDatabase
import app.treelune.core.validation.FieldLimits
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import app.treelune.core.validation.SchemaValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The AI providers the user created (docs/AI.md, « Provider abstraction »): each of a kind
 * (AIProviderKinds), a name unique among them, and its config, checked against the schema its
 * kind declares, its secrets sealed. At most one is the default, which a new conversation takes:
 * the first one created becomes it.
 *
 * Operations:
 * - ai_provider_config.kinds: the kinds a provider can be created of
 * - .list: the providers, each with its kind, name, model and whether it is the default
 * - .get (provider_id): one, its config with its secrets opened
 * - .create (kind, name, config), .update (provider_id, name, config)
 * - .delete (provider_id, new_default_id when it is the default and others remain): refused while a routine names it
 * - .set_default (provider_id), .get_default
 */
class AIProviderConfigService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val kinds = AIProviderKinds(context)
    private val dao get() = AppDatabase.getDatabase(context).aiDao()

    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        LogManager.aiService("AIProviderConfigService.execute() called: $operation")

        return withContext(Dispatchers.IO) {
            try {
                when (operation) {
                    "kinds" -> listKinds()
                    "list" -> listProviders()
                    "get" -> getProvider(params)
                    "create" -> createProvider(params)
                    "update" -> updateProvider(params)
                    "delete" -> deleteProvider(params)
                    "set_default" -> setDefault(params)
                    "get_default" -> getDefault()
                    else -> {
                        LogManager.aiService("Unknown operation: $operation", "ERROR")
                        OperationResult.error("Unknown operation: $operation")
                    }
                }
            } catch (e: Exception) {
                LogManager.aiService("AIProviderConfigService error: ${e.message}", "ERROR", e)
                OperationResult.error(s.shared("ai_error_provider_config").format(e.message ?: ""))
            }
        }
    }

    // ========================================================================================
    // Reading
    // ========================================================================================

    private fun listKinds(): OperationResult = OperationResult.success(mapOf(
        "kinds" to kinds.all().map { mapOf("kind" to it.kind, "name" to it.kindName(context), "description" to it.kindDescription(context)) }
    ))

    private suspend fun listProviders(): OperationResult = OperationResult.success(mapOf(
        "providers" to dao.getAllProviderConfigs().map { summary(it) }
    ))

    /** A provider as a list shows it: its model read from its config, its kind named. */
    private fun summary(entity: AIProviderConfigEntity): Map<String, Any?> = mapOf(
        "id" to entity.id,
        "kind" to entity.kind,
        "kind_name" to kinds.of(entity.kind)?.kindName(context),
        "name" to entity.name,
        "model" to JSONObject(entity.configJson).optString("model").ifEmpty { null },
        "is_default" to entity.isDefault
    )

    private suspend fun getProvider(params: JSONObject): OperationResult {
        val entity = stored(params) ?: return notFound(params)
        val kind = kinds.of(entity.kind) ?: return unknownKind(entity.kind)
        // Its secrets opened; one that does not open on this phone is named, for the caller to say so
        val opened = SecretSettings.open(SecretSettings.names(kind.getConfigSettings(context)), JSONObject(entity.configJson), SecretBox.of())
        // A model not chosen yet is absent, as a result holds no null at its top
        val found = summary(entity) + mapOf(
            "config" to JsonUtils.toMap(opened.settings),
            SecretSettings.UNREADABLE to opened.unreadable,
            "created_at" to entity.createdAt,
            "updated_at" to entity.updatedAt
        )
        return OperationResult.success(buildMap { found.forEach { (key, value) -> if (value != null) put(key, value) } })
    }

    private suspend fun getDefault(): OperationResult {
        val entity = dao.getDefaultProviderConfig() ?: return OperationResult.success(mapOf("has_default" to false))
        return OperationResult.success(mapOf("has_default" to true, "provider_id" to entity.id, "name" to entity.name))
    }

    // ========================================================================================
    // Writing
    // ========================================================================================

    private suspend fun createProvider(params: JSONObject): OperationResult {
        val kindName = params.optString("kind").ifEmpty { return OperationResult.error(s.shared("ai_error_param_kind_required")) }
        val kind = kinds.of(kindName) ?: return unknownKind(kindName)
        val name = nameRefusal(params, null).let { (name, refusal) -> refusal?.let { return OperationResult.error(it) }; name!! }
        val config = checkedConfig(kind, params).let { (config, refusal) -> refusal?.let { return OperationResult.error(it) }; config!! }

        val now = System.currentTimeMillis()
        val id = java.util.UUID.randomUUID().toString()
        // The first provider is the default: a conversation then has one to take
        val isDefault = dao.getDefaultProviderConfig() == null
        dao.insertProviderConfig(AIProviderConfigEntity(id, kind.kind, name, config, isDefault, now, now))
        LogManager.aiService("Provider $id created: ${kind.kind}, \"$name\"${if (isDefault) ", the default" else ""}", "INFO")
        return OperationResult.success(mapOf("provider_id" to id, "is_default" to isDefault))
    }

    private suspend fun updateProvider(params: JSONObject): OperationResult {
        val entity = stored(params) ?: return notFound(params)
        val kind = kinds.of(entity.kind) ?: return unknownKind(entity.kind)
        val name = nameRefusal(params, entity.id).let { (name, refusal) -> refusal?.let { return OperationResult.error(it) }; name!! }
        val config = checkedConfig(kind, params).let { (config, refusal) -> refusal?.let { return OperationResult.error(it) }; config!! }
        val now = System.currentTimeMillis()
        dao.updateProviderConfig(entity.copy(name = name, configJson = config, updatedAt = now))
        LogManager.aiService("Provider ${entity.id} updated", "INFO")
        return OperationResult.success(mapOf("provider_id" to entity.id, "updated_at" to now))
    }

    /**
     * Deleted unless a routine names it, the refusal naming them. The default deleted while others
     * remain needs the one that becomes it (new_default_id); the last one deleted leaves none.
     */
    private suspend fun deleteProvider(params: JSONObject): OperationResult {
        val entity = stored(params) ?: return notFound(params)
        val database = AppDatabase.getDatabase(context)
        app.treelune.core.services.InUse.provider(database, entity.id).refusal(entity.name, s)?.let { refusal ->
            LogManager.aiService("Provider ${entity.id} still used: $refusal", "INFO")
            return OperationResult.error(refusal)
        }
        val others = dao.getAllProviderConfigs().filter { it.id != entity.id }
        val newDefault = params.optString("new_default_id").ifEmpty { null }
        if (entity.isDefault && others.isNotEmpty()) {
            newDefault ?: return OperationResult.error(s.shared("ai_error_provider_new_default_required").format(entity.name))
            if (others.none { it.id == newDefault }) return OperationResult.error(s.shared("ai_error_provider_not_found").format(newDefault))
        }
        dao.deleteProviderConfigById(entity.id)
        if (entity.isDefault && newDefault != null) dao.setDefaultProvider(newDefault)
        LogManager.aiService("Provider ${entity.id} deleted${if (entity.isDefault) ", default now ${newDefault ?: "none"}" else ""}", "INFO")
        return OperationResult.success(mapOf("provider_id" to entity.id, "deleted" to true))
    }

    private suspend fun setDefault(params: JSONObject): OperationResult {
        val entity = stored(params) ?: return notFound(params)
        dao.setDefaultProvider(entity.id)
        LogManager.aiService("Provider ${entity.id} now the default", "INFO")
        return OperationResult.success(mapOf("provider_id" to entity.id))
    }

    // ========================================================================================
    // Checks
    // ========================================================================================

    private suspend fun stored(params: JSONObject): AIProviderConfigEntity? =
        params.optString("provider_id").ifEmpty { null }?.let { dao.getProviderConfig(it) }

    private fun notFound(params: JSONObject): OperationResult =
        if (params.optString("provider_id").isEmpty()) OperationResult.error(s.shared("ai_error_param_provider_id_required"))
        else OperationResult.error(s.shared("ai_error_provider_not_found").format(params.optString("provider_id")))

    private fun unknownKind(kind: String): OperationResult = OperationResult.error(s.shared("ai_error_unknown_provider").format(kind))

    /** The name given, trimmed, or why it is refused: missing, too long, or another provider's ([ownId] aside). */
    private suspend fun nameRefusal(params: JSONObject, ownId: String?): Pair<String?, String?> {
        val name = params.optString("name").trim()
        if (name.isEmpty()) return null to s.shared("ai_error_provider_name_required")
        if (name.length > FieldLimits.SHORT_LENGTH) return null to s.shared("ai_error_provider_name_too_long").format(FieldLimits.SHORT_LENGTH)
        if (dao.getAllProviderConfigs().any { it.id != ownId && it.name.equals(name, ignoreCase = true) }) {
            return null to s.shared("ai_error_provider_name_taken").format(name)
        }
        return name to null
    }

    /**
     * The config given, checked against the schema [kind] declares and its own rules, its secrets
     * sealed, or why it is refused.
     */
    private fun checkedConfig(kind: AIProviderKind, params: JSONObject): Pair<String?, String?> {
        // Written whole: a key set to null is a setting not given, taken out
        val config = params.optJSONObject("config")?.let { app.treelune.core.utils.JsonNulls.withoutNullKeys(it) }
            ?: return null to s.shared("ai_error_param_config_required")
        val validation = SchemaValidator.validate(AIProviderSettings.schema(kind, context), JsonUtils.toMap(config), context)
        if (!validation.isValid) {
            LogManager.aiService("Provider config validation failed: ${validation.errorMessage}", "WARN")
            return null to (validation.errorMessage ?: s.shared("message_validation_error_simple"))
        }
        // What the schema cannot say: a rule of the kind's own (an address's form)
        kind.configError(config, context)?.let { error ->
            LogManager.aiService("Provider config refused: $error", "WARN")
            return null to error
        }
        // The database never holds an API key in clear
        return SecretSettings.seal(SecretSettings.names(kind.getConfigSettings(context)), config, SecretBox.of()).toString() to null
    }

    /** AI provider configuration is not exposed to AI actions. */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return s.shared("action_verbalize_unknown")
    }
}
