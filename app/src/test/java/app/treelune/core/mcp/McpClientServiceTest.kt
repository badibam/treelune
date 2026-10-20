package app.treelune.core.mcp

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The connector's clients as the user manages them (McpClientService): a client registering
 * itself is the app's own write, noted, not a step; its revocation by the user is a step whose
 * copy never holds its secret; the AI cannot touch them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpClientServiceTest {

    private lateinit var context: Context
    private lateinit var clientId: String
    private val history get() = AppDatabase.getDatabase(context).historyDao()

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        clientId = UUID.randomUUID().toString()
        RoomOAuthStore(context).addClient(OAuthClient(clientId, "Claude", listOf("https://claude.ai/cb"), "hash-of-secret", 1L, null))
    }

    @Test
    fun `a client registering itself is noted, never a step`() = runBlocking {
        val row = history.getRowsOf("mcp_clients", clientId).single()
        assertFalse(history.getStep(row.stepId)!!.isStep)
        assertFalse("its secret is never copied", JSONObject(row.after!!).has("secret_hash"))
    }

    @Test
    fun `a client revoked by the user is a step, kept without its secret`() = runBlocking {
        val revoked = Coordinator(context).processUserAction("mcp_clients.delete", mapOf("client_id" to clientId))
        assertTrue(revoked.error, revoked.isSuccess)
        assertNull(AppDatabase.getDatabase(context).mcpDao().client(clientId))

        val row = history.getRowsOf("mcp_clients", clientId).last()
        val step = history.getStep(row.stepId)!!
        assertTrue(step.isStep)
        assertEquals("USER", step.source)
        assertNull(row.after)
        val before = JSONObject(row.before!!)
        assertEquals("Claude", before.getString("name"))
        assertFalse(before.has("secret_hash"))
    }

    @Test
    fun `the AI cannot change a client's access`() = runBlocking {
        val refused = Coordinator(context).processAICommand("mcp_clients.set_access", mapOf("client_id" to clientId, "access" to emptyList<Any>()))
        assertFalse(refused.isSuccess)
    }
}
