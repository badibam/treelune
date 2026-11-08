package app.treelune.core.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A part's recipe never overwrites a different folder of the same name in the repository of whoever
 * imports it; an identical one is left as it is. What « identical » means: the same files, each of
 * the same mode and bytes.
 */
class RecipeFilesTest {

    private fun file(text: String, mode: String = "100644") = GitHubRepo.RepoFile(mode, text.toByteArray())

    @Test
    fun theSameFiles_areTheSameRecipe() {
        assertTrue(RecipeFiles.same(mapOf("run" to file("a", "100755"), "recipe.json" to file("{}")),
            mapOf("recipe.json" to file("{}"), "run" to file("a", "100755"))))
    }

    @Test
    fun aFileChanged_added_orNoLongerRunnable_isAnotherRecipe() {
        val held = mapOf("run" to file("a", "100755"), "recipe.json" to file("{}"))
        assertFalse(RecipeFiles.same(held, mapOf("run" to file("b", "100755"), "recipe.json" to file("{}"))))
        assertFalse(RecipeFiles.same(held, held + ("notes.md" to file("x"))))
        assertFalse(RecipeFiles.same(held, mapOf("run" to file("a"), "recipe.json" to file("{}"))))
    }
}
