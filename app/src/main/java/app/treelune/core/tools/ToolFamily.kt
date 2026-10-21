package app.treelune.core.tools

/**
 * The family a tool type belongs to, which the add menu of a zone's section ranges its tiles by,
 * in this order: what is noted, what is planned, what draws on the others. Each tool type says
 * its own (ToolTypeContract.getFamily), the core knowing none of them.
 *
 * @property labelKey The shared string naming the family over its tiles
 */
enum class ToolFamily(val labelKey: String) {
    NOTE("add_family_note"),
    PLAN("add_family_plan"),
    USE("add_family_use")
}
