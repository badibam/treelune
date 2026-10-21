# The Metro signage theme

Designed 2026-10-18. A sixth theme, beside the default, the retro, the cosy, the swiss and the ink and wash: the app as metro signage. Each tool is a line, in its icon's colour; a zone is a station. Third of the themes kept (Swiss, Ink and wash, then Metro signage), following the ranking of `docs/theme-tracks.json`. Its identifier is `metro`.

Mock-up (screen of a zone): `docs/theme-tracks.html`, "Metro signage" card.

## What is decided

All the lines of 2026-10-18, in the order of the list of `docs/THEMES.md`.

1. **The register**: an off-white background, night blue ink, and the bright colours of the lines. A tile is a white panel with barely rounded corners, crowned with a thick stroke in its icon's colour (its line); the icon is in a circle of this colour, like a line's number. The page head is a night blue band, the title in white, like a station's name on its panel. A group title is a stop: a white circle ringed in night blue, the title in spaced capitals, then a night blue line. It is neither flat and colourless (the swiss), nor calm (the ink), nor raised (the cosy), nor pixelated (the retro).
   - **The mock-up's number is not held**: the theme does not know a tool's rank in its zone; the circle carries the icon in the number's place, which `ItemIcon` already allows (a pill larger than the icon).
   - **A tool without an icon colour** (the default case) is a grey line, like the mock-up's "Notes" card.
   - **The red is for the alert**: the waiting mark, the danger button, the interaction card. Never a decorative solid.
2. **What it draws itself**: everything, dialogs and date and time selectors included, starting from the swiss's flat pieces (same cut into files).
3. **The font**: Overpass, the mock-up's, drawn after the American road signs (Highway Gothic); SIL Open Font License 1.1, without a reserved name. Copied into `third_party/overpass/` from Google Fonts, a script `scripts/make_overpass_fonts.py` draws from it the fixed weights: 500 the text, 700 the names and labels, 800 the titles and the figures; from its italic, 500 and 700, for the italic of a read text. It covers Windows-1252, the arrows, `≤ ≥ ≠ ≈ − ×` and the narrow no-break space (U+202F), which the script therefore does not have to add (measured 2026-10-18); `✓ ✗` to Android's fallback, as in the swiss. Fixed-width figures (`tnum`) everywhere.
4. **The palettes** (OKLCH, set on the bench; contrast aimed at 4.5, held at each 15° shift by `MetroPaletteTest`):
   - light, from the mock-up: background `#f6f6f4`, white panel, night blue ink `#14213d` (14.8 on the background), secondary `#5b6170` (5.7 on the background, 6.2 on the white), band `#0b2545`, the white written on it (15.4); the red `#e2231a` is only a solid, the white written on it (4.7): in text on the background, it only holds 4.3 (measured 2026-10-18), and the danger button therefore takes a darker red;
   - dark, the metro at night: very dark night blue background, panels a night blue one notch lighter, off-white ink, the lines' colours kept a little lighter; the band stays darker than the panels;
   - the lines' colours are the theme's tags (`getTagColor`), bright as on a metro map, more saturated than in the other themes; what is written on a line colour (the icon in its circle, a tag's text) is white or night blue, whichever contrasts the most: the white on the mock-up's yellow only held 1.9, on the orange 2.5, on the turquoise 3.1 (measured 2026-10-18); at least 3 for the icon, 4.5 for a text;
   - the hue turns the night blue (ink, band, solids) and the red; states and lines kept, a line being known by its colour.
5. **The geometry**: a step of 8 dp; `UI.Space` XS 4, S 8, M 16, L 24, XL 32, `EDGE` 16. Square cells, 12 dp between two columns and two rows, left empty by the grid; 10 × 4 dp inside a tile, under its 6 dp stroke (mock-up). The corners at 4 dp wherever they are not round. The finger target at 48 dp. `drawingUnit` 1.
6. **The size**: Android's text size followed; the step (0 to 3) enlarges everything by ×1, ×1.12, ×1.25 and ×1.4, like the swiss and the ink, except the spacings, the edge margin, the grid's gaps, fixed in dp. The page title at 26 sp, 800, aligned left, going to the next line cut at the syllables.
7. **The components**:
   - the line's colour: the theme lays it under the tile (line of the addition to the contract), and what is drawn inside takes it again — the gauge, the ticked box, the bars and the curve of a chart in first-level ink; outside a tile, these pieces are in night blue;
   - frames: the tool tile, a white panel with 4 dp corners, a 6 dp stroke on top in its line's colour, a grey hairline of 1 dp under it (the mock-up's flat shadow); the icon-only tile, a solid in its line's colour with 10 dp corners, the icon on it; the zone tile, the same as a tool's, a zone being also a line of its colour on the home screen; the card (variable, routine, line of a list), the white panel without a stroke; the group title (`CardType.SECTION_HEADER`), the register's stop; the dialog and the floating window, a white panel, a 6 dp night blue stroke on top, on Android's veil; the user's bubble, a night blue solid pushed right, white text, the AI's a white panel; the interaction card, the panel with a red stroke;
   - buttons: primary, a night blue solid with 4 dp corners, white label 700; secondary, a 2 dp night blue outline; tertiary and neutral, the label alone 700; danger, red outline and label; disabled, the drawing of its type in the rail's grey, secondary label; on, the solid; icon-only, the icon without a frame in 48 dp; the floating button, a 56 dp night blue circle ringed in white then in night blue, an interchange station (mock-up), the icon white;
   - input: the field, a white panel with a 2 dp grey outline, night blue at focus, the label above in 700; the selection, the same field and a chevron, its menu a panel like the dialog; the box, a circle with a 3 dp outline, ticked full (mock-up); the switch, a pill with a 2 dp outline and a white ringed circle, on full; yes / no, two segments stuck together, the chosen in a solid; the slider, a 6 dp stroke and a stop (a white circle ringed 3 dp) that one moves; the gauge, a 6 dp stroke on a grey rail, ended by a stop (mock-up); the hue band in saturated colours, the choice ringed; the tabs, the chosen underlined by a 4 dp stroke;
   - marks: the tag, a full rectangle of its colour with 3 dp corners, its text white or night blue (line 4); the separator, a hairline of 1 dp; the handle, two 2 dp night blue strokes; the waiting mark, a red circle ringed in white with a white "!" 800 (mock-up); the timer mark, a night blue circle ringed in white; the coloured icon, in a full circle of its colour, without colour in a circle with a grey outline of the same size; the textures, on the panel under its stroke and on the page background, plan patterns (45° and 90° strokes, stop dots), faint, in night blue;
   - waiting: the loading, a dot that slides from stop to stop on a stroke; the AI thinking, three stops on a stroke that light one after the other;
   - messages: the toast and the snackbar, a night blue solid with 4 dp corners, white text, the snackbar's action in capitals 700;
   - date and time: the date in the dialog's frame, the month and the year in capitals 800 between two chevrons, the days in aligned figures, the chosen in a night blue circle, today in a ringed circle; the time, like a departures board: hours and minutes in white figures 800 on two night blue panels, each between two − and + buttons, without a dial;
   - page header and mark: the night blue band under the bar, the white title at the left, the page's icon in the circle of its line before it, the subtitle in white at 75%; the app's mark, the icon in its colours (`LauncherMark`), its night blue going to the band;
   - the bar of a selected text: a night blue solid, its white words 700 separated by white hairlines, a touched word in white, its text night blue.
8. **The touch and the movement**: the pressed element darkens by 10% of night blue, without a ripple; an appearance slides 8 dp upward in 150 ms, like a train entering the station, and closes in a 100 ms fade; the lifted element takes a flat 4 dp shadow and rises 2 dp. Animations cut in Android: everything instantaneous.
9. **The icons**: Lucide (`LUCIDE`), as is. The mock-up draws them at 2.4 instead of 2: it would take own icons (`OWN`) and a script that thickens them, a cost that is not worth the difference.
10. **The charts**: drawn by the theme (`MetroDrawing`). A curve is a metro line: a 4 dp stroke with rounded joints, its dots stops (white circles ringed in its colour); the bars with 3 dp top corners; the goal in 2 dp night blue dashes (mock-up); the axes in hairlines of 1 dp, the labels in aligned figures 700. The series colours the user gives are kept; the first-level ink takes the line's colour in a tile. The mock-up's last full bar and the others pale are not held: the core's drawing does not say which bar is today.
11. **The edit mode of the grid**: a cell, a rectangle with 4 dp corners with a dashed grey hairline; the chosen tile, the lifted tile of line 8.
12. **The sound register**: the metro without copying any of it: chimes of two or three soft notes to confirm and enter, the dry clack of a gate to tick and step, a brief beep to refuse. Its main source and what sets it apart from the other themes are on the sounds card (`docs/themes/sounds.md`): the chime and the beep by recipe (`synth`), the clack taken from BigSoundBank. By ear (`sounds-choice.json`), relays, a thermostat and a keyboard key took the chime's and the beep's place.
13. **The texts**: name "Metro"; tagline "Like metro signage: each tool a line in its colour, each zone a station". No palette name.

## Addition to the contract

`ToolCardContainer` and `ZoneCardContainer` only receive the icon's colour in their texture (`ItemTexture.color`), so only when a texture is laid; without it, the register's colour stroke is impossible. Decided 2026-10-18: a parameter `color: TagColor?` to these two pieces, which the core already has at hand (`UI.ToolCard`). For each existing theme: the default, the retro, the cosy, the swiss and the ink ignore it, their tile staying as it is.

## What is coded

`themes/metro/`: the palette (`MetroPalette`, its numbers in `palettes.json`, set on the bench `./run themes`, "Metro · palettes", seen and approved 2026-10-18), the sizes and the font by step (`MetroSize`), the panels, the pressed element, the appearance and the lifted element (`MetroPieces`), the charts (`MetroDrawing`), each contract component (`MetroTheme`), the textures' cells (`textures/`). `MetroPaletteTest` holds the two palettes' contrasts at each 15° shift. Chosen in Settings › Interface, under the name "Metro".

What the code made precise among the decisions:
- The group title's line passes under the title, starting from the middle of the stop, and not after it: the section header also carries the group's buttons (`ZoneScreen`, `MainScreen`), which take the place to the right of the title.
- A tile without an icon colour has its grey line, but what is drawn inside it (gauge, box, chart) stays night blue: a grey would say nothing more.
- A window closes at once, without the 100 ms fade: its caller removes it, and the theme does not see it leave.
- An icon's circle is a third larger than the asked place, the icon at four fifths of this place (`BADGE_SCALE`, `ICON_IN_BADGE`).
- The hue band shows the red at each shift, like the swiss: the night blue, too dark, would not stand out from one shift to the next there.

## What stays open

- Nothing has run on a phone: the whole theme is judged there, the pass of `docs/THEMES.md` first.
- The textures' cells and tones are the swiss's, copied: the plan patterns of line 7 (45° and 90° strokes, stop dots) stay to draw, and their tones to set on the textures bench (`./run themes`).
- A tile's name stays centred: the core places it (`UI.TileHeader`), as for the swiss.
