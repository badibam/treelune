# The Cut paper theme

Designed 2026-10-18. A seventh theme, beside the default, the retro, the cosy, the swiss, the ink and wash and the metro: the app as sheets of coloured paper cut with scissors, laid a little askew on kraft. Fourth of the themes kept, following the ranking of `docs/theme-tracks.json`. Its identifier is `paper`.

Mock-up (screen of a zone): `docs/theme-tracks.html`, "Cut paper" card.

## What is decided

All the lines of 2026-10-18, in the order of the list of `docs/THEMES.md`.

1. **The register**: sheets of coloured paper, cut, laid a little askew on kraft, each on a hard shifted shadow (2 dp right, 3 dp down, without blur), with slightly irregular corners (a different radius at each corner, from 2 to 7 dp). It is neither round and plump, sunk into its shadow (the cosy: the paper is flat, thin, angular, and its tiles change colour one to the next), nor pixelated (the retro), nor calm (the ink).
2. **What it draws itself**: everything, dialogs and date and time selectors included, starting from the metro's pieces (same cut: `PaperPalette`, `PaperSize`, `PaperPieces`, `PaperDrawing`, `PaperTheme`).
3. **The font**: Bricolage Grotesque, the mock-up's, SIL Open Font License 1.1 without a reserved name; copied into `third_party/bricolage/` from Google Fonts, a variable font (optical size 12 to 96, weight 200 to 800, width 75 to 100). `scripts/make_bricolage_fonts.py` draws from it three fixed cuts, each at the optical size of what it writes: 500 at 14 the text, 800 at 16 the names, labels and figures, 800 at 28 the page and group titles. Measured 2026-10-18: all of Windows-1252 except `¤ ƒ` and the soft hyphen U+00AD; the narrow space U+202F, `← → ↑ ↓ ≤ ≥ ≠ ≈ − ×` present; `↔` absent (no text has it), `✓ ✗` to Android's fallback; the hyphen U+2010 that the hyphenation lays absent. No italic: a read text's is Android's slant (validated 2026-10-18).
4. **The palettes** (OKLCH, set on the bench; contrast aimed at 4.5, held at each 15° shift by `PaperPaletteTest`):
   - light, from the mock-up: the kraft `#d6b688` and its fibres at 115°, the cream sheet `#f7efe0`, the ink `#2a2118`, the coral `#e5674e` (accent and alert), the night blue `#2f4466` (buttons);
   - dark: a very dark brown cardboard in the background, the cream sheet become a black paper (warm anthracite) with cream ink, the coloured sheets kept, darkened by one notch;
   - the tags are the sheets' colours (mustard, teal, sky, lilac…), matte like a Canson paper; what is written on a coloured sheet is in ink or cream, whichever contrasts the most, as in the metro;
   - the hue turns the kraft, the coral and the night blue; states and coloured sheets kept.
5. **The geometry**: a step of 8 dp; `UI.Space` XS 4, S 8, M 16, L 24, XL 32, `EDGE` 16. Square cells, 12 dp between two columns and two rows, the place of the shadow and the tilt. The finger target at 48 dp. `drawingUnit` 1.
6. **The size**: Android's text size followed; the step (0 to 3) enlarges everything by ×1, ×1.12, ×1.25 and ×1.4, except the spacings, the edge margin, the grid's gaps, fixed in dp. The page title at 26 sp, 800.
7. **The components**:
   - the tilt: a tile leans from −1.5° to +1.5° (the mock-up goes to 2°: past that, the text blurs), an angle fixed by the identifier of the tool or the zone (addition to the contract, below); the cards and everything that carries a long text (note, bubble, field, dialog) stay straight, with their irregular corners and their hard shadow;
   - frames: the tool and zone tile, a sheet in its icon's colour, cream without colour; the icon-only tile, the same sheet, the icon on a hand-cut cream circle; the group title, a coral band tilted −1.5°, text in ink (the cream on the mock-up's coral only holds 3.1, the ink 4.8, measured 2026-10-18), followed by a torn cream strip (mock-up); the dialog and the floating window, a straight cream sheet; the user's bubble, a night blue sheet, the AI's a cream sheet; the interaction card, a cream sheet crowned with a coral ribbon;
   - buttons: primary, a night blue sheet on its hard shadow; secondary, a cream sheet with a 2 dp ink stroke; tertiary, the label alone 800; danger, a coral sheet; disabled, the drawing of its type without a shadow, paled; the floating button, a coral circle laid on a shifted mustard circle (mock-up);
   - input: the field, a white sheet with a 2 dp ink stroke, coral at focus; the box, a white square with uneven corners tilted −4°, filled with coral when ticked (mock-up); the switch, a strip of paper and a cut circle; the slider and the gauge, a dark strip and its mustard strip cut on the bias (mock-up); the hue band in saturated colours, the choice ringed; the tabs, the chosen a sheet that sticks out;
   - marks: the tag, a label of its colour tilted from −3° to +3° after its text; the waiting mark, a cut coral circle tilted 8°, empty; the separator, a thin torn strip; the textures, paper patterns (fibres, Canson weft, confetti), faint;
   - waiting: three confetti that fall one after the other;
   - messages: the toast and the snackbar, a night blue sheet, cream text;
   - date and time: the calendar on a cream sheet, the chosen day in a cut coral circle; the time, two sheets of figures between − and +;
   - page header and mark: the title on a cream strip tilted −2° (mock-up); the app's mark (`LauncherMark`) on a cut circle;
   - the bar of a selected text: a night blue strip, its cream words.
8. **The touch and the movement**: the pressed element flattens onto its shadow, its shift brought to 0, without a ripple; an appearance falls in 160 ms from a 3° tilt to its own, like a sheet laid down; the lifted element grows its shadow to 6 dp and tilts 2° more. Animations cut in Android: everything instantaneous.
9. **The icons**: Lucide (`LUCIDE`), redrawn by the theme as pieces of cut paper, from the strokes the core brick `core/icons/IconStrokes` reads, like the ink's brush: the stroke thickened to 2.6, its tips cut straight and its corners sharp, the curves cut into small straight segments (the scissors' facet), a hard shifted shadow; the colour, the one the core gives. Computed once per icon, on its grid of 24. The end of each facet is shifted a little, a hand that trembles, the same at each drawing of an icon (the randomness drawn from its name's length, as on the bench). Its numbers (the thickness, the facet, the angle of a corner, the shadow, the tremble) are in `icons.json`, which the icons bench (`themes/paper/bench/icons.html`) reads and saves, and which the theme reads; set on the bench 2026-10-18.
10. **The charts**: drawn by the theme (`PaperDrawing`). The bars are cut strips with uneven corners, tilted in turn by ±1.5°; the curve, a 3 dp stroke, its dots small cut circles; the goal in 2 dp brown dashes; the colours the user gives to the series kept. The mock-up's last bar in coral is not held: the core's drawing does not say which bar is today.
11. **The edit mode of the grid**: a cell, a rectangle in brown dashes on the kraft; the chosen tile, the lifted tile of line 8.
12. **The sound register**: its line of the sounds card (`docs/themes/sounds.md`): thick paper, without a note, a sharp attack; the scissors to confirm, a sheet laid on the kraft to open and close, a brief crumpled sheet to refuse; taken from BigSoundBank. Chosen by ear: `sounds-choice.json`.
13. **The texts**: name "Cut paper"; tagline "Coloured sheets cut with scissors, laid askew on kraft paper". No palette name.

## Addition to the contract

`ToolCardContainer` and `ZoneCardContainer` do not know which tile they draw: `ToolGrid` passes them their rank, and an angle drawn from the rank would change all the following tiles' at the removal of a single one. Decided 2026-10-18: a parameter `seed: String`, the identifier of the tool or the zone (an identity's, a settings entry's, the icon's name of an example tile), which the theme hashes. For each existing theme: the default, the retro, the cosy, the swiss, the ink and the metro ignore it, their tile staying as it is.

## What is coded

`themes/paper/`: the palette (`PaperPalette`, its numbers in `palettes.json`, set on the bench `./run themes`, "Cut paper · palettes", seen 2026-10-18), the sizes and the font by step (`PaperSize`), the sheet, its hard shadow, its cut and its tilt drawn from the identifier (`Laid`), the sheet pressed flat, the fall of a window and the lifted element (`PaperPieces`), Lucide cut with scissors (`PaperIcons`, the same numbers as `bench/scissors.js`), the charts (`PaperDrawing`), each contract component (`PaperTheme`). `PaperPaletteTest` holds the two palettes' contrasts at each 15° shift, and a tile's tilt, stable and bounded. Chosen in Settings › Interface, under the name "Cut paper".

What the code made precise among the decisions:
- A tool's icon on a tile is always on a cream circle, the tile being already in its colour; outside a tile (a list, a form), the circle takes the icon's colour.
- In the header bar, an icon-only button is a night blue circle, the cream icon (mock-up); elsewhere, the icon is bare.
- The group title also carries the group's buttons: the torn cream strip therefore passes under the title and its buttons, and not beside.
- A window closes at once, without a fade: its caller removes it.

## What stays open

- Nothing has run on a phone: the whole theme is judged there, the pass of `docs/THEMES.md` first, the cut icons at the top (their cost at a page's opening, those that clog; `IconStrokes` has not yet run on any device, with the ink neither).
- The hyphenation without U+2010 in the font: the hyphen Android lays in its place, to see on the phone.
- The textures' cells and tones are the metro's, copied: the paper patterns of line 7 (fibres, Canson weft, confetti) stay to draw, and their tones to set on the textures bench.
- A tile's name stays centred: the core places it (`UI.TileHeader`).
