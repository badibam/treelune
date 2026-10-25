package app.treelune.core.ai.budget

import android.content.Context
import app.treelune.core.ai.data.MessageSender
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.database.AISessionEntity
import app.treelune.core.ai.database.SessionMessageEntity
import app.treelune.core.ai.domain.AILimitsConfig
import app.treelune.core.ai.data.SessionCost
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The day's budget (docs/AI.md, « Daily budget »): absent, none, and stored only when set; the
 * day sums every session's calls since its start, a call of unknown cost counting for nothing
 * and said; reached and alerted as its amount and threshold say.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BudgetTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun noBudget_isNoKey_andReadsBackAsNone() {
        val stored = JSONObject(AILimitsConfig.default().toSettingsJson())
        assertFalse(stored.has(AILimitsConfig.KEY_BUDGET))
        assertNull(AILimitsConfig.fromSettingsJson(stored).dailyBudget)

        val set = AILimitsConfig(dailyBudget = 2.0, budgetAlertPercent = 80)
        assertEquals(set, AILimitsConfig.fromSettingsJson(JSONObject(set.toSettingsJson())))
    }

    @Test
    fun reached_andAlerted_followTheAmountAndTheThreshold() {
        assertFalse(Budget.Today(1.5, 0, null, 80).reached)
        assertFalse(Budget.Today(1.5, 0, 2.0, 80).alerted)
        assertTrue(Budget.Today(1.6, 0, 2.0, 80).alerted)
        assertFalse(Budget.Today(1.9, 0, 2.0, null).alerted)
        assertTrue(Budget.Today(2.0, 0, 2.0, 80).reached)
    }

    @Test
    fun theDay_sumsEverySession_sinceItsStart_anUnknownCallSaid() = runBlocking {
        val dao = AppDatabase.getDatabase(context).aiDao()
        fun session(type: SessionType) = UUID.randomUUID().toString().also { id ->
            runBlocking { dao.insertSession(AISessionEntity(id = id, name = "S", type = type, automationId = null, scheduledExecutionTime = null,
                providerId = null, providerSessionId = id, createdAt = 0, lastActivity = 0, isActive = false)) }
        }
        fun call(sessionId: String, at: Long, tokens: Int, price: Double?, unknown: Boolean = false) = runBlocking {
            dao.insertMessage(SessionMessageEntity(id = UUID.randomUUID().toString(), sessionId = sessionId, timestamp = at,
                sender = MessageSender.AI, richContentJson = null, textContent = null, aiMessageJson = "{}", aiMessageParsedJson = null,
                systemMessageJson = null, executionMetadataJson = null, outputTokens = tokens, modelId = "m", outputPrice = price, usageUnknown = unknown))
        }
        val chat = session(SessionType.CHAT)
        val run = session(SessionType.AUTOMATION)
        call(chat, 1_000, 1_000, 0.001)
        call(run, 2_000, 500, 0.001)
        call(run, 2_500, 100, null)
        call(chat, 500, 9_999, 1.0)

        val cost = SessionCost.of(dao.getCallUsagesSince(1_000).map { it.toCallUsage() })
        assertEquals(1.5, cost.totalCost, 1e-9)
        assertEquals(1, cost.callsWithUnknownCost)
    }
}
