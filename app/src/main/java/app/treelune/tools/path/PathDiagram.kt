package app.treelune.tools.path

import app.treelune.core.drawing.diagram.DiagramDirection
import app.treelune.core.drawing.diagram.DiagramEdge
import app.treelune.core.drawing.diagram.DiagramFill
import app.treelune.core.drawing.diagram.DiagramNode
import app.treelune.core.drawing.diagram.DiagramShape
import app.treelune.core.drawing.diagram.DiagramSpec
import app.treelune.core.themes.TagColor

/**
 * A run drawn as the shared diagram (docs/design/progression.md, « Les écrans », « L'apparence
 * d'une brique »): one box per stage, inside the stage that holds it, the root being the drawing
 * itself and never a box; an arrow per link, from the stage it comes from to the one it arrives
 * on, in its type's line. The icon says what a stage is (its kind's), the paint how it stands:
 * its state's colour, fill and fading, a mark's colour where the state has none, and faded as soon
 * as one of its marks is. A kind in parts, the root's included, reads its content in parts.
 */
object PathDiagram {

    /**
     * The diagram of the run [stages] belong to, root included, each stage in the state [stateOf]
     * gives (its final state in a closed run) with the marks [marksOf] gives.
     */
    fun of(grammar: PathGrammar, stages: List<Stage>, stateOf: (Stage) -> String?, marksOf: (Stage) -> List<String>): DiagramSpec {
        val root = stages.first { it.isRoot }
        val drawn = PathOrder.planOrder(stages).filter { !it.isRoot }
        val byId = drawn.associateBy { it.id }
        val nodes = drawn.map { stage ->
            val kind = grammar.kind(stage.kind)
            val state = stateOf(stage)?.let { kind?.state(it) }
            val marks = marksOf(stage).mapNotNull { key -> kind?.marks?.firstOrNull { it.key == key } }
            val color = (state?.paint?.color ?: marks.firstNotNullOfOrNull { it.paint?.color })?.let { name -> TagColor.entries.firstOrNull { it.name == name } }
            DiagramNode(
                id = stage.id,
                label = stage.name,
                shape = DiagramShape.ROUNDED,
                thing = null,
                color = color,
                icon = kind?.icon,
                parent = stage.parent?.takeIf { it != root.id },
                parts = kind?.inParts == true,
                fill = if (state?.paint?.fill == true) DiagramFill.FULL else DiagramFill.OUTLINE,
                faded = state?.paint?.faded == true || marks.any { it.paint?.faded == true }
            )
        }
        fun ancestors(id: String) = generateSequence(byId[id]?.parent) { byId[it]?.parent }
        val edges = drawn.flatMap { stage ->
            stage.links.mapNotNull { link ->
                val type = grammar.linkTypes.firstOrNull { it.key == link.type } ?: return@mapNotNull null
                // A link from a stage gone, from the root, or between a stage and what holds it cannot be drawn
                if (link.from !in byId || link.from == stage.id || link.from in ancestors(stage.id) || stage.id in ancestors(link.from)) return@mapNotNull null
                DiagramEdge(link.from, stage.id, null, type.stroke, head = true)
            }
        }
        return DiagramSpec(DiagramDirection.TOP_DOWN, nodes, edges, parts = grammar.kind(root.kind)?.inParts == true)
    }
}
