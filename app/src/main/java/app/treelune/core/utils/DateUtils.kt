package app.treelune.core.utils

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

/**
 * Centralized date utilities for the entire application
 * Ensures consistent date formatting and parsing across all components
 *
 * IMPORTANT: All operations use the timezone configured in AppConfig, NOT the system timezone.
 * This ensures consistency across the application regardless of device timezone changes.
 *
 * Every function takes that timezone as a parameter defaulting to the configured one, so a
 * caller can compute in a stated zone rather than the app's. Production passes nothing and
 * gets the configured zone; the default is read per call, never cached.
 */
object DateUtils {

    // Date format constants
    private const val DISPLAY_DATE_FORMAT = "dd/MM/yyyy"
    private const val DISPLAY_TIME_FORMAT = "HH:mm"
    private const val FULL_DATE_TIME_FORMAT = "dd/MM/yy HH:mm"

    // DateTimeFormatter instances - use AppConfig timezone
    private val displayDateFormatter = DateTimeFormatter.ofPattern(DISPLAY_DATE_FORMAT)
    private val displayTimeFormatter = DateTimeFormatter.ofPattern(DISPLAY_TIME_FORMAT)
    private val fullDateTimeFormatter = DateTimeFormatter.ofPattern(FULL_DATE_TIME_FORMAT)

    /**
     * Get the configured timezone from AppConfig
     */
    private fun getConfiguredZone(): ZoneId {
        return AppConfigManager.getDateTimeConfig().getZoneId()
    }

    /**
     * Format timestamp for date display (dd/MM/yyyy)
     */
    fun formatDateForDisplay(timestamp: Long, zone: ZoneId = getConfiguredZone()): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = ZonedDateTime.ofInstant(instant, zone)
        return displayDateFormatter.format(zonedDateTime)
    }

    /**
     * Format timestamp for time display (HH:mm)
     */
    fun formatTimeForDisplay(timestamp: Long, zone: ZoneId = getConfiguredZone()): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = ZonedDateTime.ofInstant(instant, zone)
        return displayTimeFormatter.format(zonedDateTime)
    }

    /** A time of day as the display shows times (HH:mm). */
    fun formatTimeOfDay(time: java.time.LocalTime): String = displayTimeFormatter.format(time)

    /**
     * Format timestamp for full date-time display (dd/MM/yy HH:mm)
     */
    fun formatFullDateTime(timestamp: Long, zone: ZoneId = getConfiguredZone()): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = ZonedDateTime.ofInstant(instant, zone)
        return fullDateTimeFormatter.format(zonedDateTime)
    }

    /** A day as the display writes days (dd/MM/yyyy); null when [text] is not one. */
    fun parseDisplayDate(text: String): LocalDate? = try {
        LocalDate.parse(text, displayDateFormatter)
    } catch (e: DateTimeParseException) {
        null
    }

    /** [date] as the display writes days (dd/MM/yyyy). */
    fun formatDisplayDate(date: LocalDate): String = displayDateFormatter.format(date)

    /** Today on the calendar: the date of now, the date an instant written now is on. The day of the app is TimeResolver.today(). */
    fun calendarToday(zone: ZoneId = getConfiguredZone()): LocalDate = LocalDate.now(zone)

    /** The first day of the week the user set. */
    fun weekStart(): DayOfWeek = DayOfWeek.valueOf(AppConfigManager.getWeekStartDay().uppercase())

    /** The day a date picker of Material hands back: midnight UTC of that day, by its convention. */
    fun dateOfPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    /** [date] as a date picker of Material takes it: midnight UTC of that day. */
    fun pickerMillisOf(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * Get today's date formatted for display
     */
    fun getTodayFormatted(zone: ZoneId = getConfiguredZone()): String {
        return formatDateForDisplay(System.currentTimeMillis(), zone)
    }

    /**
     * A calendar set to [timestamp], in the app's timezone.
     *
     * The only way the code gets a Calendar: Calendar.getInstance() with no argument takes the
     * device's timezone, which differs from the app's as soon as the user sets an override --
     * and then days, weeks and months start at a different moment from the dates shown beside
     * them. scripts/check_timezone.py refuses it everywhere else.
     */
    fun calendarAt(timestamp: Long, zone: ZoneId = getConfiguredZone()): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = timestamp }

    /**
     * Format [timestamp] with a pattern of the caller's choosing, in the app's timezone.
     *
     * For formats DateUtils has no dedicated function for -- a log line, a file name. A
     * SimpleDateFormat built by hand takes the device's timezone, like Calendar.getInstance().
     */
    fun format(timestamp: Long, pattern: String, zone: ZoneId = getConfiguredZone()): String =
        DateTimeFormatter.ofPattern(pattern).format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone))

    /**
     * Get current time formatted for display (HH:mm)
     */
    fun getCurrentTimeFormatted(zone: ZoneId = getConfiguredZone()): String {
        return formatTimeForDisplay(System.currentTimeMillis(), zone)
    }

    /**
     * Parse time string to hour and minute (HH:mm)
     *
     * The range is checked here rather than left to whoever uses the pair: an hour of 99
     * parses as an integer and only fails further along, where it is no longer obvious that
     * the time string was the problem.
     *
     * @return the hour and minute, or null if the string is not a valid HH:mm time
     */
    fun parseTime(timeString: String, zone: ZoneId = getConfiguredZone()): Pair<Int, Int>? {
        val parts = timeString.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return Pair(hour, minute)
    }

    /**
     * Combine date string (dd/MM/yyyy) and time string (HH:mm) into timestamp
     *
     * @return that moment, or null if either string cannot be read
     */
    fun combineDateTime(dateString: String, timeString: String, zone: ZoneId = getConfiguredZone()): Long? {
        val localDate = parseDisplayDate(dateString) ?: return null
        val (hour, minute) = parseTime(timeString, zone) ?: return null
        val localDateTime = localDate.atTime(hour, minute, 0, 0)
        return localDateTime.atZone(zone).toInstant().toEpochMilli()
    }

    // ================================================================
    // ISO 8601 Conversion Helpers (for custom fields DATE/TIME/DATETIME)
    // ================================================================

    /**
     * A day as a DATE field stores it ("2026-10-14") as the display writes days ("14/10/2026");
     * null when [text] is not one.
     */
    fun formatIsoDate(text: String): String? = try {
        formatDisplayDate(LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE))
    } catch (e: DateTimeParseException) {
        null
    }

    /**
     * Parse ISO 8601 time string to timestamp (HH:MM → Long, today's date)
     * Used for custom fields TIME type
     *
     * @return that time today, or null if the string is not an ISO 8601 time
     */
    fun parseIso8601Time(timeStr: String, zone: ZoneId = getConfiguredZone()): Long? {
        return try {
            val localTime = LocalTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_TIME)
            val today = LocalDate.now(zone)
            val zonedDateTime = ZonedDateTime.of(today, localTime, zone)
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * Convert timestamp to ISO 8601 date string (Long → YYYY-MM-DD)
     * Used for custom fields DATE type
     */
    fun timestampToIso8601Date(timestamp: Long, zone: ZoneId = getConfiguredZone()): String {
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        return zonedDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /**
     * Convert timestamp to ISO 8601 time string (Long → HH:MM)
     * Used for custom fields TIME type
     */
    fun timestampToIso8601Time(timestamp: Long, zone: ZoneId = getConfiguredZone()): String {
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        return zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

}
