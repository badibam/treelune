# User interface

A guide to the UI patterns and components, to keep things consistent and simple.

## Hybrid architecture

**Layouts**: native Compose (Row, Column, Box, Spacer)
**Visuals**: UI.* components (Button, Text, Card, FormField)
**Business**: specialised components (ZoneCard, ToolCard)

### Standard layout
A Column with fillMaxWidth, vertical padding `UI.Space.L` and automatic spacing between elements. A screen sets no margin on the sides: the edge margin (`UI.Space.EDGE`, the smallest the theme allows, 1 cell in retro, 8 dp elsewhere) is given once by `MainScreen` around the place shown, and it is also that of a floating window (`FloatingDialog`).

### Spacings
A screen never writes a spacing in dp: it names it, `UI.Space.XS`, `S`, `M`, `L`, `XL` or `EDGE`, and the theme gives its size (`ThemeContract.spacing`) — 4, 8, 12, 16 and 24 dp for the default theme, whole cells for the retro theme. Spacings are the arguments of `padding()`, `Arrangement.spacedBy()` and `PaddingValues()`, the height or width of a `Spacer`, and a `spacing =` argument; `scripts/check_spacing.py` refuses a dp in these places, outside the themes' code. A size (of an icon, of a block) is not a spacing.

### Interface sounds
A `UI.*` component sends a signal of the closed list `UISignal` (confirm, enter, back, open, close, check, step, end of list, refusal); the theme says which sound answers it (`ThemeContract.sound`, null for silence) and `UISounds` plays it on a single track, if the setting "Interface sounds" (category `ui`) is on. A screen never plays a sound itself. A disabled element still receives the touch, for the refusal sound alone. The end of a list is heard at the root of each window (`Modifier.scrollEndSound()`), never list by list: a new dialog window made by hand sets it at its root.

### Scroll required for every container
**Rule**: all content containers (screens, dialogs, forms) MUST have a vertical scroll on their main Column.

**Applies to**: MainScreen, ZoneScreen, CreateZoneScreen, Settings, configuration dialogs, multi-section forms, etc.

**Required pattern**:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(vertical = UI.Space.L),
    verticalArrangement = Arrangement.spacedBy(UI.Space.L)
) {
    // Scrollable content
}
```

**Imports required**:
```kotlin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
```

**Rationale**: the scroll guarantees that all the content stays reachable on every device, whatever the screen's size, the amount of content shown, or the presence of the keyboard. With the keyboard open, the window shrinks to what it leaves (`adjustResize`, on the activity): a page scrolls down to its bottom above it. What stays fixed at the bottom of the screen would rise with it, so it disappears while it is open (`keyboardOpen()`): the Guide's band, the conversation button.

**Exceptions**: only containers with LazyColumn/LazyRow (which have their own native scroll) are exempt.

### Page headers
UI.PageHeader supports a title (one line: the header keeps its height), an optional subtitle, an icon or the app's mark (`appMark`, the home screen alone: the launcher icon drawn by the theme, `LauncherMark` or its pixels), and, in a bar above, left/right buttons with predefined actions and the breadcrumb in a place of the stack. A left BACK button also gives its action to the phone's Back key; without it, the key removes the place from the stack (`MainScreen`), and a form that must ask first sets its own `BackHandler`. The home screen asks for confirmation before closing the app.

## General conventions

### Language
- Comments and debug: English
- Variable/function names: English
- User messages: through the strings system (s.shared/s.tool), English in the default resources and French in the `-fr` files (`docs/reference.md`)

## Simplified text system

### UI.Text - 4 parameters at most
Takes text, type (TITLE, SUBTITLE, HEADING, BODY, STRONG, CAPTION, LABEL, ERROR, WARNING), fillMaxWidth and an optional textAlign. STRONG is running text that must stand out from its neighbours (an unread message): bold in the default theme. HEADING names a tile or a section of tiles (the name of a tool, a zone, a group), one step above running text.

### Separating layout and content
**Principle**: UI.Text for rendering, Box+Modifier for layout and interactions.
- Weight pattern with a Box wrapper to share space
- Padding pattern with a Box for spacing
- Clickable pattern with a Box for interactions

### Standard Row pattern
A Row with fillMaxWidth, vertical padding `UI.Space.XS`, spacing `UI.Space.S` between columns.
- Columns with weight + Box for precise alignment
- **Use**: tables, lists with actions, multi-column forms

## Buttons and actions

### Two kinds of buttons

**UI.Button** - generic and flexible, with type (PRIMARY/SECONDARY/DEFAULT), size (XS to XXL), state and custom content.

**UI.ActionButton** - standardised actions with a predefined action, display (ICON/LABEL), size and optional confirmation. In ICON, the button shows the Lucide icon the action holds (`ButtonAction.iconName`), drawn by the theme. `active` shows it lit as long as what it opens lasts (a group's edit mode), drawn by the theme (the default theme: a solid background of the primary colour). The default theme draws an icon button without a background, the icon in its type's colour, and the primary button as a frame of the primary colour.

**UI.FloatingButton** - a button laid above the content (the chat, on the home screen), on a background of its own, so that what scrolls below does not show through.

### Actions available
- **Main**: SAVE, CREATE, UPDATE, DELETE, CANCEL, CONFIRM
- **Navigation**: BACK, UP, DOWN
- **Utilities**: ADD, EDIT, CONFIGURE, REFRESH, SELECT

### Visual hierarchy
- **PRIMARY** (green): critical actions - SAVE, CREATE, CONFIRM, ADD, CONFIGURE, SELECT, EDIT, UPDATE
- **DEFAULT** (grey): neutral navigation - CANCEL, BACK, REFRESH, UP, DOWN
- **SECONDARY** (red): destructive actions - DELETE only

### Automatic confirmation
UI.ActionButton supports requireConfirmation with a customisable message.

**UI.ConfirmDialog** - a modal confirmation dialog with title, message, optional confirmText/cancelText, onConfirm and onDismiss.

**FloatingDialog** - the window where a theme lays its dialog frame (`UI.Dialog`): it covers the screen, centres the frame in what the bars and the keyboard leave, at the margin the theme gives, and handles the keyboard itself (Android does nothing about it): the frame shrinks above, the field's scrolling keeps the cursor in view. A touch around the frame closes it.

## Text fields and input

### FieldType extensions
- **TEXT** (60 chars): names, identifiers, labels
- **TEXT_MEDIUM** (250 chars): short descriptions
- **TEXT_LONG** (1500 chars): substantial text content
- **TEXT_UNLIMITED**: long content without a limit (journals, etc.)
- **NUMERIC**: numeric keyboard
- **EMAIL**: email keyboard, no autocorrect
- **PASSWORD**: masked, no autocorrect
- **SEARCH**: autocorrect + magnifier action

### Limits defined (FieldLimits.kt)
The following constants are referenced in the JSON schemas:
- `SHORT_LENGTH = 60`: FieldType.TEXT
- `MEDIUM_LENGTH = 250`: FieldType.TEXT_MEDIUM
- `LONG_LENGTH = 1500`: FieldType.TEXT_LONG
- `UNLIMITED_LENGTH = Int.MAX_VALUE`: FieldType.TEXT_UNLIMITED

### Schema/UI consistency - CRITICAL RULE

**Principle**: the UI limit (FieldType) MUST match the schema's limit (maxLength). A field never refuses what is typed or pasted: `UI.FormField` keeps the text too long, shows its count under the field from 80 % of the limit (`FieldInput.COUNT_FROM`), puts the field in error beyond it, and it is the check of what is stored (the schema, the service) that refuses, saying so.

**Use cases**:
- **Written content** (Journal, note): `TEXT_UNLIMITED` UI + no maxLength in the schema
- **Content of a Message** (a notification): `TEXT_LONG` UI + `maxLength: LONG_LENGTH` in the schema
- **Instruction** (a Session's step): `TEXT_LONG`
- **Names/labels**: `TEXT` UI + `maxLength: SHORT_LENGTH` in the schema
- **Descriptions**: `TEXT_MEDIUM` UI + `maxLength: MEDIUM_LENGTH` in the schema
- **The user's text field**: unlimited by default, or the length they choose

**FORBIDDEN**: a UI limit more restrictive than the schema (e.g. TEXT_MEDIUM for a LONG_LENGTH schema).

### Long texts

A long text (TEXT of length LONG or UNLIMITED, `TextLength.formatted`) is written in the app's small Markdown (`core/markup/Markup.kt`): headings `#` to `###`, lists `- ` or `* `, numbered lists `1. ` (numbered by their place on display, whatever the digit typed), `**bold**`, `*italic*`. The text stored is the one typed, nothing else; the schema the AI reads marks it `text with markup` (`FieldValueSchema.MARKUP`).

- Input (`UI.FormField`, `MarkupEditing`): a line break after a list item starts the next, on an empty item it removes its mark; two buttons at the end of the label's line, list and numbered list (`ThemeContract.LabelLine`), set or remove the mark on each line the selection touches; Bold and Italic in the bar of a selected text.
- Reading: `MarkupText`, the same reader for the Guide, a tool's usage, an identity and the user's texts; the icon `{name}` at the head of a list line is read only in the app's texts (`icons = true`).
- Abridged (a tile, a cell, a line of fields): `Markup.plain`, one line without marks, through `formatValue`; an entry copied keeps its marks (`describeValue`).

### Smart autocorrection
- **TEXT**: Words + autocorrect
- **TEXT_MEDIUM/LONG/UNLIMITED**: Sentences + autocorrect
- **EMAIL/PASSWORD**: no autocorrect (security)
- **NUMERIC**: numeric keyboard only
- **SEARCH**: Words + magnifier action

## Forms and validation

### Automatic error toast
A LaunchedEffect pattern to show and reset the error messages automatically.

### Validation before sending (dialogs)
**Pattern**: validation before sending to the service through SchemaValidator. A validationResult state, a validateForm() function, used in onConfirm with an isValid check.

### Form components

**UI.FormField** - a standard field with label, value, onChange, fieldType, required, state, readonly and an optional onClick. The theme has only one text field, which receives a `TextFieldValue` (the text, its selection, its composition): the form over a string keeps the cursor itself, the form over a `TextFieldValue` leaves it to the caller, to insert where it stands (the formula editor).

**Copy and select** - a text one reads, whether from the user, the AI or the app, read-only and outside an area that reacts to a touch or a long press, is selectable (`UI.Text(selectable = true)`, `UI.Selectable` for a block). Selectable as a matter of course: a page's title and description (`UI.PageHeader`), the Guide's texts (`MarkupText`), an error and a warning (`TextType.ERROR`, `WARNING`), an entry's fields in `FieldsLayout.EXPANDED`. Never a button's label, a tile's title or a field's label. Where the long press already serves, the whole object is copied through `UI.copy`: a conversation bubble by long press (what it shows, in words), a note by "Copy" in its menu, a List item by long press (its name, then its fields), a Journal entry and the bug report by a `ButtonAction.COPY` button. A selected text, to read or in a field, shows the theme's bar and not Android's (`SelectionMenu`, set by `UI.Selectable` and `UI.FormField`; `ThemeContract.SelectionBar`): copy, paste, cut, select all in the app's language, and the actions of other apps; what Android offers for the text itself (call a number, open a link), Compose keeps for its own bar.

**UI.FormSelection** - selections with label, options, selected, onSelect and required. A single-answer CHOICE field with more than 30 options (the time zones, the languages) has a search above its list, which narrows it to what contains the text typed (`FieldInputRenderer`).

**UI.SectionedSelection** - the same selection laid out in sections, each one's title drawn without being chosen, an option chosen by its position; each option can have the icon of a zone or a tool in its colour (`OptionIcon`), drawn before it and before the chosen value (`ZoneChoice`).

**UI.BooleanField** - yes/no as two buttons. An answer (`Boolean?`) starts with no button chosen and is emptied if optional; a state (`Boolean`) always has a button chosen and is never emptied: "On" / "Off" by default, as compact buttons laid beside what they switch (an automation); with its own labels, it is a choice between two modes, across the whole width. A list where several items are checked is made of `UI.Checkbox`.

**UI.Switch, UI.Tabs** - a setting that takes effect as soon as it is toggled, its label first; a row of tabs, one per label. Drawn by the theme.

**UI.FullScreen, UI.HeaderBar** - a view that covers the screen on the theme's background (the app's root, and the chat through `FullScreenDialog`, whose window covers the whole screen, hides the navigation bar and paints under the status bar the activity's band, its colour and the tone of its icons copied); the band at the top of such a view, its title and its buttons in a row.

**UI.StatusIndicator** - a status pill: the screen names the status (`StatusColor`: success, warning, error, info, quiet), the theme gives its colour (`UI.statusColor` for an icon's tint).

**UI.SliderField** - a scale; without an answer, no handle and "—".

**HueSlider** (the theme's) - the hue shift slider: its track is the colour each shift gives the theme, bordered, the thumb laid on it; a touch or a drag goes through `horizontalPick` and `RangePick`.

**UI.Divider** - a horizontal line that separates two parts of a screen or a card, drawn by the theme.

**UI.MarkedIcon** - the icon of a tool or zone tile and its two pills, each in its corner: at the top `UI.WaitingMark`, when one of its entries waits for the user (`ToolTypeContract.getWaiting`, counted by `tools.waiting`, read in `LocalWaiting`, which also counts the unread routine runs for a zone's tile and a routine's card); at the bottom `UI.RunningMark`, when a stopwatch runs on one of its entries, for any tool type (`tools.running`, read in `LocalRunning`). Their shape is the theme's (`ThemeContract.WaitingMark`, `RunningMark`).

**UI.Drawing** - a drawing made of shapes placed in pixels (`core/drawing`: rectangle, path, symbol, arc, segment, text, tip), laid out by whoever makes it — a chart (`tools/chart`), later a preview on a tile — and drawn by the theme (`ThemeContract.Drawing`). The colours there are meanings, never values: a name of the palette (`TagColor`), the mix of two for a quantity, or one of the theme's inks (strong, medium, light); a "missing" rectangle marks what could not be read. The texts are measured in the style the theme gives drawings (`drawingTextStyle`) before being placed. The theme also gives the size of a pixel of its drawings (`drawingUnit`: a screen pixel for the default theme, the whole factor for the retro), on which whoever lays out sets a bar's width. No primitive knows what it represents: what a touch finds is the business of whoever made the drawing.

**UI.ReorderableColumn** - a list reordered by dragging each element's handle (`DragHandle()`, laid where the element wants it). The gesture, the place of arrival and the scrolling near an edge are the core's; the handle and the lifted element are the theme's (`ThemeContract.DragHandle`, `ReorderItem`). Each list keeps its order, nested or not: an element never leaves its list. The new order goes out once, on release (`onMove(from, to)`); "move up" and "move down" stay accessibility actions of the handle.

**`required`** - each input receives it, without a default value: true for a field that can be empty and blocks validation while it is; a field that always has a value (filter, selector) is not. The theme marks it its own way (`FieldLabel`, an asterisk in the default theme); an input in several parts (range, duration, multiple choice) marks its common label.

**UI.FormActions** - a standard container for form buttons with ActionButton (SAVE, CANCEL, conditional DELETE).

**ButtonRow** - every row of buttons: side by side if they all fit at their width, otherwise each on its line at full width, in the order given (the primary first) — never only some sent to the next line. `UI.FormActions` is its centred version. A button stretches by reading `LocalButtonFill`, which each theme honours. A text does not share its buttons' line: it goes above.

**ChoiceGrid** - choices of a same size (colours, icons): as many columns as the width holds at the size of a sample cell, each line shared in equal parts; never a number per line written in a screen. Elements of different sizes (pills, values) wrap through `FlowRow`.

### State/Controller pattern
**RecordingController** - the business logic exposes an observable `StateFlow<RecordingState>`. Actions through methods (start, pause, resume, validate).

**RecordingDialog** - pure UI observes the state and delegates the actions. Cleanup through DisposableEffect.

## Cards and containers

### Full-width cards
UI.Card with type CardType.DEFAULT, content in a Column with internal padding `UI.Space.L`. A section header (`CardType.SECTION_HEADER`) is not a card, in any theme: its title on the screen's background above a line, the tiles below staying the only cards.

### Titles and sections
- **Main title**: UI.Text with TextType.TITLE, fillMaxWidth and textAlign Center
- **Section title**: UI.Text with TextType.HEADING in a Box with horizontal padding

## Recreation of the activity

The app is portrait only, but the activity is still recreated (dark theme toggled, language changed, activity not kept by the system): every `remember` starts from zero and every `LaunchedEffect` runs again. Three rules, each with the tool that applies it.

### 1. rememberSaveable for what the user produced

**rememberSaveable**: form inputs, selections, filters, current page, tab, open window and what it edits.

**remember**: reloaded data (`entries`, `toolInstance`), loading and sending indicators (`isSaving`), temporary messages (`errorMessage`), drop-down menus.

A type the Bundle cannot carry goes through a saver of `core/ui/StateSavers.kt` (`JsonObjectSaver`, `FieldDefinitionsSaver`, `FieldValuesSaver`, `NullablePeriodSaver`, `MessageSegmentsSaver`, `serializableSaver(serializer)` for any `@Serializable` type…): `rememberSaveable(stateSaver = JsonObjectSaver) { mutableStateOf(...) }`. A loaded object being edited (entity, occurrence) is kept by its id and found again in the loaded list. It is this id that is passed to the child screen and decides update or creation, never the object found: the list reloads after the recreation, and the object is `null` meanwhile. A type not covered → add a saver in that file, not at the call site.

### 2. Load the stored content once per screen: rememberLoadOnce

A `LaunchedEffect` that fills the form runs again after the recreation and overwrites the restored input. `rememberLoadOnce(keys) { ...; true }` (`core/ui/LoadState.kt`) loads only once per screen — again if the keys change — and returns a `LoadState`:

```kotlin
val configLoad = rememberLoadOnce(existingToolId) {
    if (existingToolId == null) return@rememberLoadOnce true  // creation: nothing to load
    val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to existingToolId))
    if (!result.isSuccess) return@rememberLoadOnce false
    name = ...                                                  // fills the saveable states
    true
}

if (configLoad == LoadState.LOADING) { /* loading */ return }
UI.ToolConfigActions(..., saveEnabled = !isSaving && configLoad == LoadState.LOADED)
```

### 3. An action works only on loaded data

Before a load or after its failure, the screen shows default values: saved, they would overwrite the stored content, without an error. Save (and any action that reads the loaded data) is therefore disabled outside `LoadState.LOADED`, or hidden while the screen loads.

To go back to the first page when a filter changes, `OnChangedEffect("$filter|$period") { currentPage = 1 }`: a `LaunchedEffect` on the filters would also fire at the recreation and lose the restored page.

## Tables and lists

### Weight pattern for tables
A Row with fillMaxWidth, columns in a Box with weight to share (e.g. 1f for actions, 4f for content), contentAlignment Center for buttons.

### Specialised business components

**UI.ZoneCard** - a zone with built-in business logic, onClick and contentDescription.

**UI.ToolCard** - a tool instance with displayMode (ICON, MINIMAL, LINE, CONDENSED, EXTENDED, SQUARE, FULL), onClick and onLongClick.

**ToolGrid** - the tools of a section on their grid of four columns. In edit mode (`GridEditor`, one group at a time, opened by the `ARRANGE` button of its title), the theme draws the cells (`ThemeContract.GridCell`), a touch selects a tile, and `GridEditBar` moves it with the arrows (`Grid.move`); validating writes it at once (`tools.place`), "Cancel" puts the section back as it was; the rest of the screen is set aside and does not react (`Faded`). The theme draws each tile according to its state (`ThemeContract.GridTile`: none chosen, chosen, set aside) and the rest of the screen as a tile set aside: the default theme pales, the retro dithers in a checkerboard, its open pixels falling on the cells' dotted lines (`RetroChecker`), which thus show through. The home screen lays out its zones the same way (`GridLayout`, `ZonePositions`, `zones.place`), a zone in ICON, MINIMAL, LINE or CONDENSED (`UI.ZoneCard`), its mode set with it (`display_mode`). The setting "One column" (Settings › Interface, `CurrentTheme.oneColumn`) shows each grid in one column, read from the grid without saving anything (`Grid.oneColumn`): a half of the screen takes the whole width, the two halves of a full-width tile (header and summary, header and preview) stack, one more row, the ICON tiles go two per row. Its arrows move a tile in the column, then the section is packed into the grid in that new order (`Grid.moveInOneColumn`, `Grid.pack`): its layout on the grid is not kept.

## Navigation and states

### The stack of places
A screen one may want to reach or find again is a **place** (`core/navigation/Place.kt`): the home screen, a zone, a tool, a config, a page of the Settings, an identity, an AI provider and the choice of its kind, an automation, the conversation… It has an address (`zone/<id>`, `tool/<id>/<zone>`) and knows its parent. `Navigator` holds the stack, the home screen at the bottom: a screen opens a place through `Navigator.push`, leaves it through `Navigator.pop`, gives its place to the next step through `Navigator.replace` (a provider's kind chosen, its config opened: going back skips the choice), and `MainScreen` draws the top one (the conversation lays itself over the one below). Opened from outside (`Navigator.open`: a notification, a tool asked for elsewhere), a place rebuilds its parents if nothing is open, otherwise stacks alone. The stack is saved as addresses with the activity's state; each place keeps its `rememberSaveable` as long as it stays. Passing windows (confirmation, input, picker) are not places: an `if` on a state of the screen that opens them. A full screen one opens and leaves is one: only going from one place to another plays the theme's enter and back sounds. The stack's rules, without a phone: `PlaceStackTest`.

A place's header (`UI.PageHeader`) lays a bar above the title: the left button (back, settings) and those that follow it (`afterLeft`: the Guide's book on the home screen), the breadcrumb (`BreadcrumbLine`, read in `LocalBreadcrumb`) — the places before, each touchable —, the right button (the gear) at the end. The theme draws only the title, across the whole width (`ThemeContract.PageHeader`). A place names its breadcrumb entry (`Navigator.name`).

The conversation button floats at the bottom right of the places one consults (`Place.chatButton`: home screen, zone, tool, Guide, automation), never on a form. A screen of these places leaves it room at the end of what scrolls (`Modifier.chatButtonSpace()` after `verticalScroll`, `chatButtonEnd()` as end padding of a lazy list), which `scripts/check_chat_space.py` checks; what holds fixed controls at the bottom while it shows hides it (`HideChatButton()`: a tile's move bar, a running Session). The sounds of going and coming back are played by the stack, never by the gesture: a button that changes place (`BACK`, `CONFIGURE`, `VIEW`, `AI_CHAT`) plays nothing.

### User feedback
UI.Toast with context, message and Duration (SHORT/LONG) for temporary messages.

## Themes and customisation

### Themes
What a theme is, its settings, and the recipe to code one: `docs/THEMES.md`.

### Standard spacing
- **Between sections**: spacedBy(UI.Space.L)
- **Vertical screens**: padding(vertical = UI.Space.L)
- **Inside cards**: padding(UI.Space.L)

## Standard loading/error pattern

### Required states
The data, isLoading and errorMessage states are required for all async components.

### Conditional display
- Early return if isLoading with a loading UI.Text
- Automatic toast for errors with a LaunchedEffect and a reset

### FORBIDDEN: silent default values
Always check config != null before use, no default values that hide errors.

## Time selection

### Period types

```kotlin
data class Period(val timestamp: Long, val type: PeriodType) // Absolute period
data class RelativePeriod(val offset: Int, val type: PeriodType) // Relative period (offset from now)
```

### Period components

**SinglePeriodSelector** - navigation with arrows, period, onPeriodChange, showDatePicker, useOnlyRelativeLabels.

**InstantPicker** - the only input of a date or an instant (an entry's DATE or DATETIME field, a period's bound): a custom date, a relative date (unit, offset, start or end), now, without limit for a bound. It edits a `TimePoint`. With a reference (`hasReference`), a relative date and now are stored as they are, resolved each time; without a reference, the clock resolves the choice on the spot and the date obtained is what is stored.

**PeriodPicker** - two `InstantPicker` (from, until) that edit an `EntryPeriod`; it names the context's reference once ("Relative to: the instant read").

### Labels logic
**useOnlyRelativeLabels**:
- `false`: "Week of 15 March"
- `true`: "125 weeks ago"

### Utilities
- `normalizeTimestampWithConfig()`: normalises a timestamp to the start of the period
- `getPeriodEndTimestamp()`: computes the end of the period
- `getNextPeriod()` / `getPreviousPeriod()`: period navigation
- `resolveRelativePeriod()`: resolves a RelativePeriod → absolute Period
- `calculatePeriodOffset()`: computes the offset between a timestamp and now

## Usage rules

### Do
- **ActionButton** for standard actions (SAVE, DELETE, BACK)
- **UI.Button** for dynamic texts and complex content
- **FormActions** for all form buttons
- **Box wrappers** for UI.Text's layout and interactions
- **Centralised validation** through SchemaValidator
- **isLoading pattern** for all async states
- **rememberSaveable** for business and navigation states (survives the recreation)
- **remember** for reloaded temporary states (isLoading, data)

### Avoid
- Mixing ActionButton and UI.Button in the same screen
- Manual Row/Column buttons instead of FormActions
- Layout modifiers directly on UI.Text
- Default values that hide config errors
- Hardcoded strings (always s.shared/s.tool)

---

*The UI favours consistency and simplicity without sacrificing flexibility.*
