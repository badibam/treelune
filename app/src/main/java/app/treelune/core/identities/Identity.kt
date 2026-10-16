package app.treelune.core.identities

import org.json.JSONObject

/**
 * One text of an identity in one language: its voice, its mind, its portrait.
 *
 * @property address Where personae took it from (`mediatheque:lens:1/voix@Stan Prokopenko`), kept
 *   from an import as its origin; null once retouched, or for a text written in the app. A
 *   reimport names the pieces without one as the retouches it loses.
 */
data class IdentityPiece(val content: String, val address: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("content", content).put("address", address ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject) = IdentityPiece(json.getString("content"), json.optNullableString("address"))
    }
}

/**
 * One resource of an identity's bagage in one language: what the identity reads when it needs it.
 *
 * @property description What it is and when to call it up, as the prompt lists it
 * @property address As [IdentityPiece.address]
 */
data class IdentityResource(val title: String, val description: String, val content: String, val address: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("title", title).put("description", description).put("content", content)
        .put("address", address ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject) = IdentityResource(json.getString("title"), json.getString("description"),
            json.getString("content"), json.optNullableString("address"))
    }
}

/**
 * What an identity is in one language: its three texts, each absent when not written, and its
 * bagage by key, in its order. A personae export holds all three in every language it publishes;
 * an identity written in the app may hold none.
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
 * an import from personae fills and which is edited by hand or by the AI.
 *
 * @property id personae's for an imported one, the app's own for one written here
 * @property iconName A Lucide name, [DEFAULT_ICON] until another is chosen
 * @property iconColor A name of IconColor.NAMES; null for a neutral icon
 * @property language The language it speaks, one of [languages]'s keys
 * @property languages Every language it holds, an import's all kept
 * @property publishedAt The `publie_le` of the export it was last imported from (a day,
 *   `2026-10-16`); null for one never imported
 */
data class Identity(
    val id: String,
    val name: String,
    val iconName: String,
    val iconColor: String?,
    val language: String,
    val languages: Map<String, IdentityLanguage>,
    val publishedAt: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** What it is in the language it speaks. */
    val spoken: IdentityLanguage get() = languages[language] ?: throw IllegalStateException("Identity $id holds no section for its language $language")

    /**
     * The pieces of [languages] retouched or added in the app: in an imported identity, every one
     * without its address. Named as `<language> · <piece or resource key>`, for a reimport to say
     * what it loses.
     */
    fun retouched(): List<String> = if (publishedAt == null) emptyList() else languages.flatMap { (code, section) ->
        section.pieces().filterValues { it.address == null }.keys.map { "$code · $it" } +
            section.resources.filterValues { it.address == null }.keys.map { "$code · $it" }
    }

    companion object {
        const val DEFAULT_ICON = "user-round"

        fun languagesToJson(languages: Map<String, IdentityLanguage>): JSONObject =
            JSONObject().apply { languages.forEach { (code, section) -> put(code, section.toJson()) } }

        fun languagesFromJson(json: JSONObject): Map<String, IdentityLanguage> =
            json.keys().asSequence().associateWith { IdentityLanguage.fromJson(json.getJSONObject(it)) }
    }
}

private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else getString(key)
