package app.treelune.core.identities

import org.json.JSONObject

/**
 * One text of an identity in one language: its voice, its mind, its portrait.
 *
 * @property imported Whether it is as an identity file gave it; false once retouched, or for a
 *   text written in the app. A reimport names the texts of an imported identity that are not as
 *   the retouches it loses.
 */
data class IdentityPiece(val content: String, val imported: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("content", content).put("imported", imported)

    companion object {
        fun fromJson(json: JSONObject) = IdentityPiece(json.getString("content"), json.optBoolean("imported", false))
    }
}

/**
 * One resource of an identity's bagage in one language: what the identity reads when it needs it.
 *
 * @property description What it is and when to call it up, as the prompt lists it
 * @property imported As [IdentityPiece.imported]
 */
data class IdentityResource(val title: String, val description: String, val content: String, val imported: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("title", title).put("description", description).put("content", content)
        .put("imported", imported)

    companion object {
        fun fromJson(json: JSONObject) = IdentityResource(json.getString("title"), json.getString("description"),
            json.getString("content"), json.optBoolean("imported", false))
    }
}

/**
 * What an identity is in one language: its three texts, each absent when not written, and its
 * bagage by key, in its order. An identity file holds at least one text in each of its
 * languages; an identity written in the app may hold none.
 */
data class IdentityLanguage(
    val voice: IdentityPiece? = null,
    val mind: IdentityPiece? = null,
    val portrait: IdentityPiece? = null,
    val resources: Map<String, IdentityResource> = emptyMap()
) {
    /** Its three texts by their key ([PIECES]), absent ones left out. */
    fun pieces(): Map<String, IdentityPiece> = listOfNotNull(voice?.let { VOICE to it }, mind?.let { MIND to it }, portrait?.let { PORTRAIT to it }).toMap()

    fun withPiece(key: String, piece: IdentityPiece?): IdentityLanguage = when (key) {
        VOICE -> copy(voice = piece)
        MIND -> copy(mind = piece)
        PORTRAIT -> copy(portrait = piece)
        else -> throw IllegalArgumentException("Unknown identity piece $key")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        pieces().forEach { (key, piece) -> put(key, piece.toJson()) }
        // An array, for their order: a JSON object's keys keep none
        put("resources", org.json.JSONArray().apply { resources.forEach { (key, resource) -> put(resource.toJson().put("key", key)) } })
    }

    companion object {
        const val VOICE = "voice"
        const val MIND = "mind"
        const val PORTRAIT = "portrait"

        /** The three texts, in the order the prompt and the screen give them. */
        val PIECES = listOf(VOICE, MIND, PORTRAIT)

        fun fromJson(json: JSONObject): IdentityLanguage {
            fun piece(key: String) = json.optJSONObject(key)?.let(IdentityPiece::fromJson)
            val resources = json.getJSONArray("resources")
            return IdentityLanguage(piece(VOICE), piece(MIND), piece(PORTRAIT),
                (0 until resources.length()).map { resources.getJSONObject(it) }.associate { it.getString("key") to IdentityResource.fromJson(it) })
        }
    }
}

/**
 * An identity the app's AI may speak as (docs/design/incarnations.md): an object of the app, which
 * an identity file fills (docs/IDENTITY_FILE.md) and which is edited by hand or by the AI.
 *
 * @property id The file's for an imported one, the app's own for one written here
 * @property iconName A Lucide name, [DEFAULT_ICON] until another is chosen
 * @property iconColor A name of IconColor.NAMES; null for a neutral icon
 * @property language The language it speaks, one of [languages]'s keys
 * @property languages Every language it holds, an import's all kept
 * @property publishedAt The `published_at` of the file it was last imported from (a day,
 *   `2026-10-16`); null for one never imported, or from a file that does not say
 * @property importedAt When it was last imported from a file; null for one written in the app
 */
data class Identity(
    val id: String,
    val name: String,
    val iconName: String,
    val iconColor: String?,
    val language: String,
    val languages: Map<String, IdentityLanguage>,
    val publishedAt: String?,
    val importedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** What it is in the language it speaks. */
    val spoken: IdentityLanguage get() = languages[language] ?: throw IllegalStateException("Identity $id holds no section for its language $language")

    /**
     * The pieces of [languages] retouched or added in the app: in an imported identity, every one
     * no longer as the file gave it. Named as `<language> · <piece or resource key>`, for a
     * reimport to say what it loses.
     */
    fun retouched(): List<String> = if (importedAt == null) emptyList() else languages.flatMap { (code, section) ->
        section.pieces().filterValues { !it.imported }.keys.map { "$code · $it" } +
            section.resources.filterValues { !it.imported }.keys.map { "$code · $it" }
    }

    companion object {
        const val DEFAULT_ICON = "user-round"
        const val MAX_NAME = 100
        const val MAX_KEY = 120

        /** A language code: `fr`, `en`, `pt-BR`. */
        val LANGUAGE = Regex("[a-z]{2,3}(-[A-Z]{2})?")

        fun languagesToJson(languages: Map<String, IdentityLanguage>): JSONObject =
            JSONObject().apply { languages.forEach { (code, section) -> put(code, section.toJson()) } }

        fun languagesFromJson(json: JSONObject): Map<String, IdentityLanguage> =
            json.keys().asSequence().associateWith { IdentityLanguage.fromJson(json.getJSONObject(it)) }
    }
}
