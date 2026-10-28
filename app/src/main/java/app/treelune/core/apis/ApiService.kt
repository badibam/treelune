package app.treelune.core.apis

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.database.AppDatabase
import app.treelune.core.secrets.SecretBox
import app.treelune.core.secrets.SecretSettings
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import app.treelune.core.validation.FieldLimits
import app.treelune.core.validation.SchemaValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The APIs the user declared (docs/CORE.md, « APIs »): the app calls them, never is called by
 * them. Each has a name unique among them and its config (ApiSettings), its key sealed.
 *
 * Operations:
 * - apis.list: each with its `id`, `name`, `url` and whether it answers later (`deferred`)
 * - .get (api_id): one, its config with its key opened
 * - .create (name, config), .update (api_id, name, config)
 * - .delete (api_id): refused while a Task calls it (InUse)
 * - .call (api_id, inputs): the call made. An immediate API answers `values` by name; a deferred
 *   one, the `job_id` to follow with .status. Both with `raw`, the answer's start. A failure
 *   says why, with `raw` when an answer came.
 * - .status (api_id, job_id): `status` (pending, ready, failed), and when ready its `values`
 * - .try (config, inputs): a call with a config not saved, for « Try »: the `code`, the `raw`
 *   answer whole, what each value's path gives (`values`: `name`, `value` or `error`), and for a
 *   deferred API the `job_id` its path gives. Writes nothing.
 */
class ApiService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val dao get() = AppDatabase.getDatabase(context).apiDao()
    // Every write of an API goes through the history's one door, its key never copied
    private val writes by lazy { app.treelune.core.history.Writes(context) }

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult = withContext(Dispatchers.IO) {
        try {
            when (operation) {
                "list" -> OperationResult.success(mapOf("apis" to dao.getAll().map { summary(it) }))
                "configs" -> configs()
                "get" -> get(params)
                "create" -> create(params)
                "update" -> update(params)
                "delete" -> delete(params)
                "call" -> call(params)
                "status" -> status(params)
                "try" -> tryCall(params)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: ApiCall.Failure) {
            OperationResult.error(failure(e))
        } catch (e: Exception) {
            LogManager.service("ApiService.$operation failed: ${e.message}", "ERROR", e)
            OperationResult.error(s.shared("api_error").format(e.message ?: ""))
        }
    }

    private fun summary(api: ApiEntity): Map<String, Any> {
        val config = JSONObject(api.configJson)
        return mapOf("id" to api.id, "name" to api.name, "url" to config.optString(ApiSettings.URL),
            "deferred" to (config.optString(ApiSettings.RESPONSE) == ApiSettings.DEFERRED),
            "key_missing" to keyMissing(config))
    }

    /** Whether [config] says where a key goes and holds none: an API the AI declared, its key left to the user. */
    private fun keyMissing(config: JSONObject): Boolean =
        config.optString(ApiSettings.KEY_IN, ApiSettings.KEY_NONE) != ApiSettings.KEY_NONE && config.optString(ApiSettings.KEY).isBlank()

    /**
     * Every API as the AI reads it (APIS): its whole config but its key, which never leaves the
     * phone, and for one that takes a key whether it is entered. Nothing is opened: a key sealed
     * on another phone counts as entered, its call saying it does not open.
     */
    private suspend fun configs(): OperationResult = OperationResult.success(mapOf("apis" to dao.getAll().map { api ->
        val config = JSONObject(api.configJson)
        val takesKey = config.optString(ApiSettings.KEY_IN, ApiSettings.KEY_NONE) != ApiSettings.KEY_NONE
        config.remove(ApiSettings.KEY)
        buildMap {
            put("id", api.id)
            put("name", api.name)
            put("config", JsonUtils.toMap(config))
            if (takesKey) put("key_entered", !keyMissing(JSONObject(api.configJson)))
        }
    }))

    private suspend fun get(params: JSONObject): OperationResult {
        val api = stored(params) ?: return notFound(params)
        val opened = open(api)
        return OperationResult.success(summary(api) + mapOf(
            "config" to JsonUtils.toMap(opened.settings),
            SecretSettings.UNREADABLE to opened.unreadable
        ))
    }

    private suspend fun create(params: JSONObject): OperationResult {
        val name = checkedName(params, null).let { (name, refusal) -> refusal?.let { return OperationResult.error(it) }; name!! }
        val config = checkedConfig(params).let { (config, refusal) -> refusal?.let { return OperationResult.error(it) }; config!! }
        // The app's own demo gives its ids; every other caller gets one made here
        val id = when (val given = app.treelune.core.coordinator.GivenId.read(params)) {
            app.treelune.core.coordinator.GivenId.Read.None -> java.util.UUID.randomUUID().toString()
            is app.treelune.core.coordinator.GivenId.Read.Accepted -> given.id
            is app.treelune.core.coordinator.GivenId.Read.Refused -> return OperationResult.error(s.shared("service_error_id_not_given").format(given.id))
        }
        val now = System.currentTimeMillis()
        writes.insertApi(ApiEntity(id, name, config, now, now))
        LogManager.service("API $id created: \"$name\"", "INFO")
        return OperationResult.success(mapOf("api_id" to id))
    }

    private suspend fun update(params: JSONObject): OperationResult {
        val api = stored(params) ?: return notFound(params)
        val name = checkedName(params, api.id).let { (name, refusal) -> refusal?.let { return OperationResult.error(it) }; name!! }
        val checked = checkedConfig(params).let { (config, refusal) -> refusal?.let { return OperationResult.error(it) }; config!! }
        val config = if (params.optBoolean(KEEP_KEY)) keptKey(JSONObject(api.configJson), JSONObject(checked)).toString() else checked
        writes.updateApi(api.copy(name = name, configJson = config, updatedAt = System.currentTimeMillis()))
        LogManager.service("API ${api.id} updated", "INFO")
        return OperationResult.success(mapOf("api_id" to api.id))
    }

    /**
     * [given], a config written without its key (UPDATE_API), with the key [stored] holds, still
     * sealed — unless [given] changes where it goes: an address, the status address, the header
     * or the parameter that carries it. The key is then dropped, for the user to enter again: no
     * change the AI writes, by mistake or because a text it read asked for it, sends a key to a
     * server it was not entered for.
     */
    private fun keptKey(stored: JSONObject, given: JSONObject): JSONObject {
        val key = stored.optString(ApiSettings.KEY).ifBlank { return given }
        if (keyPlace(stored) != keyPlace(given)) return given
        return JSONObject(given.toString()).put(ApiSettings.KEY, key)
    }

    /**
     * Where [config] sends its key: its addresses, and the header or the parameter that carries
     * it. Read through the declaration, a setting left out reading as its default; a setting of a
     * variant not chosen does not exist, and is not read.
     */
    private fun keyPlace(config: JSONObject): List<String?> {
        val values = ApiSettings.read(config, context)
        val keyIn = values.string(ApiSettings.KEY_IN)
        return listOf(
            values.string(ApiSettings.URL),
            if (values.string(ApiSettings.RESPONSE) == ApiSettings.DEFERRED) values.string(ApiSettings.STATUS_URL) else null,
            keyIn
        ) + when (keyIn) {
            ApiSettings.KEY_HEADER -> listOf(values.string(ApiSettings.KEY_HEADER_NAME))
            ApiSettings.KEY_QUERY -> listOf(values.string(ApiSettings.KEY_PARAM))
            else -> emptyList()
        }
    }

    private suspend fun delete(params: JSONObject): OperationResult {
        val api = stored(params) ?: return notFound(params)
        app.treelune.core.services.InUse.api(AppDatabase.getDatabase(context), api.id).refusal(api.name, s)?.let { return OperationResult.error(it) }
        writes.deleteApi(api.id)
        LogManager.service("API ${api.id} deleted", "INFO")
        return OperationResult.success(mapOf("api_id" to api.id, "deleted" to true))
    }

    private suspend fun call(params: JSONObject): OperationResult {
        val api = stored(params) ?: return notFound(params)
        val call = callOf(api) ?: return unreadableKey(api)
        val reply = call.start(inputs(params))
        LogManager.service("API ${api.id} called: ${reply.code}", "INFO")
        return try {
            if (call.deferred) OperationResult.success(mapOf("job_id" to call.jobId(reply), "raw" to raw(reply)))
            else OperationResult.success(mapOf("values" to call.read(reply), "raw" to raw(reply)))
        } catch (e: ApiCall.Failure) {
            OperationResult.error(failure(e), mapOf("raw" to raw(reply)))
        }
    }

    private suspend fun status(params: JSONObject): OperationResult {
        val api = stored(params) ?: return notFound(params)
        val call = callOf(api) ?: return unreadableKey(api)
        if (!call.deferred) return OperationResult.error(s.shared("api_error_not_deferred").format(api.name))
        val job = params.optString("job_id").ifEmpty { return OperationResult.error(s.shared("api_error_job_required")) }
        val reply = call.status(job)
        return try {
            when (call.status(reply)) {
                ApiCall.Status.PENDING -> OperationResult.success(mapOf("status" to "pending", "raw" to raw(reply)))
                ApiCall.Status.FAILED -> OperationResult.success(mapOf("status" to "failed", "raw" to raw(reply)))
                ApiCall.Status.READY -> OperationResult.success(mapOf("status" to "ready", "values" to call.read(reply), "raw" to raw(reply)))
            }
        } catch (e: ApiCall.Failure) {
            OperationResult.error(failure(e), mapOf("raw" to raw(reply)))
        }
    }

    private suspend fun tryCall(params: JSONObject): OperationResult {
        val config = params.optJSONObject("config")?.let { app.treelune.core.utils.JsonNulls.withoutNullKeys(it) }
            ?: return OperationResult.error(s.shared("ai_error_param_config_required"))
        refusal(config)?.let { return OperationResult.error(it) }
        val call = ApiCall(config, ApiSettings.read(config, context))
        val reply = call.start(inputs(params))
        val values = call.tryRead(reply).map { (name, read) ->
            read.fold({ mapOf("name" to name, "value" to it) }, { mapOf("name" to name, "error" to ((it as? ApiCall.Failure)?.let { f -> failure(f) } ?: it.message.orEmpty())) })
        }
        val job = if (call.deferred) runCatching { call.jobId(reply) }.getOrNull() else null
        return OperationResult.success(buildMap {
            put("code", reply.code)
            put("raw", reply.body)
            put("values", values)
            job?.let { put("job_id", it) }
        })
    }

    /**
     * The request that downloads [address], a file the answer of the API [apiId] gave
     * (ApiCall.fileRequest), for a Task's step: not an operation, the run doing the download.
     * Throws, said in words, when the API is gone or its key does not open on this phone.
     */
    suspend fun fileRequest(apiId: String, address: String): okhttp3.Request = withContext(Dispatchers.IO) {
        val api = dao.get(apiId) ?: throw IllegalStateException(s.shared("api_error_not_found").format(apiId))
        val call = callOf(api) ?: throw IllegalStateException(s.shared("api_error_key_unreadable").format(api.name))
        try {
            call.fileRequest(address)
        } catch (e: ApiCall.Failure) {
            throw IllegalStateException(failure(e))
        }
    }

    // ========================================================================================

    private suspend fun stored(params: JSONObject): ApiEntity? = params.optString("api_id").ifEmpty { null }?.let { dao.get(it) }

    private fun notFound(params: JSONObject): OperationResult =
        if (params.optString("api_id").isEmpty()) OperationResult.error(s.shared("api_error_id_required"))
        else OperationResult.error(s.shared("api_error_not_found").format(params.optString("api_id")))

    private fun open(api: ApiEntity): SecretSettings.Opened =
        SecretSettings.open(SecretSettings.names(ApiSettings.nodes(context)), JSONObject(api.configJson), SecretBox.of())

    /** The call [api] makes, its key opened; null when its key does not open on this phone. */
    private fun callOf(api: ApiEntity): ApiCall? {
        val opened = open(api)
        if (opened.unreadable.isNotEmpty()) return null
        return ApiCall(opened.settings, ApiSettings.read(opened.settings, context))
    }

    private fun unreadableKey(api: ApiEntity) = OperationResult.error(s.shared("api_error_key_unreadable").format(api.name))

    @Suppress("UNCHECKED_CAST")
    private fun inputs(params: JSONObject): Map<String, Any?> =
        params.optJSONObject("inputs")?.let { JsonUtils.toMap(it) }.orEmpty()

    /** The start of [reply]'s body, what a run keeps of it. */
    private fun raw(reply: ApiCall.Reply): String = reply.body.take(ApiCall.RAW_KEPT)

    private fun failure(e: ApiCall.Failure): String = s.shared(e.key).format(*e.args)

    private suspend fun checkedName(params: JSONObject, ownId: String?): Pair<String?, String?> {
        val name = params.optString("name").trim()
        if (name.isEmpty()) return null to s.shared("api_error_name_required")
        if (name.length > FieldLimits.SHORT_LENGTH) return null to s.shared("api_error_name_too_long").format(FieldLimits.SHORT_LENGTH)
        if (dao.getAll().any { it.id != ownId && it.name.equals(name, ignoreCase = true) }) return null to s.shared("api_error_name_taken").format(name)
        return name to null
    }

    /** Why [config] is refused: its schema, then what the schema cannot say; null when it holds. */
    private fun refusal(config: JSONObject): String? {
        val validation = SchemaValidator.validate(ApiSettings.schema(context), JsonUtils.toMap(config), context)
        if (!validation.isValid) return validation.errorMessage ?: s.shared("message_validation_error_simple")
        return ApiSettings.configError(config, context)
    }

    /** The config given, checked, its key sealed; or why it is refused. */
    private fun checkedConfig(params: JSONObject): Pair<String?, String?> {
        val config = params.optJSONObject("config")?.let { app.treelune.core.utils.JsonNulls.withoutNullKeys(it) }
            ?: return null to s.shared("ai_error_param_config_required")
        refusal(config)?.let { LogManager.service("API config refused: $it", "WARN"); return null to it }
        return SecretSettings.seal(SecretSettings.names(ApiSettings.nodes(context)), config, SecretBox.of()).toString() to null
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        val name = params.optString("api_id").ifEmpty { null }?.let { dao.get(it)?.name } ?: "?"
        return when (operation) {
            "call" -> s.shared("api_verbalize_call").format(name)
            "create" -> s.shared("api_verbalize_create").format(params.optString("name"))
            "update" -> s.shared("api_verbalize_update").format(name)
            "delete" -> s.shared("api_verbalize_delete").format(name)
            "configs" -> s.shared("api_verbalize_list")
            else -> s.shared("action_verbalize_unknown")
        }
    }

    companion object {
        /** The param of `update` that keeps the stored key, the config given without it (UPDATE_API, keptKey). */
        const val KEEP_KEY = "keep_key"
    }
}
