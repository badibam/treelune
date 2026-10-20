package app.treelune.core.demo

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import app.treelune.core.selection.TimeResolver
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Covers the demo's entries, generated at fixed moments: the same demo from
 * the same moment, nothing dated after the install but due dates and appointments, one stopwatch running, every
 * text given in both languages and every text used, every reference landing on a card, the rush
 * week showing across the zones, and what the free models' bench reads (docs/design/local-models.md).
 */
class DemoDataTest {

    private val zone = ZoneId.of("Europe/Paris")
    private fun asset(name: String) = JSONObject(File("src/main/assets/demo/$name").readText(Charsets.UTF_8))
    private val hand = asset("entries.json")

    /** A Wednesday afternoon, a Monday at dawn and a Sunday night: the week's edges. */
    private val moments = listOf("2026-10-01T15:30", "2026-10-05T06:10", "2026-10-04T23:50")
        .map { ZonedDateTime.of(java.time.LocalDateTime.parse(it), zone).toInstant().toEpochMilli() }

    private fun data(now: Long, weekStart: String = "monday") = DemoData(TimeResolver(now, zone, 4, weekStart), hand)
    private fun at(moment: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(moment), zone).toInstant().toEpochMilli()
    private fun all(now: Long) = data(now).entries().values.flatten()

    @Test
    fun `the same moment gives the same demo`() {
        val now = moments[0]
        assertEquals(data(now).entries().mapValues { (_, e) -> e.map { it.toString() } },
            data(now).entries().mapValues { (_, e) -> e.map { it.toString() } })
    }

    @Test
    fun `nothing is dated after the install, the running stopwatch started before it`() {
        for (now in moments) {
            data(now).entries().filterKeys { it != "demo-work-agenda" }.values.flatten()
                .forEach { e -> if (e.has("timestamp")) assertTrue(e.toString(), e.getLong("timestamp") <= now) }
            val running = all(now).filter { it.optJSONObject("state")?.has("running") == true }
            assertEquals(1, running.size)
            assertEquals(now - 40 * 60_000, running.single().getLong("timestamp"))
        }
    }

    @Test
    fun `every id is prefixed and unique`() {
        val ids = all(moments[0]).map { it.getString("id") }
        assertTrue(ids.all { it.startsWith(DemoContent.PREFIX) })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every text is given in both languages, and every text is used`() {
        val used = mutableSetOf<String>()
        fun collect(value: Any?) {
            when (value) {
                is JSONObject -> { value.optString("@").takeIf { it.isNotEmpty() }?.let(used::add); value.keys().forEach { collect(value.get(it)) } }
                is JSONArray -> (0 until value.length()).forEach { collect(value.get(it)) }
                is String -> if (value.startsWith("@")) used.add(value.substring(1))
            }
        }
        collect(asset("structure.json"))
        collect(hand)
        moments.forEach { now -> all(now).forEach(::collect) }
        for (language in listOf("texts-en.json", "texts-fr.json")) {
            val texts = asset(language)
            assertEquals(language, emptySet<String>(), used - texts.keys().asSequence().toSet())
            assertEquals(language, emptySet<String>(), texts.keys().asSequence().toSet() - used)
            // Every entry resolves whole, values put in
            all(moments[0]).forEach { DemoContent.resolve(it, texts) }
        }
    }

    @Test
    fun `every reference lands on a card of the demo`() {
        val entries = data(moments[0]).entries()
        val cards = (entries.getValue("demo-kitchen-foods") + entries.getValue("demo-balcony-plants")).map { it.getString("id") }.toSet()
        entries.values.flatten().mapNotNull { it.optJSONObject("extra") }.forEach { extra ->
            extra.keys().forEach { key -> extra.optJSONObject(key)?.takeIf { it.optString("kind") == "ENTRY" }?.let { assertTrue(it.getString("id") in cards) } }
        }
    }

    @Test
    fun `the rush week shows, one short run, short nights, evenings ignored, the town hall's hours`() {
        val now = moments[0]
        val d = data(now)
        val entries = d.entries()
        val rushStart = d.at(d.day(DemoData.RUSH, 1), 0, 0)
        val rushEnd = d.at(d.day(DemoData.RUSH - 1, 1), 0, 0)
        fun inRush(e: JSONObject) = e.optLong("timestamp") in rushStart until rushEnd
        assertEquals(1, entries.getValue("demo-course-runs").count(::inRush))
        assertTrue(entries.getValue("demo-course-sleep").filter(::inRush).all { it.getJSONObject("data").getLong("value") < 7 * 3_600_000 })
        assertTrue(entries.getValue("demo-italian-evening").filter(::inRush).count { it.getJSONObject("state").getString("status") == "ignored" } >= 6)
        assertTrue(entries.getValue("demo-work-hours").filter(::inRush).all { it.getString("name") == "Mairie de Villeurbanne" })
        val rushHours = entries.getValue("demo-work-hours").filter(::inRush).sumOf { it.getJSONObject("data").getLong("value") } / 3_600_000.0
        assertTrue(rushHours.toString(), rushHours >= 45)
    }

    @Test
    fun `afloat, a task late and notified, one due within the minute, milk unchecked, the last invoicing message unread`() {
        val now = moments[0]
        val entries = data(now).entries()
        val tasks = entries.getValue("demo-work-tasks").associateBy { it.getString("id") }
        val late = tasks.getValue("demo-task-chase_rameau")
        assertTrue(late.getJSONObject("data").getLong("due_at") < now)
        assertEquals(late.getJSONObject("data").getLong("due_at"), late.getJSONObject("state").getLong("due_notified"))
        assertEquals(now + 60_000, tasks.getValue("demo-task-call_brume").getJSONObject("data").getLong("due_at"))
        assertTrue(!entries.getValue("demo-kitchen-shopping").single { it.getString("id") == "demo-shop-milk" }.getJSONObject("state").has("checked_at"))
        val invoicing = entries.getValue("demo-work-invoicing")
        assertEquals(false, invoicing.last().getJSONObject("state").getBoolean("read"))
        assertTrue(invoicing.dropLast(1).all { it.getJSONObject("state").getBoolean("read") })
    }

    @Test
    fun `each goal has a week's attempt for twelve weeks, the current one open, last week's to validate`() {
        val attempts = data(moments[0]).attempts().groupBy { it.toolId }
        assertEquals(setOf("demo-course-goal", "demo-italian-goal"), attempts.keys)
        attempts.values.forEach { list ->
            assertEquals(DemoData.WEEKS, list.size)
            assertEquals("active", list.last().status)
            assertEquals("to_validate", list[list.size - 2].status)
            assertTrue(list.dropLast(2).all { it.validate })
        }
    }

    @Test
    fun `the calendar has appointments past and to come, a whole-day one, its series' past with one cancelled and one removed`() {
        for (now in moments) {
            val events = data(now).entries().getValue("demo-work-agenda")
            assertTrue(events.any { it.getLong("timestamp") < now } && events.any { it.getLong("timestamp") > now })
            assertTrue(events.any { it.getJSONObject("data").optBoolean("all_day") })
            val series = events.filter { it.getJSONObject("state").optString("series") == "s_brume" }
            assertTrue(series.all { it.getLong("timestamp") <= now && it.getJSONObject("state").getLong("series_at") == it.getLong("timestamp") })
            assertEquals(setOf("planned", "cancelled", "removed"), series.map { it.getJSONObject("state").getString("status") }.toSet())
        }
    }

    @Test
    fun `a session keeps its steps, their counts following them`() {
        val sessions = data(moments[0]).entries().getValue("demo-course-intervals")
        assertTrue(sessions.isNotEmpty())
        sessions.forEach { session ->
            val data = session.getJSONObject("data")
            val steps = data.getJSONArray("steps").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("status") } }
            assertEquals(17, steps.size)
            assertEquals(data.getInt("steps_done"), steps.count { it == "done" })
            assertEquals(data.getInt("steps_skipped"), steps.count { it == "skipped" })
            assertEquals(data.getInt("steps_not_done"), steps.count { it == "not_done" })
        }
    }

    @Test
    fun `the deck's reviews end before the app's today, which past midnight is still yesterday`() {
        for (moment in listOf("2026-10-20T00:30", "2026-10-20T15:30")) {
            val now = at(moment)
            val time = TimeResolver(now, zone, 4, "monday")
            val reviews = data(now).entries().getValue("demo-italian-deck")
            assertTrue(reviews.isNotEmpty())
            reviews.forEach { assertTrue("$moment: $it", it.getLong("timestamp") < time.startOf(time.dateOf(now))) }
        }
    }

    @Test
    fun `with weeks starting on Sunday, the goals' attempts keep their Monday, the current one begun, nothing else ahead`() {
        // A Sunday afternoon, the week's first day; a Monday just past midnight, still Sunday for the app
        for (now in listOf(at("2026-10-04T15:30"), at("2026-10-05T00:30"))) {
            val data = data(now, weekStart = "sunday")
            val attempts = data.attempts()
            attempts.forEach { attempt ->
                val start = java.time.Instant.ofEpochMilli(attempt.start).atZone(zone)
                assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
                assertEquals(0, start.hour)
                assertTrue(attempt.start <= now)
            }
            attempts.filter { it.status == "active" }.forEach { assertTrue(now - it.start < 7 * 24 * 3_600_000L) }
            data.entries().filterKeys { it != "demo-work-agenda" }.values.flatten()
                .forEach { e -> if (e.has("timestamp")) assertTrue(e.toString(), e.getLong("timestamp") <= now) }
        }
    }

    @Test
    fun `the foods the bench names exist`() {
        val foods = data(moments[0]).entries().getValue("demo-kitchen-foods").map { it.getString("id") }
        assertTrue("demo-food-rice" in foods && "demo-food-chicken" in foods)
        assertEquals("Riz", asset("texts-fr.json").getString("food_rice"))
        assertEquals("Poulet", asset("texts-fr.json").getString("food_chicken"))
    }
}
