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
 *
 * What lives outside that history — the last judgements of the conditions the demo's Tasks and
 * routines listen to — goes first, through plain deletions ([OUTSIDE_HISTORY]): an undo of the
 * removal does not bring it back, the next tick judging again. A Task's runs go with it (cascade),
 * which the history leaves out. The demo's API stays while a Task of the user's calls it.
 */
object DemoRemoval {
    val OUTSIDE_HISTORY = listOf(
        """DELETE FROM condition_judgements WHERE owner_id LIKE 'demo-%'
            OR owner_id IN (SELECT id FROM tasks WHERE zone_id LIKE 'demo-%')
            OR owner_id IN (SELECT id FROM routines WHERE zone_id LIKE 'demo-%')"""
    )

    val SELECTIONS = listOf(
        "tasks" to "SELECT id FROM tasks WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'",
        // Read once the demo's Tasks are gone: an API a Task of the user's calls stays
        "apis" to """SELECT id FROM apis WHERE id LIKE 'demo-%' AND NOT EXISTS
            (SELECT 1 FROM tasks WHERE tasks.steps_json LIKE '%"' || apis.id || '"%')""",
        "tool_data" to """SELECT id FROM tool_data WHERE id LIKE 'demo-%' OR tool_instance_id IN
            (SELECT id FROM tool_instances WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%')""",
        "tool_instances" to "SELECT id FROM tool_instances WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'",
        "variables" to "SELECT id FROM variables WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'",
        "ai_sessions" to """SELECT id FROM ai_sessions WHERE id LIKE 'demo-%' OR routine_id IN
            (SELECT id FROM routines WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%')""",
        "routines" to "SELECT id FROM routines WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'",
        "zones" to "SELECT id FROM zones WHERE id LIKE 'demo-%'",
        "identities" to """SELECT id FROM identities WHERE id LIKE 'demo-%'
            AND id NOT IN (SELECT identity_id FROM zones WHERE identity_id IS NOT NULL)
            AND id NOT IN (SELECT identity_id FROM routines WHERE identity_id IS NOT NULL)"""
    )
}
