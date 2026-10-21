package app.treelune.core.demo

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The demo's removal is one step (HistoryWritesTest): undone, the demo comes back whole, its
 * routines and their messages with it, its photos too, kept while the removal can be undone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoUndoTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private val database get() = AppDatabase.getDatabase(context)

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        DemoFixture.installed(context)
        coordinator = Coordinator(context)
    }

    private suspend fun lastStep(): String {
        val listed = coordinator.processUserAction("history.list", mapOf("limit" to 1))
        assertTrue(listed.error, listed.isSuccess)
        @Suppress("UNCHECKED_CAST")
        return ((listed.data!!["steps"] as List<Map<String, Any?>>).single()["id"] as String)
    }

    @Test
    fun `the demo removed is undone whole, its routines and their messages with it`() = runBlocking {
        val routine = database.aiDao().getAllAutomations().first { it.id.startsWith(DemoContent.PREFIX) }
        val seedId = routine.seedSessionId
        assertTrue(database.aiDao().getMessagesForSession(seedId).isNotEmpty())

        assertTrue(coordinator.processUserAction("demo.remove", emptyMap()).isSuccess)
        assertTrue(database.zoneDao().getAllZones().none { it.id.startsWith(DemoContent.PREFIX) })
        assertNull(database.aiDao().getAutomationById(routine.id))

        assertTrue(coordinator.processUserAction("history.undo", mapOf("id" to lastStep())).isSuccess)
        assertTrue(database.zoneDao().getAllZones().any { it.id.startsWith(DemoContent.PREFIX) })
        assertTrue(database.aiDao().getAutomationById(routine.id) != null)
        assertTrue(database.aiDao().getMessagesForSession(seedId).isNotEmpty())
    }

    @Test
    fun `the demo's photos are kept while its removal can be undone, and come back with the undo`() = runBlocking {
        val plants = listOf("demo-plant-tomato", "demo-plant-basil", "demo-plant-chilli")
        val files = plants.map { app.treelune.core.images.Images.file(context, database.imageDao().getForEntry(it).single()) }
        assertTrue(files.all { it.exists() })

        assertTrue(coordinator.processUserAction("demo.remove", emptyMap()).isSuccess)
        assertTrue("kept while the removal can be undone", files.all { it.exists() })
        assertTrue(coordinator.processUserAction("history.undo", mapOf("id" to lastStep())).isSuccess)
        plants.forEach { assertTrue(it, database.imageDao().getForEntry(it).size == 1) }
        assertTrue(files.all { it.exists() })
    }
}
