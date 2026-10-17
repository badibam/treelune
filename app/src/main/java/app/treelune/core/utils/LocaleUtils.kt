package app.treelune.core.utils

import android.content.Context
import java.util.*

/**
 * Centralized locale management utilities
 * Provides consistent app locale access with AppConfig integration
 */
object LocaleUtils {
    
    /**
     * The locale the app formats with: its override (Réglages › Format), the phone's otherwise.
     * Read from AppConfigManager's cache, refreshed whenever the settings change: a list of
     * dates formats one per line without reading the settings again.
     */
    fun getAppLocale(): Locale =
        AppConfigManager.getDateTimeConfig().localeOverride?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()

    /**
     * Get available locales for the app
     * Currently returns common locales, could be extended to support user-defined ones
     */
    fun getAvailableLocales(): List<Locale> {
        return listOf(
            Locale.ENGLISH,
            Locale.FRENCH,
            Locale("es"), // Spanish
            Locale("de"), // German
            Locale("it"), // Italian
            Locale("pt"), // Portuguese
        )
    }
    
    /**
     * Get display name for a locale in current app locale
     */
    fun getLocaleDisplayName(locale: Locale, context: Context): String {
        val appLocale = getAppLocale()
        return locale.getDisplayName(appLocale)
    }
}