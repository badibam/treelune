package app.treelune.tools.path.ui

/**
 * The pages inside a Path (Place.ToolPart), each named by what it shows: a stage's sheet
 * (`stage/<id>`).
 */
object PathParts {
    private const val STAGE = "stage/"

    fun stage(id: String) = "$STAGE$id"

    /** The stage a part names; null for a part that is no stage's sheet. */
    fun stageOf(part: String): String? = part.takeIf { it.startsWith(STAGE) }?.removePrefix(STAGE)
}
