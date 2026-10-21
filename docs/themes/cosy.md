# The Cosy theme

Designed from 2026-10-06. A third theme, beside the default and the retro: the app as a phone in a gentle game (Animal Crossing, Stardew Valley). Everything is round and plump, and it is the only theme whose movement is a spring: where the retro jumps from cell to cell, the cosy squashes and bounces.

Mock-ups (the screen of a zone, beside the other theme tracks, on hold): `docs/theme-tracks.html`, "Cosy" card.

## The register, from the mock-up

- A sand background sown with small dots; cream tiles, very rounded, laid on a solid, thick shadow under them, never blurred.
- The title of a screen in a solid pill of the main colour (leaf green), laid on its own shadow; the round buttons, on their shadow too.
- A group title on the background, followed by a rounded dotted line.
- The tags in pastel pills; a ticked box is a solid circle marked with a tick, an empty box a hollow circle; the gauge is a striped pill.
- An icon alone on its tile sits in a circle of a pale colour.
- The "waiting" mark is an orange bubble with a "!", on its own shadow.
- A round, bold font (Baloo 2 in the mock-up).

## What is decided

- **The theme draws everything itself**, like the retro: each contract component, dialogs and date and time selectors included, nothing taken from the default theme nor Material (2026-10-06).
- **The font is Baloo 2, alone, for the whole theme** (SIL Open Font License, embedded in the app), from the 400 of the running text to the 800 of the titles and figures (2026-10-06). It has no italic: Android slants the upright, which its round strokes support (validated on the board 2026-10-17, against Nunito's italic and a colour).
- **Two palettes, in the retro's method**: the light one of the mock-up (sand background, cream tiles, leaf green) and a summer night (deep night blue background, slate blue tiles on their darker shadow, cream ink, the green, the pastels and the orange brightened like lanterns); no brown, which is already the retro's night. Each is a handful of numbers in OKLCH from which everything derives, which the "Hue" setting turns, states and tags kept, and which are set on the bench (`./run themes`) (2026-10-06).
- **The movement is a spring** (2026-10-06). On touch, an element sinks into its shadow, which disappears under it; on release, it comes back up on a spring that overshoots its place a little then settles there, in about a quarter of a second. A window (dialog, chat) arrives a little smaller and in fade, then swells to its size with the same overshoot; it closes at once. A lifted tile in edit mode grows a little, tilts a few degrees, its shadow lengthens; laid down, it bounces back into place. The other tiles half fade out meanwhile, letting the cells beneath show (`GridTile`). A block that unfolds slides, without a bounce. With Android's animations cut, everything arrives in its place at once.
- **The sounds come from Kenney** (CC0, credited in `sounds.json` like the retro's), chosen by ear on a listening board of all of Kenney (2026-10-06): confirm `drop_004`, enter `select_008`, back `select_007`, open `maximize_001`, close `minimize_001`, tick `select_001`, step `tick_001`, the end of a list `bong_001`, refuse `error_006`, all from Interface Sounds. Their level: 13 dB under Kenney's originals, 3 under the retro's, to adjust by ear on the phone. The families kept for a touch-up: back, bong, click, drop, error, maximize, minimize, pluck, select, switch, tick and toggle from Interface Sounds; click, rollover and switch from UI Audio.
- **The icons** (2026-10-20, on the bench `Cosy · icônes`): Lucide redrawn by the theme (`CosyIcons`, from `IconStrokes`), the 2 stroke taken to 3, round tips and elbows, on a solid shadow shifted 0.6 downward, in the colour of the tiles' shadow, without a pale tone in the closed shapes; its numbers in `icons.json`, which the bench saves. An icon on a coloured piece (button, pill, ticked box) has no shadow, the piece having its own.
- **The bar of a selected text** (2026-10-16): a cream tile on its solid shadow, its words in Baloo at a target's height, to the next line when they do not fit; a touched word sinks the whole bar, which bounces.
- **The textures** (2026-10-16): round, plump shapes, thick strokes with round tips, a dp cell that grows with the size step, laid on the tile's face, under its shadow, and over the whole background of a textured page (`PageGround`), in the pattern's tone on the sand, in place of the background's small dots, the header's pill staying plain. `DOTS` is large dots laid staggered, unlike the small dots of the screen's background.
- **The size** (2026-10-06): Android's text size is followed, for the text alone; the step (0 to 3) enlarges everything together, text, roundings, shadows and icons, by ×1, ×1.12, ×1.25 and ×1.4, step 0 being the mock-up; the spacings, the edge margin and the grid's gaps stay those of the mock-up, in dp (2026-10-17).

## What is coded

`themes/cosy/`: the palette (`CosyPalette`, its numbers in `palettes.json`), the sizes and the font by step (`CosySize`), the piece laid on its shadow, the sinking and the spring, the swelling window, the lifted element (`CosyPieces`), each contract component (`CosyTheme`), the sounds (`res/raw/cosy_*.flac`, credits in `sounds.json`). The font comes from `third_party/baloo2/` through `scripts/make_baloo_fonts.py`. `CosyPaletteTest` holds the two palettes' contrasts. Chosen in Settings › Interface, under the name "Cosy".

## What stays open

- The pass of `docs/THEMES.md`, the palette bench first: the spring, what the springs become when Android's animations are cut (Compose is supposed to skip them, not checked), the redrawn icons (their cost at a page's opening, those that clog at 24 dp), the messages band, which is hard to tell from its cream cards.
- The palette bench (`./run themes`) does not yet have the cosy: its numbers were read on the mock-up and the summer night is laid without having seen it.
- The Chart goes through the default theme's drawing (`DefaultDrawing`), its colours included, in the cosy's font: to draw in the register, as the retro made its own (`RetroDrawing`).
- A block that unfolds is not in the themes' contract: its slide stays the core's.
- Baloo 2 lacks the narrow no-break space (U+202F) that French puts before `; : ! ?`: Android takes it from a system font, like any character it lacks.
