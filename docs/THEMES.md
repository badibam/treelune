# Themes

A theme draws the app: every visual component of `UI.*` goes through it (`core/themes/ThemeContract.kt`), the screens laying out only the layout (Row, Column, Box). This doc says what a theme is in the app, then the recipe to make one: the course of its design, what is decided before coding, its files, what checks what, its sounds, the traps seen on the phone and the pass on the device. What belongs to one theme (its register, its choices, what stays open) lives in its spec, `docs/themes/<id>.md`, and the map of the themes' sounds in `docs/themes/sounds.md`.

## What a theme is in the app

- Nine themes, registered in `ThemeScanner`: the default (Material), the retro (pixel art, `docs/themes/retro.md`), the cosy (round and plump, each piece on its shadow, `docs/themes/cosy.md`), the Swiss (a strict grid, black and white and a single red, `docs/themes/swiss.md`), ink and wash (washes on a cream paper, every stroke by brush, `docs/themes/ink.md`), the metro (each tool a line in the colour of its icon, `docs/themes/metro.md`), cut paper (sheets of colour laid askew on kraft, `docs/themes/paper.md`), the architect's blueprint (thin strokes running past the corners on a squared paper, `docs/themes/blueprint.md`) and the e-reader (pure black and white, patterns in place of colours, nothing that moves, `docs/themes/eink.md`), the last eight drawing everything themselves. The other registers mocked up, on hold, are in `docs/theme-tracks.html`, to read again before proposing one.
- A theme names itself what belongs to it, its name and its palettes, in its own texts (`themes/<id>/strings.xml`, read by `s.theme()`): the core knows none of them.
- A theme has light and dark colours (`PaletteMode`). The user chooses in Settings › Interface the theme, the mode (light, dark, or as the phone is, followed when it changes), the hue (a shift from 0 to 359° that turns all the theme's colours on the OKLCH circle, their lightness and chroma kept, except those of the states and the tags: `Appearance.hueShift`, `Oklch`, `HueShiftTest`; a theme without hue declares it, `hueShifts`, and the band is not shown) and the size step (0 to 3, which a pixel theme applies to its whole factor, upward only). A theme whose sizes follow the step reads it through `CurrentTheme.drawnStep()`, which is 0 on a tile's icon: the grid sizes that icon from the screen's width, so the waiting and stopwatch marks drawn on it (`UI.MarkedIcon`) keep their proportion to it at every step; a pixel theme, whose icons grow with its factor, reads `sizeStep`. `AppConfigManager` applies them to `CurrentTheme` (`Appearance`) at each reading of the settings, and each application is written to the log with its reason ("Theme applied").
- The screen shows each choice as soon as it is made and goes back to the saved appearance if left without saving. For the current screen to survive the change of theme, `MainActivity` moves the app's content from one screen frame (`FullScreen`) to the other without recreating it (`movableContentOf`): a `FullScreen` therefore never composes its content apart (no `BoxWithConstraints`, whose moved content would no longer follow the theme), and no field keeps the focus during the move.
- The grid of tiles takes from the theme the width of a cell (`gridCellPx`), the height of a row (`gridRowPx`, the width in the default theme; never less than a tile's header needs, measured in the theme's texts at the size step and at Android's text size: the rows grow with the text rather than cut it, `GridLayout`) and the gap between two columns and two rows (`gridColumnGapPx`, `gridRowGapPx`, zero in the default theme, which keeps the gap inside its tiles).
- A zone and a tool can have a texture, which every theme draws in two places: in the frame of their tile (`ToolCardContainer`, `ZoneCardContainer`), and over the whole background of their page (`PageGround`, which the stack of places calls around each page), hiding what the theme lays elsewhere on its screen background. The page head bears none, nor a config page, a form or a window over the page. A theme keeps its cards and the containers of a long text solid, so that no pattern shows under them.
  - The core knows only the fifteen names of `Texture`, shapes and not materials, stored apart from the theme so that a choice survives a change of theme: a tool's `texture` in its config, a zone's in its column; a tool does not take its zone's, the texture serving to tell the tools of a zone apart.
  - Each theme draws all of them: a pattern is an SVG cell repeated (`TextureCell`: a `viewBox` in whole units from 0 0, `<path>`, `<rect>` and `<circle>`, a stroke if it has a `stroke-width`), whole `<rect>` scaled by a whole factor in a pixel theme; `ICON` has no file, its cell being the thing's icon twice, staggered. The common painter is `rememberTextureBrush`, which the theme gives its surface's colour and its way of laying (`TextureStyle`).
  - A tone is a lightness gap from the surface the pattern lies on, two per mode and per pattern in the theme's `textures.json` (`TextureTones`), one per intensity, tuned on the textures' bench so that every pattern shows as much; with an icon colour the pattern takes its hue at that lightness, never a transparency. The intensity is a single setting of the app, `texture_intensity` (`SUBTLE` by default, `MARKED`), for the tile as for the page.
- The benches open through `./run themes` (`themes/bench.html`): the retro's tunes and saves its two palettes, the default's shows its colours at any shift, copied from `DefaultTheme` and held to them by `DefaultColorsBenchTest`, the textures' tunes and saves the tones of each theme's patterns, and the listening board chooses each theme's sounds ("The sounds").

## The course

0. **Where to work**: before any work on a theme, the AI asks the user whether it is done in a worktree, or on the current branch.
1. **The register**: a mock-up of a zone's screen, added to `docs/theme-tracks.html` beside the others, and chosen by eye.
2. **The decisions**: the spec, designed in French in `docs/design/<id>-theme.md`, filled line by line from the list below; once the theme is coded, it goes in English to `docs/themes/<id>.md`, which stays its reference.
3. **The threshold**: each line of the list bears a decision, and the theme's bench is seen in both modes. Nothing on the phone before coding.
4. **The code**: the theme's files, then its registration in `ThemeScanner`.
5. **The pass on the phone**, then what stays open in the spec.

The sounds are searched for in parallel, blocking nothing: the theme starts silent (`sound()` returns null), and its sounds come when they are chosen.

The AI proposes all the lines of the list at once, each with its proposed decision and what grounds it; the user answers in one go, coming back only to the lines they change. It makes what to judge with (mock-up, bench, listening board, screenshots) and computes what can be measured (contrasts, the font's coverage, licences). The user settles everything that is seen or heard: the AI does not see the phone and hears nothing, it never validates a colour or a sound.

## The decisions before the code

Each line bears a decision at the threshold; "as the default theme" is one, if it is written. An empty line blocks the threshold.

Before proposing the look of a piece, the AI reads its piece in `ThemeContract` and says what the core imposes: where it places the content (a tile's name, `UI.TileHeader`), the colours it gives (that of an icon to a texture's pattern, those of a drawing), what is common to all themes (the floating window and its veil). A look the contract does not allow is a proposal to add to the contract ("Adding a piece to the contract"), named as such, never a decision of the theme.

1. **The register**: the mock-up, and what the theme is not.
2. **What it draws itself**: everything, or pieces taken from the default theme, named.
3. **The font**: which one, its licence (free, embedded in `assets/fonts/`), its copy (`third_party/` and a script that draws the `res/font/` from it, or a neighbouring project checked by `check_font_copy`), its coverage (Windows-1252, the narrow no-break space U+202F, the arrows, `≤ ≥ ≠ ≈`), its weights, its bold and its italic in a text read (`ThemeContract.emphasis`), and what renders a character it does not have, validated by the user (a fallback). A missing character is added by the script when the licence reserves no font name (Archivo's narrow space, `make_archivo_fonts.py`); a reserved name leaves the fallback.
4. **The palettes**: light and dark, a handful of OKLCH numbers from which everything derives; the contrasts aimed at (4.5 for each ink on its background, the secondary included, and for the state colours); the tags at a common lightness and chroma; what the hue turns. The contrasts hold at every hue shift, not at shift 0 alone: at equal OKLCH lightness, the measured contrast changes with the hue (the Swiss red held 5.4 at 0 and 4.0 at 120°).
5. **The geometry**: the grid of tiles (cell, row, gaps), the named spacings and the edge margin (`UI.Space`, `EDGE`), the finger target (48 dp at the least), the drawing unit (`drawingUnit`).
6. **The size**: Android's text size followed or not, and what each step from 0 to 3 does. A step enlarges what is read and what is touched (text, icons, targets, frames), never the air around: the named spacings, the edge margin and the grid's gaps keep the length of step 0 (decided on 2026-10-17: in retro at size 3, the margins ate the room gained).
7. **The components**, one line per family of the contract: frames (card, tile, dialog, floating window, bubble, interaction card); buttons (normal, disabled, active, floating); input (field, selection, checkbox, switch, yes/no, slider, gauge, hue, tabs); marks (tag, separator, handles, waiting and stopwatch marks, coloured icon, tile and page background textures); waiting (loading, AI thinking); messages (the snackbar, which also draws the toasts: the core holds their queue and their place); date and time pickers; page header and the app's mark; the bar of a selected text.
8. **Touch and motion**: the element pressed, an appearance, the element lifted, and animations turned off in Android.
9. **The icons**: Lucide (`LUCIDE`), or its own (`OWN`), with their conversion script and their file of touch-ups.
10. **The charts**: drawn by the theme, or the default's (`DefaultDrawing`).
11. **The grid's edit mode**: the cells and the chosen tile (`GridCell`, `GridTile`).
12. **The sound register**: the theme's line on the sound map (`docs/themes/sounds.md`: material, note, attack, main source), specified signal by signal; a theme absent from the map enters it first, beside the others. Their choice is made apart.
13. **The theme's texts**: its name, its sentence (`tagline`, read by the README) and the names of its palettes, in English and in French.

## Adding a piece to the contract

A new piece (the bar of a selected text, for instance) is decided for each existing theme before being coded, as a line of the list above: its line in the theme's spec (`docs/themes/<id>.md`, and for the default theme, which has none, in the piece's comment), the line of the list of decisions, and that of the pass on the phone. The core keeps what the piece holds, where it is placed and when; the theme has only its look. The bench of a theme that has a board of components (the retro) draws it too. A piece Android drew until then (a bar, a picker) is replaced by what Compose lets one supply publicly: what it keeps internal stays Android's, and the spec's line says so.

## A theme's files

Everything is in `themes/<id>/` (under `app/src/main/java/app/treelune/`), except what Android wants elsewhere.

| File | What it holds | Where it comes from |
|---|---|---|
| `<Id>Theme.kt` | the object that implements `ThemeContract`, registered in `ThemeScanner` | by hand; its pieces in other files of the folder (`RetroFrame`, `CosyPieces`…) |
| `<Id>Palette.kt` | the colours derived from the numbers, in both modes and at any hue | by hand |
| `resources/app/treelune/themes/<id>/palettes.json` | the palettes' numbers, their only source | the bench, which saves them |
| `bench/` | the bench: a zone's screen and a board of components at a phone's resolution, in the theme's font, both modes, the hue tried without being saved | by hand, linked from `themes/bench.html`; a smooth theme in HTML and CSS, both modes side by side, its contrasts measured at every shift (the Swiss's as a model), a pixel theme in canvas (the retro's) |
| `resources/app/treelune/themes/<id>/textures/*.svg`, `textures.json` | one SVG cell per texture pattern (`ICON` apart), and the tones of each by mode and by intensity | the cells by hand, the tones at the textures bench (`texture-bench.html`), which saves them |
| `strings.xml`, `strings-fr.xml` | `name`, `tagline`, the palettes' names | by hand |
| `icons/` (`OWN` theme) | one SVG per Lucide icon, all of them | a script (`pixelize_icons.py` for the pixel), plus the touch-ups by hand |
| `res/font/<font>_*.ttf`, `assets/fonts/<font>/` | the font and its licence | a script from `third_party/` or a neighbouring project |
| `sounds-choice.json` | families, preselections, choice per signal, gain, recipes | the listening board |
| `res/raw/<id>_<signal>.flac`, `sounds.json` | the sounds played and their credits | `scripts/theme_sounds.py make <id>` |
| `test/.../themes/<id>/<Id>PaletteTest.kt` | the contrasts of both palettes | by hand |
| `docs/themes/<id>.md` | the register, the decisions, what stays open | the design, `docs/design/<id>-theme.md` in French until the theme is coded |

A theme also registers outside its folder: in `ThemeScanner`; in the list of themes of `TextureCellTest` and `TextureTonesTest`; in `THEMES` of `themes/texture-bench.html`, with its surfaces; by a link to its bench in `themes/bench.html`; in the READMEs, through `scripts/make_readme.py`; and as retained, on its card of `docs/theme-tracks.html` and in the list of `docs/reference.md`.

## What checks what

| Obligation | What holds it |
|---|---|
| Each piece of the contract supplied | the compiler (`ThemeContract`) |
| The theme offered in Settings › Interface and the README | its registration in `ThemeScanner`, read by `AppSettings` and `make_readme.py --check` |
| `name` and `tagline` in both languages | `check_string_locales.py`, `check_string_keys.py`, `make_readme.py --check` |
| Tu in the French texts | `check_french_address.py` |
| An `OWN` theme has all of Lucide's icons | `generate_icons.py`, which refuses an incomplete theme |
| The font's copy up to date | `check_font_copy.py` (Cartouche); the font's script (Baloo 2) |
| A cell for each texture pattern, nothing else in the folder, in whole pixels for a pixel theme; the two tones of each pattern in both modes | `TextureCellTest`, `TextureTonesTest` (the list of themes is written there) |
| The palettes' contrasts, at every 15° shift | `<Id>PaletteTest`, to write with the theme (`SwissPaletteTest` as a model) |
| The colours of a preview bench equal to the code's | a test like `DefaultColorsBenchTest`, if the bench copies instead of reading `palettes.json` |
| Each sound wired to its signal, no file forgotten | `ThemeSoundsTest` |
| The sounds, their credits and the choice in agreement, their licences accepted | `theme_sounds.py check`, in `./run test` |
| A screen names its spacings, the theme measures them | `check_spacing.py` (the themes' code is exempt from it) |
| The rest: what is seen, heard, touched | the pass on the phone |

## The sounds

The core sends a signal from a closed list (`UISignal`: confirm, enter, back, open, close, check, step, end of list, refusal) and the theme says which sound answers it (`ThemeContract.sound`), or none (`docs/UI.md`, "Interface sounds"). A sound is in FLAC, stored as is in the APK (`noCompress += "flac"`, `app/build.gradle.kts`).

- **The sources** are listed once for all themes in `themes/sound-sources.json`: title, author, licence, page, archive and its sha256 sum, and the rule that sorts its sounds into families. Only CC0 1.0 and CC BY 4.0 are taken: free for F-Droid, CC BY asking only for the credit `sounds.json` gives. A source is an archive taken whole (Kenney's eight sound libraries, CC0), or a list of sounds taken one by one, each with its address, its sha256 sum and its page, and its author and licence when they are not the source's: recordings from BigSoundBank (CC0), and sounds from Freesound, taken through their public Ogg preview, which asks for no account.
- **A finish** (`finish` in `sounds-choice.json`, its steps in `FINISH` of `theme_sounds.py`) holds together sounds from different sources: a channel, the frequency, the lows cut, the leading silence, the colour, a small room, the length and its fade, and the volume each finished sound is brought to, measured on its loudest 50 ms, in place of the gain applied to the raw sound; a step absent does not apply. `make` shows each sound's measures (length, loudest, floor, peak, centre of the spectrum) and refuses a sound that would clip. Each theme's register and main source are in `docs/themes/sounds.md`.
- **A recipe** (in `sounds-choice.json`) makes a sound no source has: a sound touched up (higher, cut), several mixed, or a sound made from nothing (`synth`), through a chain of sox effects; its credits are those of what it mixes.
- **The flow**:
  1. The AI: `scripts/theme_sounds.py library` downloads the sources into `tmp/sound-library/`, renders the recipes, writes the catalogue.
  2. The user, on the board (`./run themes`, "Themes' sounds", which lists each theme, silent ones included, then the theme's name): the families that suit the theme, each listened to in one go.
  3. The user, signal by signal in the families retained: listen, preselect, choose (★), compare with each other theme's sound; the theme's gain in dB, or its finish and its volume ("Finish" tab, each sound then heard finished by the server, its measures under the bar), an offset per signal; "▶ the theme" plays the chosen sounds in a row. A recording of several gestures shows its takes, where it rises from its floor (`find_takes`): a preselected take becomes a recipe that cuts it, its start and length set by hand beside it (a whole sound preselected has them too, its first change making it such a recipe), and its denoising: the recording's noise, learnt on its quietest 0.3 s away from its first and last half second, taken out at the strength given (`denoise`, sox's `noisered`), and its fade out in ms for a take cut while it still sounds (`fade_ms`). `sound-bench.html` without a theme compares what each theme plays today, a signal in all themes or a theme's signals in one go. Saving writes `sounds-choice.json`: a session resumes where the previous one stopped.
  4. A signal that finds nothing: the AI adds a source, or writes a recipe, and runs step 1 again.
  5. The AI: `scripts/theme_sounds.py make <id>` writes the FLAC files (16 bits, what the phone's decoder renders) and `sounds.json`; a signal without a choice has no file.
  6. The AI: `sound()` returns `R.raw.<id>_<signal>` for each file.
  7. The user, on the phone: a level to take up again is corrected at the gain or the offset, then step 5.

## The traps seen on the phone

Each one cost a round trip on the device; the code of the three themes respects them, a new theme too.

- **A theme's resource is read by its absolute path** (`getResourceAsStream("/app/treelune/themes/<id>/palettes.json")`): the release renames and moves the classes, and a relative path found nothing any more, crashing the settings screen. Shows only in release.
- **A component survives any size its parent gives it**, even under its minimum: the retro frame squeezed under two cells computed a negative number of pieces and crashed (the conversations' history, a long section title at a large size).
- **A lifted element keeps the same structure as when laid** (`Lifted`, `ReorderItem`, `GridTile`): only the settings change, never one branch for the other; otherwise its content is recreated at the moment of lifting, the handle with it, and the drag that lifted it is cancelled (ink and Swiss no longer reordered anything).
- **A gesture reads the last function passed to it** (`rememberUpdatedState`): the retro slider kept the `onValueChange` of its first drawing and rewrote the settings of that time.
- **The inks hold a contrast of 4.5 on their background in both modes**, the secondary included (descriptions, labels), and the text in a button takes the button's ink: a pale ink on the primary colour could no longer be read.
- **The default theme names each of Material's colour roles**: those it leaves out, Material fills with its purples (a chosen segment, a menu, a date picker).
- **Everything the finger takes is 48 dp at the least** (a button, a field, a checkbox, a list row; an icon-only button, a square of that size); three cells were 28 dp.
- **A word wider than its line is cut at syllables** (`Hyphens.Auto`), never at any letter. A heading (a tile's name, a section's title) is broken between words first, hyphenated only once a word does not hold on its line (`HeadingBreak`): evening its lines, Android wrote « Day's calor- / ies ».
- **A character missing from the font** is taken by Android from a system font: to check on the French text, the narrow space (U+202F) first.
- **A floating window holds above the keyboard itself** (`FloatingDialog`): left to Android, its buttons went under the keys.
- **The hue band shows saturated colours**: in the grey of the frames' background, one shift could not be told from another.
- **Changing theme recomposes the whole screen**: a Compose state of a global object read for the first time during this recomposition crashed the home screen. The change of theme is therefore in the pass.

## The pass on the phone

A fixed route, on the app filled by the demo, first in the worst cases: both modes and a shifted hue; the largest size step with Android's text size at its maximum; "one column"; once with animations turned off in Android. Sounds on throughout.

1. The home screen: the app's mark, the zone tiles, the floating button, the grid's edit mode.
2. A zone: tiles of each display (icon, line, compact, extended, full), the cards of the variables and the routines; textured tiles, each pattern, without and with an icon colour, at both intensities, and the header of a textured page.
3. A tool of each type, its screen and its config in pages; a Chart.
4. The windows: a confirmation, a long floating window with the keyboard open, the date and time pickers, a toast and a snackbar.
5. The chat: the bubbles, the AI thinking, a validation card, the field with the keyboard.
6. Settings › Interface: the preview, the hue, switching to another theme and back, without a crash.
7. The Guide and its band; a selected text and its bar; the screen recreated by switching the phone to dark.

The AI drives the phone through adb (`scripts/phone_ui.sh`) and puts the screenshots on a board, one screen per condition; the user judges on the device what a screenshot does not show: touch, motion, sounds, real reading. What is wrong becomes a line of the spec, "What stays open"; what is seen is pruned.
