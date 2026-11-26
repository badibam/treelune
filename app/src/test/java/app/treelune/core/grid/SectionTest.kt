package app.treelune.core.grid

import org.junit.Assert.assertEquals
import org.junit.Test

/** A page's sections: each group then its hidden part, the ungrouped last, each named by a key the grid editor reads back. */
class SectionTest {

    @Test
    fun `each section's key reads back, a hidden part apart from its group, the ungrouped one included`() {
        val sections = Section.ordered(listOf("A", "B"))
        assertEquals(listOf(Section("A"), Section("A", true), Section("B"), Section("B", true), Section(null), Section(null, true)), sections)
        sections.forEach { assertEquals(it, Section.ofKey(it.key)) }
        assertEquals(6, sections.map { it.key }.toSet().size)
    }
}
