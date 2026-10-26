package app.treelune.core.ai.prompts

import app.treelune.core.fields.FieldValueSchema
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import org.json.JSONArray
import org.json.JSONObject

/**
 * A JSON schema written for the model to read: one line per value, nested by indentation, each
 * value with its label and its description once.
 *
 * The app validates against the JSON schema itself; this is only what the model reads of it. It
 * carries the same information in a fraction of the size: a variant (a oneOf whose branches a
 * selector's const tells apart) says once what its options share and then what each adds,
 * where the JSON form repeats the whole object in every branch, and the JSON Schema vocabulary
 * around each value (properties, required lists, additionalProperties) gives way to a legend
 * the prompt states once (ai_chunk_validation_schema_principle).
 *
 * It reads the model's view of a schema (SchemaModelView.forModel): dates and durations are
 * already ISO 8601 strings there.
 *
 * An object named as a definition (SettingsSchemaGenerator.DEFINITION), which stands in several
 * places, is written once at the end of the schema under its name, each place saying "Name (see
 * Name)" in its stead; an option of a variant may be named so too, its own settings then written
 * there, as an object named the same elsewhere is (a step in a block).
 *
 * Every keyword a schema holds is either written or deliberately implied; an unknown one fails
 * rather than vanishes from what the model reads (UnknownKeyword).
 */
object SchemaNotation {

    private const val FIELD_DEFINITIONS = SettingsSchemaGenerator.FIELD_DEFINITIONS
    private const val DEFINITION = SettingsSchemaGenerator.DEFINITION

    /** What a keyword is to the notation. */
    private val WRITTEN = setOf(
        "type", "properties", "required", "title", "description", "default", "enum", "const",
        "oneOf", "items", "minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems",
        "uniqueItems", "multipleOf", "pattern", "format", "system_managed", app.treelune.core.fields.AI_READ_ONLY, SettingsSchemaGenerator.SECRET, FIELD_DEFINITIONS,
        DEFINITION
    )

    /** Implied by the legend: only the keys listed are accepted. */
    private val IMPLIED = setOf("additionalProperties")

    /** A keyword the notation does not know: a schema holding one is not sent half-read. */
    class UnknownKeyword(keyword: String, path: String) :
        IllegalStateException("Schema keyword '$keyword' at $path has no notation for the model")

    /** [schema], an object's schema, as the notation, its definitions after it. */
    fun render(schema: JSONObject): String {
        val definitions = Definitions()
        val lines = objectLines(schema, "", "$", definitions).toMutableList()
        // Written in the order first met; one may name another, met while it is written
        var written = 0
        while (written < definitions.byName.size) {
            val (name, definition) = definitions.byName.entries.elementAt(written++)
            lines += ""
            lines += "$name:" + (definition.description?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")
            lines += definition.lines(definitions)
        }
        return lines.joinToString("\n")
    }

    /**
     * A definition met: the object it stands for, written when the definitions are; or an
     * option's settings, already written.
     */
    private class Definition(val description: String?, val schema: JSONObject?, val written: List<String>?) {
        fun lines(definitions: Definitions): List<String> = written ?: objectLines(schema!!, "", "definition", definitions)
    }

    /** The definitions met in one schema, by name. */
    private class Definitions {
        val byName = LinkedHashMap<String, Definition>()

        /** [name] for [schema], which every other place under that name must hold alike. */
        fun meet(name: String, schema: JSONObject, path: String): String =
            meet(name, Definition(schema.optString("description"), schema, null), path)

        /** [name] for an option's settings written [lines], which every other place under that name must write alike. */
        fun meet(name: String, description: String?, lines: List<String>, path: String): String =
            meet(name, Definition(description, null, lines), path)

        private fun meet(name: String, met: Definition, path: String): String {
            val known = byName.getOrPut(name) { met }
            if (known !== met) {
                // Two objects compare as they are; an object and an option's settings, as written
                val alike = if (known.schema != null && met.schema != null) known.schema.toString() == met.schema.toString()
                    else known.lines(this) == met.lines(this)
                check(alike) { "The definition '$name' at $path differs from the one met first" }
            }
            return "$name (see $name)"
        }
    }

    private fun objectLines(schema: JSONObject, indent: String, path: String, definitions: Definitions): List<String> {
        check(schema, path)
        schema.optJSONArray("oneOf")?.let { return variantLines(it, indent, path, definitions) }
        val properties = schema.optJSONObject("properties") ?: return emptyList()
        val required = stringSet(schema.optJSONArray("required"))
        return properties.keys().asSequence().toList().flatMap { name ->
            valueLines(name, properties.getJSONObject(name), name in required, indent, "$path.$name", definitions)
        }
    }

    /**
     * A oneOf: when one property holds a different const in every branch, it is the selector,
     * and what every branch holds alike is written once, the rest under "if <selector> =
     * <option>". Otherwise each branch is written whole. A const the same in every branch (a
     * step's kind, around the variant of its end) is not the selector: it is written once, as
     * what the branches share.
     */
    private fun variantLines(branches: JSONArray, indent: String, path: String, definitions: Definitions): List<String> {
        val all = (0 until branches.length()).map { branches.getJSONObject(it) }
        all.forEachIndexed { i, branch -> check(branch, "$path.oneOf[$i]") }
        val props = all.map { it.optJSONObject("properties") }
        // A branch that branches again: two selectors side by side, or one inside an option
        if (props.any { it == null }) return Choices.of(all, path)?.lines(indent, path, definitions) ?: oneOfWhole(all, indent, path, definitions)
        val selector = props[0]!!.keys().asSequence().firstOrNull { key ->
            props.all { it!!.optJSONObject(key)?.has("const") == true } &&
                props.map { it!!.getJSONObject(key).get("const").toString() }.distinct().size == props.size
        } ?: return oneOfWhole(all, indent, path, definitions)

        val requireds = all.map { stringSet(it.optJSONArray("required")) }
        fun same(key: String): Boolean {
            val first = props[0]!!.optJSONObject(key)?.toString() ?: return false
            return props.all { it!!.optJSONObject(key)?.toString() == first } &&
                requireds.all { (key in it) == (key in requireds[0]) }
        }
        val shared = props[0]!!.keys().asSequence().filter { it != selector && same(it) }.toList()

        val lines = mutableListOf<String>()
        shared.forEach { lines += valueLines(it, props[0]!!.getJSONObject(it), it in requireds[0], indent, "$path.$it", definitions) }

        // The selector, with every option
        val selectorSchema = JSONObject(props[0]!!.getJSONObject(selector).toString())
        selectorSchema.remove("const")
        selectorSchema.put("enum", JSONArray(props.map { it!!.getJSONObject(selector).get("const") }))
        lines += valueLines(selector, selectorSchema, true, indent, "$path.$selector", definitions)

        all.forEachIndexed { i, branch ->
            val option = props[i]!!.getJSONObject(selector).get("const")
            val own = props[i]!!.keys().asSequence().filter { it != selector && it !in shared }.toList()
            // What the option means, which the selector's description leaves to each option
            val meaning = all[i].optString("description").takeIf { it.isNotBlank() }
            fun ownLines(at: String) = own.flatMap { key -> valueLines(key, props[i]!!.getJSONObject(key), key in requireds[i], at, "$path.$key", definitions) } +
                // A variant inside this option's own settings
                (branch.optJSONArray("oneOf")?.let { variantLines(it, at, path, definitions) } ?: emptyList())
            val named = branch.optString(DEFINITION).takeIf { it.isNotEmpty() }
            val text = listOfNotNull(meaning, named?.let { definitions.meet(it, meaning, ownLines(""), "$path.oneOf[$i]") }, if (own.isEmpty() && named == null) "nothing more" else null)
            lines += "$indent  if $selector = $option:" + text.joinToString(". ").let { if (it.isEmpty()) "" else " $it" }
            if (named == null) lines += ownLines("$indent    ")
        }
        return lines
    }

    /**
     * A oneOf whose branches branch again, read through its leaves, every whole object it allows:
     * two selectors side by side (a session's kind and its expiry), each option's settings
     * written under it once, or a selector inside an option (a step's end, inside « kind = step »),
     * read again under that option. What every leaf holds alike is written once.
     *
     * @property leaves Each whole object: its properties and its required names
     * @property meanings What a branch says it is, by the leaves it holds
     * @property names The name a branch's settings are written under once, by the leaves it holds
     */
    private class Choices(val leaves: List<Leaf>, val meanings: List<Pair<Set<Int>, String>>, val names: List<Pair<Set<Int>, String>>) {

        class Leaf(val properties: JSONObject, val required: Set<String>)

        companion object {
            /** The leaves of [branches]; null when one branch is neither an object nor a oneOf. */
            fun of(branches: List<JSONObject>, path: String): Choices? {
                val leaves = mutableListOf<Leaf>()
                val meanings = mutableListOf<Pair<Set<Int>, String>>()
                val names = mutableListOf<Pair<Set<Int>, String>>()
                fun walk(branch: JSONObject, at: String): Boolean {
                    check(branch, at)
                    val from = leaves.size
                    val nested = branch.optJSONArray("oneOf")
                    when {
                        nested != null && !branch.has("properties") ->
                            for (i in 0 until nested.length()) if (!walk(nested.getJSONObject(i), "$at.oneOf[$i]")) return false
                        nested == null && branch.has("properties") ->
                            leaves += Leaf(branch.getJSONObject("properties"), stringSet(branch.optJSONArray("required")))
                        else -> return false
                    }
                    branch.optString("description").takeIf { it.isNotBlank() }?.let { meanings += (from until leaves.size).toSet() to it }
                    branch.optString(DEFINITION).takeIf { it.isNotEmpty() }?.let { names += (from until leaves.size).toSet() to it }
                    return true
                }
                branches.forEachIndexed { i, branch -> if (!walk(branch, "$path.oneOf[$i]")) return null }
                return Choices(leaves, meanings, names)
            }
        }

        /** The lines of every leaf; null when they cannot be told apart by selectors. */
        fun lines(indent: String, path: String, definitions: Definitions): List<String>? =
            lines(leaves.indices.toList(), emptySet(), indent, path, definitions)

        /** Whether [key] is held alike, required alike, by every leaf of [group]. */
        private fun same(key: String, group: List<Int>): Boolean {
            val first = leaves[group[0]]
            val schema = first.properties.optJSONObject(key)?.toString() ?: return false
            return group.all { leaves[it].properties.optJSONObject(key)?.toString() == schema && (key in leaves[it].required) == (key in first.required) }
        }

        private fun const(leaf: Int, key: String): String? =
            leaves[leaf].properties.optJSONObject(key)?.takeIf { it.has("const") }?.get("const")?.toString()

        private fun lines(group: List<Int>, written: Set<String>, indent: String, path: String, definitions: Definitions): List<String>? {
            val keys = LinkedHashSet<String>().apply { group.forEach { leaf -> leaves[leaf].properties.keys().forEach { add(it) } } } - written
            // A selector holds a const in every leaf, and more than one
            val selectors = keys.filter { key -> group.all { const(it, key) != null } && group.map { const(it, key) }.distinct().size > 1 }
            if (selectors.isEmpty()) return null
            val shared = keys.filter { it !in selectors && same(it, group) }
            val first = leaves[group[0]]

            // Each option's own settings, held alike by its leaves, claimed by the first selector that has them
            val claimed = (written + shared + selectors).toMutableSet()
            val options = selectors.associateWith { selector ->
                group.groupBy { const(it, selector)!! }.map { (option, leavesOf) ->
                    val own = keys.filter { it !in claimed && same(it, leavesOf) }
                    Triple(option, leavesOf, own)
                }.also { found -> found.forEach { claimed += it.third } }
            }

            val out = mutableListOf<String>()
            shared.forEach { out += valueLines(it, first.properties.getJSONObject(it), it in first.required, indent, "$path.$it", definitions) }
            for (selector in selectors) {
                val selectorSchema = JSONObject(first.properties.getJSONObject(selector).toString())
                selectorSchema.remove("const")
                selectorSchema.put("enum", JSONArray(options.getValue(selector).map { it.first }))
                out += valueLines(selector, selectorSchema, true, indent, "$path.$selector", definitions)
                for ((option, leavesOf, own) in options.getValue(selector)) {
                    // What the option means: the branch that holds exactly its leaves
                    val meaning = meanings.firstOrNull { it.first == leavesOf.toSet() }?.second
                    // Left over in this option: a selector inside it, read again under it
                    val inner = keys.filter { it !in claimed && leavesOf.any { leaf -> leaves[leaf].properties.has(it) } }
                    fun ownLines(at: String): List<String>? {
                        val held = leaves[leavesOf[0]]
                        val nested = if (inner.isEmpty()) emptyList() else lines(leavesOf, written + keys - inner.toSet(), at, path, definitions) ?: return null
                        return own.flatMap { key -> valueLines(key, held.properties.getJSONObject(key), key in held.required, at, "$path.$key", definitions) } + nested
                    }
                    val named = names.firstOrNull { it.first == leavesOf.toSet() }?.second
                    val seeAlso = named?.let { definitions.meet(it, meaning, ownLines("") ?: return null, "$path.$selector=$option") }
                    out += "$indent  if $selector = $option:" + listOfNotNull(meaning, seeAlso, if (own.isEmpty() && inner.isEmpty()) "nothing more" else null)
                        .joinToString(". ").let { if (it.isEmpty()) "" else " $it" }
                    if (named == null) out += ownLines("$indent    ") ?: return null
                }
            }
            return out
        }
    }

    private fun oneOfWhole(branches: List<JSONObject>, indent: String, path: String, definitions: Definitions): List<String> =
        branches.flatMapIndexed { i, branch ->
            listOf("$indent  one of (${i + 1}):") + objectLines(branch, "$indent    ", "$path.oneOf[$i]", definitions)
        }

    /** One value under [name], then what it holds, indented. */
    private fun valueLines(name: String, schema: JSONObject, required: Boolean, indent: String, path: String, definitions: Definitions): List<String> {
        check(schema, path)
        val head = "$indent$name${if (required) "*" else ""}"
        val text = text(schema)
        val constraints = constraints(schema, required)

        if (schema.optBoolean(FIELD_DEFINITIONS)) {
            return listOf("$head: list of field definitions (see Fields)$constraints$text")
        }
        return when {
            schema.optString("type") == "array" -> {
                val items = schema.optJSONObject("items") ?: JSONObject()
                check(items, "$path[]")
                // A named element: what it is goes with its definition
                if (isObject(items) && items.has(DEFINITION)) {
                    return listOf("$head: list of ${definitions.meet(items.getString(DEFINITION), items, "$path[]")}$constraints$text")
                }
                // What one element is, after what the list is
                val each = items.optString("description").takeIf { it.isNotBlank() }?.let { " Each element: $it" } ?: ""
                if (isObject(items)) listOf("$head: list of$constraints$text$each") + objectLines(items, "$indent  ", "$path[]", definitions)
                else listOf("$head: list of ${kind(items)}${constraints(items)}${text(items)}$constraints$text")
            }
            // A named group: its label here, what it is with its definition
            isObject(schema) && schema.has(DEFINITION) ->
                listOf("$head: ${definitions.meet(schema.getString(DEFINITION), schema, path)}$constraints${text(schema.optString("title"))}")
            isObject(schema) -> listOf("$head:$constraints$text") + objectLines(schema, "$indent  ", path, definitions)
            else -> listOf("$head: ${kind(schema)}$constraints$text")
        }
    }

    /** An object written property by property; a reference is written as one value. */
    private fun isObject(schema: JSONObject) =
        (schema.has("properties") || schema.has("oneOf")) && schema.optString("format") != FieldValueSchema.REFERENCE

    /** What kind of value: the one it is held to, its options, its format, or its type. */
    private fun kind(schema: JSONObject): String {
        // A selector held to one option inside a branch keeps its options beside
        if (schema.has("const")) return "always ${schema.get("const")}"
        schema.optJSONArray("enum")?.let { options -> return (0 until options.length()).joinToString("|") { options.get(it).toString() } }
        when (schema.optString("format")) {
            "date-time" -> return "ISO 8601 date-time with offset"
            "duration" -> return "ISO 8601 duration"
            "date" -> return "date YYYY-MM-DD"
            FieldValueSchema.MARKUP -> return "text with markup"
            FieldValueSchema.REFERENCE -> {
                val kinds = schema.optJSONObject("properties")?.optJSONObject("kind")?.optJSONArray("enum")
                    ?.let { options -> (0 until options.length()).joinToString("|") { options.getString(it) } } ?: "any"
                return "reference {kind: $kinds, id}"
            }
        }
        return when (val type = schema.opt("type")) {
            is JSONArray -> (0 until type.length()).joinToString("|") { type.getString(it) }
            null -> "any"
            else -> type.toString()
        }
    }

    /** The bounds and flags of a value, in brackets. */
    private fun constraints(schema: JSONObject, required: Boolean = false): String {
        val bits = mutableListOf<String>()
        schema.opt("minimum")?.let { bits += "min $it" }
        schema.opt("maximum")?.let { bits += "max $it" }
        schema.opt("multipleOf")?.let { bits += "step $it" }
        schema.opt("minLength")?.let { bits += "min length $it" }
        schema.opt("maxLength")?.let { bits += "max length $it" }
        schema.opt("minItems")?.let { bits += "at least $it" }
        schema.opt("maxItems")?.let { bits += "at most $it" }
        if (schema.optBoolean("uniqueItems")) bits += "distinct"
        schema.opt("pattern")?.let { bits += "pattern $it" }
        // An optional value's default is what its absence means; a required one's only what to
        // write when nothing else is meant, since its absence is refused
        schema.opt("default")?.let { bits += (if (required) "suggested " else "default ") + if (it is String) "\"$it\"" else it.toString() }
        if (schema.optBoolean("system_managed")) bits += "written by the app, never sent"
        if (schema.optBoolean(app.treelune.core.fields.AI_READ_ONLY)) bits += "entered by the user, read only: never sent"
        if (schema.optBoolean("secret")) bits += "secret, never shown"
        return if (bits.isEmpty()) "" else " [${bits.joinToString(", ")}]"
    }

    /** Its label and description, after a dash. */
    private fun text(schema: JSONObject): String = text(schema.optString("title"), schema.optString("description"))

    private fun text(vararg parts: String): String {
        val written = parts.filter { it.isNotBlank() }
        return if (written.isEmpty()) "" else " — " + written.joinToString(". ")
    }

    /** Fails on a keyword the notation neither writes nor implies. */
    private fun check(schema: JSONObject, path: String) {
        schema.keys().forEach { key ->
            if (key !in WRITTEN && key !in IMPLIED) throw UnknownKeyword(key, path)
        }
    }

    private fun stringSet(array: JSONArray?): Set<String> =
        if (array == null) emptySet() else (0 until array.length()).map { array.getString(it) }.toSet()
}
