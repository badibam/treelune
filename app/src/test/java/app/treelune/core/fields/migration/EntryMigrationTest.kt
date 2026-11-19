package app.treelune.core.fields.migration

import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a config change does to the recorded entries, deduced from the fields the old and the
 * new config give them (docs/DATA.md): a value that loses its
 * meaning goes, an entry that loses a required value goes, and nothing is invented.
 */
class EntryMigrationTest {

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) =
        FieldDefinition(name, name, null, type, false, config)

    private fun entry(id: String, data: String = "{}", extra: String? = null, state: String? = null) =
        ToolDataEntity(id, "tool", "tracking", 0L, id, data, 0L, 0L, extra, state)

    private fun fields(data: List<FixedField> = emptyList(), extra: List<FieldDefinition> = emptyList()) =
        EntryMigration.Fields(data, extra)

    /** A user's field whose type changes loses its values, and the entry stays. */
    @Test
    fun aTypeChangeRemovesTheValuesOfAnOptionalField() {
        val plan = EntryMigration.plan(
            old = fields(extra = listOf(field("mood", FieldType.TEXT))),
            new = fields(extra = listOf(field("mood", FieldType.NUMERIC, mapOf("decimals" to 0)))),
            entries = listOf(entry("a", extra = """{"mood":"good"}"""), entry("b"))
        )

        assertEquals(1, plan.removedValues)
        assertTrue(plan.deleted.isEmpty())
        assertNull(plan.updated.single().extra)
        assertTrue(plan.losesData)
    }

    /** An entry that loses the value of a required field is no longer an entry: it goes. */
    @Test
    fun anEntryLosingARequiredValueIsDeleted() {
        val plan = EntryMigration.plan(
            old = fields(data = listOf(FixedField(field("value", FieldType.NUMERIC, mapOf("decimals" to 0)), required = true))),
            new = fields(data = listOf(FixedField(field("value", FieldType.SCALE, mapOf("min" to 1, "max" to 5)), required = true))),
            entries = listOf(entry("a", data = """{"value":3}"""))
        )

        assertEquals("a", plan.deleted.single().id)
        assertEquals(0, plan.removedValues)
        assertTrue(plan.losesData)
    }

    /** A field that becomes required is not filled in on the entries' behalf: they are counted. */
    @Test
    fun aNewlyRequiredFieldCountsTheEntriesWithoutIt() {
        val plan = EntryMigration.plan(
            old = fields(),
            new = fields(data = listOf(FixedField(field("value", FieldType.NUMERIC, mapOf("decimals" to 0)), required = true))),
            entries = listOf(entry("a"), entry("b"))
        )

        assertEquals(mapOf("value" to 2), plan.missing)
        assertFalse(plan.losesData)
    }

    /** The value given for a field now required goes to the entries without one, and only to them. */
    @Test
    fun aGivenValueFillsTheEntriesWithoutOne() {
        val plan = EntryMigration.plan(
            old = fields(data = listOf(FixedField(field("value", FieldType.NUMERIC, mapOf("decimals" to 0))))),
            new = fields(data = listOf(FixedField(field("value", FieldType.NUMERIC, mapOf("decimals" to 0)), required = true))),
            entries = listOf(entry("a"), entry("b", data = """{"value":7}""")),
            fill = mapOf("value" to 1)
        )

        assertTrue(plan.missing.isEmpty())
        assertEquals(1, plan.filledValues)
        assertEquals(listOf("a"), plan.updated.map { it.id })
        assertEquals(1, JSONObject(plan.updated.single().data).getInt("value"))
    }

    /** A running stopwatch is a value: it goes with a type change, out of the state too. */
    @Test
    fun aRunningDurationGoesWithItsField() {
        val plan = EntryMigration.plan(
            old = fields(extra = listOf(field("sleep", FieldType.DURATION))),
            new = fields(extra = listOf(field("sleep", FieldType.TEXT))),
            entries = listOf(entry("a", state = """{"running":{"extra":{"sleep":1000}},"read":true}"""))
        )

        assertEquals(1, plan.removedValues)
        assertEquals(JSONObject("""{"read":true}""").toString(), plan.updated.single().state)
    }

    /** A running required value counts as present: nothing is missing while it runs. */
    @Test
    fun aRunningRequiredValueIsNotMissing() {
        val timer = FixedField(field("value", FieldType.DURATION), required = true)
        val plan = EntryMigration.plan(
            old = fields(data = listOf(timer.copy(required = false))),
            new = fields(data = listOf(timer)),
            entries = listOf(entry("a", state = """{"running":{"data":{"value":1000}}}"""))
        )

        assertTrue(plan.missing.isEmpty())
    }

    /** A removed option only touches the entries that used it. */
    @Test
    fun aRemovedOptionTouchesOnlyItsEntries() {
        fun choice(vararg options: String) = field("unit", FieldType.CHOICE,
            mapOf("options" to ChoiceSettings.storedOptions(options.toList(), emptyMap())))
        val plan = EntryMigration.plan(
            old = fields(data = listOf(FixedField(choice("km", "mi")))),
            new = fields(data = listOf(FixedField(choice("km")))),
            entries = listOf(entry("a", data = """{"unit":"mi"}"""), entry("b", data = """{"unit":"km"}"""))
        )

        assertEquals(listOf("a"), plan.updated.map { it.id })
        assertEquals(1, plan.removedValues)
    }

    /**
     * A scale's bounds changed: the value inside them kept and counted as read otherwise, the one
     * outside removed; the field is offered a new field instead.
     */
    @Test
    fun aScaleBoundsChangeKeepsWhatFitsAndCountsIt() {
        val plan = EntryMigration.plan(
            old = fields(extra = listOf(field("energy", FieldType.SCALE, mapOf("min" to 0, "max" to 10)))),
            new = fields(extra = listOf(field("energy", FieldType.SCALE, mapOf("min" to 0, "max" to 5)))),
            entries = listOf(entry("a", extra = """{"energy":3}"""), entry("b", extra = """{"energy":8}"""))
        )

        assertEquals(1, plan.reinterpreted)
        assertEquals(1, plan.removedValues)
        assertEquals(listOf("energy"), plan.changedMeaning)
        assertTrue(plan.touchesPast)
    }

    /** One answer becoming a list of one is written, touches nothing recorded, and asks nothing. */
    @Test
    fun oneAnswerToSeveralIsWrittenWithoutAsking() {
        val single = ChoiceSettings.storedOptions(listOf("x", "y"))
        val plan = EntryMigration.plan(
            old = fields(extra = listOf(field("tags", FieldType.CHOICE, mapOf("options" to single)))),
            new = fields(extra = listOf(field("tags", FieldType.CHOICE, mapOf("options" to single, "multiple" to true)))),
            entries = listOf(entry("a", extra = """{"tags":"x"}"""))
        )

        assertEquals("""{"tags":["x"]}""", plan.updated.single().extra)
        assertFalse(plan.touchesPast)
    }

    /** Retitling a field changes no entry. */
    @Test
    fun aCosmeticChangeTouchesNothing() {
        val plan = EntryMigration.plan(
            old = fields(extra = listOf(field("mood", FieldType.TEXT))),
            new = fields(extra = listOf(field("mood", FieldType.TEXT).copy(displayName = "How I felt"))),
            entries = listOf(entry("a", extra = """{"mood":"good"}"""))
        )

        assertTrue(plan.updated.isEmpty())
        assertFalse(plan.losesData)
    }

    private fun named(id: String, name: String) = ToolDataEntity(id, "tool", "structured", 0L, name, "{}", 0L, 0L, null, null)

    private fun names(length: app.treelune.core.fields.TextLength) = EntryMigration.Fields(emptyList(), emptyList(), length, nameUnique = true)

    /** A name longer than the new length is cut to it, and the cut asks for agreement. */
    @Test
    fun aShorterNameLengthCutsTheLongerNames() {
        val long = "a".repeat(100) + " " + "b".repeat(100)
        val plan = EntryMigration.plan(
            old = names(app.treelune.core.fields.TextLength.MEDIUM),
            new = names(app.treelune.core.fields.TextLength.SHORT),
            entries = listOf(named("x", long), named("y", "Short"))
        )

        assertEquals(1, plan.namesCut)
        assertEquals(60, plan.nameLimit)
        assertEquals("a".repeat(60), plan.updated.single().name)
        assertTrue(plan.touchesPast)
        assertTrue(plan.nameCollisions.isEmpty())
    }

    /** Two names a cut makes one, the case not counted, are named: the plan cannot be written. */
    @Test
    fun namesTheCutMakesTheSameAreNamed() {
        val plan = EntryMigration.plan(
            old = names(app.treelune.core.fields.TextLength.MEDIUM),
            new = names(app.treelune.core.fields.TextLength.SHORT),
            entries = listOf(named("x", "c".repeat(60) + " one"), named("y", "C".repeat(60) + " two"), named("z", "Other"))
        )

        assertEquals(listOf("c".repeat(60)), plan.nameCollisions)
    }

    /** A longer length changes no name. */
    @Test
    fun aLongerNameLengthTouchesNothing() {
        val plan = EntryMigration.plan(
            old = names(app.treelune.core.fields.TextLength.SHORT),
            new = names(app.treelune.core.fields.TextLength.LONG),
            entries = listOf(named("x", "A name"))
        )

        assertEquals(0, plan.namesCut)
        assertTrue(plan.updated.isEmpty())
        assertFalse(plan.touchesPast)
    }

    private fun context(length: String) = field("context", FieldType.TEXT, mapOf("length" to length))

    /** A text field turned JSON puts what it held that is no JSON in an object, counted and agreed to; what is JSON stays. */
    @Test
    fun aFieldTurnedJsonPutsItsTextsInAnObject() {
        val plan = EntryMigration.plan(
            old = fields(extra = listOf(context("UNLIMITED"))),
            new = fields(extra = listOf(context("JSON"))),
            entries = listOf(entry("a", extra = """{"context":"A calm evening"}"""), entry("b", extra = """{"context":"{\"mood\":\"calm\"}"}"""))
        )

        assertEquals(1, plan.jsonWrapped)
        assertTrue(plan.touchesPast)
        val wrapped = JSONObject(JSONObject(plan.updated.single().extra!!).getString("context"))
        assertEquals("A calm evening", wrapped.getString(EntryMigration.JSON_WRAP_KEY))
    }

    /** A JSON field that already holds texts that are no JSON (let through before) has them put in an object at the next change, whatever changed. */
    @Test
    fun aJsonFieldHoldingTexts_isMendedAtTheNextChange() {
        val plan = EntryMigration.plan(
            old = fields(extra = listOf(context("JSON"))),
            new = fields(extra = listOf(context("JSON"), field("done", FieldType.BOOLEAN))),
            entries = listOf(entry("a", extra = """{"context":"A calm evening"}"""))
        )

        assertEquals(1, plan.jsonWrapped)
        assertTrue(plan.touchesPast)
    }
}
