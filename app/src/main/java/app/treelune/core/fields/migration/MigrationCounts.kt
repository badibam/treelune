package app.treelune.core.fields.migration

/**
 * What a config change does to the recorded entries, worded from its report (the "migration"
 * of a result, ToolInstanceService.report): the values removed, the entries deleted for a
 * required value they lost, each reason a tool type's own rules give for the entries they
 * remove, and the values read otherwise. The confirmation screen, the reason the AI's change
 * waits for approval and the refusal sent to the AI say the same parts.
 */
object MigrationCounts {

    /**
     * The parts that are not zero, in that order: « 2 valeur(s) retirée(s) », « 12 révisions
     * du sens « Français → Anglais » supprimées »…
     *
     * @param text The shared string of a key (s::shared)
     */
    fun parts(migration: Map<*, *>, text: (String) -> String): List<String> {
        fun count(key: String) = (migration[key] as? Number)?.toInt() ?: 0
        return listOfNotNull(
            count("removed_values").takeIf { it > 0 }?.let { text("migration_part_removed").format(it) },
            count("deleted_without_value").takeIf { it > 0 }?.let { text("migration_part_deleted").format(it) }
        ) + (migration["removals"] as? List<*>).orEmpty().map { it.toString() } +
            listOfNotNull(count("reinterpreted_values").takeIf { it > 0 }?.let { text("migration_part_reinterpreted").format(it) })
    }
}
