package app.treelune.tools.path.ui

/**
 * The pages inside a Path (Place.ToolPart), each named by what it shows: a stage's sheet
 * (`stage/<id>`), the edit of its plan (`edit/<id>`).
 */
object PathParts {
    private const val STAGE = "stage/"
    private const val EDIT = "edit/"

    fun stage(id: String) = "$STAGE$id"

    fun edit(id: String) = "$EDIT$id"

    /** The stage whose plan a part edits; null for a part that is no edit. */
    fun editOf(part: String): String? = part.takeIf { it.startsWith(EDIT) }?.removePrefix(EDIT)

    /** The stage a part names; null for a part that is no stage's sheet. */
    fun stageOf(part: String): String? = part.takeIf { it.startsWith(STAGE) }?.removePrefix(STAGE)
}
