package app.treelune.core.readouts

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The readouts (docs/design/readouts.md): a sentence whose named values are read when it is shown,
 * coloured by the first of its conditions that holds, which may signal; its values, its sentence,
 * its conditions and its target checked when written.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadoutsTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var zoneId: String
    private lateinit var waterId: String

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        zoneId = ok("zones.create", mapOf("name" to "Body"))["zone_id"] as String
        waterId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "tracking", "config" to mapOf(
            "name" to "Water", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to listOf("glass")
        )))["tool_instance_id"] as String
    }

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    private suspend fun refused(action: String, params: Map<String, Any?>): String {
        val result = coordinator.processUserAction(action, params)
        assertFalse("$action should be refused", result.isSuccess)
        return result.error ?: ""
    }

    private val today = mapOf("start" to mapOf("relative" to mapOf("unit" to "DAY", "offset" to 0, "edge" to "START")), "end" to mapOf("relative" to "NOW"))

    /** Glasses of water today against two expected, red with a signal under them, green from them on. */
    private fun water(extra: Map<String, Any?> = emptyMap()): Map<String, Any?> = mapOf(
        "zone_id" to zoneId,
        "name" to "Water today",
        "text" to "Water: {done} / {expected}",
        "values" to listOf(
            mapOf("name" to "done", "value" to mapOf("reading" to mapOf("selection" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to waterId), "period" to today), "reduction" to "COUNT"))),
            mapOf("name" to "expected", "value" to mapOf("constant" to 2))
        ),
        "target" to mapOf("kind" to "TOOL_INSTANCE", "id" to waterId),
        "colors" to listOf(
            mapOf("when" to mapOf("left" to mapOf("field" to "done"), "op" to "<", "right" to mapOf("field" to "expected")), "color" to "RED", "signal" to true),
            mapOf("when" to mapOf("left" to mapOf("field" to "done"), "op" to ">=", "right" to mapOf("field" to "expected")), "color" to "GREEN")
        ),
        "places" to listOf("zone_head", "zone_tile")
    ) + extra

    private suspend fun read(id: String) = ok("readouts.read", mapOf("readout_id" to id))

    private suspend fun glass() = ok("tool_data.create", mapOf("tool_instance_id" to waterId, "name" to "Glass", "data" to mapOf("value" to 1, "unit" to "glass")))

    @Test
    fun itsSentence_writesItsValues_andTheFirstColourThatHolds() = runBlocking {
        val id = ok("readouts.create", water())["readout_id"] as String
        val empty = read(id)
        assertEquals("Water: 0 / 2", empty["text"])
        assertEquals("RED", empty["color"])
        assertEquals(true, empty["signal"])

        glass(); glass()
        val full = read(id)
        assertEquals("Water: 2 / 2", full["text"])
        assertEquals("GREEN", full["color"])
        assertEquals(false, full["signal"])
    }

    @Test
    fun noConditionHolding_leavesItWithoutColour() = runBlocking {
        val id = ok("readouts.create", water(mapOf("colors" to listOf(
            mapOf("when" to mapOf("left" to mapOf("field" to "done"), "op" to ">", "right" to mapOf("constant" to 5)), "color" to "BLUE")
        ))))["readout_id"] as String
        val shown = read(id)
        assertEquals(null, shown["color"])
        assertEquals(false, shown["signal"])
    }

    @Test
    fun itsSettings_areKept_andUpdatedOneByOne() = runBlocking {
        val id = ok("readouts.create", water())["readout_id"] as String
        ok("readouts.update", mapOf("readout_id" to id, "text" to "{done} glasses"))
        val settings = (ok("readouts.get", mapOf("readout_id" to id))["readout"] as Map<*, *>)["settings"] as Map<*, *>
        assertEquals("{done} glasses", settings["text"])
        assertEquals(listOf("zone_head", "zone_tile"), settings["places"])
        assertEquals("Water today", settings["name"])
        assertEquals("0 glasses", read(id)["text"])
    }

    @Test
    fun aSentenceNamingNoValue_isRefused() = runBlocking {
        val error = refused("readouts.create", water(mapOf("text" to "Water: {drunk}")))
        assertTrue(error, error.contains("drunk"))
    }

    @Test
    fun twoValuesOfOneName_areRefused() = runBlocking {
        val error = refused("readouts.create", water(mapOf("values" to listOf(
            mapOf("name" to "done", "value" to mapOf("constant" to 1)),
            mapOf("name" to "done", "value" to mapOf("constant" to 2))
        ), "text" to "{done}", "colors" to emptyList<Any>())))
        assertTrue(error, error.contains("done"))
    }

    @Test
    fun aColourComparingNoValue_isRefused(): Unit = runBlocking {
        refused("readouts.create", water(mapOf("colors" to listOf(
            mapOf("when" to mapOf("left" to mapOf("field" to "drunk"), "op" to "<", "right" to mapOf("constant" to 2)), "color" to "RED")
        ))))
    }

    @Test
    fun withoutATarget_itIsRefused(): Unit = runBlocking {
        refused("readouts.create", water() - "target")
    }

    @Test
    fun itsTile_standsInItsSection_andLeavesWithIt() = runBlocking {
        val id = ok("readouts.create", water())["readout_id"] as String
        val listed = ok("readouts.list", mapOf("zone_id" to zoneId))["readouts"] as List<*>
        assertEquals(listOf(id), listed.map { (it as Map<*, *>)["id"] })
        assertEquals(listOf(waterId), (listed.single() as Map<*, *>)["tools_read"])
        ok("readouts.delete", mapOf("readout_id" to id))
        assertTrue((ok("readouts.list", mapOf("zone_id" to zoneId))["readouts"] as List<*>).isEmpty())
    }
}
