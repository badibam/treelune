package app.treelune.core.demo

import android.content.Context
import app.treelune.core.config.AppSettings
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.navigation.Place
import org.json.JSONObject

/**
 * Whether the demo's notifications are shown (Settings › Demo): on by default, off they are not
 * shown, and what sends them goes on as usual (a message still arrives in Messages, a goal still
 * changes state).
 */
object DemoNotifications {

    /** The setting under which the demo's notifications are shown. */
    const val SETTING = "notifications"

    /**
     * Whether a notification about [toolInstanceId], or else about the place at [place] (an
     * address, Place.address), is the demo's while its notifications are off: a tool or a place of
     * the demo, by its id or by its zone's.
     */
    suspend fun silenced(context: Context, toolInstanceId: String?, place: String?): Boolean {
        val zoneId = when {
            toolInstanceId != null -> if (toolInstanceId.startsWith(DemoContent.PREFIX)) return !shown(context)
                else AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(toolInstanceId)?.zone_id
            place != null -> Place.of(place).zoneId
            else -> null
        }
        return zoneId?.startsWith(DemoContent.PREFIX) == true && !shown(context)
    }

    private suspend fun shown(context: Context): Boolean {
        val result = Coordinator(context).processUserAction("app_config.get", mapOf("category" to AppSettingCategories.DEMO))
        val settings = result.data?.get("settings") as? Map<*, *>
            ?: throw IllegalStateException("Demo settings not read: ${result.error}")
        return AppSettings.read(AppSettingCategories.DEMO, JSONObject(settings), context).boolean(SETTING)
    }
}
