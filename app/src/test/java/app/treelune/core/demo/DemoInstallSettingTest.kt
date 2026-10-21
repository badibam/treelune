package app.treelune.core.demo

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.AppSettingCategories
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * « Install the demo at each update » (Settings › Demo, DemoStartup): switched off, the demo
 * stays and an update of the app leaves it as it is; switched on again, nothing is installed
 * before the next update. An update is the package's last update time changing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoInstallSettingTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val database get() = AppDatabase.getDatabase(context)
    private val plant = "demo-plant-tomato"

    /** The app updated: its package's last update time moved on. */
    private fun update() {
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).lastUpdateTime += 60_000
    }

    private suspend fun installOnUpdate(on: Boolean) {
        val set = Coordinator(context).processUserAction("app_config.set", mapOf("category" to AppSettingCategories.DEMO,
            "settings" to mapOf(DemoStartup.INSTALL_ON_UPDATE to on, DemoNotifications.SETTING to true)))
        assertTrue(set.error, set.isSuccess)
    }

    private suspend fun demoZones() = database.zoneDao().getAllZones().count { it.id.startsWith(DemoContent.PREFIX) }

    @Test
    fun `switched off, the demo stays and an update leaves it, switched on, nothing before the next update`() = runBlocking {
        DemoFixture.installed(context)
        val zones = demoZones()
        assertTrue(zones > 0)

        installOnUpdate(false)
        assertEquals("switching it off removes nothing", zones, demoZones())

        // A card of the demo gone: an update with the setting off does not bring it back
        database.toolDataDao().deleteById(plant)
        update()
        assertNull(DemoStartup.run(context))
        assertEquals(zones, demoZones())
        assertNull("the update installed nothing", database.toolDataDao().getById(plant))

        installOnUpdate(true)
        assertNull(DemoStartup.run(context))
        assertNull("switched on, nothing until the next update", database.toolDataDao().getById(plant))

        update()
        assertNull(DemoStartup.run(context))
        assertNotNull("the next update installs it", database.toolDataDao().getById(plant))
    }
}
