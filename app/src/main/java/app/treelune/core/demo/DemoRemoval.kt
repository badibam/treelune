package app.treelune.core.demo

/**
 * What a reinstall of the demo removes, in this order, the children before what holds them: every
 * row whose id carries the demo's prefix, and what lives in a demo zone or tool whatever its id —
 * an entry the user typed in a demo tool, a tool added to a demo zone. A session's messages and
 * files go with it (cascade). The demo's identity goes last, but when a zone or a routine of the
 * user's names it, as its deletion is refused then (InUse).
 *
 * Each is a table and the query of the ids to delete from it, read as the deletions before it
 * left the database: DemoService deletes them one by one through the one door of the history of
 * writes (Writes), so that the removal is one step an undo puts back. Plain queries, which a test
 * runs on SQLite as they are.
 */
object DemoRemoval {
    val SELECTIONS = listOf(
        "tool_data" to """SELECT id FROM tool_data WHERE id LIKE 'demo-%' OR tool_instance_id IN
            (SELECT id FROM tool_instances WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%')""",
        "tool_instances" to "SELECT id FROM tool_instances WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'",
        "variables" to "SELECT id FROM variables WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'",
        "ai_sessions" to """SELECT id FROM ai_sessions WHERE id LIKE 'demo-%' OR automation_id IN
            (SELECT id FROM automations WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%')""",
        "automations" to "SELECT id FROM automations WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'",
        "zones" to "SELECT id FROM zones WHERE id LIKE 'demo-%'",
        "identities" to """SELECT id FROM identities WHERE id LIKE 'demo-%'
            AND id NOT IN (SELECT identity_id FROM zones WHERE identity_id IS NOT NULL)
            AND id NOT IN (SELECT identity_id FROM automations WHERE identity_id IS NOT NULL)"""
    )
}
