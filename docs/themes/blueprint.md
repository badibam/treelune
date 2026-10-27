# The Architect's blueprint theme

Designed 2026-10-18. A seventh theme, beside the default, the retro, the cosy, the swiss, the ink and wash and the metro: the app as an architect's blueprint, fine lines on grid paper, dimensions and hatching. Chosen by the user outside the ranking of `docs/theme-tracks.json` (Cut paper, first, is done in parallel in another worktree). Its identifier is `blueprint`.

Mock-up (screen of a zone): `docs/theme-tracks.html`, "Architect's blueprint" card.

## What is decided

All the lines of 2026-10-18, in the order of the list of `docs/THEMES.md`.

1. **The register**: fine lines drawn with a ruler on grid paper, dimensions, hatching, a revision triangle for the waiting; the hand shows in the titles' lettering and in the line that spills out at each corner (line 7). It is neither pencil-sketched, nor trembling (the ink), nor black and white (the swiss), nor raised (the cosy).
   - **The dark is the blue print** (blue-white lines on blue, the mock-up), **the light is the diazo print** (blue lines on off-white paper): the theme keeps its look in both modes, and the light answers "the white lines on blue tire in the long run" (the AI's note, `docs/theme-tracks.json`).
2. **What it draws itself**: everything, dialogs and date and time selectors included, starting from the swiss's and the metro's flat pieces.
3. **The font**: two, the mock-up's, both under SIL Open Font License 1.1, copied into `third_party/` from Google Fonts; a script `scripts/make_blueprint_fonts.py` draws the `res/font/` from them.
   - **Architects Daughter** (Kimberly Geswein, no reserved name), the lettering: titles, tile names, labels, in spaced capitals. A single weight. It lacks (measured 2026-10-18) the arrows, `≤ ≥ ≠ ≈`, `✓ ✗`, `‰`, the soft hyphen (U+00AD) and the narrow no-break space (U+202F): the script adds these last two, its licence allowing it; the rest goes to Android's fallback, rare in a title.
   - **IBM Plex Mono** (reserved name "Plex"), all the rest: text, values, figures, like a plan's dimensions. 400 the text, 500 the values and the bold of a read text, the italic 400 for the italic. It covers Windows-1252, the arrows and `≤ ≥ ≠ ≈ − ×`; it lacks the narrow space and `✗` (measured 2026-10-18), left to Android's fallback, the reserved name forbidding to add them.
4. **The palettes** (OKLCH, set on the bench; contrast aimed at 4.5, held at each 15° shift by `BlueprintPaletteTest`):
   - dark, from the mock-up: blue paper `#1f4f8f`, ink `#eaf2ff` (7.2 measured), the secondary at 80% of the ink (5.3; at 70% it falls to 4.45); the gridding at 16% (large square) and 6% (small) of the ink;
   - light, the diazo: paper `#f7f5ef`, ink in the mock-up's blue (7.5 measured), the gridding in this ink, pale;
   - the alert (danger, interaction card): red in light; in dark, a highlighter yellow, the red not reading on the blue (`#e2231a` 1.7, `#ffd166` 5.7, measured 2026-10-18);
   - the tags and the icon colours are coloured pencils: pastel in dark, light enough to hold 4.5 on the blue, dark in light;
   - the hue turns the blue (paper, ink, gridding); states and tags kept.
5. **The geometry**: a step of 8 dp; `UI.Space` XS 4, S 8, M 16, L 24, XL 32, `EDGE` 16. Square cells, 12 dp between two columns and two rows, left empty by the grid, enough for the lines that spill out of two neighbouring tiles not to touch. The finger target at 48 dp. `drawingUnit` 1. The background gridding, small square of 8 dp, large of 40 dp, is not aligned on the tiles: the cell changes size with the screen's width.
6. **The size**: Android's text size followed; the step (0 to 3) enlarges everything by ×1, ×1.12, ×1.25 and ×1.4, like the swiss and the metro, except the spacings, the edge margin and the grid's gaps, fixed in dp.
7. **The components**:
   - the stroke: 1.5 dp, drawn with a ruler, without trembling; at each corner of a frame, the two sides cross and spill out a little (the line that spills out of an architect's sketch), which replaces the mock-up's cut marks; the spill's length is set on the bench, 0 included;
   - frames: the tile, an outline with a spilling stroke, a full background of the paper that hides the gridding; the card (variable, routine, line of a list), full, simple outline; the group title (`CardType.SECTION_HEADER`), in spaced lettering followed by a dashed centre line (mock-up); the dialog and the floating window, a title block (the title frame of a plan) with a double outline, the title in its top cell; the user's bubble, an ink solid, the AI's an outline; the interaction card, outline and hatching in the alert colour;
   - buttons: primary, an ink solid, the paper's label; secondary, an outline; tertiary and neutral, the label alone; danger, outline and label in the alert; disabled, a dashed outline, secondary label; on, the solid; icon-only, the icon without a frame in 48 dp; the floating button, a 56 dp detail circle with a double stroke, the icon at the centre;
   - input: the field, a stroke under the text, thicker at focus, the label in lettering above; the selection, the same field and a chevron, its menu a panel like the dialog; the box, a square, ticked with a tick; the switch, an outlined pill and a circle with a centre cross; yes / no, two segments stuck together, the chosen in a solid; the slider, a dimension line with its arrows, the same centre-cross circle under the finger; the gauge, an outlined rail filled with 45° hatching; the hue band in saturated colours; the tabs, the chosen underlined by a double stroke;
   - marks: the tag, an outline of its colour and its text; the separator, a fine dashed stroke; the handle, two strokes; the waiting mark, the revision triangle, empty on the paper, in the ink: a drawing's revision, not a warning; the timer mark, a circle with a centre cross; the coloured icon, drawn in its pencil; the textures, plan patterns (45° hatching, crosshatch, dots, bricks), faint, on the tile and over the whole background of a textured page;
   - waiting: the loading, a compass that traces a circle then erases it; the AI thinking, a dimension line tracing itself between its two marks;
   - messages: the toast and the snackbar, a small full title block;
   - date and time: in the dialog's frame, the days on the gridding, the chosen surrounded by a drawn circle, today underlined; the time, two − and + counters, like the swiss, without a dial;
   - page header and mark: the title in lettering in a frame, followed by the scale cell "1:1" (mock-up), this text among the theme's texts; the app's mark, the icon's monochrome layer in ink;
   - the bar of a selected text: an outlined title block, its words separated by fine strokes.
8. **The touch and the movement**: the pressed element fills with light hatching; an appearance, a 120 ms fade; the lifted element thickens its stroke and leaves its place in dashes, shifted 4 dp. Animations cut in Android: everything instantaneous.
9. **The icons**: Lucide (`LUCIDE`), redrawn by the theme from its strokes (`IconStrokes`, the core brick made for the ink): a 1.5 stroke instead of 2, and each open stroke extended at its two ends in its direction, the line that spills out of the frames; a closed stroke does not spill. Its length is set on the same bench as the frames', a few icons and a tile, in both modes: at 24 dp, the spill may not be seen or may read as a failed render, and only the eye says.
10. **The charts**: drawn by the theme (`BlueprintDrawing`). The bars outlined and hatched at 45°; the curve a fine stroke, its dots small crosses; the goal in a mixed stroke (dash-dot, an axis's); the axes in fine strokes, the labels in Plex Mono. The mock-up's dimension "← 7 j →" is not held: the core's drawing does not say the covered duration.
11. **The edit mode of the grid**: a cell, a rectangle in dashes with a spilling stroke; the chosen tile, the lifted tile of line 8.
12. **The sound register**: that of the sounds card (`docs/themes/sounds.md`): drawing tools, dry and fine; the mechanical pencil clicked (tick, step), the ruler laid (confirm), the revision stamp (refuse); BigSoundBank, the ruler looked for on Freesound. Chosen by ear: `sounds-choice.json`.
13. **The texts**: name "Blueprint"; tagline "Like an architect's blueprint: fine lines on grid paper, dimensions and hatching". No palette name.

## The threshold

Passed 2026-10-18: the bench (`./run themes`, "Architect's blueprint · palettes and stroke") seen and approved in both modes, its numbers in `palettes.json` (the light's secondary ink darkened to 0.5 to hold 4.5 at each shift). The stroke: 1.5 dp, the frames' spill 3 dp; the icons drawn at 1.5 on 24, each open end and each sharp corner (over 25°) extended by 1.2 on 24 — taken as is, to take again on the phone.

## What is coded

`themes/blueprint/`: the palette (`BlueprintPalette`, its numbers in `palettes.json`), the sizes and the two fonts by step (`BlueprintSize`), the spilling stroke, the hatching, the title block's double frame, the pressed element, the appearance and the lifted element (`BlueprintPieces`), Lucide redrawn (`BlueprintIcons`), the charts (`BlueprintDrawing`), each contract component (`BlueprintTheme`). `BlueprintPaletteTest` holds the two palettes' contrasts at each 15° shift. Chosen in Settings › Interface, under the name "Architect's blueprint".

What the code made precise among the decisions:
- The page title keeps the page's icon, in a frame cell before the title, like a title block cell; the scale cell "1:1" after it.
- The icons' corners: a bend of over 25° over a third of a unit, which does not grow by half when looked at on the double (`BlueprintIcons.corners`); a curve, even a tight one, therefore has no corner. These are the strokes `IconStrokes` reads, points every 0.08 unit, and not the SVG: the bench and the code do not find the corners the same way.
- The snackbar has no state mark: no state colour reads on the ink solid in either palette; its message says so.
- The hue band shows a saturated blue at each shift: the ink, too dark in light, would not stand out from one shift to the next there.
- The stroke is not rounded to the whole pixel (`blueprintStroke`): at 1.5 dp, the rounding made some strokes twice as thick as others.
- A chart's dot is a small cross, whatever its round or cross shape; a square, a triangle and a diamond keep their shape, in outline.
- The identifier of the thing a tile represents (`seed`, added to the contract by the cut paper) is not read: all the tiles are drawn the same, with the ruler.
- The lettering has a single weight: a read text's bold in lettering is only what Android simulates.

## What stays open

- Nothing has run on a phone: the whole theme is judged there, the pass of `docs/THEMES.md` first; the icons' spill at the top (a missed corner, a curve taken for a corner).
- The textures' cells and tones are the metro's, copied: the plan patterns of line 7 (45° hatching, crosshatch, dots, bricks) stay to draw, and their tones to set on the textures bench (`./run themes`).
