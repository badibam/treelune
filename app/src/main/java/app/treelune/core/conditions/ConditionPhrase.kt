package app.treelune.core.conditions

import android.content.Context
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FilterOperator
import app.treelune.core.fields.ReferenceName
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.loadReferenceNames
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.EntryPeriod
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.terms.Term
import app.treelune.core.ui.selectors.PointerDescription
import app.treelune.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject

/** A piece of a condition's phrase: text, or a constant it holds, which can be written over in place. */
sealed interface PhrasePart {
    data class Text(val text: String) : PhrasePart

    /**
     * A constant: its [text], where its term stands in the condition ([path], keys and positions
     * from the condition's root down to the term), its [value] in its Kotlin form, the [field] it
     * is a value of (its other side's) when known, and the [op] it is compared by.
     */
    data class Constant(val text: String, val path: List<Any>, val value: Any?, val field: FieldDefinition?, val op: FilterOperator?) : PhrasePart
}

/**
 * A condition in words (docs/design/progression.md, « Les écrans »): generic, written for any
 * condition by walking its tree, each nested piece between brackets — the filters of a reading
 * inside a comparison, a combination inside another — so that where each piece ends is never
 * ambiguous: « nombre de [étapes dont id ∈ liens Requis de l'étape jugée, et état ∈ {Faite}] = … ».
 * Readable, not natural. Its constants are parts of their own, with where they stand, for a
 * screen to let them be written over in place ([write]).
 *
 * @param sources Where the condition's names read what they name (ConfigConditions.Sources)
 * @param root The whole config the condition stands in, and [at], the condition's place in it:
 *   what the value of a thing judged (`self`) is read against
 * @param ownType The tool type of the config, whose own views a reading of `self` reads
 */
class ConditionPhrase(
    private val context: Context,
    private val sources: ConfigConditions.Sources,
    private val root: JSONObject,
    private val at: List<Any>,
    private val ownType: String?
) {
    private val s: StringsContext = Strings.`for`(context = context)

    /** [condition]'s phrase, in parts. */
    suspend fun of(condition: JSONObject): List<PhrasePart> = Phrase().apply { condition(condition, emptyList(), nested = false, scope = null) }.parts

    /** The parts as built, consecutive texts kept apart: a screen splits them as it wraps. */
    private inner class Phrase {
        val parts = mutableListOf<PhrasePart>()

        fun text(text: String) { if (text.isNotEmpty()) parts += PhrasePart.Text(text) }

        /**
         * A combination, or a comparison: on an entry's or a row's fields when [scope] gives them
         * (a reading's filter), on terms otherwise.
         */
        suspend fun condition(json: JSONObject, path: List<Any>, nested: Boolean, scope: Map<String, FieldDefinition>?) {
            val join = when {
                json.has(Conditions.ALL) -> Conditions.ALL
                json.has(Conditions.ANY) -> Conditions.ANY
                else -> null
            }
            if (join != null) {
                val items = json.optJSONArray(join) ?: JSONArray()
                if (nested) text("[")
                text(s.shared("phrase_$join"))
                for (i in 0 until items.length()) {
                    if (i > 0) text(s.shared("phrase_and_then"))
                    condition(items.optJSONObject(i) ?: JSONObject(), path + join + i, nested = true, scope = scope)
                }
                if (nested) text("]")
                return
            }
            val left = json.opt(Conditions.LEFT)
            val leftField = field(left, scope)
            term(left, path + Conditions.LEFT, scope, null, null)
            val op = FilterOperator.of(json.optString(Conditions.OP))
            text(op?.let { PointerDescription.operator(it, s) } ?: json.optString(Conditions.OP))
            if (op == FilterOperator.ABSENT || op == FilterOperator.PRESENT) return
            when (val right = json.opt(Conditions.RIGHT)) {
                // Between two terms
                is JSONArray -> for (i in 0 until right.length()) {
                    if (i > 0) text(s.shared("phrase_between_and"))
                    term(right.opt(i), path + Conditions.RIGHT + i, scope, leftField, op)
                }
                else -> term(right, path + Conditions.RIGHT, scope, leftField, op)
            }
        }

        /** One side: a constant of [field] compared by [op], a field of [scope], a value of the thing judged, a variable, a reading. */
        suspend fun term(json: Any?, path: List<Any>, scope: Map<String, FieldDefinition>?, field: FieldDefinition?, op: FilterOperator?) {
            val term = json as? JSONObject ?: return text("?")
            when {
                term.has(Conditions.CONSTANT) -> {
                    val value = JsonUtils.toValue(term.opt(Conditions.CONSTANT)?.takeIf { it != JSONObject.NULL })
                    val shown = when {
                        value == null -> s.shared("label_no_value")
                        field != null -> PointerDescription.value(field, value, s)
                        value is List<*> -> "{${value.joinToString(", ")}}"
                        value is Double && value % 1.0 == 0.0 -> value.toLong().toString()
                        else -> value.toString()
                    }
                    parts += PhrasePart.Constant(shown, path, value, field, op)
                }
                term.has(Conditions.FIELD) -> {
                    val name = term.optString(Conditions.FIELD)
                    text(scope?.get(name)?.displayName ?: name)
                }
                term.has(Term.SELF) -> text(self(term))
                term.has(Term.Kind.VARIABLE.key) -> text(name(Reference(ReferenceKind.VARIABLE, term.optString(Term.Kind.VARIABLE.key))))
                term.has(Term.Kind.READING.key) -> reading(term.optJSONObject(Term.Kind.READING.key) ?: JSONObject(), path + Term.Kind.READING.key)
                else -> text(term.keys().asSequence().joinToString(", "))
            }
        }

        /** « nombre de [Repas dont Kcal > 300, cette semaine] », « somme de Kcal de Repas ». */
        suspend fun reading(json: JSONObject, path: List<Any>) {
            val selection = json.optJSONObject("selection") ?: JSONObject()
            val target = ReferenceTarget.referenceOf(selection.opt("target"))
            val view = selection.optString("view").takeIf { it.isNotEmpty() }
            val fields = target?.let { targetFields(it, view) }.orEmpty()
            val reduction = Reduction.entries.firstOrNull { it.name == json.optString("reduction") }
            val field = json.optString("field").takeIf { it.isNotEmpty() }
            text(reduction?.let { s.shared(Reduction.labelKey(it, field?.let { f -> fields[f]?.type })).lowercase() } ?: json.optString("reduction"))
            text(s.shared("phrase_of"))
            field?.let { text(fields[it]?.displayName ?: it); text(s.shared("phrase_of")) }
            val filters = selection.optJSONArray("filters") ?: JSONArray()
            val period = selection.optJSONObject("period")?.let { periodText(it) }
            val bracketed = filters.length() > 0 || period != null
            if (bracketed) text("[")
            text(target?.let { subject(it, view) } ?: "?")
            if (filters.length() > 0) {
                text(s.shared("phrase_where"))
                for (i in 0 until filters.length()) {
                    if (i > 0) text(s.shared("phrase_and"))
                    condition(filters.optJSONObject(i) ?: JSONObject(), path + "selection" + "filters" + i, nested = true, scope = fields)
                }
            }
            period?.let { text(","); text(it) }
            if (bracketed) text("]")
            // A series counted while its condition holds
            json.optJSONObject(app.treelune.core.reading.Streaks.WHILE)?.let { keep ->
                text(s.shared("phrase_while"))
                condition(keep, path + app.treelune.core.reading.Streaks.WHILE, nested = true, scope = fields)
            }
        }

        /** What a reading reads: a tool, this tool when `self`, a view of either named by its type. */
        suspend fun subject(target: Reference, view: String?): String {
            val self = target.id == Term.SELF_TOOL
            val name = if (self) s.shared("phrase_this_tool") else name(target)
            val type = if (self) ownType else target.id?.let { app.treelune.core.database.AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(it)?.tooltype }
            val viewName = view?.let { v -> type?.let { Strings.`for`(tool = it, context = context).tool("view_$v") } ?: v }
            return listOfNotNull(name, viewName).joinToString(" ${s.shared("settings_path_separator")} ")
        }
    }

    /** The field [json] is a value of, on a comparison's side: what its constant on the other side is written as. */
    private suspend fun field(json: Any?, scope: Map<String, FieldDefinition>?): FieldDefinition? {
        val term = json as? JSONObject ?: return null
        return when {
            term.has(Conditions.FIELD) -> scope?.get(term.optString(Conditions.FIELD))
            term.has(Term.SELF) -> selfField(term)
            term.has(Term.Kind.VARIABLE.key) -> try { sources.variableField(term.optString(Term.Kind.VARIABLE.key)) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: IllegalStateException) { null }
            term.has(Term.Kind.READING.key) -> {
                val reading = term.optJSONObject(Term.Kind.READING.key) ?: return null
                val reduction = Reduction.entries.firstOrNull { it.name == reading.optString("reduction") }
                val path = reading.optString("field").takeIf { it.isNotEmpty() }
                if (reduction == Reduction.COUNT || reduction == Reduction.STREAK || path == null) NUMBER
                else {
                    val selection = reading.optJSONObject("selection") ?: return null
                    val target = ReferenceTarget.referenceOf(selection.opt("target")) ?: return null
                    targetFields(target, selection.optString("view").takeIf { it.isNotEmpty() })[path]
                }
            }
            else -> null
        }
    }

    private suspend fun selfField(term: JSONObject): FieldDefinition? = try {
        sources.selfField(root, at, Term.Self(term.optString(Term.SELF), term.optString(Term.SELF_TYPE).takeIf { it.isNotEmpty() }))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        null
    }

    /** « seuil de l'étape jugée », « liens Requis de l'étape jugée ». */
    private suspend fun self(term: JSONObject): String {
        // Its field names it, a link type with it; its key and its type otherwise
        val named = selfField(term)?.displayName ?: listOfNotNull(term.optString(Term.SELF), term.optString(Term.SELF_TYPE).takeIf { it.isNotEmpty() }).joinToString(" ")
        return sources.judged?.let { s.shared("phrase_of_judged").format(named, it) } ?: named
    }

    private suspend fun targetFields(target: Reference, view: String?): Map<String, FieldDefinition> = try {
        sources.targetFields(target, view)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        emptyMap()
    }

    private suspend fun name(reference: Reference): String =
        when (val named = loadReferenceNames(listOf(reference), context).values.firstOrNull()) {
            is ReferenceName.Named -> named.name
            else -> s.shared("pointer_target_deleted")
        }

    private fun periodText(json: JSONObject): String? = try {
        PointerDescription.period(EntryPeriod.fromJson(json) { s.shared(it) }, s)
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        /** What a count or a series is a value of: a whole number. */
        private val NUMBER = FieldDefinition("count", "", null, FieldType.NUMERIC, false, mapOf("decimals" to 0))

        /**
         * The phrase cut into what wraps: each word of a text, each constant whole, each followed
         * by the space that follows it in the phrase. No space before a comma, a semicolon or a
         * closing bracket, nor after an opening one; a word bringing its own space (« ; » in
         * French, after a narrow no-break space) keeps it.
         */
        fun tokens(parts: List<PhrasePart>): List<Pair<String, PhrasePart.Constant?>> {
            val pieces = parts.flatMap { part ->
                when (part) {
                    is PhrasePart.Text -> part.text.split(' ').filter { it.isNotEmpty() }.map { it to null }
                    is PhrasePart.Constant -> listOf(part.text to part)
                }
            }
            return pieces.mapIndexed { i, (text, constant) ->
                val next = pieces.getOrNull(i + 1)?.first
                val glued = next == null || text.endsWith("[") || next.first().isWhitespace() || next.first() in ",;]"
                (if (glued) text else "$text ") to constant
            }
        }

        /** The phrase as one text, spaced as it shows. */
        fun text(parts: List<PhrasePart>): String = tokens(parts).joinToString("") { it.first }

        /**
         * [condition] with the constant at [path] (ConditionPhrase's parts) written as [value],
         * the rest kept.
         */
        fun write(condition: JSONObject, path: List<Any>, value: Any?): JSONObject {
            val copy = JSONObject(condition.toString())
            var at: Any = copy
            for (step in path) at = when (at) {
                is JSONObject -> at.get(step as String)
                is JSONArray -> at.get(step as Int)
                else -> throw IllegalArgumentException("No constant at $path")
            }
            (at as JSONObject).put(Conditions.CONSTANT, value?.let { Term.Constant(it).toJson().get(Conditions.CONSTANT) } ?: JSONObject.NULL)
            return copy
        }
    }
}
