#!/usr/bin/env python3
"""Make the cut paper theme's fonts from Bricolage Grotesque, whose source is copied in third_party/bricolage/.

The source is one variable font on three axes: optical size (12 to 96), weight (200 to 800) and
width (75 to 100). The app takes three fixed cuts, as res/font/bricolage_*.ttf: a fixed cut is
what every Android version draws the same. Each is set at the optical size of what it writes, as
a browser would set it by itself: the text at 14, names, labels and numbers at 16, titles at 28,
where the face is at its most marked. The width stays at 100. Bricolage has no italic: a read
text's italic is the slant Android makes (ThemeContract.emphasis).

The output is committed, as the icons are: the build never needs this script nor fontTools. The
licence goes with the fonts into the app's assets (fonts/bricolage/OFL.txt), as the SIL Open Font
License asks.

Bricolage has the narrow no-break space (U+202F) French puts before ; : ! and ?, so nothing is
added to it: the script refuses to run if that ever changes, rather than ship a font without it.

Updating Bricolage Grotesque = replacing third_party/bricolage/, running this script again, committing.

Usage:
    ./scripts/make_bricolage_fonts.py
"""

import shutil
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "third_party" / "bricolage" / "BricolageGrotesque[opsz,wdth,wght].ttf"
LICENCE = ROOT / "third_party" / "bricolage" / "OFL.txt"
FONTS = ROOT / "app" / "src" / "main" / "res" / "font"
ASSET_LICENCE = ROOT / "app" / "src" / "main" / "assets" / "fonts" / "bricolage" / "OFL.txt"

# The cuts the theme draws in, by the name of their file: weight and optical size.
CUTS = {
    "text": {"wght": 500, "opsz": 14},
    "label": {"wght": 800, "opsz": 16},
    "title": {"wght": 800, "opsz": 28},
}

NARROW_NO_BREAK_SPACE = 0x202F


def main():
    for name, axes in CUTS.items():
        # The names are left as they are: the font's own table names only some optical sizes,
        # not 16 nor 28, and Android reads a font of res/font/ by its file.
        font = instantiateVariableFont(TTFont(SOURCE), {**axes, "wdth": 100})
        if NARROW_NO_BREAK_SPACE not in font.getBestCmap():
            raise SystemExit(f"{SOURCE.name} lost U+202F: add it as make_archivo_fonts.py does")
        out = FONTS / f"bricolage_{name}.ttf"
        font.save(out)
        print(f"{out.relative_to(ROOT)}  {out.stat().st_size // 1024} KB")
    ASSET_LICENCE.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(LICENCE, ASSET_LICENCE)
    print(ASSET_LICENCE.relative_to(ROOT))


if __name__ == "__main__":
    main()
