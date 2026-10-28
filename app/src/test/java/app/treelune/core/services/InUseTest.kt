package app.treelune.core.services

import android.content.Context
import app.treelune.core.ai.database.AIProviderConfigEntity
import app.treelune.core.ai.data.RoutineMessage
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What docs/AI.md (« Identities ») promised of a provider or an identity about to be deleted: the
 * deletion is refused while a routine (or a zone, for an identity) names it, the refusal naming
 * them (InUse); a provider nothing names is deleted, the default needing a successor while others
 * remain.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InUseTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private val database get() = AppDatabase.getDatabase(context)

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        // The database is a singleton shared by the whole run: start from a clean providers table,
        // so a test's "the last provider" is the last one it created.
        database.aiDao().getAllProviderConfigs().forEach { database.aiDao().deleteProviderConfigById(it.id) }
    }

    private suspend fun provider(id: String, name: String, isDefault: Boolean) =
        database.aiDao().insertProviderConfig(AIProviderConfigEntity(id, "claude", name, "{}", isDefault, 0, 0))

    private suspend fun zone(name: String): String {
        val created = coordinator.processUserAction("zones.create", mapOf("name" to name))
        assertTrue(created.error, created.isSuccess)
        return created.data!!["zone_id"] as String
    }

    private suspend fun routine(zoneId: String, name: String, providerId: String) {
        val created = coordinator.processUserAction("routines.create", mapOf(
            "zone_id" to zoneId,
            "name" to name,
            "identity_id" to null,
            "provider_id" to providerId,
            "is_enabled" to false,
            "message" to listOf(mapOf("type" to RoutineMessage.TEXT, "text" to "Hi"))
        ))
        assertTrue(created.error, created.isSuccess)
    }

    @Test
    fun `a provider a routine names refuses its deletion, naming the routine`() = runBlocking {
        provider("claude", "Claude", isDefault = true)
        routine(zone("Garden"), "Weekly review", "claude")

        val users = InUse.provider(database, "claude")
        assertEquals(listOf("Weekly review"), users.routines)

        val refused = coordinator.processUserAction("ai_provider_config.delete", mapOf("provider_id" to "claude"))
        assertFalse(refused.isSuccess)
        assertTrue(refused.error, refused.error!!.contains("Weekly review"))
    }

    @Test
    fun `a provider nothing names is deleted, the default needing a successor while others remain`() = runBlocking {
        provider("alpha", "Alpha", isDefault = true)
        provider("beta", "Beta", isDefault = false)

        val refused = coordinator.processUserAction("ai_provider_config.delete", mapOf("provider_id" to "alpha"))
        assertFalse(refused.isSuccess)

        val deleted = coordinator.processUserAction("ai_provider_config.delete", mapOf("provider_id" to "alpha", "new_default_id" to "beta"))
        assertTrue(deleted.error, deleted.isSuccess)
        assertEquals("beta", database.aiDao().getDefaultProviderConfig()!!.id)

        // The last one goes without a successor
        val last = coordinator.processUserAction("ai_provider_config.delete", mapOf("provider_id" to "beta"))
        assertTrue(last.error, last.isSuccess)
        assertEquals(null, database.aiDao().getDefaultProviderConfig())
    }

    @Test
    fun `an identity a zone or a routine names refuses its deletion, naming them`() = runBlocking {
        val created = coordinator.processUserAction("identities.create", mapOf("name" to "Stan Prokopenko", "language" to "en"))
        assertTrue(created.error, created.isSuccess)
        val identityId = created.data!!["identity_id"] as String

        val zoned = coordinator.processUserAction("zones.create", mapOf("name" to "Art", "identity_id" to identityId))
        assertTrue(zoned.error, zoned.isSuccess)
        val zoneId = zoned.data!!["zone_id"] as String
        val routine = coordinator.processUserAction("routines.create", mapOf(
            "zone_id" to zoneId,
            "name" to "Studio",
            "identity_id" to identityId,
            "provider_id" to null,
            "is_enabled" to false,
            "message" to listOf(mapOf("type" to RoutineMessage.TEXT, "text" to "Hi"))
        ))
        assertTrue(routine.error, routine.isSuccess)

        val users = InUse.identity(database, identityId)
        assertEquals(listOf("Art"), users.zones)
        assertEquals(listOf("Studio"), users.routines)

        val refused = coordinator.processUserAction("identities.delete", mapOf("identity_id" to identityId))
        assertFalse(refused.isSuccess)
        assertTrue(refused.error, refused.error!!.contains("Art"))
    }
}
