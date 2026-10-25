package app.treelune.core.demo

import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The app's first start on a phone it was never installed on: the demo is installed whole on an
 * empty database, through the services as at the start (DemoStartup), on the app's own SQLite —
 * its goals' attempts validated by filters that read the entries through json_extract, which an
 * Android 10 phone's SQLite lacked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoInstallTest {

    @Test
    fun `the demo installs on an empty database at the first start`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()

        assertNull(DemoStartup.run(context))

        val database = AppDatabase.getDatabase(context)
        val zones = database.zoneDao().getAllZones().filter { it.id.startsWith(DemoContent.PREFIX) }
        assertTrue("no demo zone", zones.isNotEmpty())
        val validated = database.toolDataDao().getAllEntries()
            .filter { it.id.startsWith("demo-e-attempt-") && it.state?.contains("\"validated_by\"") == true }
        assertTrue("no goal attempt validated", validated.isNotEmpty())
        // The photos shipped, each prepared and owned by its plant's card
        for (plant in listOf("demo-plant-tomato", "demo-plant-basil", "demo-plant-chilli")) {
            assertEquals("$plant's photo", 1, database.fileDao().getForEntry(plant).size)
        }
        // Its Tasks, the event one emits and a routine listens to, the API one calls
        assertEquals(setOf("demo-t-weather", "demo-t-heat", "demo-t-busy-week"), database.taskDao().getAll().map { it.id }.toSet())
        assertTrue("no demo event", database.eventDao().get("demo-event-busy-week") != null)
        assertTrue("no demo API", database.apiDao().get("demo-api-weather") != null)
        assertEquals("the start installs it once", null, DemoStartup.run(context))
    }
}
