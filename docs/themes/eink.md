# The E-reader theme

Designed 2026-10-19. A ninth theme: the app as on an e-reader. Pure black and white, no grey, patterns instead of colours, and nothing that moves. First of the on-hold registers by the user's note (`docs/theme-tracks.json`), tied with Notebook and Terminal, chosen because no theme covers this use: an e-ink screen, full sun, the strongest contrast.

Mock-up (screen of a zone): `docs/theme-tracks.html`, "Monochrome / e-ink" card.

## What is decided

All the lines of 2026-10-19, in the order of the list of `docs/THEMES.md`.

1. **The register**: a white background, black ink, 2 dp frames without rounding; the group titles in capitals, underlined by a 3 dp stroke; the top of the page band inverted, black with white text. It is neither the swiss (no red, framed tiles), nor the retro (no visible pixel), nor decorative: no soft shadow, no transparency, no gradient, no movement.
   - **No grey**: everything the core gives in colour becomes a pattern (hatching, dots, gridding) or a stroke. A screened grey reads badly when small, and an e-ink screen renders no other.
2. **What it draws itself**: everything, dialogs and date and time selectors included; nothing of Material (no ripple, no tinted surface, no elevation).
3. **The font**: Atkinson Hyperlegible Next (Braille Institute, SIL Open Font License 1.1, without a reserved name), copied into `third_party/atkinson-next/` (the variable font, its italic, `OFL.txt`, `VERSION`). A script `scripts/make_atkinson_fonts.py` draws from it four fixed weights: 400 the text, 600 the labels, 700 the names, 800 the titles and the figures; from the italic, 400 and 600, for the italic of a read text. Measured 2026-10-19 on Google Fonts' version: weight axis 200 to 800, Windows-1252 except `µ`, `≤ ≥ ≠ ≈ − ×`, the fixed-width figures (`tnum`); it lacks the narrow no-break space (U+202F), which the script adds (an empty space of one eighth of an em), the arrows `← → ↑ ↓` and `✓ ✗`, left to Android's fallback: only the command lines of an AI message write them (`ChatComponents`).
4. **The palettes**: light, background `#ffffff` and ink `#000000`; dark, the inverse. Contrast 21 in both modes.
   - The secondary ink is the full ink: the hierarchy passes through the weight and the size.
   - The state colours are in ink: a state says itself by its icon and its text.
   - Each tag (and icon) colour is a pattern: red, full; orange, `/` hatching; yellow, sparse dots; green, gridding; turquoise, horizontal lines; blue, `\` hatching; violet, crossed hatching; pink, dense dots; grey, an empty square.
   - The hue has nothing to turn: the theme declares it ("Addition to the contract") and Settings › Interface hides its band.
   - `EinkPaletteTest` holds the two palettes' contrasts, as for every theme.
5. **The geometry**: a step of 8 dp; `UI.Space` XS 4, S 8, M 16, L 24, XL 32, `EDGE` 16. Square cells, 8 dp between two columns and between two rows, left empty by the grid. A tile's content starts 8 dp inside its 2 dp frame. The finger target at 48 dp. `drawingUnit` 1.
6. **The size**: Android's text size followed; the step (0 to 3) enlarges everything by ×1, ×1.12, ×1.25 and ×1.4, except the spacings, the edge margin and the grid's gaps, fixed in dp; the strokes rounded to the whole pixel. The running text at 16 sp, one step above the other themes: it is a reading theme.
7. **The components**:
   - frames: the tile and the card, a 2 dp frame; the dialog and the floating window, a 2 dp frame on a full black shadow shifted 6 dp down and right, without blur; the user's bubble, an ink solid with the background's text, pushed right; the AI's, a 2 dp frame, at the left; the interaction card, a double frame (2 dp, a 2 dp void, 2 dp);
   - buttons: primary in an ink solid, the background's label in 600; secondary in a 2 dp outline; tertiary and neutral, the underlined label; danger in a 2 dp outline, the label in 800 preceded by its icon; disabled, a dashed outline, without reaction to the touch; on in an ink solid; icon-only, the icon without a frame in 48 dp; the floating button, a 56 dp ink square surrounded by a 3 dp edging of the background;
   - input: the field, a 2 dp frame, 3 dp at focus; the selection, the same frame and a chevron, its menu a panel like the dialog, the chosen option inverted; the box, a square with a 2 dp outline, ticked an ink solid and a tick of the background; the switch, a rectangle with a 2 dp outline and a square thumb, on full of ink; yes / no and the tabs, segments stuck together, the chosen inverted; the slider, a 2 dp rail and an ink square; the gauge, a 2 dp frame filled with ink; the hue band, hidden (line 4);
   - marks: the tag, a 12 dp square in its colour's pattern before its text, the whole in a 1.5 dp frame; the separator, a 2 dp hairline; the handle, two ink bars; the waiting mark, an ink triangle at the top right corner; the timer mark, a small full square that lights and goes out each second, by jump; the coloured icon, the icon in ink and an 8 dp square in its pattern at the bottom right corner, the same place without colour; the textures, in ink alone, without the icon's colour, the faint more sparse than the strong;
   - waiting: the loading and the AI thinking, three squares that fill one after the other, by jump every 400 ms;
   - messages: the toast and the snackbar, an ink solid with the background's text surrounded by an edging of the background; the snackbar's action in underlined capitals;
   - date and time: the date in the dialog's frame, the days on 7 columns, the chosen inverted, today framed 2 dp; the time, two counters between − and + buttons with a 2 dp outline, without a dial;
   - page header and mark: the ink band carries the title and its buttons in the background; the app's mark, the icon's monochrome layer (`make_launcher_icon.py`) in the background on the band;
   - the bar of a selected text: an ink solid, its words of the background separated by hairlines of the background, the touched word inverted.
8. **The touch and the movement**: nothing moves. The pressed element inverts at once; an appearance and a closing happen at once, without a fade; the lifted element takes the dialogs' full shadow (4 dp) and shifts 4 dp up and left. Animations cut in Android: only the jumps of the waiting and the timer marks stop, on their full state.
9. **The icons**: Lucide (`LUCIDE`), as is.
10. **The charts**: drawn by the theme (`EinkDrawing`). A series colour becomes its pattern for a solid (bar, slice) and a dash pattern for a stroke, unless the core gives one; a blend (`Mix`) and an opacity, a regular dot pattern more or less dense; what is missing, hatched; the ink levels, strong a 2 dp stroke, medium 1 dp, faint dotted 1 dp.
11. **The edit mode of the grid**: a cell, a 1 dp dashed square; the chosen tile, the lifted tile of line 8.
12. **The sound register**: none, an e-reader makes no noise (decided 2026-10-21).
13. **The texts**: name "E-reader"; tagline "Like an e-reader: pure black and white, patterns instead of greys, nothing that moves". No palette name.

## Addition to the contract

Decided with the theme, for the eight others as for it (`docs/THEMES.md`, "Adding a piece to the contract"): a theme declares whether it has a hue to turn (`ThemeContract.hueShifts`), true for the eight others; Settings › Interface shows the hue band only if it has one, the saved shift staying as it is.

The choice of a colour does not ask for it: it passes through pieces the theme draws (`Tag` with the colour's name, `ItemIcon`), which show the pattern here.

What the core keeps: the veil behind a dialog is Android's, a translucent black, common to all themes. It is the screen's only grey.

## What is coded

`themes/eink/`: the palettes and the patterns (`EinkPalette`: two colours per mode, without `palettes.json`, nothing being to set; each pattern drawn without smoothing in a tile of whole pixels), the sizes and the font by step (`EinkSize`), the pressed element, the frames and the full shadow (`EinkPieces`), the charts (`EinkDrawing`), each contract component (`EinkTheme`), the textures' cells (`textures/`, the swiss's, on the 8 step). The bench, `themes/eink/bench/screens.html`, seen 2026-10-19. `EinkPaletteTest` holds the black and white, the contrast, and dashes and a density proper to each colour. Chosen in Settings › Interface, under the name "E-reader".

What the code made precise among the decisions:
- The ink band carries only the page's title, its icon and its subtitle: the row of buttons above (back, actions) is laid by the core outside any theme piece, and stays on the background.
- A texture is in full ink at both intensities (`textures.json`, its two tones brought to the ink); the discreet one erases one pixel of one dp out of two of its pattern (a checkerboard of the background laid over it), to stay in two colours.
- A group's title is in capitals, a tile's name in bold: both are a `HEADING`, which the theme tells apart by the section header it is in.
- A tile set aside while another moves is covered with a pattern of the background, instead of being paled.
- The timer mark lights and goes out each second; the three waiting squares fill every 400 ms.

## What stays open

- The row of buttons in the ink band, as in the mock-up: an addition to the contract (the piece that carries the top-of-page buttons), to decide.
- The pass of `docs/THEMES.md`, the patterns first: two colours to the pixel, the textures' discreet one.
