package app.treelune.tools.path

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.selection.TimePoint
import app.treelune.core.terms.Term
import org.json.JSONArray
import org.json.JSONObject

/**
 * What `self` reaches on the stage a rule judges (« Les règles »): its facts (its id, name, kind,
 * parent, position, the stage before it, its siblings and content, its links of a type, its last
 * move and its date, its run's dates), its fields, and the state computed for it so far with its
 * `since`. A side `{"self": "…"}` of a comparison is read here with the type of what it is; met
 * inside a reading's filters or a date's `from`, it is put in its place as a written value before
 * the reading goes to the core, which never meets it (Term.SELF).
 */
object PathSelf {

    const val ID = "id"
    const val NAME = "name"
    const val KIND = "kind"
    const val PARENT = "parent"
    const val POSITION = "position"
    const val PREVIOUS = "previous"
    const val SIBLINGS = "siblings"
    const val CONTENT = "content"
    const val LINKS = "links"
    const val TYPE = "type"
    const val STATE = "state"
    const val SINCE = "since"
    const val LAST_MOVE = "last_move"
    const val LAST_MOVE_AT = "last_move_at"
    const val RUN_START = "run_start"
    const val RUN_END = "run_end"

    /** The key of a relative date's offset, which may read the stage judged. */
    private const val OFFSET = "offset"

    /** The keys of `self` that are not a field: a kind's field may not take one. */
    val RESERVED = setOf(ID, NAME, KIND, PARENT, POSITION, PREVIOUS, SIBLINGS, CONTENT, LINKS, STATE, SINCE, LAST_MOVE, LAST_MOVE_AT, RUN_START, RUN_END,
        PathRows.ANCESTORS, PathRows.MARKS, PathRows.RUN, PathRows.CLOSED)

    /**
     * A date's `from` or `offset` the stage has no value for (no move yet): what reads it is not
     * known, as a value missing is, rather than wrong.
     */
    class Missing(message: String) : IllegalArgumentException(message)

    /** A value of `self` and the field it is a value of, for the judge to compare it as. */
    data class Value(val value: Any?, val field: FieldDefinition)

    /** What `self` reads on one stage, the replay's state of it so far. */
    interface Of {
        val stage: Stage
        val kind: Kind
        fun previous(): Stage?
        fun siblings(): List<Stage>
        fun content(): List<Stage>
        /** Its last move, virtual ones included; null when it has none. */
        fun lastMove(): Move?
        /** Its state computed so far and since when; null before it is computed. */
        fun state(): Pair<String, Long?>?
        fun runStart(): Long?
        fun runEnd(): Long?
    }

    /**
     * The value of `self` [json] names on [of] (`{"self": "since"}`, `{"self": "links", "type": "required"}`).
     *
     * @throws IllegalArgumentException on a key `self` does not reach, in words
     */
    fun value(json: JSONObject, of: Of, text: (String) -> String): Value {
        val key = json.optString(Term.SELF)
        fun plain(v: Any?) = Value(v, field(key, FieldType.TEXT))
        fun ids(v: List<Stage>) = Value(v.map { it.id }, field(key, FieldType.TEXT))
        fun instant(v: Long?) = Value(v, field(key, FieldType.DATETIME))
        return when (key) {
            ID -> plain(of.stage.id)
            NAME -> plain(of.stage.name)
            KIND -> plain(of.stage.kind)
            PARENT -> plain(of.stage.parent)
            POSITION -> Value(of.stage.position, field(key, FieldType.NUMERIC))
            PREVIOUS -> plain(of.previous()?.id)
            SIBLINGS -> ids(of.siblings())
            CONTENT -> ids(of.content())
            LINKS -> {
                val type = json.optString(TYPE).takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException(text("path_error_self_links_type"))
                Value(of.stage.links.filter { it.type == type }.map { it.from }, field(key, FieldType.TEXT))
            }
            STATE -> plain(of.state()?.first)
            SINCE -> instant(of.state()?.second)
            LAST_MOVE -> plain(of.lastMove()?.state)
            LAST_MOVE_AT -> instant(of.lastMove()?.at)
            RUN_START -> instant(of.runStart())
            RUN_END -> instant(of.runEnd())
            else -> of.kind.fields.firstOrNull { it.name == key }?.let { Value(of.stage.fields[key], it) }
                ?: throw IllegalArgumentException(text("path_error_self_unknown").format(key, of.kind.name))
        }
    }

    /**
     * [json] with each `self` it holds, at any depth, put in its place as a written value: under a
     * relative date's `from` as the instant itself, under its `offset` as the number itself (« the
     * last injection plus its validity », the validity a field of the stage), anywhere else as
     * `{"constant": …}`. A value that is empty
     * stays empty: a filter against nothing keeps nothing, which is what an absent previous means.
     *
     * @throws IllegalArgumentException on a key `self` does not reach, or a `from` or an `offset` the stage has no value for
     */
    fun substituted(json: Any?, of: Of, text: (String) -> String): Any? = when (json) {
        is JSONObject -> if (json.has(Term.SELF)) JSONObject().put(Term.Kind.CONSTANT.key, written(value(json, of, text).value))
            else JSONObject().also { out ->
                json.keys().forEach { key ->
                    val inner = json.opt(key)
                    out.put(key, when {
                        inner !is JSONObject || !inner.has(Term.SELF) -> substituted(inner, of, text)
                        key == TimePoint.FROM -> value(inner, of, text).value
                            ?: throw Missing(text("path_error_self_from").format(inner.optString(Term.SELF)))
                        key == OFFSET -> value(inner, of, text).value
                            ?: throw Missing(text("path_error_self_offset").format(inner.optString(Term.SELF)))
                        else -> substituted(inner, of, text)
                    })
                }
            }
        is JSONArray -> JSONArray((0 until json.length()).map { substituted(json.opt(it), of, text) })
        else -> json
    }

    /** Whether [json] holds a `self` anywhere. */
    fun reads(json: Any?): Boolean = when (json) {
        is JSONObject -> json.has(Term.SELF) || json.keys().asSequence().any { reads(json.opt(it)) }
        is JSONArray -> (0 until json.length()).any { reads(json.opt(it)) }
        else -> false
    }

    /** A value as a filter's written side: nothing as an empty list, against which no row is kept. */
    private fun written(value: Any?): Any = when (value) {
        null -> JSONArray()
        is List<*> -> JSONArray(value)
        else -> value
    }

    private fun field(key: String, type: FieldType) = FieldDefinition(key, key, null, type, false, null)
}
