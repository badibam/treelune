package app.treelune.core.identities

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * An identity file, the public format any producer writes (docs/IDENTITY_FILE.md, its JSON Schema
 * beside it in docs/schemas): `format`, `id`, `name`, `published_at`?, and `languages.<code>`,
 * each with any of `voice`, `mind`, `portrait` as `{content}`, at least one, and `resources`?, a
 * list of `{key, title, description, content}`.
 *
 * A [format] the app does not know is refused. At the one it knows, a field required and missing
 * or of another type is refused, naming its path; a field more is passed over, a producer adding
 * its own without raising the format. Nothing is ever read in part.
 */
object IdentityFile {

    /** The format this reader knows. */
    const val FORMAT = 2

    /** What a file holds, in the app's words; every text marked imported. */
    data class Read(val id: String, val name: String, val publishedAt: String?, val languages: Map<String, IdentityLanguage>)

    /**
     * Why a file does not read: [key] is the string of the reason, [args] its arguments (the
     * field's path, the format found).
     */
    class Refused(val key: String, vararg val args: Any) : Exception("$key ${args.joinToString()}")

    /** An identity's id: lower-case ASCII letters, digits and hyphens. */
    private val ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")

    /**
     * The file in [text].
     *
     * @throws Refused when it does not read, saying why
     */
    fun read(text: String): Read {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw Refused("identity_import_not_json", e.message ?: "")
        } as? JsonObject ?: throw Refused("identity_import_not_json", "")

        val format = (root["format"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            ?: throw Refused("identity_import_field", "format")
        if (format != FORMAT) throw Refused("identity_import_format", format, FORMAT)

        val id = root.text("id", "id")
        if (!ID.matches(id)) throw Refused("identity_import_field", "id")
        val name = root.text("name", "name").trim()
        if (name.length > Identity.MAX_NAME) throw Refused("identity_import_field", "name")
        val publishedAt = root["published_at"]?.takeIf { it !is JsonNull }?.let {
            val day = root.text("published_at", "published_at")
            try { LocalDate.parse(day) } catch (e: DateTimeParseException) { throw Refused("identity_import_field", "published_at") }
            day
        }
        val languages = root["languages"] as? JsonObject ?: throw Refused("identity_import_field", "languages")
        if (languages.isEmpty()) throw Refused("identity_import_no_language")
        return Read(id, name, publishedAt, languages.mapValues { (code, section) -> section(code, section) })
    }

    private fun section(code: String, element: JsonElement): IdentityLanguage {
        val path = "languages.$code"
        if (!Identity.LANGUAGE.matches(code)) throw Refused("identity_import_field", path)
        val section = element as? JsonObject ?: throw Refused("identity_import_field", path)
        fun piece(key: String): IdentityPiece? {
            val value = section[key]?.takeIf { it !is JsonNull } ?: return null
            val piece = value as? JsonObject ?: throw Refused("identity_import_field", "$path.$key")
            return IdentityPiece(piece.text("content", "$path.$key.content"), imported = true)
        }
        val resources = linkedMapOf<String, IdentityResource>()
        section["resources"]?.takeIf { it !is JsonNull }?.let { value ->
            val list = value as? JsonArray ?: throw Refused("identity_import_field", "$path.resources")
            list.forEachIndexed { i, item ->
                val at = "$path.resources.$i"
                val resource = item as? JsonObject ?: throw Refused("identity_import_field", at)
                val key = resource.text("key", "$at.key")
                if (key.length > Identity.MAX_KEY || key in resources) throw Refused("identity_import_field", "$at.key")
                resources[key] = IdentityResource(resource.text("title", "$at.title"), resource.text("description", "$at.description"),
                    resource.text("content", "$at.content"), imported = true)
            }
        }
        val language = IdentityLanguage(piece(IdentityLanguage.VOICE), piece(IdentityLanguage.MIND), piece(IdentityLanguage.PORTRAIT), resources)
        if (language.pieces().isEmpty()) throw Refused("identity_import_no_text", code)
        return language
    }

    /** The text under [key], which must be a non-empty string; [path] names it when refused. */
    private fun JsonObject.text(key: String, path: String): String {
        val value = this[key]
        if (value == null || value is JsonNull || value !is JsonPrimitive || !value.isString || value.content.isBlank())
            throw Refused("identity_import_field", path)
        return value.content
    }
}
