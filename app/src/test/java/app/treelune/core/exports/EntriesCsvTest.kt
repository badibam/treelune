package app.treelune.core.exports

import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.StateField
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.utils.FileNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.ZoneId

/**
 * What the CSV export promises (docs/DATA.md, « Export »): the columns in the file's order, named by
 * their labels, a label borne twice followed by its path; each type written in its fixed form
 * whatever the language; a cell quoted only when it must be; a file name cleaned, never written over.
 */
class EntriesCsvTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val texts = mapOf(
        "label_name" to "Nom", "label_date" to "Date", "label_created_at" to "Créé le", "label_updated_at" to "Modifié le",
        "export_csv_range_start" to "%1\$s (début)", "export_csv_range_end" to "%1\$s (fin)", "pointer_target_deleted" to "supprimé"
    )
    private val text: (String) -> String = { texts[it] ?: it }

    private fun field(name: String, label: String, type: FieldType) = FieldDefinition(name, label, null, type, false, null)

    @Test
    fun `the columns go name, date, the type's fields, the user's, the state, then created and changed`() {
        val declared = EntryFields(
            name = CoreFieldUsage.REQUIRED, timestamp = CoreFieldUsage.OPTIONAL,
            data = listOf(FixedField(field("quantity", "Quantité", FieldType.NUMERIC))),
            state = listOf(StateField(field("checked_at", "Fait le", FieldType.DATETIME), filterable = true),
                StateField(field("reminded_until", "Rappel", FieldType.DATETIME), filterable = false, bookkeeping = true))
        )
        val columns = EntriesCsv.columns(declared, listOf(field("mood", "Humeur", FieldType.TEXT)), text)
        assertEquals(listOf("name", "timestamp", "data.quantity", "extra.mood", "state.checked_at", "created_at", "updated_at"), columns.map { it.path })
    }

    @Test
    fun `a tool type without a name or a date has no column for them`() {
        val columns = EntriesCsv.columns(EntryFields(name = CoreFieldUsage.ABSENT, timestamp = CoreFieldUsage.ABSENT), emptyList(), text)
        assertEquals(listOf("created_at", "updated_at"), columns.map { it.path })
    }

    @Test
    fun `the header names each field by its label, a shared label with its path, a range in two`() {
        val columns = listOf(
            EntriesCsv.Column("data.note", field("note", "Note", FieldType.TEXT)),
            EntriesCsv.Column("extra.note", field("note", "Note", FieldType.TEXT)),
            EntriesCsv.Column("extra.pulse", field("pulse", "Pouls", FieldType.RANGE))
        )
        assertEquals(listOf("Note (data.note)", "Note (extra.note)", "Pouls (début)", "Pouls (fin)"), EntriesCsv.header(columns, text))
    }

    @Test
    fun `each type is written in its fixed form`() {
        val rice = Reference(ReferenceKind.ENTRY, "e1")
        val gone = Reference(ReferenceKind.ENTRY, "e2")
        val columns = listOf(
            EntriesCsv.Column("timestamp", field("timestamp", "Date", FieldType.DATETIME)),
            EntriesCsv.Column("extra.weight", field("weight", "Poids", FieldType.NUMERIC)),
            EntriesCsv.Column("extra.count", field("count", "Nombre", FieldType.NUMERIC)),
            EntriesCsv.Column("extra.day", field("day", "Jour", FieldType.DATE)),
            EntriesCsv.Column("extra.at", field("at", "Heure", FieldType.TIME)),
            EntriesCsv.Column("extra.run", field("run", "Durée", FieldType.DURATION)),
            EntriesCsv.Column("extra.done", field("done", "Fait", FieldType.BOOLEAN)),
            // A choice by the label the screen shows, an option without one by its value
            EntriesCsv.Column("extra.tags", FieldDefinition("tags", "Tags", null, FieldType.CHOICE, false,
                mapOf("multiple" to true, "options" to listOf(mapOf("value" to "a", "label" to "Alpha"), mapOf("value" to "b"))))),
            EntriesCsv.Column("extra.pulse", field("pulse", "Pouls", FieldType.RANGE)),
            EntriesCsv.Column("extra.food", field("food", "Aliment", FieldType.REFERENCE)),
            EntriesCsv.Column("extra.other", field("other", "Autre", FieldType.REFERENCE)),
            EntriesCsv.Column("extra.photos", field("photos", "Photos", FieldType.IMAGE)),
            EntriesCsv.Column("extra.empty", field("empty", "Vide", FieldType.TEXT))
        )
        val entry = mapOf(
            // 2026-09-12 08:12:30.456 in Paris, the fraction of a second dropped
            "timestamp" to 1_789_193_550_456L,
            "extra" to mapOf(
                "weight" to 72.4, "count" to 150.0, "day" to "2026-09-12", "at" to "8:05", "run" to 5_100_000L,
                "done" to true, "tags" to listOf("a", "b"), "pulse" to mapOf("start" to 60, "end" to 80.5),
                "food" to mapOf("kind" to "ENTRY", "id" to "e1"), "other" to mapOf("kind" to "ENTRY", "id" to "e2"),
                "photos" to listOf("i1", "i2")
            )
        )
        val line = EntriesCsv.write(columns, listOf(entry), mapOf(rice to "Riz", gone to null), zone, text).split("\r\n")[1]
        assertEquals("2026-09-12T08:12:30+02:00,72.4,150,2026-09-12,08:05,1:25:00,true,Alpha;b,60,80.5,Riz,supprimé,2,", line)
    }

    @Test
    fun `the references found are those of the reference fields`() {
        val columns = listOf(EntriesCsv.Column("extra.food", field("food", "Aliment", FieldType.REFERENCE)))
        val entries = listOf(mapOf("extra" to mapOf("food" to mapOf("kind" to "ENTRY", "id" to "e1"))), mapOf("extra" to emptyMap<String, Any?>()))
        assertEquals(setOf(Reference(ReferenceKind.ENTRY, "e1")), EntriesCsv.references(columns, entries))
    }

    @Test
    fun `the file is UTF-8 after its byte order mark`() {
        val bytes = EntriesCsv.bytes("Nom\r\nCafé\r\n")
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
        assertEquals("Nom\r\nCafé\r\n", String(bytes.drop(3).toByteArray(), Charsets.UTF_8))
    }

    @Test
    fun `a cell is quoted only when it holds a comma, a quote or a line break`() {
        assertEquals("riz", EntriesCsv.quoted("riz"))
        assertEquals("\"riz, cuit\"", EntriesCsv.quoted("riz, cuit"))
        assertEquals("\"le \"\"bon\"\"\"", EntriesCsv.quoted("le \"bon\""))
        assertEquals("\"deux\nlignes\"", EntriesCsv.quoted("deux\nlignes"))
    }

    @Test
    fun `a file name is cleaned of what a folder refuses, and never written over`() {
        assertEquals("Repas_midi", FileNames.clean("Repas/midi"))
        assertEquals("Poids du matin", FileNames.clean("  Poids   du matin. "))
        assertEquals("con_", FileNames.clean("CON".lowercase()))
        assertThrows(IllegalArgumentException::class.java) { FileNames.clean("???") }
        assertEquals("Repas_2026.csv", FileNames.unique("Repas_2026", "csv", setOf("Autre.csv")))
        assertEquals("Repas_2026_3.csv", FileNames.unique("Repas_2026", "csv", setOf("Repas_2026.csv", "Repas_2026_2.csv")))
    }
}
