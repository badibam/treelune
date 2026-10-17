#!/usr/bin/env python3
"""Make the Swiss theme's fonts from Archivo, whose source is copied in third_party/archivo/.

The source is two variable fonts, the upright and the italic, on two axes: weight (100 to 900) and
width (62 to 125). The app takes four fixed upright weights and two italic ones at the normal width,
as res/font/archivo_*.ttf: a fixed weight is
what every Android version draws the same. The output is committed, as the icons are: the build
never needs this script nor fontTools. The licence goes with the fonts into the app's assets
(fonts/archivo/OFL.txt), as the SIL Open Font License asks.

Archivo has no narrow no-break space (U+202F), which French puts before ; : ! and ?: Android
would take it from a system font. The script adds it, empty, an eighth of the em wide, the fine
space of French typography. The licence reserves no font name, so the modified font may keep
Archivo's.

Updating Archivo = replacing third_party/archivo/, running this script again, committing.

Usage:
    ./scripts/make_archivo_fonts.py
"""

import shutil
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import Glyph
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "third_party" / "archivo" / "Archivo[wdth,wght].ttf"
ITALIC_SOURCE = ROOT / "third_party" / "archivo" / "Archivo-Italic[wdth,wght].ttf"
LICENCE = ROOT / "third_party" / "archivo" / "OFL.txt"
FONTS = ROOT / "app" / "src" / "main" / "res" / "font"
ASSET_LICENCE = ROOT / "app" / "src" / "main" / "assets" / "fonts" / "archivo" / "OFL.txt"

# The weights the theme draws in, by the name of their file: the text, names and labels, group
# titles, and a screen's title with the numbers.
WEIGHTS = {"regular": 400, "semibold": 600, "extrabold": 800, "black": 900}

# The italic of a read text (ThemeContract.emphasis), at the text's weight and at its bold, STRONG's.
ITALIC_WEIGHTS = {"italic": 400, "semibold_italic": 600}

# The normal width: the theme never draws Archivo narrowed nor widened.
WIDTH = 100

NARROW_NO_BREAK_SPACE = 0x202F
NARROW_SPACE_GLYPH = "uni202F"


def add_narrow_space(font):
    """Map U+202F to a new empty glyph an eighth of the em wide, in every cmap that maps Unicode."""
    if NARROW_NO_BREAK_SPACE in font.getBestCmap():
        raise SystemExit("Archivo now has U+202F: drop add_narrow_space from this script")
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
        font = instantiateVariableFont(TTFont(source), {"wght": weight, "wdth": WIDTH}, updateFontNames=True)
        add_narrow_space(font)
        out = FONTS / f"archivo_{name}.ttf"
        font.save(out)
        print(f"{out.relative_to(ROOT)}  {out.stat().st_size // 1024} KB")
    ASSET_LICENCE.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(LICENCE, ASSET_LICENCE)
    print(ASSET_LICENCE.relative_to(ROOT))


if __name__ == "__main__":
    main()
