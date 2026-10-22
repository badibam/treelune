package app.treelune.core.fields

import android.content.Context
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.tools.ToolTypeContract

/**
 * The first level of a tool type's entry, as the AI reads it: what each part every entry has —
 * name, timestamp, data, extra, state — is in this type, whatever a tool's config (EntryMeanings,
 * the core's text where the type says nothing). The second level, which parts a tool's entries
 * have and their fields, is that tool's entries schema (EntrySchemaGenerator), which no longer
 * says what the parts mean.
 *
 * The AI reads it with the type's config schema, when it sets up a tool, and with a tool's
 * entries schema, when it writes entries; once in a conversation, whichever brings it first
 * (CommandExecutor, SessionReads).
 */
object EntryDescription {

    /** The read target of [tooltype]'s first level, sent once in a session (SessionReads). */
    fun target(tooltype: String): String = "entry:$tooltype"

    /**
     * The first level of [toolType]'s entry: one line per part the type uses in its default config,
     * or whose meaning it declares (a note's title, there only when the titles are on); data only
     * when the type says what it is, its fields saying it otherwise; extra for a type that takes
     * the user's fields; under state, how an entry starts when the type says (EntryStart). Null
     * for a type that keeps no entries (a chart).
     */
    fun text(toolType: ToolTypeContract, context: Context): String? {
        if (!toolType.keepsEntries()) return null
        val s = Strings.`for`(context = context)
        val declared = toolType.getEntryFields(ToolConfigSettings.defaults(toolType, context), context)
        val meanings = toolType.getEntryMeanings(context)

        val lines = mutableListOf<String>()
        fun line(part: String, meaning: String?, coreKey: String) { lines += "- `$part`: ${meaning ?: s.shared(coreKey)}" }

        if (declared.name != CoreFieldUsage.ABSENT || meanings.name != null)
            line("name", meanings.name, if (declared.nameUnique) "tools_base_schema_data_name_unique" else "tools_base_schema_data_name")
        if (declared.timestamp != CoreFieldUsage.ABSENT || meanings.timestamp != null)
            line("timestamp", meanings.timestamp, "tools_base_schema_data_timestamp")
        meanings.data?.let { lines += "- `data`: $it" }
        if (toolType.takesUserFields())
            line("extra", meanings.extra, "entry_schema_extra")
        val start = declared.start
        if (declared.state.isNotEmpty() || start != null || meanings.state != null) {
            val starts = when {
                start == null -> null
                start.statuses.isNotEmpty() -> s.shared("entry_start_statuses").format(start.statuses.joinToString(", "))
                else -> start.refusal
            }
            line("state", listOfNotNull(meanings.state ?: s.shared("entry_schema_state"), starts).joinToString(". "), "entry_schema_state")
        }

        return s.shared("entry_description_intro").format(toolType.getDisplayName(context)) + "\n" + lines.joinToString("\n")
    }
}
