package app.treelune.core.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers what docs/AI.md (« Images ») and docs/DATA.md (« IMAGE ») promised of a kept
 * image: reduced to 1 568 px on its longest side and never enlarged, decoded at a fraction that
 * never falls under that size, its format kept (JPEG a JPEG, PNG a PNG, another one by its
 * transparency), an animated one told apart, and the startup sweep removing only the parts and
 * the images no row names.
 */
class ImagesTest {

    @Test
    fun `a photo is reduced to 1568 px on its longest side, its ratio kept`() {
        assertEquals(1568 to 1176, Images.reducedSize(4000, 3000))
        assertEquals(1176 to 1568, Images.reducedSize(3000, 4000))
    }

    @Test
    fun `a small image is never enlarged`() {
        assertEquals(800 to 600, Images.reducedSize(800, 600))
        assertEquals(1568 to 1000, Images.reducedSize(1568, 1000))
        assertEquals(200 to 200, Images.reducedSize(200, 200))
    }

    @Test
    fun `the decoding fraction keeps the longest side at least 1568 px`() {
        assertEquals(1, Images.sampleSize(3000, 2000))
        assertEquals(2, Images.sampleSize(4000, 3000))
        assertEquals(4, Images.sampleSize(8160, 6120))
        assertEquals(1, Images.sampleSize(800, 600))
    }

    @Test
    fun `a JPEG stays a JPEG and a PNG a PNG, transparent or not`() {
        assertEquals(ImageFormat.JPEG, Images.formatFor("image/jpeg", hasAlpha = false))
        assertEquals(ImageFormat.PNG, Images.formatFor("image/png", hasAlpha = true))
        assertEquals(ImageFormat.PNG, Images.formatFor("image/png", hasAlpha = false))
    }

    @Test
    fun `another format becomes a PNG with transparency, a JPEG without`() {
        assertEquals(ImageFormat.PNG, Images.formatFor("image/webp", hasAlpha = true))
        assertEquals(ImageFormat.JPEG, Images.formatFor("image/webp", hasAlpha = false))
        assertEquals(ImageFormat.JPEG, Images.formatFor("image/heif", hasAlpha = false))
    }

    @Test
    fun `an animated GIF, WebP or PNG is told apart from a still one`() {
        fun bytes(vararg parts: Any): ByteArray = parts.flatMap { part ->
            when (part) {
                is String -> part.toByteArray(Charsets.US_ASCII).toList()
                is Int -> listOf(part.toByte())
                else -> error(part)
            }
        }.toByteArray()
        assertTrue(Images.animated(bytes("GIF89a", 0, 0, "!", "NETSCAPE2.0")))
        assertFalse(Images.animated(bytes("GIF89a", 0, 0, 0)))
        // RIFF, its size, WEBPVP8X, the chunk's size, then the flags: bit 1 is the animation
        assertTrue(Images.animated(bytes("RIFF", 0, 0, 0, 0, "WEBPVP8X", 10, 0, 0, 0, 0x02)))
        assertFalse(Images.animated(bytes("RIFF", 0, 0, 0, 0, "WEBPVP8X", 10, 0, 0, 0, 0x10)))
        assertTrue(Images.animated(bytes(0x89, "PNG", 13, 10, 26, 10, "acTL")))
        assertFalse(Images.animated(bytes(0x89, "PNG", 13, 10, 26, 10, "IDAT")))
        assertFalse(Images.animated(bytes(0xFF, 0xD8, 0xFF)))
    }

    @Test
    fun `the sweep removes the parts and the images no row names, nothing else`() {
        val sweep = Images.sweep(
            names = listOf("a.jpg", "b.jpg", "c.png", "d.png", "c.jpg.part", "notes.txt", "entries", "pending"),
            expected = setOf("a.jpg", "c.png")
        )
        assertEquals(listOf("c.jpg.part"), sweep.parts)
        assertEquals(listOf("b.jpg", "d.png"), sweep.orphans)
    }
}
