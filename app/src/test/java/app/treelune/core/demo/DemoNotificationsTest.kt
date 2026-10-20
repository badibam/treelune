package app.treelune.core.demo

import android.app.NotificationManager
import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.AppSettingCategories
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The demo's notifications (Settings › Demo): shown by default; off, a demo tool's or a demo
 * place's are not shown and their sending still succeeds, while the user's are shown whatever
 * the setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoNotificationsTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val coordinator = Coordinator(context)

    private fun shownCount() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size

    private suspend fun send(vararg about: Pair<String, String>) {
        val sent = coordinator.processUserAction("notifications.send", mapOf("title" to "Reminder", "priority" to "default", *about))
        assertTrue(sent.error, sent.isSuccess)
    }

    private suspend fun demoNotifications(on: Boolean) {
        val set = coordinator.processUserAction("app_config.set", mapOf("category" to AppSettingCategories.DEMO,
            "settings" to mapOf(DemoStartup.INSTALL_ON_UPDATE to true, DemoNotifications.SETTING to on)))
        assertTrue(set.error, set.isSuccess)
    }

    @Test
    fun `a demo tool's notification is shown by default`() = runBlocking {
        send("tool_instance_id" to "demo-tool")
        assertEquals(1, shownCount())
    }

    @Test
    fun `off, the demo's are not shown and the user's are`() = runBlocking {
        demoNotifications(on = false)
        send("tool_instance_id" to "demo-tool")
        send("place" to "zone/demo-zone")
        assertEquals("the demo's left out", 0, shownCount())
        send("place" to "zone/my-zone")
        assertEquals("the user's shown", 1, shownCount())
    }
}
