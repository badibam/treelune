package app.treelune.core.utils

import android.content.Context
import app.treelune.core.strings.Strings

/**
 * Formatting utilities for durations and relative times
 * Generic utilities used across the application for consistent formatting
 */
object FormatUtils {

    /**
     * Format relative time for future timestamp
     * Examples: "dans 2h", "dans 15 min", "dans 3 jours", "maintenant"
     *
     * @param timestamp Future timestamp in milliseconds
     * @param context Android context for strings
     * @return Formatted relative time string
     */
    fun formatRelativeTime(timestamp: Long, context: Context): String {
        val s = Strings.`for`(context = context)
        val now = System.currentTimeMillis()
        val diffMs = timestamp - now

        // Past or now
        if (diffMs <= 0) {
            return s.shared("time_now")
        }

        val seconds = diffMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> s.shared("time_in_days").format(days)
            hours > 0 -> s.shared("time_in_hours").format(hours)
            minutes > 0 -> s.shared("time_in_minutes").format(minutes)
            else -> s.shared("time_in_seconds").format(seconds)
        }
    }

    /** A size in bytes as the user reads it: "412 B", "12.3 KB", "1.2 MB", in the app's language. */
    fun formatFileSize(bytes: Long, context: Context): String {
        val s = Strings.`for`(context = context)
        return when {
            bytes < 1024 -> s.shared("size_bytes").format(bytes)
            bytes < 1024 * 1024 -> s.shared("size_kilobytes").format(bytes / 1024.0)
            else -> s.shared("size_megabytes").format(bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Format relative time for past timestamp
     * Examples: "il y a 2h", "il y a 15 min", "il y a 3 jours", "à l'instant"
     *
     * @param timestamp Past timestamp in milliseconds
     * @param context Android context for strings
     * @return Formatted relative time string
     */
    fun formatRelativeTimePast(timestamp: Long, context: Context): String {
        val s = Strings.`for`(context = context)
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp

        // Future or now
        if (diffMs <= 0) {
            return s.shared("time_now")
        }

        val seconds = diffMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days == 1L -> s.shared("time_ago_day_one")
            days > 0 -> s.shared("time_ago_days").format(days)
            hours > 0 -> s.shared("time_ago_hours").format(hours)
            minutes > 0 -> s.shared("time_ago_minutes").format(minutes)
            else -> s.shared("time_ago_seconds").format(seconds)
        }
    }

    /**
     * The short form of [formatRelativeTimePast], for a tile, where it stands beside a title it
     * must not crowd: "12 d", "3 h", "5 min", "now" — the date being past, "ago" says nothing more.
     */
    fun formatRelativeTimeShort(timestamp: Long, context: Context): String {
        val s = Strings.`for`(context = context)
        val diffMs = System.currentTimeMillis() - timestamp
        if (diffMs <= 0) return s.shared("time_now")
        val minutes = diffMs / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> s.shared("time_short_days").format(days)
            hours > 0 -> s.shared("time_short_hours").format(hours)
            minutes > 0 -> s.shared("time_short_minutes").format(minutes)
            else -> s.shared("time_now")
        }
    }
}
