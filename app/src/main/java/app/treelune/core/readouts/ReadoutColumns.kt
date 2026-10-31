package app.treelune.core.readouts

/**
 * How many columns the cards gathering readouts take (docs/design/readouts.md, « Où il
 * s'affiche »): one, a line each, or two side by side. The home screen sets it for its head and
 * its zone groups' heads (`readout_columns` of its settings, one when absent); a zone for its
 * head and its tool groups' heads, or follows the home screen's (`readout_columns` of the zone,
 * `home`, stored as null). In one column (CurrentTheme.oneColumn) a card takes one whatever is set.
 */
object ReadoutColumns {

    const val KEY = "readout_columns"
    const val HOME = "home"
    const val ONE = "one"
    const val TWO = "two"

    /** What the home screen sets. */
    val HOME_VALUES = listOf(ONE, TWO)

    /** What a zone sets: the home screen's, or its own. */
    val ZONE_VALUES = listOf(HOME, ONE, TWO)

    /** A zone's value as its column stores it: null for the home screen's; any other kept for the check to refuse. */
    fun stored(given: String?): String? = given?.takeIf { it != HOME }

    /**
     * The columns a gathered card takes: the zone's [zone] (null on the home screen, or following
     * it), else the home screen's [home] (null when unset: one); one in [oneColumn] whatever is set.
     */
    fun of(zone: String?, home: String?, oneColumn: Boolean): Int =
        if (oneColumn) 1 else if ((stored(zone) ?: home ?: ONE) == TWO) 2 else 1
}
