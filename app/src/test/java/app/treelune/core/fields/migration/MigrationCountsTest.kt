package app.treelune.core.fields.migration

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A config change's confirmation says why each entry goes: those deleted for a required value
 * they lost under that reason only, those a tool type's rule removes under the rule's own
 * sentence (a deck's direction removed takes its reviews), never one count for both.
 */
class MigrationCountsTest {

    private val text: (String) -> String = { key -> "$key %d" }

    @Test
    fun `each reason is its own part, in order, the zero counts left out`() {
        val migration = mapOf(
            "removed_values" to 2,
            "deleted_entries" to 15,
            "deleted_without_value" to 3,
            "removals" to listOf("12 révision(s) du sens « Français → Anglais » supprimée(s)"),
            "reinterpreted_values" to 0
        )
        assertEquals(
            listOf("migration_part_removed 2", "migration_part_deleted 3", "12 révision(s) du sens « Français → Anglais » supprimée(s)"),
            MigrationCounts.parts(migration, text)
        )
    }

    @Test
    fun `entries removed by a rule alone say nothing of a required value`() {
        val migration = mapOf("deleted_entries" to 5, "deleted_without_value" to 0, "removals" to listOf("5 élément(s) coché(s) retiré(s)"))
        assertEquals(listOf("5 élément(s) coché(s) retiré(s)"), MigrationCounts.parts(migration, text))
    }
}
