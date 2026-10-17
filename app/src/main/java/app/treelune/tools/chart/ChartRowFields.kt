package app.treelune.tools.chart

import android.content.Context
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.RowFields
import app.treelune.core.selection.EntrySelection
import app.treelune.core.strings.Strings
import app.treelune.core.terms.Term
import app.treelune.core.ui.components.PeriodType
import app.treelune.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * The columns a chart's form offers where a setting names one (RowFields): in a layer, those its
 * source gives once the transforms before the setting have turned them — a transform's own
 * fields name what the transforms before it leave; elsewhere (a facet's field, a repeat's), those
 * of every layer. A repeat's channels also offer the field repeated.
 *
 * The config is being written: what does not read yet is left out rather than refused, the
 * service's check (ChartCheck) naming it at the save.
 */
object ChartRowFields : RowFields {

    override suspend fun at(root: JSONObject, path: List<Any>, context: Context): Map<String, FieldDefinition> {
        val reads = reads(root, path)
        val sources = ChartSources(context)
        val found = reads.fold(emptyMap<String, FieldDefinition>()) { all, layer ->
            all + try { columns(sources, layer) } catch (e: IllegalStateException) {
                LogManager.ui("ChartRowFields: columns not read: ${e.message}", "WARN")
                emptyMap()
            }
        }
        // A repeat's channels also offer the field repeated
        val repeat = root.optString(ChartKeys.COMPOSITION) == ChartKeys.REPEAT && reads.size == 1
        return if (repeat) found + (ChartKeys.REPEAT to FieldDefinition(ChartKeys.REPEAT, Strings.`for`(tool = "chart", context = context).tool("column_repeat"), null, FieldType.TEXT, false, null))
        else found
    }

    /** Only what gives the columns: read again when it changes, not at every keystroke elsewhere. */
    override fun dependsOn(root: JSONObject, path: List<Any>): Any =
        root.optString(ChartKeys.COMPOSITION) + "|" + reads(root, path).joinToString("|") { it.toString() }

    /**
     * What gives the columns at [path]: the layer it stands in, its transforms before the one it
     * stands in; every layer when it stands in none.
     */
    private fun reads(root: JSONObject, path: List<Any>): List<JSONObject> {
        val at = path.indices.lastOrNull { path[it] == ChartKeys.LAYER && path.getOrNull(it + 1) is Int }
            ?: return allLayers(root).map { it.withTransforms(null) }
        val layer = navigate(root, path.take(at + 2)) ?: return emptyList()
        val transform = (path.getOrNull(at + 3) as? Int).takeIf { path.getOrNull(at + 2) == ChartKeys.TRANSFORM }
        return listOf(layer.withTransforms(transform))
    }

    /** The layer with only what gives its columns: its source, and its transforms before [upTo]. */
    private fun JSONObject.withTransforms(upTo: Int?): JSONObject {
        val kept = JSONObject()
        listOf(ChartKeys.SOURCE, ChartKeys.SELECTION, ChartKeys.STEP, ChartKeys.COLUMNS).forEach { key -> opt(key)?.let { kept.put(key, it) } }
        optJSONArray(ChartKeys.TRANSFORM)?.let { all -> kept.put(ChartKeys.TRANSFORM, JSONArray((0 until (upTo ?: all.length()).coerceAtMost(all.length())).map { all.get(it) })) }
        return kept
    }

    private fun allLayers(root: JSONObject): List<JSONObject> {
        fun layersOf(view: JSONObject) = view.optJSONArray(ChartKeys.LAYER)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
        val views = root.optJSONArray(root.optString(ChartKeys.COMPOSITION))?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
        return views?.flatMap { layersOf(it) } ?: layersOf(root)
    }

    private fun navigate(root: JSONObject, path: List<Any>): JSONObject? {
        var at: Any? = root
        path.forEach { step ->
            at = when (step) {
                is Int -> (at as? JSONArray)?.opt(step)
                else -> (at as? JSONObject)?.opt(step.toString())
            }
        }
        return at as? JSONObject
    }

    /** The columns of a layer being written: its source as far as it reads, then the transforms that do. */
    private suspend fun columns(sources: ChartSources, layer: JSONObject): Map<String, FieldDefinition> {
        val text: (String) -> String = { it }
        val source = when (layer.optString(ChartKeys.SOURCE).ifEmpty { ChartKeys.GRID }) {
            ChartKeys.ENTRIES -> layer.optJSONObject(ChartKeys.SELECTION)?.takeIf { it.has("target") }
                ?.let { runCatching { Source.Entries(EntrySelection.fromJson(it, text)) }.getOrNull() } ?: return emptyMap()
            else -> Source.Grid(
                ChartKeys.STEPS[layer.optString(ChartKeys.STEP)] ?: PeriodType.DAY,
                layer.optJSONArray(ChartKeys.COLUMNS)?.let { a -> (0 until a.length()).mapNotNull { i ->
                    val column = a.optJSONObject(i) ?: return@mapNotNull null
                    val name = column.optString(ChartKeys.NAME).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val term = column.optJSONObject(ChartKeys.TERM)?.let { runCatching { Term.fromJson(it, name, text) }.getOrNull() }
                        ?.takeIf { it is Term.Variable && it.id.isNotEmpty() || it is Term.Reading && it.selection.target.id != null } ?: return@mapNotNull null
                    GridColumn(name, term)
                } } ?: emptyList()
            )
        }
        var table = ChartTable(sources.sourceColumns(source), emptyList())
        layer.optJSONArray(ChartKeys.TRANSFORM)?.let { all ->
            (0 until all.length()).mapNotNull { all.optJSONObject(it) }.forEach { json ->
                val fold = strings(json.optJSONArray(ChartKeys.FOLD))
                val flatten = strings(json.optJSONArray(ChartKeys.FLATTEN))
                val names = strings(json.optJSONArray(ChartKeys.AS))
                val transform = when {
                    fold.isNotEmpty() && flatten.isEmpty() && fold.all { it in table.columns } ->
                        Transform.Fold(fold, names.getOrElse(0) { ChartKeys.FOLD_KEY }, names.getOrElse(1) { ChartKeys.FOLD_VALUE })
                    flatten.isNotEmpty() && fold.isEmpty() && flatten.all { it in table.columns } -> Transform.Flatten(flatten)
                    else -> null
                }
                transform?.let { table = table.transformed(it) }
            }
        }
        return table.columns
    }

    private fun strings(array: JSONArray?): List<String> = array?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotEmpty() } } } ?: emptyList()
}
