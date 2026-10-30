package app.treelune.core.apis

import app.treelune.core.ai.providers.awaitReply
import app.treelune.core.fields.settings.SettingValues
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

/**
 * One call to an API, its config opened (its key in clear): the request its settings and the
 * inputs given make, and what is read from the answer. Knows nothing of the database nor of a
 * Task: ApiService calls it, and says the failures in words.
 */
class ApiCall(private val config: JSONObject, private val values: SettingValues) {

    /** What came back: the HTTP status and the body, whole. */
    data class Reply(val code: Int, val body: String) {
        val isSuccessful: Boolean get() = code in 200..299
    }

    /** Why a call did not give what it should, in a key of the strings and its arguments. */
    class Failure(val key: String, vararg val args: Any) : Exception(key)

    /**
     * The first call, with [inputs] by name: in the address for GET, a flat JSON object for POST.
     * An input the API does not declare is refused, as one declared of another type.
     */
    suspend fun start(inputs: Map<String, Any?>): Reply {
        val declared = list(ApiSettings.INPUTS).associate { it.getString(ApiSettings.NAME) to it.optString(ApiSettings.TYPE, ApiSettings.TEXT) }
        (inputs.keys - declared.keys).firstOrNull()?.let { throw Failure("api_error_input_unknown", it, declared.keys.joinToString(", ").ifEmpty { "-" }) }
        val given = inputs.filterValues { it != null }.mapValues { (name, value) ->
            // A list goes as it is, a JSON array of values or of objects
            if (declared.getValue(name) == ApiSettings.LIST) value as? List<*> ?: throw Failure("api_error_value_type", name, ApiSettings.LIST)
            else typed(name, value!!, declared.getValue(name))
        }
        val post = values.string(ApiSettings.METHOD) == ApiSettings.POST
        val url = address(values.string(ApiSettings.URL)!!, if (post) emptyMap() else given)
        val request = Request.Builder().url(url).withKey()
        if (post) request.post(JSONObject(given).toString().toRequestBody(JSON)) else request.get()
        return send(request.build())
    }

    /** The status of the job [id], for a deferred API: its status address, called with GET. */
    suspend fun status(id: String): Reply {
        val url = address(values.string(ApiSettings.STATUS_URL)!!.replace(ApiSettings.ID_MARK, java.net.URLEncoder.encode(id, "UTF-8")), emptyMap())
        return send(Request.Builder().url(url).withKey().get().build())
    }

    /**
     * The request that downloads a file this API's answer gave at [address]: with its key when the
     * file is on the API's own server (the scheme, host and port of its address or of its status
     * address), without it anywhere else, so that an answer never sends the key to another site.
     */
    fun fileRequest(address: String): Request {
        val url = address.toHttpUrlOrNull() ?: throw Failure("api_error_url", address)
        val own = listOfNotNull(values.string(ApiSettings.URL), values.string(ApiSettings.STATUS_URL)?.takeIf { deferred })
        if (!sameServer(url, own)) return Request.Builder().url(url).get().build()
        return Request.Builder().url(address(address, emptyMap())).withKey().get().build()
    }

    /** Whether the API answers later: its first answer is then an id to follow. */
    val deferred: Boolean get() = values.string(ApiSettings.RESPONSE) == ApiSettings.DEFERRED

    /** The id a deferred API's first answer gives, at its path. */
    fun jobId(reply: Reply): String {
        val found = at(parse(reply), values.string(ApiSettings.ID_PATH)!!) ?: throw Failure("api_error_path_absent", values.string(ApiSettings.ID_PATH)!!)
        if (found is JSONObject || found is JSONArray) throw Failure("api_error_id_form", values.string(ApiSettings.ID_PATH)!!)
        return found.toString()
    }

    /** Where a deferred job stands, from a status answer. */
    enum class Status { PENDING, READY, FAILED }

    fun status(reply: Reply): Status {
        val json = parse(reply)
        if (said(json, ApiSettings.READY_PATH, ApiSettings.READY_VALUE)) return Status.READY
        if (values.string(ApiSettings.FAILED_PATH) != null && said(json, ApiSettings.FAILED_PATH, ApiSettings.FAILED_VALUE)) return Status.FAILED
        return Status.PENDING
    }

    /** Whether the answer holds, at the path the setting [path] names, the value [value] names, compared as text. */
    private fun said(json: Any, path: String, value: String): Boolean =
        at(json, values.string(path)!!)?.let { it !is JSONObject && it !is JSONArray && it.toString() == values.string(value) } == true

    /**
     * The values the API names, read from [reply] at their paths, each of its type: a file is its
     * address. A path the answer does not hold, or a value not of its type, is a failure.
     */
    fun read(reply: Reply): Map<String, Any> {
        val json = parse(reply)
        return list(ApiSettings.VALUES).associate { value ->
            val name = value.getString(ApiSettings.NAME)
            val path = value.getString(ApiSettings.PATH)
            val found = at(json, path) ?: throw Failure("api_error_value_absent", name, path)
            name to valueOf(value, found)
        }
    }

    /**
     * What [found] is as the value [value] declares it: a list read element by element, each of
     * its fields at its path, one an element does not hold being null (an optional part: a
     * correction given or not); anything else as [typed] reads it.
     */
    private fun valueOf(value: JSONObject, found: Any): Any {
        val name = value.getString(ApiSettings.NAME)
        val type = value.optString(ApiSettings.TYPE, ApiSettings.TEXT)
        if (type != ApiSettings.LIST) return typed(name, found, type)
        val array = found as? JSONArray ?: throw Failure("api_error_value_type", name, type)
        val items = value.optJSONArray(ApiSettings.ITEMS)?.let { list -> (0 until list.length()).map { list.getJSONObject(it) } }.orEmpty()
        return (0 until array.length()).map { index ->
            val element = array.opt(index)
            items.associate { item ->
                val field = item.getString(ApiSettings.NAME)
                field to element?.let { at(it, item.getString(ApiSettings.PATH)) }?.let { typed("$name › $field", it, item.optString(ApiSettings.TYPE, ApiSettings.TEXT)) }
            }
        }
    }

    /**
     * What [reply] gives at each value's path, for « Try »: the value, or why there is none. Never
     * throws for a path: it is what « Try » is for.
     */
    fun tryRead(reply: Reply): List<Pair<String, Result<Any>>> {
        val json = runCatching { parse(reply) }
        return list(ApiSettings.VALUES).map { value ->
            val name = value.getString(ApiSettings.NAME)
            name to json.mapCatching { at(it, value.getString(ApiSettings.PATH)) ?: throw Failure("api_error_value_absent", name, value.getString(ApiSettings.PATH)) }
                .mapCatching { valueOf(value, it) }
        }
    }

    private fun list(name: String): List<JSONObject> =
        config.optJSONArray(name)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()

    /**
     * The key, for an API that says where it goes. One it expects and that was never entered (an
     * API the AI declared, which never gives a key) refuses the call before it leaves: sent
     * without it, the service's refusal would read as a failure of the service.
     */
    private fun key(): String =
        values.string(ApiSettings.KEY)?.takeIf { it.isNotBlank() } ?: throw Failure("api_error_key_missing")

    private fun Request.Builder.withKey(): Request.Builder {
        if (values.string(ApiSettings.KEY_IN) != ApiSettings.KEY_HEADER) return this
        return header(values.string(ApiSettings.KEY_HEADER_NAME)!!, values.string(ApiSettings.KEY_PREFIX).orEmpty() + key())
    }

    /** [base] with [query] added, and the key when it goes in the address. */
    private fun address(base: String, query: Map<String, Any>): HttpUrl {
        val url = base.toHttpUrlOrNull()?.newBuilder() ?: throw Failure("api_error_url", base)
        query.forEach { (name, value) -> url.addQueryParameter(name, value.toString()) }
        if (values.string(ApiSettings.KEY_IN) == ApiSettings.KEY_QUERY) url.addQueryParameter(values.string(ApiSettings.KEY_PARAM)!!, key())
        return url.build()
    }

    private suspend fun send(request: Request): Reply {
        val reply = try {
            CLIENT.awaitReply(request)
        } catch (e: java.io.IOException) {
            throw Failure("api_error_network", e.message ?: e.javaClass.simpleName)
        }
        return Reply(reply.code, reply.body)
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** The longest a call waits for its answer: a service at home may be slow, never this slow. */
        private val CLIENT = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(1, TimeUnit.MINUTES)
            .build()

        /** The most of a raw answer a run keeps to show, for a failure to be understood. */
        const val RAW_KEPT = 4_000

        /** [reply]'s body as JSON, or a failure saying the status when it is not a success. */
        fun parse(reply: Reply): Any {
            if (!reply.isSuccessful) throw Failure("api_error_status", reply.code)
            return try {
                JSONTokener(reply.body).nextValue().takeIf { it is JSONObject || it is JSONArray } ?: throw Failure("api_error_not_json")
            } catch (e: org.json.JSONException) {
                throw Failure("api_error_not_json")
            }
        }

        /** Whether [url] is on the server of one of [addresses]: same scheme, host and port. */
        fun sameServer(url: HttpUrl, addresses: List<String>): Boolean =
            addresses.mapNotNull { it.replace(ApiSettings.ID_MARK, "id").toHttpUrlOrNull() }
                .any { it.scheme == url.scheme && it.host == url.host && it.port == url.port }

        /**
         * What [json] holds at [path]: names and indexes joined by dots, an index also written in
         * brackets (`current.temperature_2m`, `results.0.url`, `results[0].url`). Null when it
         * holds nothing there, or null.
         */
        fun at(json: Any, path: String): Any? {
            var here: Any? = json
            for (step in path.replace("[", ".").replace("]", "").split('.').filter { it.isNotEmpty() }) {
                here = when (here) {
                    is JSONObject -> here.opt(step)
                    is JSONArray -> step.toIntOrNull()?.let { here.opt(it) }
                    else -> null
                }
                if (here == null || here == JSONObject.NULL) return null
            }
            return here
        }

        /**
         * [value] as [type] names it: a text of anything (an object or a list as its JSON), a
         * number of a number or of a text that reads as one, a yes or no of a boolean or of
         * "true" / "false", a file of an address.
         */
        fun typed(name: String, value: Any, type: String): Any = when (type) {
            ApiSettings.NUMBER -> (value as? Number)?.toDouble()?.let { if (it % 1.0 == 0.0 && kotlin.math.abs(it) < Long.MAX_VALUE) it.toLong() else it }
                ?: value.toString().toDoubleOrNull()?.let { if (it % 1.0 == 0.0) it.toLong() else it }
                ?: throw Failure("api_error_value_type", name, type)
            ApiSettings.BOOLEAN -> value as? Boolean ?: value.toString().toBooleanStrictOrNull() ?: throw Failure("api_error_value_type", name, type)
            ApiSettings.FILE -> value.takeIf { it is String && (it.startsWith("https://") || it.startsWith("http://")) } ?: throw Failure("api_error_value_type", name, type)
            else -> value.toString()
        }
    }
}
