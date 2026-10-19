package app.treelune.tools.flashcards

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.ToolFields
import app.treelune.core.reading.FieldReading
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.Strings
import app.treelune.core.utils.JsonUtils

/**
 * The lines of a deck's source, read through tool_data.get as the deck filters them, in the order
 * they were added: what the cards are made of. A card is a line in a direction whose first front
 * field the line fills; the line's groups are the values of the field group_by.
 *
 * @property fields The source's fields, by path
 * @property entries The lines read, by id, as tool_data gives them
 */
class FlashcardsSource(
    val name: String,
    val fields: Map<String, FieldDefinition>,
    val lines: List<FlashcardsDue.Line>,
    val entries: Map<String, Map<String, Any?>>
) {
    /** The groups present in the source, sorted: a value typed in shows its group, a value emptied takes it away. */
    val groups: List<String> get() {
        val collator = java.text.Collator.getInstance()
        return lines.flatMap { it.groups }.distinct().sortedWith(collator)
    }

    /** The values of [paths] in the line [id], in their order. */
    fun values(id: String, paths: List<String>): List<Pair<String, Any?>> {
        val entry = entries[id] ?: return emptyList()
        return paths.map { it to FieldReading.valueAt(entry, it) }
    }

    companion object {
        /**
         * The source of [deck], its filters' relative dates resolved at [now].
         *
         * @throws IllegalStateException when the source cannot be read, or lacks a field the deck
         *   names, the error naming it: never a card left blank
         */
        suspend fun read(context: Context, deck: FlashcardsDeck, now: Long): FlashcardsSource {
            val s = Strings.`for`(tool = "flashcards", context = context)
            val coordinator = Coordinator(context)
            val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to deck.sourceId))
            val name = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("name") as? String)
                ?: throw IllegalStateException(s.tool("error_source_gone"))
            val fields = ToolFields.filterable(deck.sourceId, context, s)
            deck.namedFields.firstOrNull { it !in fields }?.let { path ->
                throw IllegalStateException(s.tool("error_field_gone").format(path.substringAfter('.'), name))
            }
            val result = coordinator.processUserAction("tool_data.get", mapOf(
                "tool_instance_id" to deck.sourceId,
                "filters" to JsonUtils.toList(deck.source.storedFilters(fields, TimeResolver.at(now)) { s.shared(it) })
            ))
            if (!result.isSuccess) throw IllegalStateException(result.error ?: s.shared("service_error_operation_failed"))
            @Suppress("UNCHECKED_CAST")
            val entries = (result.data?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
                .sortedWith(compareBy({ (it["created_at"] as Number).toLong() }, { it["id"] as String }))
            val lines = entries.map { entry ->
                FlashcardsDue.Line(
                    id = entry["id"] as String,
                    groups = deck.groupBy?.let { groupsOf(FieldReading.valueAt(entry, it)) } ?: emptySet(),
                    asks = deck.directions.filter { d -> d.front.firstOrNull()?.let { FieldReading.valueAt(entry, it) } != null }.map { it.key }.toSet()
                )
            }
            return FlashcardsSource(name, fields, lines, entries.associateBy { it["id"] as String })
        }

        /** The groups a value puts its line in: each of a list's values, a value alone otherwise. */
        private fun groupsOf(value: Any?): Set<String> = when (value) {
            null -> emptySet()
            is List<*> -> value.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }.toSet()
            else -> setOfNotNull(value.toString().takeIf { it.isNotBlank() })
        }
    }
}
