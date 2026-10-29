package app.treelune.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the stack of places promises (docs/UI.md, « The stack of places »): every place read back from its
 * address; a place opened with nothing open rebuilt from its parents, opened over something
 * stacked alone; the breadcrumb naming the parent of a place stacked without it; a deleted zone
 * taking its places with it; the entry a tool place opens on handed once.
 */
class PlaceStackTest {

    private val home = Place.Home
    private val course = Place.Zone("z-course")
    private val runs = Place.Tool("t-runs", "z-course")
    private val kitchen = Place.Zone("z-kitchen")

    private val everyPlace = listOf(
        Place.Home, Place.HomeConfig, Place.Settings, Place.SettingsPage("ai_providers"), Place.Identity("i1"),
        Place.Export, Place.Import("content://com.android.providers/document/primary%3ADownload%2FCuisine.zip"), Place.AIProviderKind, Place.AIProvider("p-1", null, null), Place.AIProvider(null, "openai", "p-1"),
        Place.CreateZone, course, Place.ZoneConfig("z-course"), runs,
        Place.ToolConfig("z-course", "tracking", "t-runs"), Place.ToolConfig("z-course", "chart", null),
        Place.Variable("z-course", null), Place.Variable("z-course", "v-1"),
        Place.Routine("a-1", "z-course"), Place.Execution("s-1", "a-1", "z-course"),
        Place.RoutineConfig("z-course", "a-1"), Place.RoutineConfig("z-course", null),
        Place.Task("k-1", "z-course"), Place.TaskRun("r-1", "k-1", "z-course"),
        Place.TaskConfig("z-course", "k-1"), Place.TaskConfig("z-course", null),
        Place.Guide, Place.Chapter("first_steps"), Place.Chat
    )

    @Test
    fun everyPlaceIsReadBackFromItsAddress() {
        everyPlace.forEach { assertEquals(it, Place.of(it.address)) }
    }

    @Test
    fun aParameterWithASlashStaysOne() {
        assertEquals(Place.ToolConfig("z / 1", "notes", null), Place.of(Place.ToolConfig("z / 1", "notes", null).address))
    }

    @Test
    fun openedWithNothingOpenTheStackIsRebuiltFromTheParents() {
        assertEquals(listOf(home, course, runs), PlaceStack.open(listOf(home), runs))
        assertEquals(
            listOf(home, course, Place.Routine("a-1", "z-course"), Place.Execution("s-1", "a-1", "z-course")),
            PlaceStack.open(listOf(home), Place.Execution("s-1", "a-1", "z-course"))
        )
    }

    @Test
    fun openedOverSomethingThePlaceIsStackedAlone() {
        val stack = listOf(home, kitchen, Place.Chat)
        assertEquals(listOf(home, kitchen, Place.Chat, runs), PlaceStack.open(stack, runs))
        // One back returns to the chat
        assertEquals(stack, PlaceStack.pop(PlaceStack.open(stack, runs)))
    }

    @Test
    fun theHomeScreenNeverLeaves() {
        assertEquals(listOf(home), PlaceStack.pop(listOf(home)))
        assertEquals(listOf(home), PlaceStack.popTo(listOf(home, course, runs), -1))
    }

    @Test
    fun theBreadcrumbGoesBackToAnyPlaceUnder() {
        assertEquals(listOf(home, course), PlaceStack.popTo(listOf(home, course, runs, Place.ToolConfig("z-course", "tracking", "t-runs")), 1))
    }

    @Test
    fun theChatIsLaidOverThePlaceUnderIt() {
        assertEquals(1, PlaceStack.baseIndex(listOf(home, kitchen, Place.Chat)))
        assertEquals(3, PlaceStack.baseIndex(listOf(home, kitchen, Place.Chat, runs)))
    }

    @Test
    fun aPlaceStackedWithoutItsParentNamesIt() {
        val names = mapOf(home to "Accueil", kitchen to "Cuisine", Place.Chat to "Conversation", runs to "Sorties", course to "Course")
        val stack = listOf(home, kitchen, Place.Chat, runs)
        val onRuns = PlaceStack.breadcrumb(stack, 3) { names[it] }
        assertEquals(listOf("Accueil", "Cuisine", "Conversation"), onRuns.crumbs.map { it.name })
        assertEquals(listOf(null, null, null), onRuns.crumbs.map { it.parent })
        assertEquals("Course", onRuns.parent)

        // Further on, the place stacked alone carries its parent in the line
        val config = Place.ToolConfig("z-course", "tracking", "t-runs")
        val onConfig = PlaceStack.breadcrumb(stack + config, 4) { names[it] }
        assertEquals("Course", onConfig.crumbs[3].parent)
        assertNull(onConfig.parent)
    }

    @Test
    fun aPlaceUnderItsParentNamesNothingMore() {
        val crumb = PlaceStack.breadcrumb(listOf(home, course, runs), 2) { "x" }
        assertEquals(listOf(null, null), crumb.crumbs.map { it.parent })
        assertNull(crumb.parent)
    }

    @Test
    fun aDeletedZoneTakesItsPlacesWithIt() {
        val stack = listOf(home, kitchen, Place.Chat, course, runs, Place.ToolConfig("z-course", "tracking", "t-runs"))
        assertEquals(listOf(home, kitchen, Place.Chat), PlaceStack.dropZone(stack, "z-course"))
    }

    @Test
    fun aReplacedStepIsSkippedOnTheWayBack() {
        val providers = Place.SettingsPage("ai_providers")
        val new = Place.AIProvider(null, "openai", null)
        val stack = PlaceStack.replace(listOf(home, Place.Settings, providers, Place.AIProviderKind), new)
        assertEquals(listOf(home, Place.Settings, providers, new), stack)
        assertEquals(listOf(home, Place.Settings, providers), PlaceStack.pop(stack))
        assertEquals(listOf(home, new), PlaceStack.replace(listOf(home), new))
    }

    /** An activity started afresh while the process lives on finds the stack, not the entry to open again. */
    @Test
    fun anEntryToOpenIsHandedOnce() {
        Navigator.openings[runs.address] = app.treelune.core.tools.EntryToOpen.Existing("e-1")
        assertEquals(app.treelune.core.tools.EntryToOpen.Existing("e-1"), Navigator.take(runs))
        assertNull(Navigator.take(runs))
    }

    @Test
    fun `a thing moved to another zone stands in it, what is over it kept`() {
        val config = Place.RoutineConfig("old", "r")
        val stack = listOf(Place.Home, Place.Zone("old"), Place.Routine("r", "old"), config)
        assertEquals(listOf(Place.Home, Place.Zone("new"), Place.Routine("r", "new"), config),
            PlaceStack.moved(stack, Place.Routine("r", "old"), Place.Routine("r", "new")))
        assertEquals(stack, PlaceStack.moved(stack, Place.Task("t", "old"), Place.Task("t", "new")))
    }
}
