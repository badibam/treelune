package app.treelune.core.apis

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A value is found at its path in an API's answer, by names and positions, and read as the type
 * the API declares; what is not there, or not of its type, is said, never guessed.
 */
class ApiCallTest {

    private val answer = JSONObject("""{"current": {"temperature_2m": 12.5, "ok": "true"}, "results": [{"url": "https://x.org/a.mp3"}], "count": "3", "gone": null}""")

    @Test
    fun aPath_goesDownNamesAndPositions() {
        assertEquals(12.5, (ApiCall.at(answer, "current.temperature_2m") as Number).toDouble(), 0.0)
        assertEquals("https://x.org/a.mp3", ApiCall.at(answer, "results.0.url"))
        assertEquals("https://x.org/a.mp3", ApiCall.at(answer, "results[0].url"))
    }

    @Test
    fun aPathToNothing_givesNothing() {
        assertNull(ApiCall.at(answer, "current.wind"))
        assertNull(ApiCall.at(answer, "results.3.url"))
        assertNull(ApiCall.at(answer, "results.first.url"))
        assertNull(ApiCall.at(answer, "gone"))
    }

    @Test
    fun aValue_isReadAsItsType() {
        assertEquals(3L, ApiCall.typed("n", "3", ApiSettings.NUMBER))
        assertEquals(12.5, ApiCall.typed("n", 12.5, ApiSettings.NUMBER))
        assertEquals(true, ApiCall.typed("b", "true", ApiSettings.BOOLEAN))
        assertEquals("12.5", ApiCall.typed("t", 12.5, ApiSettings.TEXT))
        assertEquals("https://x.org/a.mp3", ApiCall.typed("f", "https://x.org/a.mp3", ApiSettings.FILE))
    }

    @Test(expected = ApiCall.Failure::class)
    fun aTextThatIsNoNumber_isRefused() {
        ApiCall.typed("n", "twelve", ApiSettings.NUMBER)
    }

    @Test(expected = ApiCall.Failure::class)
    fun aFileWithoutAddress_isRefused() {
        ApiCall.typed("f", "a.mp3", ApiSettings.FILE)
    }

    @Test(expected = ApiCall.Failure::class)
    fun anAnswerInError_isAFailure() {
        ApiCall.parse(ApiCall.Reply(500, "{}"))
    }

    @Test
    fun aFileOnTheApisServer_isAskedWithItsKey_elsewhereWithout() {
        val own = listOf("https://hypnogen.example/jobs.php", "https://status.example/jobs.php?job={id}")
        assertTrue(ApiCall.sameServer("https://hypnogen.example/jobs.php/abc/audio.ogg".toHttpUrl(), own))
        assertTrue(ApiCall.sameServer("https://status.example/files/a.ogg".toHttpUrl(), own))
        assertFalse(ApiCall.sameServer("https://other.example/a.ogg".toHttpUrl(), own))
        assertFalse(ApiCall.sameServer("https://cdn.hypnogen.example/a.ogg".toHttpUrl(), own))
        assertFalse(ApiCall.sameServer("http://hypnogen.example/a.ogg".toHttpUrl(), own))
        assertFalse(ApiCall.sameServer("https://hypnogen.example:8443/a.ogg".toHttpUrl(), own))
    }
}
