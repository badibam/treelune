package app.treelune.core.identities

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * An identity's export from personae (`personae/docs/reference.md`, « Fichiers »), read for an
 * import: `format`, `id`, `nom`, `distribuable`, `publie_le`, and `langues.<langue>`, each
 * section with `voix`, `esprit`, `portrait` as `{adresse, contenu}` and `bagage` by key as
 * `{adresse, titre, description, contenu}`.
 *
 * A [format] the app does not know is refused. At the one it knows, a field expected and missing
 * or of another type is refused, naming it; a field more is passed over, personae adding optional
 * ones without raising the format. Nothing is ever read in part.
 */
object PersonaeExport {

    /** The formats this reader knows. */
    const val FORMAT = 1

    /** What an export holds, in the app's words. */
    data class Read(val id: String, val name: String, val publishedAt: String, val languages: Map<String, IdentityLanguage>)

    /**
     * Why an export does not read: [key] is the string of the reason, [args] its arguments (the
     * field's path, the format found).
     */
    class Refused(val key: String, vararg val args: Any) : Exception("$key ${args.joinToString()}")

    /** personae's id: lower-case ASCII letters, digits and hyphens. */
    private val ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")

    /**
     * The export in [text].
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
        val name = root.text("nom", "nom")
        // Read to be refused when of another type; the app distributes no identity, so it changes nothing
        (root["distribuable"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: throw Refused("identity_import_field", "distribuable")
        val publishedAt = root.text("publie_le", "publie_le")
        val langues = root["langues"] as? JsonObject ?: throw Refused("identity_import_field", "langues")
        if (langues.isEmpty()) throw Refused("identity_import_no_language")
        val languages = langues.mapValues { (code, section) -> section(code, section) }
        return Read(id, name, publishedAt, languages)
    }

    private fun section(code: String, element: JsonElement): IdentityLanguage {
        val path = "langues.$code"
        val section = element as? JsonObject ?: throw Refused("identity_import_field", path)
        fun piece(key: String): IdentityPiece {
            val piece = section[key] as? JsonObject ?: throw Refused("identity_import_field", "$path.$key")
            return IdentityPiece(piece.text("contenu", "$path.$key.contenu"), piece.text("adresse", "$path.$key.adresse"))
        }
        val bagage = section["bagage"] as? JsonObject ?: throw Refused("identity_import_field", "$path.bagage")
        val resources = bagage.mapValues { (key, value) ->
            val at = "$path.bagage.$key"
            val resource = value as? JsonObject ?: throw Refused("identity_import_field", at)
            IdentityResource(resource.text("titre", "$at.titre"), resource.text("description", "$at.description"),
                resource.text("contenu", "$at.contenu"), resource.text("adresse", "$at.adresse"))
        }
        return IdentityLanguage(piece("voix"), piece("esprit"), piece("portrait"), resources)
    }

    /** The text under [key], which must be a non-empty string; [path] names it when refused. */
    private fun JsonObject.text(key: String, path: String): String {
        val value = this[key]
        if (value == null || value is JsonNull || value !is JsonPrimitive || !value.isString || value.content.isBlank())
            throw Refused("identity_import_field", path)
        return value.content
    }
}
