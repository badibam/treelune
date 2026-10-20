package app.treelune.core.conditions

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.migration.EntryMigration
import app.treelune.core.fields.settings.SettingNode
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A config change that takes an option out of a choice, or renames it, names the tools whose
 * filters compare the field with it: a chart of Meals filtering « moment = lunch » once « lunch »
 * is gone finds no entry and says nothing.
 */
class FiltersOnOptionsTest {

    private fun moment(vararg options: String) = FieldDefinition("moment", "Moment", null, FieldType.CHOICE, false,
        mapOf("options" to ChoiceSettings.storedOptions(options.toList())))
    private fun fields(data: FieldDefinition, extra: List<FieldDefinition> = emptyList()) = EntryMigration.Fields(listOf(FixedField(data)), extra)

    private val removed = FiltersOnOptions.removed(fields(moment("breakfast", "lunch", "dinner")), fields(moment("breakfast", "midday", "dinner")))

    /** A chart's selection, its filters on the entries of the tool it targets. */
    private val nodes = listOf(SettingNode.Selection("selection", "Data", "period_end"))
    private fun chart(tool: String, vararg filters: JSONObject) = JSONObject().put("selection", JSONObject()
        .put("target", JSONObject().put("kind", "TOOL_INSTANCE").put("id", tool))
        .put("filters", JSONArray(filters.toList())))

    @Test
    fun aRenamedOption_isRemoved_underTheFieldsPath() {
        assertEquals(mapOf("data.moment" to setOf("lunch")), removed)
    }

    @Test
    fun aFilterOnTheRemovedOption_aloneOrInAList_isCaught() {
        assertTrue(FiltersOnOptions.aims(Conditions.onField("data.moment", "=", "lunch"), removed))
        assertTrue(FiltersOnOptions.aims(Conditions.onField("data.moment", "in", listOf("dinner", "lunch")), removed))
    }

    @Test
    fun aFilterOnAKeptOption_anotherField_orNoWrittenValue_isNot() {
        assertFalse(FiltersOnOptions.aims(Conditions.onField("data.moment", "=", "dinner"), removed))
        assertFalse(FiltersOnOptions.aims(Conditions.onField("data.kcal", "=", "lunch"), removed))
        assertFalse(FiltersOnOptions.aims(Conditions.onField("data.moment", "present", null), removed))
    }

    @Test
    fun aConfig_holdsTheFilter_onlyWhenItsSelectionTargetsTheChangedTool() = runBlocking {
        val lunch = Conditions.onField("data.moment", "=", "lunch")
        assertTrue(FiltersOnOptions.holds(nodes, chart("meals", lunch), "meals", removed))
        assertFalse(FiltersOnOptions.holds(nodes, chart("snacks", lunch), "meals", removed))
        assertFalse(FiltersOnOptions.holds(nodes, chart("meals", Conditions.onField("data.moment", "=", "dinner")), "meals", removed))
    }
}
