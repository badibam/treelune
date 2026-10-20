package app.treelune.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A message stays long enough to be read whole: longer as it grows, never long enough to be in the way. */
class ToastsTest {

    @Test
    fun `a short message stays the time asked, a long one longer, at most ten seconds`() {
        assertEquals(2500L, Toasts.millis("Copié", Duration.SHORT))
        assertEquals(4000L, Toasts.millis("Copié", Duration.LONG))
        val refusal = "Aucun dossier n'est choisi pour l'app : choisis-en un dans Réglages › Données pour y écrire le fichier."
        assertTrue(Toasts.millis(refusal, Duration.LONG) > 4000L)
        assertEquals(10_000L, Toasts.millis("x".repeat(1000), Duration.LONG))
    }
}
