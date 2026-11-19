package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the v118 migration promises (RoutineUsagesAtV118): a routine used « both » keeps its
 * behaviour under its new name, the other usages stay as they were, in a backup as in a row.
 */
class RoutineUsagesAtV118Test {

    @Test
    fun bothBecomesAloneOrConversation() {
        assertEquals("alone_or_conversation", RoutineUsagesAtV118.values(JSONObject().put("usage", "both")).getString("usage"))
    }

    @Test
    fun otherUsagesStay() {
        listOf("alone", "conversation", "alone_or_conversation").forEach { usage ->
            assertEquals(usage, RoutineUsagesAtV118.values(JSONObject().put("usage", usage)).getString("usage"))
        }
    }

    @Test
    fun backupRoutinesRewritten() {
        val data = JSONObject().put("routines", JSONArray().put(JSONObject().put("id", "r").put("usage", "both")))
        RoutineUsagesAtV118.backup(data)
        assertEquals("alone_or_conversation", data.getJSONArray("routines").getJSONObject(0).getString("usage"))
    }
}
