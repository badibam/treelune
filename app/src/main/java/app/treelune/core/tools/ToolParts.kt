package app.treelune.core.tools

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How a tool's screens open a page inside the tool (Place.ToolPart, ToolTypeContract.PartScreen):
 * [open] stacks the page [part] names over the one shown, as any place is; the tool's place and
 * each of its pages provide it, knowing the tool and its zone.
 */
fun interface ToolParts {
    fun open(part: String)
}

/** The tool's pages, provided where its screen and its pages are drawn; none outside them. */
val LocalToolParts = staticCompositionLocalOf<ToolParts> { ToolParts { throw IllegalStateException("No tool's place to open '$it' in") } }
