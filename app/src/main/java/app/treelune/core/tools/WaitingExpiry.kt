package app.treelune.core.tools

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.SettingNode
import org.json.JSONObject

/**
 * When an item a tool made to wait for the user expires (a questionnaire to fill, a session of a
 * sequence planned and never started): never, the default; at the next one the tool makes, by its
 * schedule or asked now, which leaves one alone to wait; or past a delay, days counted on the
 * app's calendar. One setting in the tool's config ([node]); its scheduler expires what [expired]
 * names, at each pass and at each item it makes.
 */
object WaitingExpiry {

    const val KEY = "expiry"
    const val DELAY = "expiry_delay"
    const val NEVER = "never"
    const val NEXT = "next"
    const val AFTER = "after"

    /** A day, the delay first offered. */
    private const val DEFAULT_DELAY = 86_400_000L

    /** What the setting says. */
    sealed interface Rule {
        data object Never : Rule
        data object Next : Rule
        data class After(val delay: Long) : Rule
    }

    /** The setting, as a tool's config declares it; [shared] a shared string by its key. */
    fun node(shared: (String) -> String): SettingNode {
        val options = listOf(NEVER, NEXT, AFTER)
        return SettingNode.Variant(
            selector = SettingNode.Field(FieldDefinition(KEY, shared("expiry_label"), shared("expiry_schema"), FieldType.CHOICE, false,
                mapOf("options" to ChoiceSettings.storedOptions(options, mapOf(
                    NEVER to shared("expiry_never"), NEXT to shared("expiry_next"), AFTER to shared("expiry_after")), emptyMap()))),
                // Absent, as in a config made before it: never
                default = NEVER),
            cases = mapOf(
                NEVER to emptyList(),
                NEXT to emptyList(),
                AFTER to listOf(SettingNode.Field(FieldDefinition(DELAY, shared("expiry_delay_label"), shared("expiry_delay_schema"), FieldType.DURATION, false,
                    app.treelune.core.fields.DurationUnits.DAY_HOUR.config()), required = true, default = DEFAULT_DELAY))
            ),
            descriptions = mapOf(NEVER to shared("expiry_schema_never"), NEXT to shared("expiry_schema_next"), AFTER to shared("expiry_schema_after"))
        )
    }

    /** The rule [config] sets; a config made before the setting, or without it, never expires. */
    fun of(config: JSONObject): Rule = when (config.optString(KEY, NEVER)) {
        NEXT -> Rule.Next
        AFTER -> Rule.After((config.opt(DELAY) as? Number)?.toLong() ?: throw IllegalStateException("$KEY $AFTER without its $DELAY"))
        else -> Rule.Never
    }

    /**
     * Of [waiting] (each item's id and the instant it is dated from), those [rule] expires now:
     * at the next, all but [made], the item just made — none when nothing was made; past a delay,
     * those whose delay ran out by [now] on the calendar [after] counts on.
     */
    fun expired(rule: Rule, waiting: List<Pair<String, Long>>, made: String?, now: Long, after: (Long, Long) -> Long): List<String> = when (rule) {
        Rule.Never -> emptyList()
        Rule.Next -> if (made == null) emptyList() else waiting.map { it.first }.filter { it != made }
        is Rule.After -> waiting.filter { (id, at) -> id != made && after(at, rule.delay) <= now }.map { it.first }
    }
}
