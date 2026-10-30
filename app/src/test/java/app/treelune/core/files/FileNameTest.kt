package app.treelune.core.files

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A file downloaded from an address whose name does not say what it is (`audio.php?element=…`)
 * goes by the type its server announced; a name that says it is kept, whatever the server says.
 */
class FileNameTest {

    @Test
    fun anAddressThatSaysNothing_takesTheAnnouncedType() {
        assertEquals("audio.mp3", FileFormat.nameFor("audio.php", "audio/mpeg"))
    }

    @Test
    fun aNameThatSaysASound_isKept() {
        assertEquals("sim.ogg", FileFormat.nameFor("sim.ogg", "audio/mpeg"))
    }

    @Test
    fun neitherSayingOne_leavesTheNameAsItIs() {
        assertEquals("notes.txt", FileFormat.nameFor("notes.txt", "text/plain"))
        assertEquals("download", FileFormat.nameFor("download", null))
    }
}
