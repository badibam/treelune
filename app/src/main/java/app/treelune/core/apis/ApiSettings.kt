package app.treelune.core.apis

import android.content.Context
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.DurationUnits
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingValues
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import org.json.JSONObject

/**
 * An API's config (docs/CORE.md, « APIs »), declared with the fields: where it is called and
 * how, what it is given, what it gives back and whether it answers at once or later. Its form,
 * its schema and its sealed key all come from here, as an AI provider's do.
 *
 * An API knows no tool: it names the values its answer holds, each by a path in the JSON; a
 * Task's steps say where they go.
 */
object ApiSettings {

    const val URL = "url"
    const val METHOD = "method"
    const val GET = "GET"
    const val POST = "POST"

    /** Where the key goes: nowhere, in a header, or in a parameter of the address. */
    const val KEY_IN = "key_in"
    const val KEY_NONE = "none"
    const val KEY_HEADER = "header"
    const val KEY_QUERY = "query"
    const val KEY = "key"
    const val KEY_HEADER_NAME = "key_header"
    const val KEY_PREFIX = "key_prefix"
    const val KEY_PARAM = "key_param"

    /** What a call is given, each by name: in the address for GET, a flat JSON object for POST. */
    const val INPUTS = "inputs"

    /** What its answer holds, each by name, type and path. */
    const val VALUES = "values"
    const val NAME = "name"
    const val TYPE = "type"
    const val PATH = "path"

    /** The types an input takes. */
    const val TEXT = "text"
    const val NUMBER = "number"
    const val BOOLEAN = "boolean"

    /**
     * The decimals of a number input: it is never written to an entry, so never rounded; they only
     * let a decimal number be typed, and 6 holds a GPS coordinate to some 10 cm.
     */
    private const val NUMBER_DECIMALS = 6

    /** The field an input fills, by its name and type: in a Task's step on screen, and in the schema the AI reads. */
    fun inputField(input: JSONObject): FieldDefinition {
        val name = input.getString(NAME)
        return when (input.optString(TYPE)) {
            NUMBER -> FieldDefinition(name, name, null, FieldType.NUMERIC, false, mapOf("decimals" to NUMBER_DECIMALS))
            BOOLEAN -> FieldDefinition(name, name, null, FieldType.BOOLEAN, false, null)
            else -> FieldDefinition(name, name, null, FieldType.TEXT, false, mapOf("length" to TextLength.UNLIMITED.name))
        }
    }

    /** The fields of the inputs of an API's [config]. */
    fun inputFields(config: JSONObject): List<FieldDefinition> =
        config.optJSONArray(INPUTS)?.let { list -> (0 until list.length()).map { inputField(list.getJSONObject(it)) } }.orEmpty()

    /** A value's type beside those: a file, its path the address to download it from. */
    const val FILE = "file"

    /**
     * A value's type beside those: a list, its path a JSON array, each element an object whose
     * [ITEMS] are read at their paths in it — what a Task's step repeats over (`for_each`).
     */
    const val LIST = "list"
    const val ITEMS = "items"

    /** Whether the answer is the result, or an id to follow until the result is ready. */
    const val RESPONSE = "response"
    const val IMMEDIATE = "immediate"
    const val DEFERRED = "deferred"
    const val ID_PATH = "id_path"
    const val STATUS_URL = "status_url"
    const val READY_PATH = "ready_path"
    const val READY_VALUE = "ready_value"
    const val FAILED_PATH = "failed_path"
    const val FAILED_VALUE = "failed_value"
    const val WAIT_LIMIT = "wait_limit"

    /** What the status address holds where the id goes. */
    const val ID_MARK = "{id}"

    const val SCHEMA_ID = "api_config"

    fun nodes(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        // MEDIUM: an address or a key runs past SHORT's 60 characters
        fun text(name: String, required: Boolean = false, secret: Boolean = false, address: Boolean = false, length: TextLength = TextLength.MEDIUM) =
            SettingNode.Field(FieldDefinition(name, s.shared("api_$name"), s.shared("api_schema_$name"), FieldType.TEXT, false,
                mapOf("length" to length.name)), required = required, secret = secret, address = address)
        fun choice(name: String, options: List<String>, default: String) = SettingNode.Field(
            FieldDefinition(name, s.shared("api_$name"), s.shared("api_schema_$name"), FieldType.CHOICE, false,
                mapOf("options" to ChoiceSettings.storedOptions(options, options.associateWith { s.shared("api_${name}_${it.lowercase()}") }))),
            required = true, default = default)
        return listOf(
            text(URL, required = true, address = true),
            choice(METHOD, listOf(GET, POST), GET),
            text(KEY, secret = true),
            SettingNode.Variant(
                choice(KEY_IN, listOf(KEY_NONE, KEY_HEADER, KEY_QUERY), KEY_NONE),
                mapOf(
                    KEY_NONE to emptyList(),
                    KEY_HEADER to listOf(
                        SettingNode.Field(FieldDefinition(KEY_HEADER_NAME, s.shared("api_$KEY_HEADER_NAME"), s.shared("api_schema_$KEY_HEADER_NAME"), FieldType.TEXT, false,
                            mapOf("length" to TextLength.SHORT.name)), required = true, default = "Authorization"),
                        text(KEY_PREFIX, length = TextLength.SHORT)
                    ),
                    KEY_QUERY to listOf(text(KEY_PARAM, required = true, length = TextLength.SHORT))
                ),
                descriptions = mapOf(KEY_NONE to s.shared("api_schema_key_in_none"), KEY_HEADER to s.shared("api_schema_key_in_header"), KEY_QUERY to s.shared("api_schema_key_in_query"))
            ),
            SettingNode.ListOf(INPUTS, s.shared("api_$INPUTS"), SettingNode.Item.Of(listOf(
                text(NAME, required = true, length = TextLength.SHORT),
                choice(TYPE, listOf(TEXT, NUMBER, BOOLEAN), TEXT)
            )), summary = listOf(NAME, TYPE), description = s.shared("api_schema_$INPUTS")),
            SettingNode.ListOf(VALUES, s.shared("api_$VALUES"), SettingNode.Item.Of(listOf(
                text(NAME, required = true, length = TextLength.SHORT),
                choice(TYPE, listOf(TEXT, NUMBER, BOOLEAN, FILE, LIST), TEXT),
                text(PATH, required = true),
                SettingNode.ListOf(ITEMS, s.shared("api_$ITEMS"), SettingNode.Item.Of(listOf(
                    text(NAME, required = true, length = TextLength.SHORT),
                    choice(TYPE, listOf(TEXT, NUMBER, BOOLEAN, FILE), TEXT),
                    text(PATH, required = true)
                )), summary = listOf(NAME, PATH), shownWith = SettingNode.ShownWith(TYPE, LIST), description = s.shared("api_schema_$ITEMS"))
            )), summary = listOf(NAME, PATH), description = s.shared("api_schema_$VALUES")),
            SettingNode.Variant(
                choice(RESPONSE, listOf(IMMEDIATE, DEFERRED), IMMEDIATE),
                mapOf(
                    IMMEDIATE to emptyList(),
                    DEFERRED to listOf(
                        text(ID_PATH, required = true),
                        text(STATUS_URL, required = true, address = true),
                        text(READY_PATH, required = true),
                        text(READY_VALUE, required = true, length = TextLength.SHORT),
                        text(FAILED_PATH),
                        text(FAILED_VALUE, length = TextLength.SHORT),
                        SettingNode.Field(FieldDefinition(WAIT_LIMIT, s.shared("api_$WAIT_LIMIT"), s.shared("api_schema_$WAIT_LIMIT"), FieldType.DURATION, false,
                            DurationUnits.DAY_HOUR_MINUTE.config()), required = true, default = 6 * 3_600_000L)
                    )
                ),
                descriptions = mapOf(IMMEDIATE to s.shared("api_schema_response_immediate"), DEFERRED to s.shared("api_schema_response_deferred"))
            )
        )
    }

    fun schema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = SCHEMA_ID,
            displayName = s.shared("api_display_name"),
            description = "",
            category = SchemaCategory.API_CONFIG,
            content = SettingsSchemaGenerator.generate(nodes(context), s::shared).toString()
        )
    }

    /** [config] read through its declaration. */
    fun read(config: JSONObject, context: Context): SettingValues = SettingValues(nodes(context), config)

    /**
     * What the schema cannot say: two inputs or two values of one name, a status address without
     * the place of its id, a failure half said. Null when nothing.
     */
    fun configError(config: JSONObject, context: Context): String? {
        val s = Strings.`for`(context = context)
        for (list in listOf(INPUTS, VALUES)) {
            val names = config.optJSONArray(list)?.let { array -> (0 until array.length()).map { array.getJSONObject(it).optString(NAME) } }.orEmpty()
            names.groupBy { it }.filter { it.value.size > 1 }.keys.firstOrNull()?.let { return s.shared("api_error_name_twice").format(it) }
            names.firstOrNull { !NAME_FORM.matches(it) }?.let { return s.shared("api_error_name_form").format(it) }
        }
        // A list's elements are read by their fields: at least one, each named once
        config.optJSONArray(VALUES)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()
            .filter { it.optString(TYPE) == LIST }.forEach { list ->
                val names = list.optJSONArray(ITEMS)?.let { items -> (0 until items.length()).map { items.getJSONObject(it).optString(NAME) } }.orEmpty()
                if (names.isEmpty()) return s.shared("api_error_list_items").format(list.optString(NAME))
                names.groupBy { it }.filter { it.value.size > 1 }.keys.firstOrNull()?.let { return s.shared("api_error_name_twice").format(it) }
                names.firstOrNull { !NAME_FORM.matches(it) }?.let { return s.shared("api_error_name_form").format(it) }
            }
        if (config.optString(RESPONSE) == DEFERRED) {
            if (!config.optString(STATUS_URL).contains(ID_MARK)) return s.shared("api_error_status_url").format(ID_MARK)
            if (config.optString(FAILED_PATH).isBlank() != config.optString(FAILED_VALUE).isBlank()) return s.shared("api_error_failed_half")
        }
        config.optString(URL).let { if (!it.startsWith("https://") && !it.startsWith("http://")) return s.shared("api_error_url").format(it) }
        return null
    }

    /** A name an input or a value takes: what a key is written as (docs/reference.md). */
    private val NAME_FORM = Regex("[a-z][a-z0-9_]*")
}
