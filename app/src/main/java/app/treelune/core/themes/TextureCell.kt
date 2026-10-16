package app.treelune.core.themes

/**
 * A theme's cell of a motif (Texture): a small tile repeated over the surface, read from the
 * theme's `textures/<name>.svg` (resources, `app/treelune/themes/<id>/textures/`), the file the
 * texture bench reads too.
 *
 * A cell is a plain SVG: a `viewBox` of whole units from 0 0, and shapes, each a `<path d>`, a
 * `<rect x y width height>` (with `rx` for rounded corners) or a `<circle cx cy r>`. A shape with a
 * `stroke-width` is a line of that width (`stroke-linecap="round"` rounds its ends); any other is
 * filled. Colours in the file are ignored: the theme gives the motif's (TextureTones). Anything
 * else in the file is refused, so a cell never draws differently in the app and on the bench.
 */
data class TextureCell(val width: Int, val height: Int, val shapes: List<Shape>) {

    /** One shape of the cell, as SVG path data in the cell's units. */
    data class Shape(val pathData: String, val strokeWidth: Float?, val roundCaps: Boolean, val tag: String, val box: Box?) {
        /** Whether it is drawn as a line rather than filled. */
        val stroked: Boolean get() = strokeWidth != null
    }

    /** A rectangle's box, kept to check a pixel theme's cell (whole units, no rounding). */
    data class Box(val x: Float, val y: Float, val width: Float, val height: Float, val rounded: Boolean)

    /** Whether every shape is a filled rectangle of whole units: what a pixel theme's cell must be. */
    val wholePixels: Boolean
        get() = shapes.all { shape ->
            val box = shape.box
            shape.tag == "rect" && !shape.stroked && box != null && !box.rounded &&
                listOf(box.x, box.y, box.width, box.height).all { it == kotlin.math.floor(it) }
        }

    companion object {

        private val VIEW_BOX = Regex("""viewBox\s*=\s*"\s*0\s+0\s+(\d+)\s+(\d+)\s*"""")
        private val ELEMENT = Regex("""<\s*([a-zA-Z]+)([^>]*?)/?>""")
        private val ATTRIBUTE = Regex("""([a-zA-Z-]+)\s*=\s*"([^"]*)"""")

        /** The elements a cell may hold besides its shapes. */
        private val FRAME = setOf("svg", "g")

        /** The cell [svg] describes; refused with the reason when it is not one. */
        fun parse(svg: String): TextureCell {
            val (width, height) = VIEW_BOX.find(svg)?.destructured?.let { (w, h) -> w.toInt() to h.toInt() }
                ?: throw IllegalArgumentException("a cell needs a viewBox of whole units from 0 0")
            require(width > 0 && height > 0) { "a cell's viewBox is empty" }
            val withoutComments = svg.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            val shapes = ELEMENT.findAll(withoutComments).mapNotNull { element ->
                val tag = element.groupValues[1]
                if (tag in FRAME) return@mapNotNull null
                val attributes = ATTRIBUTE.findAll(element.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
                fun number(name: String): Float = attributes[name]?.toFloatOrNull()
                    ?: throw IllegalArgumentException("<$tag> needs a number in $name")
                val strokeWidth = attributes["stroke-width"]?.let { it.toFloatOrNull() ?: throw IllegalArgumentException("<$tag> has a stroke-width that is not a number") }
                val round = attributes["stroke-linecap"] == "round"
                when (tag) {
                    "path" -> Shape(attributes["d"] ?: throw IllegalArgumentException("<path> needs d"), strokeWidth, round, tag, null)
                    "rect" -> {
                        val x = attributes["x"]?.toFloatOrNull() ?: 0f
                        val y = attributes["y"]?.toFloatOrNull() ?: 0f
                        val w = number("width")
                        val h = number("height")
                        val r = attributes["rx"]?.toFloatOrNull() ?: 0f
                        Shape(rectPath(x, y, w, h, r), strokeWidth, round, tag, Box(x, y, w, h, r > 0f))
                    }
                    "circle" -> Shape(circlePath(number("cx"), number("cy"), number("r")), strokeWidth, round, tag, null)
                    else -> throw IllegalArgumentException("a cell holds only path, rect and circle, not <$tag>")
                }
            }.toList()
            require(shapes.isNotEmpty()) { "a cell has no shape" }
            return TextureCell(width, height, shapes)
        }

        private fun rectPath(x: Float, y: Float, w: Float, h: Float, r: Float): String {
            if (r <= 0f) return "M$x ${y}h${w}v${h}h${-w}z"
            val k = r.coerceAtMost(minOf(w, h) / 2f)
            return "M${x + k} ${y}h${w - 2 * k}a$k $k 0 0 1 $k ${k}v${h - 2 * k}a$k $k 0 0 1 ${-k} ${k}h${-(w - 2 * k)}a$k $k 0 0 1 ${-k} ${-k}v${-(h - 2 * k)}a$k $k 0 0 1 $k ${-k}z"
        }

        private fun circlePath(cx: Float, cy: Float, r: Float): String =
            "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

        /** Where a theme's cells live in the resources. */
        fun resourcePath(themeId: String, texture: Texture): String =
            "/app/treelune/themes/$themeId/textures/${texture.name.lowercase()}.svg"

        private val cache = java.util.concurrent.ConcurrentHashMap<String, TextureCell>()

        /**
         * [themeId]'s cell of [texture], read once. A missing or broken cell is a bug of the theme,
         * which TextureCellsTest catches: it fails here with the file it wanted.
         */
        fun of(themeId: String, texture: Texture): TextureCell {
            require(texture.hasCell) { "$texture has no cell: the thing's icon is its cell" }
            val path = resourcePath(themeId, texture)
            return cache.getOrPut(path) {
                // An absolute path: the release moves the classes, and a relative one finds nothing (docs/THEMES.md)
                val text = TextureCell::class.java.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
                    ?: error("Theme '$themeId' has no cell for $texture ($path)")
                parse(text)
            }
        }
    }
}
