package app.treelune.core.conditions

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.toFieldConfig
import app.treelune.core.reading.FieldReading
import app.treelune.core.reading.ReadingGroup
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.ReferenceKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * The conditions a config holds, found through its declaration (docs/BRICKS.md, « Condition »):
 * the walk follows the SettingNodes along with the value, and hands each condition with what its
 * sides may name. A tool type declaring conditions has them found without writing anything.
 *
 * Found: a condition setting (SettingNode.Condition), judged once or put on each row; the filters
 * of a selection (SettingNode.Selection), of a term's reading (SettingNode.Term), and of a
 * reading standing on a side of a condition judged once; the keep of a grouped reading
 * (ReadingGroup), judged once with the group's value on its left.
 *
 * @param sources Where the fields its sides may name are read
 * @param text The shared string of a key, for the labels
 */
class ConfigConditions(private val sources: Sources, private val text: (String) -> String) {

    /** Where the walk and the check read the fields of what a condition names, from declarations only. */
    interface Sources {
        /**
         * The fields of a tool instance's entries, by path (ToolFields.filterable).
         *
         * @throws IllegalStateException when the tool cannot be read
         */
        suspend fun toolFields(toolInstanceId: String): Map<String, FieldDefinition>

        /**
         * The fields of the entries a reading's [target] holds: a tool instance's, or for one
         * entry, its tool's; or of the rows of its [view] (core/views), which only the app's own
         * sources open.
         *
         * @throws IllegalStateException when they cannot be read
         */
        suspend fun targetFields(target: app.treelune.core.selection.Reference, view: String?): Map<String, FieldDefinition> =
            if (view == null) toolFields(target.id!!) else throw IllegalStateException(view)

        /** The field of the variable [id] as its definition stores it; null when there is none. */
        suspend fun variableField(id: String): FieldDefinition?

        /**
         * The fields of the rows the config describes at [path] (RowFields); null when its
         * owner gives none.
         */
        suspend fun rowFields(root: JSONObject, path: List<Any>): Map<String, FieldDefinition>?

        /**
         * The field the value of the thing judged [self] names is a value of, for a condition
         * found at [path] of [root] (a Path's rule, on a stage of the kind it belongs to); null
         * where no thing is judged, which refuses it. Only a config that judges a thing of its
         * own answers (ToolTypeContract.conditionSources).
         *
         * @throws IllegalArgumentException naming a value the thing judged does not have
         */
        suspend fun selfField(root: JSONObject, path: List<Any>, self: app.treelune.core.terms.Term.Self): FieldDefinition? = null

        /** What the thing judged is called in a condition's phrase (« l'étape jugée »); null where none is (ConditionPhrase). */
        val judged: String? get() = null
    }

    /** How a condition is judged, which says what its sides may be. */
    enum class Kind {
        /** Once: each side a term, its left the value [Found.entered] when one is declared. */
        ONCE,
        /** On each entry of a selection, in SQL: a field of the entry on the left, written values on the right. */
        ON_ENTRY,
        /** On each row the config describes: fields of the row or written values. */
        ON_ROW
    }

    /**
     * One condition found.
     *
     * @property label Where it stands, for an error to name it as the screen does
     * @property condition Its stored form
     * @property fields The fields a side may name, by path or name: the entries' for [Kind.ON_ENTRY], the row's for [Kind.ON_ROW]
     * @property entered For [Kind.ONCE], the value entered in each attempt that stands on its left whatever is stored there
     * @property tool For [Kind.ON_ENTRY], the tool instance whose entries it is put on; null when its selection names none
     * @property root The config it stands in, and [path] the place of the object holding it, for
     *   what a side reads of the thing judged there (Sources.selfField)
     */
    data class Found(
        val label: String,
        val condition: JSONObject,
        val kind: Kind,
        val fields: Map<String, FieldDefinition> = emptyMap(),
        val entered: FieldDefinition? = null,
        val tool: String? = null,
        val root: JSONObject = JSONObject(),
        val path: List<Any> = emptyList()
    )

    /**
     * [config] with each condition it holds through [visit], in the order of its declaration
     * [nodes]: what [visit] returns takes its place. Inside a condition judged once, the readings
     * on its sides are walked once [visit] has turned it.
     *
     * @throws IllegalArgumentException naming a selection whose tool cannot be read
     */
    suspend fun map(nodes: List<SettingNode>, config: JSONObject, visit: suspend (Found) -> JSONObject): JSONObject {
        val root = JSONObject(config.toString())
        walk(nodes, root, root, emptyList(), "", visit)
        return root
    }

    /** Each condition of [config], for a check that only reads. */
    suspend fun each(nodes: List<SettingNode>, config: JSONObject, visit: suspend (Found) -> Unit) {
        map(nodes, config) { found -> visit(found); found.condition }
    }

    private suspend fun walk(nodes: List<SettingNode>, obj: JSONObject, root: JSONObject, path: List<Any>, label: String, visit: suspend (Found) -> JSONObject) {
        for (node in nodes) when (node) {
            is SettingNode.Field, is SettingNode.Sort, is SettingNode.Period, is SettingNode.Instants, is SettingNode.FieldValues -> Unit
            is SettingNode.Group -> obj.optJSONObject(node.name)?.let { walk(node.nodes, it, root, path + node.name, under(label, node.label), visit) }
            is SettingNode.ListOf -> {
                val item = node.item as? SettingNode.Item.Of ?: continue
                val elements = obj.optJSONArray(node.name) ?: continue
                for (i in 0 until elements.length()) {
                    val element = elements.optJSONObject(i) ?: continue
                    // An element is named by the first value of its summary, or by its position
                    val name = node.summary.firstNotNullOfOrNull { key -> element.optString(key).takeIf { it.isNotBlank() } } ?: "${i + 1}"
                    walk(item.nodes, element, root, path + node.name + i, under(under(label, node.label), name), visit)
                }
            }
            // Only the chosen case is stored, flat beside the selector
            is SettingNode.Variant -> {
                val chosen = obj.opt(node.selector.definition.name)?.takeIf { it != JSONObject.NULL }?.toString() ?: node.selector.default?.toString()
                node.cases[chosen]?.let { walk(it, obj, root, path, label, visit) }
            }
            is SettingNode.Section -> walk(node.nodes, obj, root, path, label, visit)
            // A combination (all, any) found comparison by comparison, kept around them
            is SettingNode.Condition -> {
                val condition = obj.optJSONObject(node.name) ?: continue
                val here = under(label, node.label)
                Conditions.combinationProblem(condition, here, text)?.let { throw IllegalArgumentException(it) }
                if (!node.combines && Conditions.isCombined(condition)) throw IllegalArgumentException(text("condition_error_not_combined").format(here))
                if (node.onRow) {
                    val fields = sources.rowFields(root, path) ?: error("The condition '${node.name}' is put on each row, and its config's owner gives no RowFields")
                    obj.put(node.name, Conditions.mapComparisons(condition) { visit(Found(here, it, Kind.ON_ROW, fields, root = root, path = path)) })
                } else {
                    val turned = Conditions.mapComparisons(condition) { comparison ->
                        visit(Found(here, comparison, Kind.ONCE, entered = entered(node, obj), root = root, path = path)).also { turned ->
                            sidesOf(turned).forEach { side -> reading(side, here, root, path, visit) }
                        }
                    }
                    obj.put(node.name, turned)
                }
            }
            is SettingNode.Term -> obj.optJSONObject(node.name)?.let { reading(it, under(label, node.label), root, path, visit) }
            is SettingNode.Selection -> obj.optJSONObject(node.name)?.let { selection(it, under(label, node.label), root, path, visit) }
        }
    }

    /** The value declared beside [node] (SettingNode.Condition.enteredField), as a field; null when none is. */
    private fun entered(node: SettingNode.Condition, obj: JSONObject): FieldDefinition? {
        val declared = node.enteredField?.let { obj.optJSONObject(it) } ?: return null
        val type = FieldType.entries.firstOrNull { it.name == declared.optString("type") } ?: return null
        return FieldDefinition(node.name, node.label, null, type, false, declared.optJSONObject("config")?.toFieldConfig())
    }

    /** The sides of a condition as stored: its left, then its right, a pair for `between`. */
    private fun sidesOf(condition: JSONObject): List<JSONObject> = buildList {
        condition.optJSONObject(Conditions.LEFT)?.let { add(it) }
        when (val right = condition.opt(Conditions.RIGHT)) {
            is JSONObject -> add(right)
            is JSONArray -> (0 until right.length()).forEach { i -> right.optJSONObject(i)?.let { add(it) } }
        }
    }

    /**
     * The filters of the reading [term] stands for, if it is one, then the keep of its groups and
     * the readings on its sides.
     */
    private suspend fun reading(term: JSONObject, label: String, root: JSONObject, path: List<Any>, visit: suspend (Found) -> JSONObject) {
        val reading = term.optJSONObject("reading") ?: return
        reading.optJSONObject("selection")?.let { selection(it, label, root, path, visit) }
        // A streak of rows: its condition put on each row's value of the field read
        reading.optJSONObject(app.treelune.core.reading.Streaks.WHILE)?.let { streak ->
            val value = readField(reading) ?: return@let
            val here = under(label, text("reading_streak_while"))
            val turned = visit(Found(here, ReadingGroup.keepCondition(streak), Kind.ONCE, entered = value, root = root, path = path))
            turned.remove(Conditions.LEFT)
            reading.put(app.treelune.core.reading.Streaks.WHILE, turned)
            sidesOf(turned).forEach { side -> reading(side, here, root, path, visit) }
        }
        val group = reading.optJSONObject("group") ?: return
        // A group's streak: its condition on each group's value
        group.optJSONObject(app.treelune.core.reading.Streaks.WHILE)?.let { streak ->
            val value = groupValue(reading) ?: return@let
            val here = under(label, text("reading_streak_while"))
            val turned = visit(Found(here, ReadingGroup.keepCondition(streak), Kind.ONCE, entered = value, root = root, path = path))
            turned.remove(Conditions.LEFT)
            group.put(app.treelune.core.reading.Streaks.WHILE, turned)
            sidesOf(turned).forEach { side -> reading(side, here, root, path, visit) }
        }
        val keep = group.optJSONObject(ReadingGroup.KEEP) ?: return
        // A group's value whose field does not read leaves the keep to the reading's own check, which names it
        val value = groupValue(reading) ?: return
        val here = under(label, text("reading_group_keep"))
        val turned = visit(Found(here, ReadingGroup.keepCondition(keep), Kind.ONCE, entered = value, root = root, path = path))
        turned.remove(Conditions.LEFT)
        group.put(ReadingGroup.KEEP, turned)
        sidesOf(turned).forEach { side -> reading(side, here, root, path, visit) }
    }

    /** The field [reading] reads, as its target declares it; null when it does not read. */
    private suspend fun readField(reading: JSONObject): FieldDefinition? {
        val selection = reading.optJSONObject("selection")
        val target = selection?.optJSONObject("target")?.let { app.treelune.core.selection.Reference.fromJson(it) } ?: return null
        val fields = try { sources.targetFields(target, selection.optString("view").takeIf { it.isNotEmpty() }) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: IllegalStateException) { return null } catch (e: IllegalArgumentException) { return null }
        return fields[reading.optString("field")]
    }

    /** The field each group of [reading] has a value of: its own field reduced, or a count; null when it does not read. */
    private suspend fun groupValue(reading: JSONObject): FieldDefinition? {
        val reduction = Reduction.entries.firstOrNull { it.name == reading.optString("reduction") } ?: return null
        if (reduction == Reduction.COUNT) return FieldReading.COUNT_FIELD
        val selection = reading.optJSONObject("selection")
        val target = selection?.optJSONObject("target")?.let { app.treelune.core.selection.Reference.fromJson(it) } ?: return null
        val fields = try { sources.targetFields(target, selection.optString("view").takeIf { it.isNotEmpty() }) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: IllegalStateException) { return null } catch (e: IllegalArgumentException) { return null }
        return fields[reading.optString("field")]
    }

    /** The filters of [selection], each put on an entry of its tool and naming its fields. */
    private suspend fun selection(selection: JSONObject, label: String, root: JSONObject, path: List<Any>, visit: suspend (Found) -> JSONObject) {
        val filters = selection.optJSONArray("filters")?.takeIf { it.length() > 0 } ?: return
        val target = selection.optJSONObject("target")
        // A tool's entries, or a view's rows (core/views: a variable's series), whose fields its filters name
        val view = selection.optString("view").takeIf { it.isNotEmpty() }
        val kind = target?.optString("kind")?.takeIf { it == ReferenceKind.TOOL_INSTANCE.name || (view != null && it == ReferenceKind.VARIABLE.name) }
        val id = target?.optString("id")?.takeIf { kind != null && it.isNotEmpty() }
        // Filters on anything else name no field it has: each is refused as such
        val fields = id?.let {
            try {
                sources.targetFields(app.treelune.core.selection.Reference(ReferenceKind.valueOf(kind!!), it), view)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                throw IllegalArgumentException("$label : ${e.message}")
            }
        } ?: emptyMap()
        for (i in 0 until filters.length()) {
            val filter = filters.optJSONObject(i) ?: continue
            filters.put(i, visit(Found(label, filter, Kind.ON_ENTRY, fields, tool = id, root = root, path = path)))
        }
    }

    private fun under(label: String, step: String) = if (label.isEmpty()) step else "$label › $step"
}
