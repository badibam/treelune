package app.treelune.core.guide

import android.content.Context
import app.treelune.core.coordinator.Source
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTypeManager
import org.json.JSONObject

/**
 * The three parts of the Guide's page, by how far one has gone. Discovery is the journey, the
 * tour of what the app does, its tutorials done in their order, numbered ([ordered]); getting
 * started learns each feature, going further its subtleties, both read in the order one likes.
 * A part with no chapter is not shown. [key] names the part in chapters.json and in its strings
 * (guide_part_<key>, _help).
 */
enum class GuidePart(val key: String, val ordered: Boolean) {
    DISCOVERY("discovery", true),
    GETTING_STARTED("getting_started", false),
    TOOLS("tools", false),
    DEEPER("deeper", false)
}

/**
 * What a step asks: to reach a place (GO), to do something the dispatcher sees pass (DO), or to
 * read, then Next, once at its place when it has one (READ).
 */
enum class StepKind { GO, DO, READ }

/**
 * What a DO step waits for: an operation the dispatcher sees pass, or a message the user sends
 * the AI, which goes through the AI's orchestrator instead (SentMessages).
 */
sealed interface Await {

    /**
     * An operation: its action, who must ask it (the user, or the AI when the step is to have the
     * AI do it), and params it must carry, each value an exact one or the name of what an earlier
     * step kept (`{zone}`).
     */
    data class Operation(val operation: String, val origin: Source, val params: Map<String, String>) : Await

    /** A message sent to the AI, carrying what [carrying] says. */
    data class Message(val carrying: MessageWait) : Await
}

/** What a message a step waits for must carry; [key] names it in chapters.json. */
enum class MessageWait(val key: String) {
    /** Anything: a message sent. */
    ANY("any"),
    /** A pointer whose target is a tool. */
    POINTER_TOOL("pointer_tool")
}

/**
 * A step of a chapter, its texts named after its chapter and its number from 1
 * (`guide_<chapter>_<n>_goal`, `_text`).
 *
 * @property target The address of the place it happens at, `{name}` standing for what an earlier
 *   step kept; null for a step read anywhere
 * @property keep What the step keeps once done, by name: the key of the operation's result (or,
 *   lacking it, of its params) whose value it keeps — the zone created, for the steps after
 */
data class GuideStep(
    val number: Int,
    val kind: StepKind,
    val target: String?,
    val await: Await?,
    val keep: Map<String, String>
) {
    /** Whether the step is about the demo: its place or what it waits for names a demo id. */
    val usesDemo: Boolean
        get() = target.orEmpty().contains(DEMO_PREFIX) || (await as? Await.Operation)?.params?.values?.any { it.startsWith(DEMO_PREFIX) } == true
}

/**
 * A chapter of the Guide. A chapter with steps is a tutorial, done on the app itself; one
 * without is read. A chapter with no steps and no text yet is a stub.
 *
 * @property toolType Set for a tool type's reference chapter, whose name and line are the tool's,
 *   and whose text is its usage (what it is for, what it already does, what it is not for)
 * @property text Set for a read chapter written in the Guide's own texts, its body
 *   `guide_<chapter>_body`: a subject going further
 */
data class GuideChapter(
    val id: String,
    val part: GuidePart,
    val icon: String,
    val minutes: Int,
    val steps: List<GuideStep>,
    val toolType: String? = null,
    val text: Boolean = false
) {
    val isTutorial: Boolean get() = steps.isNotEmpty()

    /** Still to be written: neither steps nor a text of its own. */
    val isStub: Boolean get() = steps.isEmpty() && toolType == null && !text

    /** A read chapter's text: a tool type's usage, or the chapter's own body. */
    fun body(context: Context): String? = toolType?.let { ToolTypeManager.getToolType(it)?.getUsage(context) }
        ?: if (text) Strings.`for`(context = context).shared("guide_${id}_body") else null

    /** A tool type's chapter's parts after its usage: its config, its screen; each absent until written. */
    fun toolConfig(context: Context): String? = toolType?.let { ToolTypeManager.getToolType(it)?.getGuideConfig(context) }
    fun toolScreen(context: Context): String? = toolType?.let { ToolTypeManager.getToolType(it)?.getGuideScreen(context) }

    /** A chapter needs the demo when one of its steps is about it: read from the steps, never declared. */
    val usesDemo: Boolean get() = steps.any { it.usesDemo }

    fun title(context: Context): String = toolType?.let { ToolTypeManager.getToolTypeName(it, context) }
        ?: Strings.`for`(context = context).shared("guide_${id}_title")

    fun intro(context: Context): String = toolType?.let { Strings.`for`(tool = it, context = context).tool("tagline") }
        ?: Strings.`for`(context = context).shared("guide_${id}_intro")

    /** The end of a tutorial; a read chapter has none. */
    fun outro(context: Context): String? = if (isTutorial) Strings.`for`(context = context).shared("guide_${id}_outro") else null

    fun goal(context: Context, step: GuideStep) = Strings.`for`(context = context).shared("guide_${id}_${step.number}_goal")
    fun text(context: Context, step: GuideStep) = Strings.`for`(context = context).shared("guide_${id}_${step.number}_text")
}

/** The ids of the demo's rows begin with this (DemoRemoval). */
const val DEMO_PREFIX = "demo-"

/**
 * The Guide's chapters, in their order: those of `assets/guide/chapters.json`, then in a part of
 * their own one per tool type, from the tool type's own name, line, usage and texts, so that a
 * tool type comes with its chapter. That part, each line with the tool's line, is the tools at a
 * glance.
 */
object GuideChapters {

    private const val ASSET = "guide/chapters.json"

    @Volatile private var loaded: List<GuideChapter>? = null

    fun all(context: Context): List<GuideChapter> = loaded ?: synchronized(this) {
        loaded ?: run {
            val declared = parse(context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() })
            // A tool type's chapter under the icon a new tool of that type takes
            val tools = ToolTypeManager.getAllToolTypes().map { (tooltype, type) ->
                GuideChapter("tool_$tooltype", GuidePart.TOOLS, type.getDefaultIconName(), 3, emptyList(), toolType = tooltype)
            }
            // The tools' chapters together in their own part, which the page shows in its order
            (declared + tools).also { loaded = it }
        }
    }

    fun byId(context: Context, id: String): GuideChapter =
        all(context).firstOrNull { it.id == id } ?: throw IllegalArgumentException("No Guide chapter '$id'")

    /** The journey: the tutorials of the ordered parts, in their order. */
    fun journey(context: Context) = all(context).filter { it.part.ordered }

    /** The chapters a file declares; fails on anything it does not read. */
    fun parse(json: String): List<GuideChapter> {
        val chapters = JSONObject(json).getJSONArray("chapters")
        return (0 until chapters.length()).map { i ->
            val c = chapters.getJSONObject(i)
            val steps = c.getJSONArray("steps")
            GuideChapter(
                id = c.getString("id"),
                part = GuidePart.valueOf(c.getString("part").uppercase()),
                icon = c.getString("icon"),
                minutes = c.getInt("minutes"),
                text = c.optBoolean("text", false),
                steps = (0 until steps.length()).map { n ->
                    val st = steps.getJSONObject(n)
                    GuideStep(
                        number = n + 1,
                        kind = StepKind.valueOf(st.getString("kind").uppercase()),
                        target = if (st.has("target")) st.getString("target") else null,
                        await = st.optJSONObject("await")?.let { a ->
                            if (a.has("message")) {
                                val key = a.getString("message")
                                Await.Message(MessageWait.entries.firstOrNull { it.key == key }
                                    ?: throw IllegalArgumentException("Step ${n + 1} of '${c.getString("id")}': no message wait '$key'"))
                            } else {
                                val params = a.optJSONObject("params")
                                Await.Operation(
                                    operation = a.getString("operation"),
                                    origin = Source.valueOf(a.getString("origin")),
                                    params = params?.keys()?.asSequence()?.associateWith { params.getString(it) } ?: emptyMap()
                                )
                            }
                        },
                        keep = st.optJSONObject("keep")?.let { k -> k.keys().asSequence().associateWith { k.getString(it) } } ?: emptyMap()
                    ).also {
                        require((it.kind == StepKind.DO) == (it.await != null)) { "Step ${it.number} of '${c.getString("id")}': a DO step waits for an operation or a message, and only it" }
                        require(it.kind != StepKind.GO || it.target != null) { "Step ${it.number} of '${c.getString("id")}': a GO step has a place" }
                    }
                }
            )
        }
    }
}
