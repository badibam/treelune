package app.treelune.core.fields.settings

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The relay's address and secret through a change of the access mode: shown in the relay mode
 * only, and kept under Tailscale, so that going back to the relay finds them as they were.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalAccessRelayTest {

    private val nodes = AppSettings.nodes(AppSettingCategories.EXTERNAL_ACCESS, RuntimeEnvironment.getApplication())

    private fun config(mode: String) = JSONObject()
        .put(AppSettings.ACCESS_MODE, mode)
        .put(AppSettings.RELAY_URL, "https://relay.example.org")
        .put(AppSettings.RELAY_SECRET, "secret")

    private fun shownNames(config: JSONObject) = nodes.shown(config).filterIsInstance<SettingNode.Field>().map { it.definition.name }

    @Test
    fun `under Tailscale the relay is kept`() {
        val kept = SettingVariants.declared(nodes, config(AppSettings.ACCESS_MODE_TAILSCALE))
        assertEquals("https://relay.example.org", kept.optString(AppSettings.RELAY_URL))
        assertEquals("secret", kept.optString(AppSettings.RELAY_SECRET))
    }

    @Test
    fun `the relay is shown in the relay mode only`() {
        assertTrue(shownNames(config(AppSettings.ACCESS_MODE_RELAY)).containsAll(listOf(AppSettings.RELAY_URL, AppSettings.RELAY_SECRET)))
        val underTailscale = shownNames(config(AppSettings.ACCESS_MODE_TAILSCALE))
        assertFalse(AppSettings.RELAY_URL in underTailscale)
        assertFalse(AppSettings.RELAY_SECRET in underTailscale)
    }
}
