#!/usr/bin/env python3
"""Make the metro theme's fonts from Overpass, whose source is copied in third_party/overpass/.

The source is two variable fonts, the upright and the italic, on one axis: weight (100 to 900).
The app takes three fixed upright weights and two italic ones, as res/font/overpass_*.ttf: a
fixed weight is what every Android version draws the same. The output is committed, as the icons
are: the build never needs this script nor fontTools. The licence goes with the fonts into the
app's assets (fonts/overpass/OFL.txt), as the SIL Open Font License asks.

Overpass has the narrow no-break space (U+202F) French puts before ; : ! and ?, so nothing is
added to it: the script refuses to run if that ever changes, rather than ship a font without it.

Updating Overpass = replacing third_party/overpass/, running this script again, committing.

Usage:
    ./scripts/make_overpass_fonts.py
"""

import shutil
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "third_party" / "overpass" / "Overpass[wght].ttf"
ITALIC_SOURCE = ROOT / "third_party" / "overpass" / "Overpass-Italic[wght].ttf"
LICENCE = ROOT / "third_party" / "overpass" / "OFL.txt"
FONTS = ROOT / "app" / "src" / "main" / "res" / "font"
ASSET_LICENCE = ROOT / "app" / "src" / "main" / "assets" / "fonts" / "overpass" / "OFL.txt"

# The weights the theme draws in, by the name of their file: the text, names and labels, and the
# titles with the numbers.
WEIGHTS = {"medium": 500, "bold": 700, "extrabold": 800}

# The italic of a read text (ThemeContract.emphasis), at the text's weight and at its bold, STRONG's.
ITALIC_WEIGHTS = {"medium_italic": 500, "bold_italic": 700}

NARROW_NO_BREAK_SPACE = 0x202F


def main():
    cuts = [(name, weight, SOURCE) for name, weight in WEIGHTS.items()]
    cuts += [(name, weight, ITALIC_SOURCE) for name, weight in ITALIC_WEIGHTS.items()]
    for name, weight, source in cuts:
        font = instantiateVariableFont(TTFont(source), {"wght": weight}, updateFontNames=True)
        if NARROW_NO_BREAK_SPACE not in font.getBestCmap():
            raise SystemExit(f"{source.name} lost U+202F: add it as make_archivo_fonts.py does")
        out = FONTS / f"overpass_{name}.ttf"
        font.save(out)
        print(f"{out.relative_to(ROOT)}  {out.stat().st_size // 1024} KB")
    ASSET_LICENCE.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(LICENCE, ASSET_LICENCE)
    print(ASSET_LICENCE.relative_to(ROOT))


if __name__ == "__main__":
    main()
