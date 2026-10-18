#!/usr/bin/env python3
"""Make the blueprint theme's fonts from Architects Daughter and IBM Plex Mono, copied in third_party/.

Architects Daughter is the lettering of titles, names and labels: one weight. It has no narrow
no-break space (U+202F), which French puts before ; : ! and ?, nor a soft hyphen (U+00AD): the
script adds the first, empty, an eighth of the em wide, and maps the second to the font's own
hyphen. Its licence reserves no font name, so the modified font may keep its name.

IBM Plex Mono is every other text, values and numbers: Regular, Medium and Italic, copied as they
are. Its licence reserves the name "Plex", so a modified font would have to be renamed: nothing is
added to it, and the narrow no-break space it lacks is taken by Android from a system font.

The output is committed, as the icons are: the build never needs this script nor fontTools. Each
licence goes with its fonts into the app's assets (fonts/<font>/OFL.txt), as the SIL Open Font
License asks.

Updating a font = replacing its folder in third_party/, running this script again, committing.

Usage:
    ./scripts/make_blueprint_fonts.py
"""

import shutil
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import Glyph

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "app" / "src" / "main" / "res" / "font"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "fonts"

LETTERING = ROOT / "third_party" / "architects-daughter"
MONO = ROOT / "third_party" / "ibm-plex-mono"

# Plex Mono's cuts, by the name of their file in res/font: the text, the values and a read text's
# bold, and a read text's italic.
MONO_CUTS = {"plexmono_regular": "IBMPlexMono-Regular.ttf", "plexmono_medium": "IBMPlexMono-Medium.ttf",
             "plexmono_italic": "IBMPlexMono-Italic.ttf"}

NARROW_NO_BREAK_SPACE = 0x202F
NARROW_SPACE_GLYPH = "uni202F"
SOFT_HYPHEN = 0x00AD
HYPHEN = 0x002D


def add_narrow_space(font):
    """Map U+202F to a new empty glyph an eighth of the em wide, in every cmap that maps Unicode."""
    if NARROW_NO_BREAK_SPACE in font.getBestCmap():
        raise SystemExit("Architects Daughter now has U+202F: drop add_narrow_space from this script")
    order = font.getGlyphOrder() + [NARROW_SPACE_GLYPH]
    font.setGlyphOrder(order)
    glyf = font["glyf"]
    glyf.glyphOrder = order
    glyf[NARROW_SPACE_GLYPH] = Glyph()  # no contour: an empty outline
    font["hmtx"][NARROW_SPACE_GLYPH] = (font["head"].unitsPerEm // 8, 0)
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap[NARROW_NO_BREAK_SPACE] = NARROW_SPACE_GLYPH


def add_soft_hyphen(font):
    """Map U+00AD to the hyphen's glyph, in every cmap that maps Unicode."""
    cmap = font.getBestCmap()
    if SOFT_HYPHEN in cmap:
        raise SystemExit("Architects Daughter now has U+00AD: drop add_soft_hyphen from this script")
    hyphen = cmap[HYPHEN]
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap[SOFT_HYPHEN] = hyphen


def copy_licence(source_dir, name):
    out = ASSETS / name / "OFL.txt"
    out.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source_dir / "OFL.txt", out)
    print(out.relative_to(ROOT))


def main():
    font = TTFont(LETTERING / "ArchitectsDaughter-Regular.ttf")
    add_narrow_space(font)
    add_soft_hyphen(font)
    out = FONTS / "architects_daughter.ttf"
    font.save(out)
    print(f"{out.relative_to(ROOT)}  {out.stat().st_size // 1024} KB")
    copy_licence(LETTERING, "architects-daughter")

    for name, source in MONO_CUTS.items():
        out = FONTS / f"{name}.ttf"
        shutil.copyfile(MONO / source, out)
        print(f"{out.relative_to(ROOT)}  {out.stat().st_size // 1024} KB")
    copy_licence(MONO, "ibm-plex-mono")


if __name__ == "__main__":
    main()
