package app.treelune.core.exports

import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.CoreFields
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.Reference
import app.treelune.core.utils.DateTimeConverter
import java.math.BigDecimal
import java.time.ZoneId

/**
 * The entries of a tool as a CSV to read in a spreadsheet (docs/DATA.md, « Export »): RFC 4180,
 * UTF-8, a fixed form whatever the phone's language — a point for decimals, ISO 8601 for dates —
 * but for its first line, the fields' labels in the app's language, written for whoever reads it.
 *
 * Pure: the fields, the entries (as tool_data.get gives them) and the names of what their
 * references designate come in, the text goes out. Made to be read, not imported again: a
 * reference is written by the name it shows, an image field by its number of images.
 */
object EntriesCsv {

    /** A field of the entries by its path (`name`, `data.kcal`, `extra.mood`, `state.read`), one or two columns. */
    data class Column(val path: String, val field: FieldDefinition)

    /**
     * Every field an entry of the tool holds, in the order of the file: its name and its date
     * when the tool type uses them, the tool type's fields, the user's in the order of their
     * config, its state, when it was created and last changed. The notes the app keeps for
     * itself in a state (a reminder sent) are not the entry's.
     */
    fun columns(declared: EntryFields, extra: List<FieldDefinition>, text: (String) -> String): List<Column> = buildList {
        if (declared.name != CoreFieldUsage.ABSENT) add(Column("name", CoreFields.name(text, declared.nameUnique)))
        if (declared.timestamp != CoreFieldUsage.ABSENT) add(Column("timestamp", CoreFields.timestamp(text)))
        declared.data.forEach { add(Column("data.${it.definition.name}", it.definition)) }
        extra.forEach { add(Column("extra.${it.name}", it)) }
        declared.state.filterNot { it.bookkeeping }.forEach { add(Column("state.${it.definition.name}", it.definition)) }
        add(Column("created_at", CoreFields.createdAt(text)))
        add(Column("updated_at", CoreFields.updatedAt(text)))
    }

    /**
     * The first line: each field's label; a label two fields bear followed, on each, by its path
     * ("Note (data.note)"); a range's two columns its start and its end.
     */
    fun header(columns: List<Column>, text: (String) -> String): List<String> {
        val shared = columns.groupingBy { it.field.displayName }.eachCount().filterValues { it > 1 }.keys
        return columns.flatMap { column ->
            val label = if (column.field.displayName in shared) "${column.field.displayName} (${column.path})" else column.field.displayName
            if (column.field.type == FieldType.RANGE) listOf(text("export_csv_range_start").format(label), text("export_csv_range_end").format(label))
            else listOf(label)
        }
    }

    /** The references the entries' REFERENCE fields hold, whose names the file needs. */
    fun references(columns: List<Column>, entries: List<Map<String, Any?>>): Set<Reference> =
        columns.filter { it.field.type == FieldType.REFERENCE }.flatMap { column ->
            entries.flatMap { entry -> listOfValues(valueAt(entry, column.path)).mapNotNull { ReferenceTarget.referenceOf(it) } }
        }.toSet()

    /**
     * The whole file: the header, then a line per entry, each ended by CRLF.
     *
     * @param names The name shown for each reference, null for one that no longer exists
     * @param zone The app's time zone, for the instants
     */
    fun write(columns: List<Column>, entries: List<Map<String, Any?>>, names: Map<Reference, String?>, zone: ZoneId, text: (String) -> String): String =
        buildString {
            line(header(columns, text))
            entries.forEach { entry -> line(columns.flatMap { cells(it, valueAt(entry, it.path), names, zone, text) }) }
        }

    /**
     * The file's bytes: UTF-8 after its byte order mark, without which a spreadsheet opening the
     * file by a double tap reads its accents in the computer's own encoding.
     */
    fun bytes(csv: String): ByteArray = ("\uFEFF" + csv).toByteArray(Charsets.UTF_8)

    private fun StringBuilder.line(cells: List<String>) {
        append(cells.joinToString(",") { quoted(it) })
        append("\r\n")
    }

    /** A cell between quotes when it holds a comma, a quote or a line break, its quotes doubled (RFC 4180). */
    internal fun quoted(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${cell.replace("\"", "\"\"")}\"" else cell

    /** The value at [path] in an entry as tool_data.get gives it: a column, or a key of data, extra or state. */
    private fun valueAt(entry: Map<String, Any?>, path: String): Any? {
        val container = path.substringBefore('.', missingDelimiterValue = "")
        return if (container.isEmpty()) entry[path] else (entry[container] as? Map<*, *>)?.get(path.substringAfter('.'))
    }

    private fun listOfValues(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is List<*> -> value
        else -> listOf(value)
    }

    /** The cells of one field of an entry: two for a range, one otherwise; empty for no answer. */
    private fun cells(column: Column, value: Any?, names: Map<Reference, String?>, zone: ZoneId, text: (String) -> String): List<String> {
        if (column.field.type == FieldType.RANGE) {
            val range = value as? Map<*, *>
            return listOf(number(range?.get("start")), number(range?.get("end")))
        }
        if (value == null) return listOf("")
        return listOf(when (column.field.type) {
            FieldType.TEXT, FieldType.DATE -> value.toString()
            FieldType.NUMERIC, FieldType.SCALE -> number(value)
            FieldType.DATETIME -> instant(value, zone)
            // "8:05" is stored as written: the file gives the hour two digits, like a clock
            FieldType.TIME -> value.toString().split(':').let { (h, m) -> "${h.padStart(2, '0')}:$m" }
            FieldType.DURATION -> duration((value as Number).toLong())
            FieldType.BOOLEAN -> (value as Boolean).toString()
            // By the label the screen shows, as the first line names the fields
            FieldType.CHOICE -> app.treelune.core.fields.ChoiceSettings.fromConfig(column.field.config).let { choice ->
                listOfValues(value).joinToString(";") { choice.labelOf(it.toString()) }
            }
            FieldType.REFERENCE -> listOfValues(value).joinToString(";") { reference ->
                val read = ReferenceTarget.referenceOf(reference) ?: error("A reference that does not read: $reference")
                names[read] ?: text("pointer_target_deleted")
            }
            FieldType.IMAGE -> listOfValues(value).size.toString()
            FieldType.RANGE -> error("A range is two cells")
        })
    }

    /** A number as it is, its decimals with a point, no trailing zero ("72.4", "150"). */
    private fun number(value: Any?): String = (value as? Number)?.let { BigDecimal(it.toString()).stripTrailingZeros().toPlainString() } ?: ""

    /** An instant in the app's time zone, to the second: a spreadsheet reads no fraction of a second. */
    private fun instant(value: Any?, zone: ZoneId): String {
        val millis = (value as Number).toLong()
        return DateTimeConverter.timestampToISO(millis - Math.floorMod(millis, 1000L), zone)
    }

    /** A duration as hours, minutes and seconds ("1:25:00"), the seconds rounded down. */
    private fun duration(millis: Long): String {
        val seconds = millis / 1000
        return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
}
