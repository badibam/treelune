#!/usr/bin/env python3
"""Make the e-reader theme's fonts from Atkinson Hyperlegible Next, copied in third_party/atkinson-next/.

The source is two variable fonts, the upright and the italic, on one axis: weight (200 to 800).
The app takes four fixed upright weights and two italic ones, as res/font/atkinson_*.ttf: a fixed
weight is what every Android version draws the same. The output is committed, as the icons are:
the build never needs this script nor fontTools. The licence goes with the fonts into the app's
assets (fonts/atkinson-next/OFL.txt), as the SIL Open Font License asks.

Atkinson Hyperlegible Next has no narrow no-break space (U+202F), which French puts before ; : !
and ?: Android would take it from a system font. The script adds it, empty, an eighth of the em
wide, the fine space of French typography. The licence reserves no font name, so the modified
font may keep its name.

Updating the font = replacing third_party/atkinson-next/, running this script again, committing.

Usage:
    ./scripts/make_atkinson_fonts.py
"""

import shutil
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import Glyph
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "third_party" / "atkinson-next" / "AtkinsonHyperlegibleNext[wght].ttf"
ITALIC_SOURCE = ROOT / "third_party" / "atkinson-next" / "AtkinsonHyperlegibleNext-Italic[wght].ttf"
LICENCE = ROOT / "third_party" / "atkinson-next" / "OFL.txt"
FONTS = ROOT / "app" / "src" / "main" / "res" / "font"
ASSET_LICENCE = ROOT / "app" / "src" / "main" / "assets" / "fonts" / "atkinson-next" / "OFL.txt"

# The weights the theme draws in, by the name of their file: the text, labels, names, and titles
# with the numbers.
WEIGHTS = {"regular": 400, "semibold": 600, "bold": 700, "extrabold": 800}

# The italic of a read text (ThemeContract.emphasis), at the text's weight and at its bold, STRONG's.
ITALIC_WEIGHTS = {"italic": 400, "semibold_italic": 600}

NARROW_NO_BREAK_SPACE = 0x202F
NARROW_SPACE_GLYPH = "uni202F"


def add_narrow_space(font):
    """Map U+202F to a new empty glyph an eighth of the em wide, in every cmap that maps Unicode."""
    if NARROW_NO_BREAK_SPACE in font.getBestCmap():
        raise SystemExit("Atkinson Hyperlegible Next now has U+202F: drop add_narrow_space from this script")
    order = font.getGlyphOrder() + [NARROW_SPACE_GLYPH]
    font.setGlyphOrder(order)
    glyf = font["glyf"]
    glyf.glyphOrder = order
    glyf[NARROW_SPACE_GLYPH] = Glyph()  # no contour: an empty outline
    font["hmtx"][NARROW_SPACE_GLYPH] = (font["head"].unitsPerEm // 8, 0)
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap[NARROW_NO_BREAK_SPACE] = NARROW_SPACE_GLYPH


def main():
    cuts = [(name, weight, SOURCE) for name, weight in WEIGHTS.items()]
    cuts += [(name, weight, ITALIC_SOURCE) for name, weight in ITALIC_WEIGHTS.items()]
    for name, weight, source in cuts:
        font = instantiateVariableFont(TTFont(source), {"wght": weight}, updateFontNames=True)
        add_narrow_space(font)
        out = FONTS / f"atkinson_{name}.ttf"
        font.save(out)
        print(f"{out.relative_to(ROOT)}  {out.stat().st_size // 1024} KB")
    ASSET_LICENCE.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(LICENCE, ASSET_LICENCE)
    print(ASSET_LICENCE.relative_to(ROOT))


if __name__ == "__main__":
    main()
