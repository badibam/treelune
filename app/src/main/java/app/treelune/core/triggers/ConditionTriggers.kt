package app.treelune.core.triggers

import android.content.Context
import app.treelune.core.conditions.Condition
import app.treelune.core.conditions.ConditionJudge
import app.treelune.core.coordinator.Chains
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Origin
import app.treelune.core.coordinator.Source
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FilterOperator
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.tasks.TaskRun
import app.treelune.core.tasks.TaskRunner
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermReader
import app.treelune.core.ui.selectors.PointerDescription
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * What a condition become true starts (docs/CORE.md, « Triggers »): each Task and routine
 * switched on whose triggers name a condition (Triggers.Trigger.ConditionBecameTrue), when it
 * passes from false to true, then nothing until it is false again.
 *
 * - **Its last judgement** is kept by what listens (ConditionJudgementEntity), one row per
 *   condition: the first judgement of a row is noted and starts nothing, so that a trigger added
 *   on a condition already true waits for it to be false then true again.
 * - **Judged again** after a step's writes, those conditions only that read a tool written —
 *   through a Reading or a variable —, what becomes true continuing the writer's chain; and all
 *   of them at each tick of the scheduler, what becomes true starting a chain of its own.
 * - **Neither true nor false**: without an answer (a value missing), the verdict stays as it
 *   was, unknown is not false. A failure (a variable read deleted) keeps it too, written on the
 *   row, shown under the trigger, and said by a notification once, when it starts.
 * - **What a run receives**: the values read on each side when it became true (TaskRun.Received.Condition).
 *
 * The readings run outside the writer's context, as the scheduler's: an AI held to a mask that
 * wrote must not make a condition it cannot read fail. The runs start back in that context, for
 * its chain (Chains).
 */
object ConditionTriggers {

    const val TASK = "task"
    const val ROUTINE = "routine"

    /** One judging at a time: the tick's and a write's would otherwise both start the same passage. */
    private val judging = Mutex()

    /** A condition judged once: whether it holds (null without an answer) and what was read; or why it could not be read. */
    sealed interface Judged {
        data class Answer(val holds: Boolean?, val values: Map<String, Any?>, val said: String) : Judged
        data class Failed(val message: String) : Judged
    }

    /** Something that listens to a condition, and the conditions it listens to, by key. */
    private class Listener(val kind: String, val id: String, val name: String, val conditions: Map<String, JSONObject>)

    /** A listener whose condition [key] became true, with what was read. */
    private class Passage(val listener: Listener, val key: String, val answer: Judged.Answer)

    /**
     * Judges again the conditions listened to and starts what became true: after a step that
     * wrote the entries of [written] (tool ids), only those that read one of them; at the tick
     * ([written] null), all of them, the rows nothing listens to any more removed.
     */
    suspend fun dispatch(context: Context, written: Set<String>?) {
        val passages = detached {
            judging.withLock { judge(context, written) }
        }
        for (passage in passages) start(context, passage)
    }

    private suspend fun listeners(context: Context): List<Listener> {
        val database = AppDatabase.getDatabase(context)
        fun conditions(json: String?, owner: String): Map<String, JSONObject> = try {
            Triggers.read(json).filterIsInstance<Triggers.Trigger.ConditionBecameTrue>().associate { it.key to it.condition }
        } catch (e: IllegalArgumentException) {
            LogManager.service("$owner: triggers not read: ${e.message}", "ERROR", e)
            emptyMap()
        }
        val tasks = database.taskDao().getAll().filter { it.isEnabled }.map { Listener(TASK, it.id, it.name, conditions(it.triggersJson, "Task ${it.id}")) }
        val routines = database.aiDao().getAllRoutines().filter { it.isEnabled }.map { Listener(ROUTINE, it.id, it.name, conditions(it.triggersJson, "Routine ${it.id}")) }
        return (tasks + routines).filter { it.conditions.isNotEmpty() }
    }

    private suspend fun judge(context: Context, written: Set<String>?): List<Passage> {
        val dao = AppDatabase.getDatabase(context).conditionJudgementDao()
        val listeners = listeners(context)
        val rows = dao.getAll().associateBy { Triple(it.ownerKind, it.ownerId, it.conditionKey) }
        if (written == null) {
            // Rows of a condition no longer listened to, or of what is switched off, go: listened to again, it starts from a first judgement
            val kept = listeners.flatMap { listener -> listener.conditions.keys.map { Triple(listener.kind, listener.id, it) } }.toSet()
            rows.keys.filter { it !in kept }.forEach { dao.delete(it.first, it.second, it.third) }
        }
        val now = System.currentTimeMillis()
        // A condition listened to by several is read once
        val judged = mutableMapOf<String, Judged>()
        val passages = mutableListOf<Passage>()
        for (listener in listeners) {
            for ((key, condition) in listener.conditions) {
                if (written != null && !reads(context, condition, written)) continue
                val result = judged.getOrPut(key) { judgeOnce(context, condition, now) }
                val row = rows[Triple(listener.kind, listener.id, key)]
                when (result) {
                    is Judged.Answer -> {
                        // Unknown keeps the verdict; false then true starts it, a first judgement nothing
                        val verdict = result.holds ?: row?.verdict
                        dao.upsert(ConditionJudgementEntity(listener.kind, listener.id, key, verdict, now))
                        if (row?.verdict == false && result.holds == true) passages.add(Passage(listener, key, result))
                    }
                    is Judged.Failed -> {
                        dao.upsert(ConditionJudgementEntity(listener.kind, listener.id, key, row?.verdict, now, result.message, row?.failedSince ?: now))
                        if (row?.failedSince == null) failed(context, listener, result.message)
                    }
                }
            }
        }
        // Once per listener, whichever of its conditions comes first
        return passages.distinctBy { it.listener.kind to it.listener.id }
    }

    /**
     * [condition] judged once at [at], its readings over their whole history when they have no
     * period of their own.
     */
    suspend fun judgeOnce(context: Context, condition: JSONObject, at: Long): Judged {
        val s = Strings.`for`(context = context)
        val text = { key: String -> s.shared(key) }
        return try {
            val read = Condition.fromJson(condition, s.shared("trigger_condition"), text)
            val reader = TermReader(context)
            val sides = (listOf(read.left) + read.right).map { side ->
                when (side) {
                    is Condition.Side.Of -> reader.read(side.term, at, null)
                    is Condition.Side.Field -> throw IllegalArgumentException(s.shared("trigger_error_condition_field"))
                }
            }
            // What it reads gone fails; data missing is unknown
            sides.filterIsInstance<TermReader.Read.Failed>().firstOrNull { it.broken }?.let { return Judged.Failed(it.message) }
            if (sides.any { it is TermReader.Read.Failed }) return Judged.Answer(null, emptyMap(), "")
            val values = sides.map { it as TermReader.Read.Value }
            // Compared as the side that has a type: a constant takes the other's
            val field = values.firstNotNullOfOrNull { it.field } ?: throw IllegalArgumentException(s.shared("trigger_error_condition_constants"))
            val left = values[0].value
            val right = values.drop(1).map { it.value }
            val holds = ConditionJudge.holds(field, read.op, left, right, TimeResolver.at(at), text)
            Judged.Answer(holds, given(read.op, left, right), said(field, read.op, left, right, s))
        } catch (e: IllegalArgumentException) {
            Judged.Failed(e.message ?: s.shared("message_validation_error_simple"))
        } catch (e: IllegalStateException) {
            Judged.Failed(e.message ?: s.shared("message_validation_error_simple"))
        }
    }

    /** What a run receives, by name: `left`, and `right` — a pair for between, absent with nothing opposite. */
    private fun given(op: FilterOperator, left: Any?, right: List<Any?>): Map<String, Any?> = when (op) {
        FilterOperator.ABSENT, FilterOperator.PRESENT -> mapOf("left" to left)
        FilterOperator.BETWEEN -> mapOf("left" to left, "right" to right)
        else -> mapOf("left" to left, "right" to right.single())
    }

    /**
     * What a run started by [condition] receives, by name, as the field each is a value of
     * (TaskSteps.receivedValues): `left` and `right` as [given] names them, of the type of the
     * side that has one; none when the condition does not read.
     */
    suspend fun values(context: Context, condition: JSONObject): Map<String, FieldDefinition> {
        val s = Strings.`for`(context = context)
        val sources = app.treelune.core.conditions.AppConditionSources(context, null)
        return try {
            val read = Condition.fromJson(condition, "", { s.shared(it) })
            val field = (listOf(read.left) + read.right).filterIsInstance<Condition.Side.Of>().firstNotNullOfOrNull { side ->
                app.treelune.core.terms.TermFields.of(side.term, "", { s.shared(it) }, sources::targetFields, sources::variableField)
            } ?: return emptyMap()
            val names = when (read.op) {
                // A pair is no single value a step's input takes
                FilterOperator.ABSENT, FilterOperator.PRESENT, FilterOperator.BETWEEN -> listOf("left")
                else -> listOf("left", "right")
            }
            names.associateWith { field.copy(name = it, displayName = s.shared("trigger_condition_$it")) }
        } catch (e: IllegalArgumentException) {
            emptyMap()
        }
    }

    /** What was read, in words: "82.4 > 80", "82.4 between 70 and 80", "Mood no answer". */
    private fun said(field: FieldDefinition, op: FilterOperator, left: Any?, right: List<Any?>, s: StringsContext): String {
        val value = { v: Any? -> PointerDescription.value(field, v, s) }
        return when (op) {
            FilterOperator.ABSENT, FilterOperator.PRESENT -> "${value(left)} ${PointerDescription.operator(op, s)}"
            FilterOperator.BETWEEN -> s.shared("filter_between").format(value(left), value(right[0]), value(right[1]))
            else -> "${value(left)} ${PointerDescription.operator(op, s)} ${value(right.single())}"
        }
    }

    /** Whether [condition] reads one of the tools of [written]; one whose reads cannot be told is judged. */
    private suspend fun reads(context: Context, condition: JSONObject, written: Set<String>): Boolean {
        val tools = toolsRead(context, condition) ?: return true
        return VariableAnyTool in tools || tools.any { it in written }
    }

    private const val VariableAnyTool = app.treelune.core.services.VariableService.ANY_TOOL

    /**
     * The tools [condition] reads: its Readings' (a tool, or one entry's tool), and those its
     * variables read (`tools_read`); null when one cannot be told — a variable or an entry gone,
     * which the judging then says.
     */
    private suspend fun toolsRead(context: Context, condition: JSONObject): Set<String>? {
        val tools = mutableSetOf<String>()
        for (term in terms(condition)) {
            when (term) {
                is Term.Constant -> Unit
                is Term.Reading -> {
                    val target = term.selection.target
                    tools.add(when (target.kind) {
                        ReferenceKind.TOOL_INSTANCE -> target.id ?: return null
                        ReferenceKind.ENTRY -> target.id?.let { AppDatabase.getDatabase(context).toolDataDao().getById(it) }?.toolInstanceId ?: return null
                        else -> return null
                    })
                }
                is Term.Variable -> {
                    val result = Coordinator(context).processUserAction("variables.get", mapOf("variable_id" to term.id))
                    if (result.status != app.treelune.core.commands.CommandStatus.SUCCESS) return null
                    val variable = result.data?.get("variable") as? Map<*, *> ?: return null
                    (variable["tools_read"] as? List<*>)?.forEach { tools.add(it.toString()) }
                }
            }
        }
        return tools
    }

    /**
     * The terms on the sides of [condition], and those a grouped reading's groups are compared
     * with; none when it does not read, which the judging says.
     */
    private fun terms(condition: JSONObject): List<Term> = try {
        val read = Condition.fromJson(condition, "", { it })
        val sides = (listOf(read.left) + read.right).mapNotNull { (it as? Condition.Side.Of)?.term }
        sides + sides.filterIsInstance<Term.Reading>().flatMap { it.keepTerms() }
    } catch (e: IllegalArgumentException) {
        emptyList()
    }

    /** What listening to [condition] reaches (AccessRules.reach): each reading and each variable, read. */
    fun reaches(condition: JSONObject, isToolType: (String) -> Boolean): List<app.treelune.core.access.Reach> = terms(condition).flatMap { term ->
        when (term) {
            is Term.Reading -> app.treelune.core.access.AccessRules.reach("readings", "read", mapOf("selection" to JsonUtils.toMap(term.selection.toJson())), isToolType)
            is Term.Variable -> app.treelune.core.access.AccessRules.reach("variables", "evaluate", mapOf("variable_id" to term.id), isToolType)
            is Term.Constant -> emptyList()
        }
    }

    /** The judgement failing from now on, said once. */
    private suspend fun failed(context: Context, listener: Listener, message: String) {
        val s = Strings.`for`(context = context)
        LogManager.service("${listener.kind} ${listener.id}: condition not judged: $message", "WARN")
        Coordinator(context).processScheduledTask("notifications.send", mapOf(
            "title" to listener.name,
            "content" to s.shared("trigger_condition_failed").format(message),
            "priority" to "default"
        ))
    }

    /** [passage]'s listener started, in the caller's context for its chain, refused past its cap. */
    private suspend fun start(context: Context, passage: Passage) {
        val listener = passage.listener
        val database = AppDatabase.getDatabase(context)
        val chain = when (val admission = Chains.admit(context, listener.name)) {
            is Chains.Admission.Capped -> return
            is Chains.Admission.Continues -> admission.chainId
            Chains.Admission.Starts -> null
        }
        val condition = listener.conditions.getValue(passage.key)
        when (listener.kind) {
            TASK -> {
                val task = database.taskDao().get(listener.id) ?: return
                TaskRunner.start(context, task, TaskRun.Trigger.CONDITION, System.currentTimeMillis(), awaited = false, chainId = chain,
                    received = TaskRun.Received.Condition(condition, passage.answer.values, passage.answer.said))
            }
            ROUTINE -> {
                val s = Strings.`for`(context = context)
                app.treelune.core.ai.orchestration.AIOrchestrator.executeRoutine(listener.id, scheduledFor = System.currentTimeMillis(),
                    trigger = app.treelune.core.ai.data.ExecutionTrigger.CONDITION, chainId = chain,
                    attached = listOf(app.treelune.core.ai.data.MessageSegment.Text(s.shared("routine_run_condition").format(passage.answer.said))))
            }
        }
    }

    /**
     * The last judgement of each condition the triggers stored in [triggersJson] name, for the
     * screen of [ownerKind] [ownerId]: by the trigger's place in the list, its verdict (null
     * unknown or not judged yet), when, and its failure.
     */
    suspend fun judgements(context: Context, ownerKind: String, ownerId: String, triggersJson: String?): List<Map<String, Any?>> {
        val triggers = try {
            Triggers.read(triggersJson)
        } catch (e: IllegalArgumentException) {
            return emptyList()
        }
        val rows = AppDatabase.getDatabase(context).conditionJudgementDao().getByOwner(ownerKind, ownerId).associateBy { it.conditionKey }
        return triggers.mapIndexedNotNull { index, trigger ->
            val key = (trigger as? Triggers.Trigger.ConditionBecameTrue)?.key ?: return@mapIndexedNotNull null
            val row = rows[key]
            mapOf("index" to index, "verdict" to row?.verdict, "judged_at" to row?.judgedAt, "failure" to row?.failure)
        }
    }

    /** [block] run outside the caller's context, as the scheduler's: no mask, no chain, no step. */
    private suspend fun <T> detached(block: suspend () -> T): T =
        CoroutineScope(Dispatchers.IO + Origin(Source.SCHEDULER)).async { block() }.await()
}
