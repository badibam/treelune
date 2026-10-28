package app.treelune.tools.tracking

import android.content.Context
import app.treelune.core.backup.BackupPart
import app.treelune.core.backup.PartImport
import app.treelune.core.backup.PartSelection
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.files.Files
import app.treelune.core.services.BackupService
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipInputStream

/**
 * A tracking shortcut's values of the user's fields (SettingNode.FieldValues, ConfigFieldValues):
 * each held to what an entry's value is held to; a reference whose entry was deleted kept, the
 * config still writable; a value leaving with its field, or with the option it was, without
 * refusing the change; a reference following its entry's new id when a part is brought back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortcutFieldsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val coordinator get() = Coordinator(context)

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    /** A zone with Foods, a note per food, and Meals, a tracking whose fields designate a food and a moment. */
    private class Meals(val zone: String, val foods: String, val salmon: String, val meals: String)

    private suspend fun meals(): Meals {
        val zone = ok("zones.create", mapOf("name" to "Kitchen"))["zone_id"] as String
        val foods = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to "notes", "config" to mapOf("name" to "Foods")))["tool_instance_id"] as String
        val salmon = ok("tool_data.create", mapOf("tool_instance_id" to foods, "data" to mapOf("content" to "Salmon")))["id"] as String
        val meals = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to "tracking", "config" to mapOf(
            "name" to "Meals", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to listOf("g"),
            "extra_fields" to listOf(
                mapOf("name" to "food", "display_name" to "Food", "type" to "REFERENCE", "always_visible" to false,
                    "config" to mapOf("target" to mapOf("kinds" to listOf("ENTRY"), "tool_instances" to listOf(mapOf("kind" to "TOOL_INSTANCE", "id" to foods))))),
                mapOf("name" to "moment", "display_name" to "Moment", "type" to "CHOICE", "always_visible" to false,
                    "config" to mapOf("options" to ChoiceSettings.storedOptions(listOf("breakfast", "lunch", "dinner"))))
            )
        )))["tool_instance_id"] as String
        return Meals(zone, foods, salmon, meals)
    }

    private suspend fun config(tool: String): JSONObject =
        JsonUtils.toJSONObject(@Suppress("UNCHECKED_CAST") ((ok("tools.get", mapOf("tool_instance_id" to tool))["tool_instance"] as Map<*, *>)["config"] as Map<String, Any?>))

    private fun reference(id: String) = mapOf("kind" to "ENTRY", "id" to id)

    /** [tool]'s config with one shortcut, "Salmon", holding [extra]. */
    private suspend fun withSalmon(tool: String, extra: Map<String, Any?>): JSONObject =
        config(tool).put("items", JSONArray().put(JSONObject().put("name", "Salmon").put("value", 150).put("unit", "g").put("extra", JsonUtils.toJSONObject(extra))))

    private suspend fun update(tool: String, config: JSONObject) =
        coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to tool, "config" to JsonUtils.toMap(config)))

    @Test
    fun `a shortcut's values are held as an entry's, a deleted entry kept, a value leaving with its field or its option`() = runBlocking {
        val kitchen = meals()

        // Stored as given, read back by the shortcut
        assertTrue(update(kitchen.meals, withSalmon(kitchen.meals, mapOf("food" to reference(kitchen.salmon), "moment" to "lunch"))).isSuccess)
        val salmon = TrackingConfig.shortcuts(config(kitchen.meals)).single()
        assertEquals(mapOf("food" to reference(kitchen.salmon), "moment" to "lunch"), salmon.extra)

        // Refused as an entry's value would be: an option the choice does not have, an entry
        // that does not exist, a key that is no field
        assertFalse(update(kitchen.meals, withSalmon(kitchen.meals, mapOf("moment" to "brunch"))).isSuccess)
        assertFalse(update(kitchen.meals, withSalmon(kitchen.meals, mapOf("food" to reference("no-such-entry")))).isSuccess)
        assertFalse(update(kitchen.meals, withSalmon(kitchen.meals, mapOf("nothing" to "x"))).isSuccess)

        // The salmon's card deleted: the shortcut keeps designating it, and the config stays writable
        ok("tool_data.delete", mapOf("id" to kitchen.salmon))
        assertTrue(update(kitchen.meals, config(kitchen.meals).put("name", "Lunches")).isSuccess)
        assertEquals(reference(kitchen.salmon), TrackingConfig.shortcuts(config(kitchen.meals)).single().extra["food"])

        // An option removed: its value leaves the shortcut, counted, the change not refused
        val withoutLunch = config(kitchen.meals)
        val fields = withoutLunch.getJSONArray("extra_fields")
        (0 until fields.length()).map { fields.getJSONObject(it) }.first { it.getString("name") == "moment" }
            .getJSONObject("config").put("options", JSONObject.wrap(ChoiceSettings.storedOptions(listOf("breakfast", "dinner"))))
        val optionRemoved = update(kitchen.meals, withoutLunch)
        assertTrue(optionRemoved.error, optionRemoved.isSuccess)
        assertEquals(1, optionRemoved.data!!["removed_field_values"])
        assertEquals(setOf("food"), TrackingConfig.shortcuts(config(kitchen.meals)).single().extra.keys)

        // A field removed: its value leaves the shortcut too
        val withoutFood = config(kitchen.meals)
        withoutFood.put("extra_fields", JSONArray((0 until withoutFood.getJSONArray("extra_fields").length())
            .map { withoutFood.getJSONArray("extra_fields").getJSONObject(it) }.filter { it.getString("name") != "food" }))
        val fieldRemoved = update(kitchen.meals, withoutFood)
        assertTrue(fieldRemoved.error, fieldRemoved.isSuccess)
        assertEquals(1, fieldRemoved.data!!["removed_field_values"])
        assertTrue(TrackingConfig.shortcuts(config(kitchen.meals)).single().extra.isEmpty())
    }

    @Test
    fun `a part brought back gives the shortcut the new id of the entry it designates`() = runBlocking {
        val kitchen = meals()
        assertTrue(update(kitchen.meals, withSalmon(kitchen.meals, mapOf("food" to reference(kitchen.salmon)))).isSuccess)

        // The app's backup, cut to the kitchen, both tools with their entries
        val service = BackupService(context)
        val token = CancellationToken()
        val zip = File(context.cacheDir, "kitchen.zip")
        assertTrue(service.writeZip(zip.outputStream(), service.build(token)!!, token))
        val document = ZipInputStream(zip.inputStream()).use { input ->
            generateSequence { input.nextEntry }.first { it.name == "backup.json" }
            JSONObject(input.readBytes().toString(Charsets.UTF_8))
        }
        val database = AppDatabase.getDatabase(context)
        val toolsBefore = database.toolInstanceDao().getAllToolInstances().map { it.id }.toSet()
        val entriesBefore = database.toolDataDao().getAllEntries().map { it.id }.toSet()
        val part = BackupPart.cut(document, PartSelection(setOf(kitchen.zone), mapOf(kitchen.foods to true, kitchen.meals to true), emptySet(), emptySet(), emptySet()), emptyList())
        PartImport(context).run(part, Files.importDir(context), emptyMap(), emptySet())

        val arrived = database.toolInstanceDao().getAllToolInstances().single { it.tooltype == "tracking" && it.id !in toolsBefore }
        val salmon = database.toolDataDao().getAllEntries().single { it.id !in entriesBefore && JSONObject(it.data).optString("content") == "Salmon" }
        assertNotEquals(kitchen.salmon, salmon.id)
        assertEquals(reference(salmon.id), TrackingConfig.shortcuts(JSONObject(arrived.config_json)).single().extra["food"])
    }
}
