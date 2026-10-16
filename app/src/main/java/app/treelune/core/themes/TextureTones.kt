package app.treelune.core.themes

import org.json.JSONObject

/**
 * A theme's tones of the motifs (Texture), read from its `textures.json` (resources,
 * `app/treelune/themes/<id>/`), which the texture bench sets by eye and saves.
 *
 * For each mode, the tone of each motif at each intensity: how far the motif's lightness stands
 * from the surface it is laid on, toward the surface's ink (darker on a light surface, lighter on
 * a dark one), in OKLCH lightness. A motif covering much of its cell (CHECKER) needs less than one
 * covering little (DOTS) to show as much: the bench sets each so that all show alike. And the
 * chroma a motif takes in a thing's icon colour, at that colour's hue.
 */
data class TextureTones(val modes: Map<PaletteMode, Mode>) {

    /** The tones of one mode. */
    data class Mode(val tagChroma: Float, val tones: Map<Texture, Map<TextureIntensity, Float>>) {

        /** The tone of [texture] at [intensity]. */
        fun tone(texture: Texture, intensity: TextureIntensity): Float =
            tones[texture]?.get(intensity) ?: error("No tone for $texture at $intensity")

        /**
         * The colour of a motif laid on [surface]: [surface]'s lightness moved by its tone toward
         * the ink, at [surface]'s own chroma and hue, or at [tag]'s hue and this mode's chroma when
         * the thing's icon has a colour. A solid colour, never a blend: a pixel theme stays in
         * whole tones.
         */
        fun motifColor(surface: androidx.compose.ui.graphics.Color, tag: androidx.compose.ui.graphics.Color?, texture: Texture, intensity: TextureIntensity): androidx.compose.ui.graphics.Color {
            val ground = Oklch.toLch(surface)
            val toward = if (ground.l > 0.5f) -1f else 1f
            val lightness = ground.l + toward * tone(texture, intensity)
            val hue = tag?.let { Oklch.toLch(it) }
            return Lch(lightness, hue?.let { tagChroma } ?: ground.c, hue?.h ?: ground.h).srgb
        }
    }

    /** The tones of [mode]. */
    fun mode(mode: PaletteMode): Mode = modes[mode] ?: error("No texture tones for $mode")

    companion object {

        /** Where a theme's tones live in the resources. */
        fun resourcePath(themeId: String): String = "/app/treelune/themes/$themeId/textures.json"

        /**
         * The tones [json] holds. Every mode and every motif must have both intensities: a missing
         * one is refused with its name, so a theme never draws a motif it has no tone for.
         */
        fun parse(json: String): TextureTones {
            val modes = JSONObject(json).getJSONObject("modes")
            return TextureTones(PaletteMode.entries.associateWith { mode ->
                val m = modes.optJSONObject(mode.name) ?: throw IllegalArgumentException("no tones for $mode")
                val tones = m.getJSONObject("tones")
                Mode(
                    tagChroma = m.getDouble("tag_chroma").toFloat(),
                    tones = Texture.entries.associateWith { texture ->
                        val t = tones.optJSONObject(texture.name) ?: throw IllegalArgumentException("no tones for $texture in $mode")
                        TextureIntensity.entries.associateWith { intensity ->
                            if (!t.has(intensity.name)) throw IllegalArgumentException("no $intensity tone for $texture in $mode")
                            t.getDouble(intensity.name).toFloat()
                        }
                    }
                )
            })
        }

        private val cache = java.util.concurrent.ConcurrentHashMap<String, TextureTones>()

        /** [themeId]'s tones, read once; a theme without them is a bug, which TextureTonesTest catches. */
        fun of(themeId: String): TextureTones = cache.getOrPut(themeId) {
            // An absolute path: the release moves the classes (docs/THEMES.md)
            val text = TextureTones::class.java.getResourceAsStream(resourcePath(themeId))?.bufferedReader()?.use { it.readText() }
                ?: error("Theme '$themeId' has no texture tones (${resourcePath(themeId)})")
            parse(text)
        }
    }
}
