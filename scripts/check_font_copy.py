#!/usr/bin/env python3
"""Check that the app's copy of the Cartouche font and its licence is the latest, byte for byte.

Cartouche, the pixel font of the retro theme, lives in a project of its own
(cartouche-font, beside this repository) that Saylune and the assistant both copy from:
its glyph maps are the source, its ttf/ the compiled product. The app carries a copy in
res/font, committed, so that the build never reaches outside the repository. A copy nobody
checks ages in silence: a glyph drawn there after the copy never shows here, and nothing
else fails. The licence travels with the font, as the SIL Open Font License asks: the
fonts' metadata points to OFL.txt, carried in the app's assets beside Baloo 2's.

The font project is found beside the main checkout, so that the check also runs from a
worktree. Its absence is a failure, not a pass: a check that cannot look has not looked.

Usage:
    ./scripts/check_font_copy.py
"""

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app" / "src" / "main"

# What the app takes from the font project, as (path there, path here): the text family,
# both weights, from its ttf/, and the licence.
FILES = [
    ("ttf/cartouche_regular.ttf", MAIN / "res" / "font" / "cartouche_regular.ttf"),
    ("ttf/cartouche_thin.ttf", MAIN / "res" / "font" / "cartouche_thin.ttf"),
    ("OFL.txt", MAIN / "assets" / "fonts" / "cartouche" / "OFL.txt"),
]


def font_project():
    """The font project's directory: beside the main checkout, which a worktree is not."""
    common = subprocess.run(
        ["git", "rev-parse", "--path-format=absolute", "--git-common-dir"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout.strip()
    return Path(common).parent.parent / "cartouche-font"


def main():
    source = font_project()
    if not source.is_dir():
        print(f"font copy: {source} not found -- the copy cannot be checked")
        return 1
    stale = [(there, here) for there, here in FILES
             if not here.is_file() or here.read_bytes() != (source / there).read_bytes()]
    for there, here in stale:
        print(f"font copy: {here.relative_to(ROOT)} differs from {source / there} -- copy it again")
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
