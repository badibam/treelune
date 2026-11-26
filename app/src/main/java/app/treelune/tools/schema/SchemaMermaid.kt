package app.treelune.tools.schema

import app.treelune.core.drawing.diagram.*
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.Reference
import app.treelune.core.validation.FieldLimits
import org.json.JSONArray
import org.json.JSONObject

/** Why a Mermaid text cannot be converted, naming its line when it has one. */
class MermaidError(message: String) : IllegalArgumentException(message)

/**
 * A Mermaid text converted, nothing stored yet: what it would put in the config, and what the
 * conversion changed or left out, to be read before accepting it.
 */
data class MermaidImport(
    val direction: String,
    val nodes: JSONArray,
    val edges: JSONArray,
    val notes: List<String>
) {
    /** [config] with this schema in place of its own, its other settings untouched. */
    fun applyTo(config: JSONObject): JSONObject = JSONObject(config.toString())
        .put(SchemaKeys.DIRECTION, direction)
        .put(SchemaKeys.NODES, JSONArray(nodes.toString()))
        .put(SchemaKeys.EDGES, JSONArray(edges.toString()))

    /** How many of its boxes hold others: its subgraphs. */
    val holders: Int get() = (0 until nodes.length()).mapNotNull { nodes.optJSONObject(it)?.optString(SchemaKeys.PARENT)?.takeIf { p -> p.isNotEmpty() } }.toSet().size
}

/**
 * Mermaid's flowchart text, in and out (docs/design/schema.md, « La saisie »). Never stored: the
 * config stays JSON. Reading takes what a schema can hold and says what it changes: a sideways
 * direction, a shape it does not have, a link end, the styling it ignores. A subgraph is a box
 * that holds others, which an arrow may reach. A box whose id stays keeps what Mermaid cannot say:
 * its link, color, icon, fill and fading, and for one that holds others its shape, whether it starts folded and
 * whether it reads in parts.
 *
 * @param own The schema tool's strings
 */
class SchemaMermaid(private val own: (String) -> String) {

    // ---------------------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------------------

    /**
     * The flowchart text of [spec], a linked box by its text or the current name of its thing, a box
     * that holds others as a subgraph.
     */
    fun write(spec: DiagramSpec, nameOf: (Reference) -> String?): String {
        val out = StringBuilder("flowchart ${spec.direction.key}\n")
        val children = spec.nodes.filter { it.parent != null }.groupBy { it.parent!! }
        fun box(node: DiagramNode, indent: String) {
            val text = quoted(node.label ?: node.thing?.let(nameOf) ?: node.id)
            if (node.id in spec.holders) {
                out.append(indent).append("subgraph ").append(node.id).append(" [").append(text).append("]\n")
                children[node.id].orEmpty().forEach { box(it, "$indent    ") }
                out.append(indent).append("end\n")
                return
            }
            val shape = when (node.shape) {
                DiagramShape.RECT -> "[$text]"
                DiagramShape.ROUNDED -> "($text)"
                DiagramShape.STADIUM -> "([$text])"
                DiagramShape.CIRCLE -> "(($text))"
                DiagramShape.DIAMOND -> "{$text}"
            }
            out.append(indent).append(node.id).append(shape).append('\n')
        }
        spec.nodes.filter { it.parent == null }.forEach { box(it, "    ") }
        spec.edges.forEach { edge ->
            val link = when (edge.line) {
                DiagramLine.NORMAL -> if (edge.head) "-->" else "---"
                DiagramLine.DOTTED -> if (edge.head) "-.->" else "-.-"
                DiagramLine.THICK -> if (edge.head) "==>" else "==="
                // Refused in a schema's config (SchemaConfig.LINES)
                DiagramLine.DASHED -> throw IllegalStateException("A schema's arrow is never dashed")
            }
            val label = edge.label?.let { "|${quoted(it)}|" }.orEmpty()
            out.append("    ").append(edge.from).append(' ').append(link).append(label).append(' ').append(edge.to).append('\n')
        }
        return out.toString()
    }

    private fun quoted(text: String) = "\"" + text.replace("\"", "#quot;").replace(Regex("\\s+"), " ") + "\""

    // ---------------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------------

    private class ReadBox(val id: String) {
        var label: String? = null
        var shape: DiagramShape? = null
        var parent: String? = null
        var subgraph = false
    }

    private class ReadEdge(val from: String, val to: String, val label: String?, val line: DiagramLine, val head: Boolean, val atLine: Int)

    /** One end of a link as written: the id, and its text and shape when it gives them. */
    private class Written(val id: String, val label: String?, val shape: DiagramShape?)

    /**
     * [text] converted.
     *
     * @param existing The config being edited, whose boxes keep what Mermaid cannot say
     * @param nameOf The current name of a thing: a box whose text is only that stays without a text of its own
     * @throws MermaidError naming the line at fault
     */
    fun read(text: String, existing: JSONObject, nameOf: (Reference) -> String?): MermaidImport {
        val boxes = LinkedHashMap<String, ReadBox>()
        val edges = mutableListOf<ReadEdge>()
        val open = ArrayDeque<Pair<ReadBox, Int>>()
        var direction: String? = null
        var ignored = 0
        var approximated = 0
        var linkEnds = 0

        fun mention(written: Written) {
            val box = boxes.getOrPut(written.id) { ReadBox(written.id) }
            written.label?.let { box.label = it }
            written.shape?.let { box.shape = it }
            val inside = open.lastOrNull()?.first
            // A subgraph named by an arrow inside itself stays where it is
            if (box.parent == null && inside != null && inside !== box) box.parent = inside.id
        }

        statements(text).forEach { (line, statement) ->
            val word = statement.substringBefore(' ').lowercase()
            if (direction == null) {
                if (word != "flowchart" && word != "graph") throw MermaidError(own("mermaid_error_header").format(line))
                val written = statement.substringAfter(' ', "").trim().uppercase()
                direction = written.ifEmpty { "TD" }
                if (direction !in DIRECTIONS) throw MermaidError(own("mermaid_error_syntax").format(line, statement))
                return@forEach
            }
            when (word) {
                "subgraph" -> {
                    val (id, title) = subgraph(statement.substring(word.length).trim(), boxes.values.count { it.subgraph }, line, statement)
                    val box = boxes.getOrPut(id) { ReadBox(id) }
                    // A subgraph written twice, or over a box given its own text or shape
                    if (box.subgraph || box.label != null || box.shape != null) throw MermaidError(own("error_id_twice").format(id))
                    box.subgraph = true
                    box.label = title
                    val inside = open.lastOrNull()?.first
                    if (box.parent == null && inside != null) box.parent = inside.id
                    open.addLast(box to line)
                }
                "end" -> {
                    if (statement.lowercase() != "end" || open.isEmpty()) throw MermaidError(own("mermaid_error_syntax").format(line, statement))
                    open.removeLast()
                }
                "direction" -> ignored++
                "style", "classdef", "class", "linkstyle", "click" -> ignored++
                else -> {
                    val chain = Chain(statement, line)
                    chain.read().forEach(::mention)
                    edges += chain.edges
                    approximated += chain.approximated
                    linkEnds += chain.linkEnds
                    if (chain.classes) ignored++
                }
            }
        }
        val written = direction ?: throw MermaidError(own("mermaid_error_header").format(1))
        open.lastOrNull()?.let { (box, line) -> throw MermaidError(own("mermaid_error_unclosed").format(line, box.id)) }

        val arrows = edges.map { edge ->
            JSONObject().put(SchemaKeys.FROM, edge.from).put(SchemaKeys.TO, edge.to).put(SchemaKeys.LINE, edge.line.key).put(SchemaKeys.HEAD, edge.head).apply {
                edge.label?.let {
                    checkLength(it, FieldLimits.SHORT_LENGTH, edge.atLine)
                    put(SchemaKeys.LABEL, it)
                }
            }
        }

        val kept = existing.optJSONArray(SchemaKeys.NODES).byId()
        val holders = boxes.values.mapNotNull { it.parent }.toSet()
        val nodeArray = JSONArray(boxes.values.map { box ->
            val before = kept[box.id]
            val thing = before?.takeUnless { it.isNull(SchemaKeys.THING) }?.opt(SchemaKeys.THING)
            val thingName = ReferenceTarget.referenceOf(thing)?.let(nameOf)
            // A box linked without a text of its own was written by its thing's name: it keeps following it
            val ownLabel = before?.optString(SchemaKeys.LABEL).orEmpty()
            val label = when {
                thing == null -> box.label ?: box.id
                box.label == null || (ownLabel.isEmpty() && box.label == thingName) -> ownLabel.takeIf { it.isNotEmpty() }
                else -> box.label
            }
            label?.let { checkLength(it, BOX_TEXT_LENGTH, null) }
            // Mermaid gives a subgraph no shape: one that holds others keeps the one it had, shown when folded
            val holds = box.id in holders
            val shape = box.shape?.key ?: (if (holds) before?.optString(SchemaKeys.SHAPE)?.takeIf { it.isNotEmpty() } else null) ?: (if (holds) null else DiagramShape.RECT.key)
            JSONObject().put(SchemaKeys.ID, box.id).apply {
                shape?.let { put(SchemaKeys.SHAPE, it) }
                label?.let { put(SchemaKeys.LABEL, it) }
                thing?.let { put(SchemaKeys.THING, it) }
                box.parent?.let { put(SchemaKeys.PARENT, it) }
                if (holds && before?.optBoolean(SchemaKeys.FOLDED) == true) put(SchemaKeys.FOLDED, true)
                if (holds && before?.optBoolean(SchemaKeys.PARTS) == true) put(SchemaKeys.PARTS, true)
                copyLook(before, this)
            }
        })

        val target = if (written == "BT") DiagramDirection.BOTTOM_UP.key else DiagramDirection.TOP_DOWN.key
        val notes = buildList {
            if (written == "LR" || written == "RL") add(own("mermaid_note_direction").format(written, target))
            if (approximated > 0) add(own("mermaid_note_shapes").format(approximated))
            if (linkEnds > 0) add(own("mermaid_note_link_ends").format(linkEnds))
            if (ignored > 0) add(own("mermaid_note_ignored").format(ignored))
        }
        val result = MermaidImport(target, nodeArray, JSONArray(arrows), notes)
        // What the config's own check refuses is said now, not at the save
        try {
            SchemaConfig.read(result.applyTo(JSONObject()), own)
        } catch (e: IllegalArgumentException) {
            throw MermaidError(e.message.orEmpty())
        }
        return result
    }

    private fun JSONArray?.byId(): Map<String, JSONObject> {
        val array = this ?: return emptyMap()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.associateBy { it.optString(SchemaKeys.ID) }
    }

    private fun copyLook(from: JSONObject?, to: JSONObject) {
        listOf(SchemaKeys.COLOR, SchemaKeys.ICON, SchemaKeys.FILL, SchemaKeys.FADED).forEach { key -> from?.takeUnless { it.isNull(key) }?.opt(key)?.let { to.put(key, it) } }
    }

    private fun checkLength(text: String, limit: Int, line: Int?) {
        if (text.length <= limit) return
        val shown = text.take(30) + "…"
        throw MermaidError(if (line != null) own("mermaid_error_long_line").format(line, shown, limit) else own("mermaid_error_long").format(shown, limit))
    }

    /** The statements of [text] with their line numbers: comments, fences and a front matter left out. */
    private fun statements(text: String): List<Pair<Int, String>> {
        val lines = text.replace("\r\n", "\n").split('\n')
        val frontEnd = if (lines.firstOrNull()?.trim() == "---") lines.drop(1).indexOfFirst { it.trim() == "---" } + 1 else 0
        return lines.withIndex().drop(frontEnd.let { if (it > 0) it + 1 else 0 }).flatMap { (i, raw) ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("%%") || line.startsWith("```")) emptyList()
            else splitOutsideQuotes(line).map { it.trim() }.filter { it.isNotEmpty() }.map { (i + 1) to it }
        }
    }

    private fun splitOutsideQuotes(line: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        line.forEach { c ->
            if (c == '"') quoted = !quoted
            if (c == ';' && !quoted) {
                parts += current.toString()
                current.clear()
            } else current.append(c)
        }
        parts += current.toString()
        return parts
    }

    /** A subgraph's id and title: `id [title]`, `id["title"]`, `"title"`, or a title alone. */
    private fun subgraph(rest: String, index: Int, line: Int, statement: String): Pair<String, String> {
        if (rest.isEmpty()) throw MermaidError(own("mermaid_error_syntax").format(line, statement))
        Regex("""^($ID)\s*\[(.*)]$""").find(rest)?.let { return it.groupValues[1] to clean(it.groupValues[2]) }
        if (Regex("^$ID$").matches(rest)) return rest to rest
        // A title with no id: an id is made from its place
        return "group${index + 1}" to clean(rest)
    }

    /** A label as a box shows it: without its quotes, its line breaks a space. */
    private fun clean(raw: String): String {
        var text = raw.trim()
        if (text.length >= 2 && text.startsWith('"') && text.endsWith('"')) text = text.substring(1, text.length - 1)
        if (text.length >= 2 && text.startsWith('`') && text.endsWith('`')) text = text.substring(1, text.length - 1)
        return text.replace(Regex("(?i)<br\\s*/?>"), " ").replace("#quot;", "\"").replace(Regex("\\s+"), " ").trim()
    }

    /** One statement of boxes and links: `A[text] -->|label| B & C --> D`. */
    private inner class Chain(private val text: String, private val line: Int) {
        private var at = 0
        val edges = mutableListOf<ReadEdge>()
        var approximated = 0
        var linkEnds = 0
        var classes = false

        private fun fail(): Nothing = throw MermaidError(own("mermaid_error_syntax").format(line, text))

        private fun spaces() {
            while (at < text.length && text[at].isWhitespace()) at++
        }

        /** Every box the statement names, its arrows gathered in [edges]. */
        fun read(): List<Written> {
            val all = mutableListOf<Written>()
            var left = nodes()
            all += left
            spaces()
            while (at < text.length) {
                val link = link() ?: fail()
                spaces()
                val right = nodes()
                all += right
                if (link.visible) left.forEach { from ->
                    right.forEach { to ->
                        edges += ReadEdge(from.id, to.id, link.label, link.line, link.head, line)
                        if (link.both) edges += ReadEdge(to.id, from.id, link.label, link.line, link.head, line)
                    }
                }
                left = right
                spaces()
            }
            return all
        }

        private fun nodes(): List<Written> {
            val found = mutableListOf(node())
            while (true) {
                val before = at
                spaces()
                if (at < text.length && text[at] == '&') {
                    at++
                    spaces()
                    found += node()
                } else {
                    at = before
                    return found
                }
            }
        }

        private fun node(): Written {
            val id = Regex("^$ID").find(text.substring(at))?.value ?: fail()
            at += id.length
            val written = when {
                text.startsWith("@{", at) -> extended(id)
                else -> SHAPES.firstOrNull { text.startsWith(it.open, at) }?.let { shaped(id, it) } ?: Written(id, null, null)
            }
            if (text.startsWith(":::", at)) {
                at += 3
                at += Regex("^[\\w-]+").find(text.substring(at))?.value?.length ?: fail()
                classes = true
            }
            return written
        }

        private fun shaped(id: String, shape: WrittenShape): Written {
            at += shape.open.length
            val start = at
            val end = if (at < text.length && text[at] == '"') {
                val closing = text.indexOf('"', at + 1)
                if (closing < 0) fail()
                shape.closes.mapNotNull { c -> text.indexOf(c, closing).takeIf { it >= 0 }?.let { it to c } }.minByOrNull { it.first } ?: fail()
            } else shape.closes.mapNotNull { c -> text.indexOf(c, at).takeIf { it >= 0 }?.let { it to c } }.minByOrNull { it.first } ?: fail()
            val label = clean(text.substring(start, end.first))
            at = end.first + end.second.length
            if (shape.shape == null) approximated++
            return Written(id, label.ifEmpty { null }, shape.shape ?: DiagramShape.RECT)
        }

        /** `@{ shape: diamond, label: "text" }`. */
        private fun extended(id: String): Written {
            var i = at + 2
            var quoted = false
            while (i < text.length && (quoted || text[i] != '}')) {
                if (text[i] == '"') quoted = !quoted
                i++
            }
            if (i >= text.length) fail()
            val body = text.substring(at + 2, i)
            at = i + 1
            val label = Regex("""label\s*:\s*("[^"]*"|[^,]+)""").find(body)?.groupValues?.get(1)?.let { clean(it) }
            val name = Regex("""shape\s*:\s*([\w-]+)""").find(body)?.groupValues?.get(1)?.lowercase()
            val shape = name?.let { NAMED_SHAPES[it] }
            if (name != null && shape == null) approximated++
            return Written(id, label?.ifEmpty { null }, shape ?: if (name != null) DiagramShape.RECT else null)
        }

        private inner class Link(val line: DiagramLine, val head: Boolean, val both: Boolean, val label: String?, val visible: Boolean = true)

        private fun link(): Link? {
            val rest = text.substring(at)
            INLINE.forEach { (regex, kind) ->
                regex.find(rest)?.let { found ->
                    at += found.value.length
                    return Link(kind, found.groupValues[2].endsWith(">"), both = false, label = clean(found.groupValues[1]))
                }
            }
            Regex("^~{3,}").find(rest)?.let { at += it.value.length; return Link(DiagramLine.NORMAL, false, false, null, visible = false) }
            val found = PLAIN.find(rest) ?: return null
            at += found.value.length
            val body = found.groupValues[2]
            val kind = when {
                body.startsWith("=") -> DiagramLine.THICK
                body.contains('.') -> DiagramLine.DOTTED
                else -> DiagramLine.NORMAL
            }
            val before = found.groupValues[1]
            val after = found.groupValues[3]
            if (after == "o" || after == "x" || before == "o" || before == "x") linkEnds++
            spaces()
            var label: String? = null
            if (at < text.length && text[at] == '|') {
                val closing = text.indexOf('|', at + 1)
                if (closing < 0) fail()
                label = clean(text.substring(at + 1, closing)).ifEmpty { null }
                at = closing + 1
            }
            return Link(kind, after.isNotEmpty(), both = before == "<" && after == ">", label = label)
        }
    }

    /** A way to write a box's shape; [shape] null for one a schema does not have, drawn as a rectangle. */
    private class WrittenShape(val open: String, val closes: List<String>, val shape: DiagramShape?)

    private companion object {
        const val ID = """[\p{L}\p{N}_]+(?:[-.][\p{L}\p{N}_]+)*"""
        val DIRECTIONS = setOf("TD", "TB", "BT", "LR", "RL")
        // A box's text, as its setting takes it
        val BOX_TEXT_LENGTH = FieldLimits.MEDIUM_LENGTH

        // The longest opening first: « (( » must not read as « ( »
        val SHAPES = listOf(
            WrittenShape("(((", listOf(")))"), null),
            WrittenShape("((", listOf("))"), DiagramShape.CIRCLE),
            WrittenShape("([", listOf("])"), DiagramShape.STADIUM),
            WrittenShape("[[", listOf("]]"), null),
            WrittenShape("[(", listOf(")]"), null),
            WrittenShape("[/", listOf("/]", "\\]"), null),
            WrittenShape("[\\", listOf("\\]", "/]"), null),
            WrittenShape("{{", listOf("}}"), null),
            WrittenShape("(", listOf(")"), DiagramShape.ROUNDED),
            WrittenShape("[", listOf("]"), DiagramShape.RECT),
            WrittenShape("{", listOf("}"), DiagramShape.DIAMOND),
            WrittenShape(">", listOf("]"), null)
        )

        // The names of Mermaid 11's shapes a schema has, with their aliases
        val NAMED_SHAPES = mapOf(
            "rect" to DiagramShape.RECT, "proc" to DiagramShape.RECT, "process" to DiagramShape.RECT, "rectangle" to DiagramShape.RECT,
            "rounded" to DiagramShape.ROUNDED, "event" to DiagramShape.ROUNDED,
            "stadium" to DiagramShape.STADIUM, "pill" to DiagramShape.STADIUM, "terminal" to DiagramShape.STADIUM,
            "circle" to DiagramShape.CIRCLE, "circ" to DiagramShape.CIRCLE,
            "diamond" to DiagramShape.DIAMOND, "diam" to DiagramShape.DIAMOND, "decision" to DiagramShape.DIAMOND, "question" to DiagramShape.DIAMOND
        )

        // `-- text -->`, `== text ==>`, `-. text .->`, with or without a head
        val INLINE = listOf(
            Regex("""^--\s+(.+?)\s*(-{2,}>|-{3,})""") to DiagramLine.NORMAL,
            Regex("""^==\s+(.+?)\s*(={2,}>|={3,})""") to DiagramLine.THICK,
            Regex("""^-\.\s+(.+?)\s*(\.+->|\.+-)""") to DiagramLine.DOTTED
        )

        // `-->`, `---`, `==>`, `-.->`, `<-->`, `--o`, `--x`; an o or an x only when it stands apart from a lowercase word
        val PLAIN = Regex("""^(<|o(?=-|=)|x(?=-|=))?(-{2,}|={2,}|-\.+-)(>|[ox](?=\s|$|\p{Lu}))?""")
    }
}
