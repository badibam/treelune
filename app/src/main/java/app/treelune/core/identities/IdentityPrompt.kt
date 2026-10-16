package app.treelune.core.identities

/**
 * An identity as the AI reads it (docs/design/incarnations.md, « Ce que porte le prompt »): the
 * section of a session's L1 that says who speaks, and the results of the identity reads, written
 * as their texts are rather than as escaped JSON.
 */
object IdentityPrompt {

    /**
     * The L1 section of a session speaking as [identity], right after the AI's role: who writes
     * what the user reads, its three texts in its language (an empty one left out), and its bagage
     * listed. [text] gives a string of the prompt's chunks.
     */
    fun block(identity: Identity, text: (String) -> String): String {
        val section = identity.spoken
        val sb = StringBuilder()
        sb.appendLine(text("ai_identity_block").format(identity.name, identity.language))
        pieces(section, text).takeIf { it.isNotEmpty() }?.let { sb.appendLine().append(it) }
        sb.appendLine()
        sb.appendLine("### ${text("ai_identity_bagage_title")}")
        sb.appendLine()
        if (section.resources.isEmpty()) sb.appendLine(text("ai_identity_bagage_none"))
        else {
            sb.appendLine(text("ai_identity_bagage").format(identity.name, identity.id))
            sb.appendLine()
            section.resources.forEach { (key, resource) -> sb.appendLine(resourceLine(key, resource.title, resource.description, resource.content.length)) }
        }
        return sb.toString().trimEnd()
    }

    /** The three texts of [section] under their headings, an absent one left out. */
    private fun pieces(section: IdentityLanguage, text: (String) -> String): String = buildString {
        section.pieces().forEach { (key, piece) ->
            appendLine("### ${text("ai_identity_piece_$key")}")
            appendLine()
            appendLine(piece.content.trim())
            appendLine()
        }
    }

    private fun resourceLine(key: String, title: String, description: String, chars: Int) =
        "- `$key` — $title ($chars chars): $description"

    /** The heading of an identity read's result. */
    fun title(operation: String, data: Map<String, Any>): String = when (operation) {
        "read" -> "Identity '${(data["identity"] as? Map<*, *>)?.get("name")}'"
        "read_resource" -> (data["resource"] as? Map<*, *>)?.let { "Resource '${it["key"]}' of identity ${it["identity_id"]} (${it["language"]})" } ?: ""
        "list" -> "Identities"
        else -> ""
    }

    /**
     * An identity read's result as the AI reads it: an identity's texts under their headings and
     * its bagage listed, a resource's content as written. Null for a result read as JSON (the list).
     */
    fun forModel(operation: String, data: Map<String, Any>): String? = when (operation) {
        "read" -> (data["identity"] as? Map<*, *>)?.let { identity ->
            buildString {
                appendLine("id: ${identity["id"]}, language: ${identity["language"]}")
                IdentityLanguage.PIECES.forEach { key ->
                    (identity[key] as? String)?.let {
                        appendLine()
                        appendLine("## $key")
                        appendLine()
                        appendLine(it.trim())
                    }
                }
                appendLine()
                appendLine("## resources")
                appendLine()
                (identity["resources"] as? List<*>).orEmpty().forEach { item ->
                    val resource = item as Map<*, *>
                    appendLine(resourceLine(resource["key"] as String, resource["title"] as String, resource["description"] as String, (resource["chars"] as Number).toInt()))
                }
            }.trimEnd()
        }
        "read_resource" -> (data["resource"] as? Map<*, *>)?.let { resource ->
            "## ${resource["title"]}\n\n${resource["description"]}\n\n${(resource["content"] as String).trim()}"
        }
        else -> null
    }
}
