package app.treelune.tools.schema

/**
 * A schema's content read in parts (docs/design/progression.md, « La linéarisation »): what a box
 * in [SchemaNode.parts] holds, or the schema's top level in [SchemaSpec.parts], is stacked one part
 * under the other, each part one of the boxes it holds with all that is inside it.
 *
 * Between two parts no line is drawn. An arrow from a part to the next one, both ends the parts
 * themselves, is said by the order and left out. Any other arrow a stack cuts (one that skips
 * parts, goes back up, or starts or ends inside a part, or comes into a part from outside the
 * stack) becomes a pair of references, the same letter at both ends.
 *
 * Works on what stays visible ([SchemaVisible]): a folded box is one part, the arrows of what it
 * holds reaching it. Arrows are taken as placed, turned for a schema running upward, so that the
 * parts stack in the order the arrows run on the page.
 *
 * @param upward Whether the schema runs bottom to top, its arrows placed turned
 */
internal class SchemaParts(spec: SchemaSpec, visible: SchemaVisible, upward: Boolean) {

    private val parentOf: Map<String, String?> = spec.nodes.associate { it.id to it.parent }
    private val order = spec.nodes.withIndex().associate { (i, node) -> node.id to i }

    /** The ids drawn, a box or a frame, in the config's order. */
    private val drawn: List<String> = (visible.nodes.map { it.id } + visible.frames.map { it.id }).sortedBy { order[it] }

    /** The stacks: by the box that holds them, null for the top level, the parts in their order. */
    val stacks: Map<String?, List<String>>

    /** The arrows, by their index among the visible ones, said by the order and not drawn. */
    val told: Set<Int>

    /** The arrows, by their index among the visible ones, drawn as a pair of references, with their letter. */
    val cut: Map<Int, String>

    init {
        val containers = buildList<String?> {
            if (spec.parts) add(null)
            visible.frames.filter { it.node.parts }.forEach { add(it.id) }
        }
        // The arrows as placed: turned when the schema runs upward
        val placed = visible.edges.map { if (upward) it.to to it.from else it.from to it.to }

        stacks = containers.associateWith { container ->
            val parts = drawn.filter { parentOf[it] == container }
            val after = placed.mapNotNull { (a, b) ->
                val pa = partIn(container, a)
                val pb = partIn(container, b)
                if (pa != null && pb != null && pa != pb) pa to pb else null
            }.distinct()
            ordered(parts, after)
        }

        val told = mutableSetOf<Int>()
        val cutEdges = mutableListOf<Int>()
        placed.forEachIndexed { i, (a, b) ->
            if (a == b) return@forEachIndexed
            val cutting = containers.filter { container ->
                val pa = partIn(container, a)
                val pb = partIn(container, b)
                (pa != null || pb != null) && pa != pb
            }
            if (cutting.isEmpty()) return@forEachIndexed
            // Told by the order: both ends are parts of one stack, the second right after the first
            // The top level's stack is under null: a single container is read by the count, not by singleOrNull
            val stack = if (cutting.size == 1) stacks.getValue(cutting[0]) else null
            if (stack != null && a in stack && b in stack && stack.indexOf(b) == stack.indexOf(a) + 1) told += i
            else cutEdges += i
        }
        this.told = told
        cut = cutEdges.withIndex().associate { (n, i) -> i to letter(n) }
    }

    /** The part of [container]'s stack that [id] is or stands in; null when [id] is not inside it, or is the container itself. */
    private fun partIn(container: String?, id: String): String? {
        var current = id
        while (true) {
            if (current == container) return null
            val parent = parentOf[current]
            if (parent == container) return current
            current = parent ?: return null
        }
    }

    /**
     * [parts] in an order the arrows [after] agree with: each part after those that lead to it,
     * the config's order between parts free to go. Arrows going round in a loop leave the first
     * part waiting in the config's order to go first.
     */
    private fun ordered(parts: List<String>, after: List<Pair<String, String>>): List<String> {
        val waiting = parts.toMutableList()
        val result = mutableListOf<String>()
        while (waiting.isNotEmpty()) {
            val next = waiting.firstOrNull { part -> after.none { (from, to) -> to == part && from in waiting } } ?: waiting.first()
            waiting.remove(next)
            result += next
        }
        return result
    }

    private companion object {
        /** A, B, … Z, then AA, AB… */
        fun letter(n: Int): String = if (n < 26) ('A' + n).toString() else letter(n / 26 - 1) + ('A' + n % 26)
    }
}
