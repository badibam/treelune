package app.treelune.core.reading

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.selection.PeriodType
import app.treelune.core.selection.TimeResolver
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A grouped reading (docs/BRICKS.md, « Reading »): entries grouped by a field's value, a date
 * rounded to the user's day, week or month; a group without entries exists only where the
 * values list ahead (the units of the period, as the period says, the options of a choice), and
 * counts 0; an entry of several values goes into each of their groups.
 */
class GroupedReadingTest {

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(day: Int, hour: Int, month: Int = 9) = LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    /** Days start at 4:00, weeks on Monday; read on Wednesday 16 September 2026 at 15:00. */
    private val calendar = TimeResolver(at(16, 15), zone, dayStartHour = 4, weekStartDay = "MONDAY")

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) = FieldDefinition(name, name, null, type, false, config)
    private val timestamp = field("timestamp", FieldType.DATETIME)
    private val cups = field("cups", FieldType.NUMERIC, mapOf("decimals" to 0))
    private val category = field("category", FieldType.CHOICE, mapOf("options" to app.treelune.core.fields.ChoiceSettings.storedOptions(listOf("food", "rent", "fun"), emptyMap())))
    private val with = field("with", FieldType.REFERENCE)

    private var ids = 0
    private fun entry(timestamp: Long, data: Map<String, Any?> = emptyMap()) = mapOf("id" to "e${ids++}", "timestamp" to timestamp, "data" to data)

    private fun byDay(keep: JSONObject?, reduction: Reduction) = ReadingGroup("timestamp", PeriodType.DAY, keep, reduction)
    private fun keep(op: String, right: Any) = JSONObject().put("op", op).put("right", JSONObject().put("constant", right))

    /** Monday 14 to Wednesday 16 as the period's days. */
    private val week = GroupedReading.units(at(14, 4), at(16, 15), PeriodType.DAY, calendar)

    private fun read(entries: List<Map<String, Any?>>, path: String?, f: FieldDefinition?, reduction: Reduction, group: ReadingGroup, by: FieldDefinition, listed: List<Any>, kept: (Any) -> Boolean = { true }) =
        GroupedReading.reduce(entries.reversed(), path, f, reduction, group, by, listed, calendar, kept, calendar.reference)

    @Test
    fun `a day without an entry counts 0 and is kept under at most 2`() {
        // Monday 3 cups, Tuesday none, Wednesday 1
        val entries = listOf(entry(at(14, 8), mapOf("cups" to 2)), entry(at(14, 16), mapOf("cups" to 1)), entry(at(16, 9), mapOf("cups" to 1)))
        val result = read(entries, "data.cups", cups, Reduction.SUM, byDay(keep("<=", 2), Reduction.COUNT), timestamp, week) { (it as Number).toDouble() <= 2 }
        assertEquals(2, (result as ReadingResult.Value).value)
    }

    @Test
    fun `days are cut at the hour the day starts, weeks on its first day`() {
        // 2:00 on Tuesday is still Monday
        assertEquals(listOf(at(14, 4)), GroupedReading.keysOf(at(15, 2), timestamp, PeriodType.DAY, calendar))
        assertEquals(listOf(at(14, 4)), GroupedReading.keysOf(at(20, 12), timestamp, PeriodType.WEEK, calendar))
        assertEquals(listOf(at(1, 4)), GroupedReading.keysOf(at(16, 12), timestamp, PeriodType.MONTH, calendar))
        // A day of the change of hour is one day
        assertEquals(3, GroupedReading.units(at(24, 12, month = 10), at(26, 12, month = 10), PeriodType.DAY, calendar).size)
    }

    @Test
    fun `the period decides, its days to come are groups without entries`() {
        val sunday = GroupedReading.units(at(14, 4), at(21, 3), PeriodType.DAY, calendar)
        assertEquals(7, sunday.size)
        val days = read(listOf(entry(at(14, 8))), null, null, Reduction.COUNT, byDay(null, Reduction.COUNT), timestamp, sunday)
        assertEquals(7, (days as ReadingResult.Value).value)
        // « il y a 7 jours → maintenant » cuts 8 days
        assertEquals(8, GroupedReading.units(at(9, 15), at(16, 15), PeriodType.DAY, calendar).size)
    }

    @Test
    fun `an average of counts per day has decimals`() {
        val entries = listOf(entry(at(14, 8)), entry(at(14, 9)), entry(at(16, 9)))
        val result = read(entries, null, null, Reduction.COUNT, byDay(null, Reduction.AVERAGE), timestamp, week) as ReadingResult.Value
        assertEquals(1.0, result.value)
        assertEquals(ReadingGroup.AVERAGE_COUNT_FIELD, result.field)
    }

    @Test
    fun `an option of a choice without an entry is an empty group`() {
        val entries = listOf(entry(at(14, 8), mapOf("category" to "food")), entry(at(15, 8), mapOf("category" to "food")))
        val group = ReadingGroup("data.category", null, keep("=", 0), Reduction.COUNT)
        val listed = GroupedReading.listed(group, category, null, calendar.reference, calendar)
        assertEquals(listOf("food", "rent", "fun"), listed)
        val result = read(entries, null, null, Reduction.COUNT, group, category, listed) { (it as Number).toInt() == 0 }
        assertEquals(2, (result as ReadingResult.Value).value)
    }

    @Test
    fun `an entry with two people counts in both groups`() {
        fun person(id: String) = mapOf("kind" to "ENTRY", "id" to id)
        val entries = listOf(entry(at(14, 8), mapOf("with" to listOf(person("anna"), person("paul")))), entry(at(15, 8), mapOf("with" to listOf(person("anna")))))
        val group = ReadingGroup("data.with", null, null, Reduction.MAX)
        val result = read(entries, null, null, Reduction.COUNT, group, with, emptyList())
        assertEquals(2, (result as ReadingResult.Value).value)
        assertEquals(2, (read(entries, null, null, Reduction.COUNT, group.copy(reduction = Reduction.COUNT), with, emptyList()) as ReadingResult.Value).value)
    }

    @Test
    fun `an entry without an answer fails the whole reading`() {
        val entries = listOf(entry(at(14, 8), mapOf("cups" to 2)), entry(at(15, 8)))
        val result = read(entries, "data.cups", cups, Reduction.SUM, byDay(null, Reduction.COUNT), timestamp, week)
        assertEquals(FailureReason.MISSING_VALUE, (result as ReadingResult.Failure).reason)
    }

    @Test
    fun `the last of groups is refused, as a unit on what is not a date and an instant without one`() {
        val fields = mapOf("timestamp" to timestamp, "data.cups" to cups, "data.category" to category)
        assertNotNull(byDay(null, Reduction.LAST).problem(fields, cups) { it })
        assertNull(byDay(null, Reduction.AVERAGE).problem(fields, cups) { it })
        assertNotNull(ReadingGroup("data.category", PeriodType.DAY, null, Reduction.COUNT).problem(fields, cups) { it })
        assertNotNull(ReadingGroup("timestamp", null, null, Reduction.COUNT).problem(fields, cups) { it })
        assertNull(ReadingGroup("data.category", null, null, Reduction.COUNT).problem(fields, cups) { it })
    }

    @Test
    fun `its stored form reads back`() {
        val group = byDay(keep(">=", "PT30M"), Reduction.COUNT)
        assertEquals(group.toJson().toString(), ReadingGroup.fromJson(group.toJson()) { it }.toJson().toString())
    }
}
