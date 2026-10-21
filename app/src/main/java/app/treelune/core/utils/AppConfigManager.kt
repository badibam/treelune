package app.treelune.core.utils

import android.content.Context
import app.treelune.core.config.DateTimeConfig
import app.treelune.core.ai.domain.AILimitsConfig
import app.treelune.core.services.AppConfigService
import kotlinx.coroutines.runBlocking

/**
 * Singleton manager for app configuration with caching
 * Provides synchronous access to frequently-used config values
 */
object AppConfigManager {

    /** The values read together from the database, replaced whole: a reader sees the old ones or the new ones, never none. */
    private class Snapshot(
        val dayStartHour: Int,
        val weekStartDay: String,
        val dateTimeConfig: DateTimeConfig,
        val aiLimits: AILimitsConfig,
        val uiSounds: Boolean
    )

    @Volatile
    private var snapshot: Snapshot? = null

    /**
     * Initialize cache from database
     * Must be called at app startup
     */
    fun initialize(context: Context) {
        if (snapshot != null) return
        load(context)
    }

    /**
     * Refresh cache from database
     * Call after config changes. The previous values stay readable until the new ones are in:
     * a screen reading them meanwhile (a touch's sound, the day's start) must not find them gone.
     */
    fun refresh(context: Context) {
        load(context)
    }

    /** Reads the values, then swaps them in at once. */
    private fun load(context: Context) {
        try {
            val service = AppConfigService(context)
            val read = runBlocking {
                // The look is applied rather than cached: CurrentTheme holds it for every screen
                app.treelune.core.themes.CurrentTheme.apply(service.getUIAppearance(), app.treelune.core.themes.CurrentTheme.Reason.STORED)
                Snapshot(service.getDayStartHour(), service.getWeekStartDay(), service.getDateTimeConfig(), service.getAILimits(), service.getUISounds())
            }
            snapshot = read
            LogManager.service("AppConfigManager initialized: dayStartHour=${read.dayStartHour}, weekStartDay=${read.weekStartDay}, dateTimeConfig=${read.dateTimeConfig}, aiLimits=${read.aiLimits}")
        } catch (e: Exception) {
            LogManager.service("CRITICAL: Failed to initialize AppConfigManager: ${e.message}", "ERROR", e)
            throw RuntimeException("AppConfigManager initialization failed - app cannot start without valid configuration", e)
        }
    }

    /** The values in place; throws IllegalStateException if not initialized. */
    private fun current(): Snapshot =
        checkNotNull(snapshot) { "AppConfigManager not initialized. Call initialize(context) at app startup." }

    /** Get day start hour (cached) */
    fun getDayStartHour(): Int = current().dayStartHour

    /** Get week start day (cached) */
    fun getWeekStartDay(): String = current().weekStartDay

    /**
     * Get comprehensive date/time configuration (cached).
     * Includes timezone, locale, display formats, and business logic parameters.
     *
     * @return DateTimeConfig with all date/time related settings
     */
    fun getDateTimeConfig(): DateTimeConfig = current().dateTimeConfig

    /** Get AI limits configuration (cached) */
    fun getAILimits(): AILimitsConfig = current().aiLimits

    /** Whether the theme's interface sounds play (cached), read at each touch */
    fun getUISounds(): Boolean = current().uiSounds
}
