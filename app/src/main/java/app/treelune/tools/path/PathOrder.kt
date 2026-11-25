package app.treelune.tools.path

import app.treelune.core.terms.Term
import org.json.JSONArray
import org.json.JSONObject

/**
 * The order states are computed in (« Les règles », the order of computation): each stage once,
 * after what its rules may read the state of — its content, at any depth, before it; the stages
 * its links of a type a rule reads come from, before it; the stage before it in its parent's
 * content, before it. The order comes from the structure alone, never from what a rule says: a
 * rule reading the state of a stage outside these relations is refused when it is saved
 * (PathCheck).
 */
object PathOrder {

    /** The link types the states' rules of [grammar] read (`{"self": "links", "type": …}`): those whose loops are refused. */
    fun linkTypesRead(grammar: PathGrammar): Set<String> =
        grammar.kinds.flatMap { kind -> kind.states.mapNotNull { it.whenRule } }.flatMap { linkTypesIn(it) }.toSet()

    /** The link types [json] names under `self`, at any depth. */
    fun linkTypesIn(json: Any?): Set<String> = when (json) {
        is JSONObject -> {
            val own = if (json.optString(Term.SELF) == PathSelf.LINKS) setOfNotNull(json.optString(PathSelf.TYPE).takeIf { it.isNotEmpty() }) else emptySet()
            own + json.keys().asSequence().flatMap { linkTypesIn(json.opt(it)) }
        }
        is JSONArray -> (0 until json.length()).flatMap { linkTypesIn(json.opt(it)) }.toSet()
        else -> emptySet()
    }

    /** What the order says: the stages in it, or a loop, the stages caught in it. */
    sealed interface Result {
        data class Ordered(val stages: List<Stage>) : Result
        data class Loop(val stages: List<Stage>) : Result
    }

    /**
     * The stages of one run in the order their states are computed: bottom-up, the sources of a
     * link of a type in [readTypes] before the stage it arrives on, the stage before before the
     * next; among stages free at once, the plan's order (parent, then position).
     */
    fun of(stages: List<Stage>, readTypes: Set<String>): Result {
        val byId = stages.associateBy { it.id }
        // What each stage waits for
        val before = stages.associate { it.id to mutableSetOf<String>() }
        val children = stages.groupBy { it.parent }
        for (stage in stages) {
            children[stage.id].orEmpty().forEach { before.getValue(stage.id) += it.id }
            stage.links.filter { it.type in readTypes && it.from in byId }.forEach { before.getValue(stage.id) += it.from }
            previous(stage, children)?.let { before.getValue(stage.id) += it.id }
        }
        val planOrder = planOrder(stages)
        val done = LinkedHashSet<String>()
        while (done.size < stages.size) {
            val next = planOrder.firstOrNull { it.id !in done && before.getValue(it.id).all { b -> b in done } }
                ?: return Result.Loop(stages.filter { it.id !in done && waitsOnItself(it.id, before, done) })
            done += next.id
        }
        return Result.Ordered(done.map { byId.getValue(it) })
    }

    /** The stage before [stage] in its parent's content; null for the first, and for the root. */
    fun previous(stage: Stage, children: Map<String?, List<Stage>>): Stage? {
        if (stage.parent == null) return null
        return children[stage.parent].orEmpty().filter { it.position < stage.position }.maxByOrNull { it.position }
    }

    /** The stages in the plan's order: each parent then its content by position, depth first. */
    fun planOrder(stages: List<Stage>): List<Stage> {
        val children = stages.groupBy { it.parent }
        val out = mutableListOf<Stage>()
        fun walk(parent: String?) {
            children[parent].orEmpty().sortedBy { it.position }.forEach { out += it; walk(it.id) }
        }
        walk(null)
        // A stage whose parent is missing would be lost: put at the end rather than dropped
        return out + stages.filter { it !in out }
    }

    /** Whether [id] reaches itself through what it waits for, among what is not done. */
    private fun waitsOnItself(id: String, before: Map<String, Set<String>>, done: Set<String>): Boolean {
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque(before.getValue(id).filter { it !in done })
        while (stack.isNotEmpty()) {
            val next = stack.removeLast()
            if (next == id) return true
            if (seen.add(next)) stack += before[next].orEmpty().filter { it !in done }
        }
        return false
    }
}
