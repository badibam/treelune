package app.treelune.core.mcp

import android.app.NotificationManager
import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.AppSettingCategories
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * An identity file imported by a connector client (`import_identity`, docs/IDENTITY_FILE.md):
 * created with its texts « imported »; again without `replace`, refused saying the retouches and
 * languages it would lose; with `replace`, replaced, the retouched voice back as the file gives
 * it, the icon kept. The app's protection on, the approval's notification comes first; a client
 * limited to zones is refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpIdentityImportTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private val dao get() = AppDatabase.getDatabase(context).identityDao()

    private val file = JSONObject().put("format", 2).put("id", "teacher").put("name", "Teacher").put("languages", JSONObject()
        .put("fr", JSONObject().put("voice", JSONObject().put("content", "Je parle simplement.")))
        .put("en", JSONObject().put("voice", JSONObject().put("content", "I speak plainly.")))
    ).toString()

    /** The base outlives a test within this class: each starts without the identity, the protection off. */
    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        dao.deleteById("teacher")
        protection(false)
    }

    private suspend fun protection(on: Boolean) {
        val set = coordinator.processUserAction("app_config.set", mapOf("category" to AppSettingCategories.VALIDATION_CONFIG,
            "settings" to mapOf("validate_app" to on, "validate_past_data" to true)))
        assertTrue(set.error, set.isSuccess)
    }

    /** A client of the connector given [access] (the « Access » button of its row). */
    private fun client(access: List<Map<String, Any>>): McpCaller = runBlocking {
        val id = UUID.randomUUID().toString()
        RoomOAuthStore(context).addClient(OAuthClient(id, "Claude", listOf("https://claude.ai/cb"), "hash-of-secret", 1L, null))
        val set = coordinator.processUserAction("mcp_clients.set_access", mapOf("client_id" to id, "access" to access))
        assertTrue(set.error, set.isSuccess)
        McpCaller(id, "Claude")
    }

    private suspend fun import(caller: McpCaller, replace: Boolean? = null): McpToolResult {
        val arguments = JSONObject().put("text", file).put("language", "fr")
        replace?.let { arguments.put("replace", it) }
        return AppMcpBackend(context).call("import_identity", arguments, caller)
    }

    private suspend fun voice() = dao.getById("teacher")!!.toIdentity().languages.getValue("fr").voice!!

    @Test
    fun `imported, refused again without replace saying what it loses, replaced with it`() = runBlocking {
        val everything = client(emptyList())
        val created = import(everything)
        assertFalse(created.text, created.isError)
        assertTrue("its texts imported", voice().imported)

        val again = import(everything)
        assertTrue(again.text, again.isError)

        // The voice retouched in the app, an icon chosen
        val retouched = coordinator.processUserAction("identities.update", mapOf(
            "identity_id" to "teacher", "icon_name" to "palette", "section" to "fr", "voice" to "Ma voix à moi."
        ))
        assertTrue(retouched.error, retouched.isSuccess)
        assertFalse("written in the app", voice().imported)
        val preview = coordinator.processUserAction("identities.import_preview", mapOf("text" to file))
        assertEquals(listOf("fr · voice"), (preview.data!!["existing"] as Map<*, *>)["retouched"])
        val refused = import(everything)
        assertTrue(refused.text, refused.isError && refused.text.contains("fr · voice"))

        val replaced = import(everything, replace = true)
        assertFalse(replaced.text, replaced.isError)
        assertEquals("the voice back as the file gives it", "Je parle simplement.", voice().content)
        assertTrue(voice().imported)
        assertEquals("the icon kept", "palette", dao.getById("teacher")!!.toIdentity().iconName)
    }

    @Test
    fun `the app's protection on, the approval comes first`() = runBlocking {
        protection(true)
        val everything = client(emptyList())

        val call = async { import(everything) }
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val asked = withTimeout(10_000) {
            while (manager.allNotifications.isEmpty()) delay(20)
            manager.allNotifications.single()
        }
        assertTrue("nothing written before the answer", dao.getById("teacher") == null)
        // « Allow », as its button sends it
        val allow = asked.actions.last().actionIntent
        McpApprovals.answer(shadowOf(allow).savedIntent.getStringExtra(McpApprovals.EXTRA_ID)!!, true)
        val result = call.await()
        assertFalse(result.text, result.isError)
        assertTrue(dao.getById("teacher") != null)
    }

    @Test
    fun `a client limited to zones is refused`() = runBlocking<Unit> {
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Health"))
        assertTrue(zone.error, zone.isSuccess)
        val limited = client(listOf(mapOf("target" to mapOf("kind" to "ZONE", "id" to zone.data!!["zone_id"]), "level" to "full")))
        val refused = import(limited)
        assertTrue(refused.text, refused.isError)
        assertTrue(dao.getById("teacher") == null)
    }
}
