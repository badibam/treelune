package app.treelune.core.imports

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A CSV imported into a table of foods through the service (imports.detect then imports.apply,
 * docs/DATA.md, « Import »): the new fields created in the order of their columns, in the same
 * transaction as the lines without blocking it, a sheet whose name exists updated, a line that
 * does not read named in the report; the same file again updates everything and duplicates nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportApplyTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var foodsId: String

    private val csv = """
        Name;kcal;Récolte;Catégorie
        Pomme;52,5;15/09/2026;fruit
        Carotte;41;03/10/2026;légume
        Riz;beaucoup;01/10/2026;céréale
        Poire;57;20/09/2026;fruit
    """.trimIndent()

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Kitchen"))
        assertTrue(zone.error, zone.isSuccess)
        val foods = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "structured",
            "config" to mapOf("name" to "Foods", "extra_fields" to listOf(mapOf(
                "name" to "kcal", "display_name" to "kcal", "type" to "NUMERIC", "always_visible" to false, "config" to mapOf("decimals" to 1)
            )))
        ))
        assertTrue(foods.error, foods.isSuccess)
        foodsId = foods.data!!["tool_instance_id"] as String
        val pomme = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to foodsId, "name" to "Pomme", "data" to emptyMap<String, Any>(), "extra" to mapOf("kcal" to 50)))
        assertTrue(pomme.error, pomme.isSuccess)
    }

    /** The file detected then applied as proposed, as the screen does when nothing is corrected. */
    private suspend fun import(): Map<String, Any?> {
        val detected = coordinator.processUserAction("imports.detect", mapOf("tool_instance_id" to foodsId, "csv" to csv))
        assertTrue(detected.error, detected.isSuccess)
        @Suppress("UNCHECKED_CAST")
        val columns = detected.data!!["columns"] as List<Map<String, Any?>>
        assertEquals(emptyList<Any>(), detected.data!!["missing"])
        columns.forEach { assertTrue("${it["column"]} has an example read", it["example"] != null) }
        // A transaction blocked by the calls inside it never ends: the import is held to a minute
        val applied = withTimeout(60_000) {
            coordinator.processUserAction("imports.apply", mapOf("tool_instance_id" to foodsId, "csv" to csv, "columns" to columns))
        }
        assertTrue(applied.error, applied.isSuccess)
        return applied.data!!
    }

    private suspend fun extraFields(): List<String> {
        val config = JSONObject(AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(foodsId)!!.config_json)
        val fields = config.getJSONArray("extra_fields")
        return (0 until fields.length()).map { fields.getJSONObject(it).getString("display_name") }
    }

    private suspend fun sheets(): List<app.treelune.core.database.entities.ToolDataEntity> = AppDatabase.getDatabase(context).toolDataDao().getByToolInstance(foodsId)

    @Test
    fun `the new fields come in their columns' order, a name that exists is updated, a line that does not read is named`() = runBlocking {
        val report = import()
        assertEquals(listOf("kcal", "Récolte", "Catégorie"), extraFields())
        assertEquals(2, report["created"])
        assertEquals(1, report["updated"])
        @Suppress("UNCHECKED_CAST")
        val refused = report["refused"] as List<Map<String, Any?>>
        assertEquals("the line of Riz, counted after the header", listOf(3), refused.map { it["line"] })

        val pomme = sheets().single { it.name == "Pomme" }
        assertEquals(52.5, JSONObject(pomme.extra!!).getDouble("kcal"), 0.0)
        assertEquals(listOf("Carotte", "Poire", "Pomme"), sheets().mapNotNull { it.name }.sorted())
    }

    @Test
    fun `the same file again updates everything and duplicates nothing`() = runBlocking {
        import()
        val again = import()
        assertEquals(0, again["created"])
        assertEquals(3, again["updated"])
        assertEquals(listOf("kcal", "Récolte", "Catégorie"), extraFields())
        assertEquals(3, sheets().size)
    }
}
