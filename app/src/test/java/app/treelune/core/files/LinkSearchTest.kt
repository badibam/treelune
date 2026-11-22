package app.treelune.core.files

import app.treelune.core.files.LinkSearch.Found
import app.treelune.core.files.LinkSearch.Wanted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What docs/design/linked-files.md promises of « Relier un lien introuvable »: a file is
 * recognised by its name and its size to the byte, and the app never chooses between two.
 */
class LinkSearchTest {

    private val link = Wanted("a", "vol 01.flac", 1000)

    @Test
    fun `one file of the same name and size gives the link its address`() {
        val outcome = LinkSearch.match(listOf(link), listOf(Found("d1", "new", "vol 01.flac", 1000), Found("d2", "other", "vol 02.flac", 1000)))
        assertEquals(mapOf("a" to "new"), outcome.relinked)
    }

    @Test
    fun `the same name with another size is said, not taken`() {
        val outcome = LinkSearch.match(listOf(link), listOf(Found("d1", "new", "vol 01.flac", 999)))
        assertTrue(outcome.relinked.isEmpty())
        assertEquals(listOf(link), outcome.otherSize)
    }

    @Test
    fun `two candidates are said, the app does not choose`() {
        val outcome = LinkSearch.match(listOf(link), listOf(Found("d1", "x", "vol 01.flac", 1000), Found("d2", "y", "vol 01.flac", 1000)))
        assertTrue(outcome.relinked.isEmpty())
        assertEquals(listOf(link), outcome.several)
    }

    @Test
    fun `one file seen through two overlapping folders is one candidate`() {
        val outcome = LinkSearch.match(listOf(link), listOf(Found("d1", "by-parent", "vol 01.flac", 1000), Found("d1", "by-child", "vol 01.flac", 1000)))
        assertEquals(mapOf("a" to "by-parent"), outcome.relinked)
    }

    @Test
    fun `a name nowhere is not found, and two links may find one file`() {
        val twin = Wanted("b", "vol 01.flac", 1000)
        val absent = Wanted("c", "vol 03.flac", 1000)
        val outcome = LinkSearch.match(listOf(link, twin, absent), listOf(Found("d1", "new", "vol 01.flac", 1000)))
        assertEquals(mapOf("a" to "new", "b" to "new"), outcome.relinked)
        assertEquals(listOf(absent), outcome.notFound)
    }
}
