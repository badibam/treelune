package app.treelune.core.coordinator

import android.content.Context
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.database.AISessionEntity
import app.treelune.core.database.AppDatabase
import app.treelune.core.history.StepSession
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The cap per chain (docs/CORE.md, « Chains »): what a routine's run or a Task's run starts
 * continues its chain, refused once the chain holds the cap, counted over every run of it; what a
 * person, an hour, a chat starts begins a chain of its own, whatever the others hold.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChainsTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = AppDatabase.getDatabase(context)
    }

    /** A session of [type], continuing [chainId] when given: its id. */
    private suspend fun session(type: SessionType, chainId: String? = null): String {
        val id = UUID.randomUUID().toString()
        database.aiDao().insertSession(AISessionEntity(
            id = id, name = "Run", type = type, routineId = if (type == SessionType.ROUTINE_RUN) "routine" else null,
            scheduledExecutionTime = 0, providerId = null, providerSessionId = id, createdAt = 0, lastActivity = 0,
            isActive = false, chainId = chainId
        ))
        return id
    }

    /** A routine's run that started its chain, and [more] runs continuing it: the first's id. */
    private suspend fun chainOf(more: Int): String {
        val first = session(SessionType.ROUTINE_RUN)
        repeat(more) { session(SessionType.ROUTINE_RUN, first) }
        return first
    }

    @Test
    fun aRoutinesAI_continuesItsRunsChain_belowTheCap() = runBlocking {
        val first = chainOf(10)
        val last = session(SessionType.ROUTINE_RUN, first)

        val admission = withContext(StepSession(last)) { Chains.admit(context, "Weekly") }

        assertEquals(Chains.Admission.Continues(first), admission)
    }

    @Test
    fun aChainHoldingTheCap_startsNothingMore() = runBlocking {
        // The first and 49 after it: 50, the default cap
        val first = chainOf(49)

        val admission = withContext(StepSession(first)) { Chains.admit(context, "Weekly") }

        assertTrue(admission is Chains.Admission.Capped)
    }

    @Test
    fun aTasksSteps_continueItsChain() = runBlocking {
        val first = chainOf(49)

        assertTrue(withContext(ChainLink(first)) { Chains.admit(context, "Regenerate") } is Chains.Admission.Capped)
        assertEquals(Chains.Admission.Continues("fresh"), withContext(ChainLink("fresh")) { Chains.admit(context, "Regenerate") })
    }

    @Test
    fun aScreenOrAChat_startsAChain_whateverTheOthersHold() = runBlocking {
        chainOf(49)
        val chat = session(SessionType.CHAT)

        assertEquals(Chains.Admission.Starts, Chains.admit(context, "Weekly"))
        assertEquals(Chains.Admission.Starts, withContext(StepSession(chat)) { Chains.admit(context, "Weekly") })
    }
}
