package app.treelune.core.identities

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.database.AppDatabase
import app.treelune.core.icons.Icons
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.InUse
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.themes.IconColor
import app.treelune.core.utils.DataChangeNotifier
import org.json.JSONObject
import java.util.UUID

/**
 * The identities (docs/design/incarnations.md), for the screens and every AI alike, the
 * connector's included.
 *
 * - list: each identity, without its texts
 * - get: `identity_id`; everything it holds, every language, for its screen
 * - read: `identity_id`, `language`?; its texts in one language (its own when not given), and its
 *   bagage listed by key, title, description and size -- what the AI reads
 * - read_resource: `identity_id`, `key`, `language`?; one resource of the bagage, whole
 * - create: `name`, `language`, and any of `icon_name`, `icon_color`; `id` from the app alone, for the demo's (GivenId)
 * - update: `identity_id`, and any of `name`, `icon_name`, `icon_color`, `language` (a language
 *   it does not hold yet is given an empty section), and in `section` (its language when not
 *   given) any of `voice`, `mind`, `portrait` (a text; empty removes it) and `resources` (by key,
 *   `{title, description, content}` to write one whole, null to remove it). A text written here
 *   is no longer as its file gave it: a reimport names it among the retouches it loses.
 * - delete: `identity_id`; refused while a zone or a routine names it (InUse)
 * - import_preview: `text`, an identity file (docs/IDENTITY_FILE.md); what it holds, and what
 *   replacing the identity of its id would lose
 * - import: `text`, `language` (one of the file's), `replace`; creates the identity, or replaces
 *   the one of its id whole, its icon kept — only with `replace`, the refusal saying otherwise what
 *   replacing loses, as the screen's confirmation does
 */
class IdentityService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.identityDao()
    private val writes = app.treelune.core.history.Writes(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return try {
            when (operation) {
                "list" -> OperationResult.success(mapOf("identities" to dao.getAll().map { summary(it.toIdentity()).present() }))
                "get" -> get(params)
                "read" -> read(params)
                "read_resource" -> readResource(params)
                "create" -> create(params)
                "update" -> update(params)
                "delete" -> delete(params)
                "import_preview" -> importPreview(params)
                "import" -> import(params)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Refused) {
            OperationResult.error(e.message ?: "")
        }
    }

    /** A request refused, with what to say. */
    private class Refused(message: String) : Exception(message)

    private suspend fun identity(params: JSONObject): Identity {
        val id = params.optString("identity_id").takeIf { it.isNotEmpty() } ?: throw Refused(s.shared("identity_error_param").format("identity_id"))
        return dao.getById(id)?.toIdentity() ?: throw Refused(s.shared("identity_error_not_found").format(id))
    }

    /** The section of [identity] in the language [params] give under [key], its own when not given. */
    private fun section(identity: Identity, params: JSONObject, key: String = "language"): Pair<String, IdentityLanguage> {
        val code = params.optString(key).takeIf { it.isNotEmpty() } ?: identity.language
        val section = identity.languages[code]
            ?: throw Refused(s.shared("identity_error_language").format(code, identity.name, identity.languages.keys.joinToString(", ")))
        return code to section
    }

    /** [this] without its null values: a result says nothing of what is absent. */
    private fun Map<String, Any?>.present(): Map<String, Any> = filterValues { it != null }.mapValues { it.value!! }

    private fun summary(identity: Identity): Map<String, Any?> = mapOf(
        "id" to identity.id,
        "name" to identity.name,
        "icon_name" to identity.iconName,
        IconColor.KEY to identity.iconColor,
        "language" to identity.language,
        "languages" to identity.languages.keys.toList(),
        "published_at" to identity.publishedAt,
        "imported_at" to identity.importedAt
    )

    private suspend fun get(params: JSONObject): OperationResult {
        val identity = identity(params)
        return OperationResult.success(mapOf("identity" to (summary(identity) + mapOf(
            "sections" to identity.languages.mapValues { (_, section) ->
                mapOf(
                    "pieces" to section.pieces().mapValues { (_, piece) -> mapOf("content" to piece.content, "imported" to piece.imported) },
                    "resources" to section.resources.map { (key, resource) -> mapOf(
                        "key" to key, "title" to resource.title, "description" to resource.description,
                        "content" to resource.content, "imported" to resource.imported) }
                )
            },
            "retouched" to identity.retouched(),
            "created_at" to identity.createdAt,
            "updated_at" to identity.updatedAt
        )).present()))
    }

    private suspend fun read(params: JSONObject): OperationResult {
        val identity = identity(params)
        val (code, section) = section(identity, params)
        return OperationResult.success(mapOf("identity" to mapOf(
            "id" to identity.id,
            "name" to identity.name,
            "language" to code,
            IdentityLanguage.VOICE to section.voice?.content,
            IdentityLanguage.MIND to section.mind?.content,
            IdentityLanguage.PORTRAIT to section.portrait?.content,
            "resources" to section.resources.map { (key, resource) ->
                mapOf("key" to key, "title" to resource.title, "description" to resource.description, "chars" to resource.content.length)
            }
        ).present()))
    }

    private suspend fun readResource(params: JSONObject): OperationResult {
        val identity = identity(params)
        val (code, section) = section(identity, params)
        val key = params.optString("key").takeIf { it.isNotEmpty() } ?: throw Refused(s.shared("identity_error_param").format("key"))
        val resource = section.resources[key]
            ?: throw Refused(s.shared("identity_error_resource").format(key, identity.name, code, section.resources.keys.joinToString(", ").ifEmpty { "-" }))
        return OperationResult.success(mapOf("resource" to mapOf(
            "identity_id" to identity.id, "language" to code, "key" to key,
            "title" to resource.title, "description" to resource.description, "content" to resource.content
        )))
    }

    private suspend fun create(params: JSONObject): OperationResult {
        val name = name(params) ?: throw Refused(s.shared("identity_error_param").format("name"))
        val language = language(params.optString("language")) ?: throw Refused(s.shared("identity_error_param").format("language"))
        val id = when (val given = app.treelune.core.coordinator.GivenId.read(params)) {
            app.treelune.core.coordinator.GivenId.Read.None -> UUID.randomUUID().toString()
            is app.treelune.core.coordinator.GivenId.Read.Accepted -> given.id
            is app.treelune.core.coordinator.GivenId.Read.Refused -> throw Refused(s.shared("service_error_id_not_given").format(given.id))
        }
        val now = System.currentTimeMillis()
        val identity = Identity(
            id = id,
            name = name,
            iconName = icon(params) ?: Identity.DEFAULT_ICON,
            iconColor = iconColor(params),
            language = language,
            languages = mapOf(language to IdentityLanguage()),
            publishedAt = null,
            importedAt = null,
            createdAt = now, updatedAt = now
        )
        writes.putIdentity(IdentityEntity.of(identity))
        DataChangeNotifier.notifyIdentitiesChanged()
        return OperationResult.success(mapOf("identity_id" to identity.id, "name" to identity.name))
    }

    private suspend fun update(params: JSONObject): OperationResult {
        val existing = identity(params)
        var languages = existing.languages
        val language = if (params.has("language")) {
            language(params.optString("language")) ?: throw Refused(s.shared("identity_error_param").format("language"))
        } else existing.language
        if (language !in languages) languages = languages + (language to IdentityLanguage())

        val edits = IdentityLanguage.PIECES.filter { params.has(it) } + listOfNotNull("resources".takeIf { params.has(it) })
        if (edits.isNotEmpty()) {
            val (code, before) = section(existing.copy(language = language, languages = languages), params, "section")
            var section = before
            IdentityLanguage.PIECES.filter { params.has(it) }.forEach { key ->
                val text = if (params.isNull(key)) "" else params.optString(key)
                section = section.withPiece(key, text.takeIf { it.isNotBlank() }?.let { IdentityPiece(it) })
            }
            params.optJSONObject("resources")?.let { given ->
                val resources = section.resources.toMutableMap()
                given.keys().forEach { key ->
                    if (key.isBlank() || key.length > Identity.MAX_KEY) throw Refused(s.shared("identity_error_resource_key").format(key, Identity.MAX_KEY))
                    if (given.isNull(key)) {
                        resources.remove(key) ?: throw Refused(s.shared("identity_error_resource").format(key, existing.name, code, section.resources.keys.joinToString(", ").ifEmpty { "-" }))
                    } else {
                        val resource = given.optJSONObject(key) ?: throw Refused(s.shared("identity_error_resource_form").format(key))
                        fun text(field: String) = resource.optString(field).takeIf { it.isNotBlank() }
                            ?: throw Refused(s.shared("identity_error_resource_form").format(key))
                        resources[key] = IdentityResource(text("title"), text("description"), text("content"))
                    }
                }
                section = section.copy(resources = resources)
            }
            languages = languages + (code to section)
        }

        val updated = existing.copy(
            name = if (params.has("name")) name(params) ?: throw Refused(s.shared("identity_error_param").format("name")) else existing.name,
            iconName = if (params.has("icon_name")) icon(params) ?: Identity.DEFAULT_ICON else existing.iconName,
            iconColor = if (params.has(IconColor.KEY)) iconColor(params) else existing.iconColor,
            language = language,
            languages = languages,
            updatedAt = System.currentTimeMillis()
        )
        writes.putIdentity(IdentityEntity.of(updated))
        DataChangeNotifier.notifyIdentitiesChanged()
        return OperationResult.success(mapOf("identity_id" to updated.id, "name" to updated.name))
    }

    private suspend fun delete(params: JSONObject): OperationResult {
        val identity = identity(params)
        InUse.identity(database, identity.id).refusal(identity.name, s)?.let { throw Refused(it) }
        writes.deleteIdentity(identity.id)
        DataChangeNotifier.notifyIdentitiesChanged()
        return OperationResult.success(mapOf("identity_id" to identity.id, "name" to identity.name))
    }

    /** The identity file in [params]' `text`, read or refused with its reason. */
    private fun file(params: JSONObject): IdentityFile.Read {
        val text = params.optString("text").takeIf { it.isNotEmpty() } ?: throw Refused(s.shared("identity_error_param").format("text"))
        return try {
            IdentityFile.read(text)
        } catch (e: IdentityFile.Refused) {
            throw Refused(s.shared(e.key).format(*e.args))
        }
    }

    private suspend fun importPreview(params: JSONObject): OperationResult {
        val read = file(params)
        val existing = dao.getById(read.id)?.toIdentity()
        return OperationResult.success(mapOf<String, Any?>(
            "id" to read.id,
            "name" to read.name,
            "published_at" to read.publishedAt,
            "languages" to read.languages.keys.toList(),
            "existing" to existing?.let { mapOf(
                "name" to it.name,
                "published_at" to it.publishedAt,
                "imported_at" to it.importedAt,
                "language" to it.language,
                // A file older than the one in place is said, not refused: it is the way back
                "older" to (it.publishedAt != null && read.publishedAt != null && read.publishedAt < it.publishedAt),
                "languages_gained" to (read.languages.keys - it.languages.keys).toList(),
                "languages_lost" to (it.languages.keys - read.languages.keys).toList(),
                "retouched" to it.retouched()
            ).present() }
        ).present())
    }

    private suspend fun import(params: JSONObject): OperationResult {
        val read = file(params)
        val language = params.optString("language").takeIf { it.isNotEmpty() } ?: throw Refused(s.shared("identity_error_param").format("language"))
        if (language !in read.languages) throw Refused(s.shared("identity_error_language").format(language, read.name, read.languages.keys.joinToString(", ")))
        val existing = dao.getById(read.id)?.toIdentity()
        if (existing != null && !params.optBoolean("replace", false)) {
            val lost = (existing.retouched() + (existing.languages.keys - read.languages.keys)).joinToString(", ").ifEmpty { "-" }
            throw Refused(s.shared("identity_import_replace_needed").format(existing.name, lost))
        }
        val now = System.currentTimeMillis()
        val identity = Identity(
            id = read.id,
            name = read.name,
            iconName = if (params.has("icon_name")) icon(params) ?: Identity.DEFAULT_ICON else existing?.iconName ?: Identity.DEFAULT_ICON,
            iconColor = if (params.has(IconColor.KEY)) iconColor(params) else existing?.iconColor,
            language = language,
            languages = read.languages,
            publishedAt = read.publishedAt,
            importedAt = now,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        writes.putIdentity(IdentityEntity.of(identity))
        DataChangeNotifier.notifyIdentitiesChanged()
        return OperationResult.success(mapOf("identity_id" to identity.id, "name" to identity.name, "replaced" to (existing != null)))
    }

    private fun name(params: JSONObject): String? =
        params.optString("name").trim().takeIf { it.isNotEmpty() }?.also {
            if (it.length > Identity.MAX_NAME) throw Refused(s.shared("identity_error_name_length").format(Identity.MAX_NAME))
        }

    /** A language code (`fr`, `en`, `pt-BR`), or null when none is given. */
    private fun language(given: String): String? {
        if (given.isEmpty()) return null
        if (!Identity.LANGUAGE.matches(given)) throw Refused(s.shared("identity_error_language_code").format(given))
        return given
    }

    /** The Lucide name given, under its current name; null when none is given. */
    private fun icon(params: JSONObject): String? {
        val given = params.optString("icon_name").takeIf { it.isNotEmpty() && !params.isNull("icon_name") } ?: return null
        return Icons.storedName(context, given) ?: throw Refused(s.shared("service_error_icon_unknown").format(given))
    }

    private fun iconColor(params: JSONObject): String? {
        val given = params.optString(IconColor.KEY).takeIf { it.isNotEmpty() && !params.isNull(IconColor.KEY) }
        if (!IconColor.accepts(given)) throw Refused(s.shared("service_error_icon_color_unknown").format(given, IconColor.NAMES.joinToString(", ")))
        return given
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        val name = params.optString("name").takeIf { it.isNotEmpty() }
            ?: params.optString("identity_id").takeIf { it.isNotEmpty() }?.let { dao.getById(it)?.name }
            ?: ""
        return when (operation) {
            "create" -> s.shared("action_verbalize_identity_create").format(name)
            "update" -> s.shared("action_verbalize_identity_update").format(name)
            "delete" -> s.shared("action_verbalize_identity_delete").format(name)
            "import" -> s.shared("action_verbalize_identity_import")
            else -> s.shared("action_verbalize_unknown")
        }
    }

}
