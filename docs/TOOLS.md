# Tools and extensibility

A guide to understanding and creating tools in the modular architecture.

## Fundamental concepts

### Tool type vs tool instance
- **Tool type**: static metadata and behaviours (e.g. TrackingToolType)
- **Tool instance**: a specific configuration in a zone (e.g. "Daily weight")

### One instance = one concept
- **Tracking**: 1 specific metric (weight, mood, etc.)
- **Goal**: 1 goal with sub-goals and criteria
- **Chart**: 1 chart, which can layer several layers or repeat by category
- **List**: 1 themed list (shopping, tasks)
- **Records**: 1 table of records (foods, books, contacts)
- **Journal**: 1 kind of journal (reflections, dreams)
- **Note**: 1 individual note
- **Message**: 1 scheduled message/reminder
- **Agenda**: 1 themed calendar (medical, personal)
- **Flashcards**: 1 deck of cards to review, drawn from the rows of another tool

### Custom fields

All tooltypes support **extra fields** defined by the user to extend the data.

**Structure**:
- **Definitions**: in the config's `custom_fields` array (name, display_name, type, always_visible)
- **Values**: in the data's `custom_fields` object (key = the field's name)
- **Types**: single source `FieldType` — TEXT, NUMERIC, SCALE, CHOICE, BOOLEAN, RANGE, DATE, TIME, DATETIME
- **Validation**: built automatically into the enriched data schemas

**Pattern**: the data schemas need `toolInstanceId` to be enriched with the custom fields.

#### `name` is given, never chosen

`name` is the technical identifier: the key the values are stored under. It is given by `ToolInstanceService.processCustomFields` from `display_name`, and by it alone — neither the configuration screen nor the AI supplies one. A new field therefore arrives **without** `name`; `display_name` stays freely changeable.

Consequence: a `name` sent in a config update must name an existing field, otherwise the update is refused. This is what makes a renaming impossible to express rather than detected afterwards — the fields are compared by their `name`, so a renaming would come out as a removal followed by an addition, and the removal erases the field from all the entries.

Removing a field from the list stays an ordinary removal: its values are erased, that is what it means.

## Tool architecture

### Physical structure
The folder tools/[type]/ holds:
- ToolType.kt (contract and metadata)
- Service.kt (business logic)
- Dao.kt (data access)
- Data.kt (database entity)
- ui/ (usage screen and display; the config screen is the core's)

### ToolTypeContract interface
The main interface, with methods for:
- **Metadata**: getDisplayName(), getUsage() (the `usage` text: what the type is for, what it already does by itself, what it is not for; read by the AI always in English, shown by the Guide), getAIDetail() (what only the AI reads besides, none by default: its rules of use and its ergonomic tips, what the descriptions cannot imply), getSuggestedIcons(), getDefaultIconName(), getFamily() (`ToolFamily`: note, plan or draw on the others, the family its tile stands in, in the add menu of a zone's section), getDefaultDisplayMode(), getDefaultShowFieldLabels()
- **Declarations**: getEntryFields() (the entries' fields), getEntryMeanings() (what each part every entry has means in the type, whatever the config: the name a tracking's thing noted, a record's key; the extra fields a questionnaire's questions, a table's columns; null keeps the core's text, and `data` is left null when its fields say it; read by the AI as the first level of the entry, `EntryDescription`), getConfigSettings() (its own settings, beside the common part `ToolConfigSettings`; those the type puts in no section, the core puts in a section named after the type, so that on screen everything is in a frame); schemas, default config and reading are generated from them; configWithOptionsAdded() for a type that declares an open choice in `data`; getOperations() (default: none), the operations its service runs on its entries beside the generic writes, each a name, a sentence and its parameters declared as fields (`ToolOperation`)
- **User interface**: getUsageScreen() @Composable; rememberTile() @Composable, the tool's tile on a zone (`ToolTile`): its 2×1 summary, its body on the rows its mode gives it, and its preview in place of the summary in the modes without a body (LINE, CONDENSED; the summary by default, the Chart's band), placed by `UI.ToolCard` beside the header; the body lays out its elements through `TileGrid`, each cell as high in FULL as in the limited modes, each written by `TileLine`: on the left a checkbox or an icon, on the right a value or buttons that keep their size, in the middle the text on two lines at most (one only when the tile stacks four per row, a List of short items), or a line and a second, quieter one below; each type fills every mode, with no default tile, and a tile opens the tool on an entry or a new entry (`EntryToOpen`); the config screen is generated from the declaration (`ToolConfigScreen`, `SettingsForm`), a schedule (`ScheduleSettings.group`) included
- **Discovery pattern**: getService(), getDatabaseEntities(), getDatabaseMigrations(), getScheduler()
- **Enrichment**: enrichData() (identity by default, automatic enrichment before persistence)
- **Rule between entries**: settleEntries() (default: nothing to change), see below
- **Without entries**: keepsEntries() (default: yes); a tool that shows the entries of others (the Chart) keeps none: no user fields in its config, and every write of an entry is refused; takesUserFields() (default: keepsEntries()), false for a tool whose entries the app alone writes from what the user does elsewhere (a deck's reviews): no user fields in its config
- **Config check**: refuseConfig() (default: nothing), what only the app's reading says beyond the schema (a column no source gives), asked at each creation and update; getRowFields() (default: none), the fields of the rows the config describes where a setting names them (`RowFields`)
- **Update**: refuseUpdate() (default: nothing), what is not changed by the ordinary write but deleted (a deck's review, written once, which undoing deletes)
- **Entry**: refuseEntry() (default: nothing), what an entry cannot hold beyond each field (a whole day with a duration), asked at each creation and update
- **Deletion**: refuseDelete() (default: nothing), what is not deleted by the ordinary write but changed (an occurrence of an Agenda series, which is withdrawn), with the means to read the tool's other entries, read only if it asks for them (a deck's review, only the last of its card)
- **Waiting**: getWaiting() (default: none), the conditions of an entry that waits; countWaiting() (default: null), for a type that counts itself what waits, for lack of an entry that holds it (a deck's due cards, due by the day): the tile bears the dot, and opens on the tool alone
- **Validation**: validateData() (delegates to SchemaValidator)

## Implementation method

**Golden rule**: always copy-paste Tracking first, adapt afterwards.

### Order of implementation
1. **ToolType** with its declarations (getEntryFields, getConfigSettings, defaults included)
2. **Service** with strict validation
3. **UI screens** with robust parsing
4. **Registration** in ToolTypeScanner

### Critical checkpoints
- SchemaValidator API: schemaType = "config|data"
- Services: tools.* uses tool_instance_id, tool_data.* uses toolInstanceId
- LaunchedEffect: all variables checked in the scope = dependencies
- Validation: on save only, not preventive

## Creating a new tool

### Base structure
1. **Data entity**: @Entity with id, toolInstanceId, timestamp, value (JSON), metadata
2. **DAO**: an interface with queries to fetch/insert/change the data

### Business service
A class implementing ExecutableService with:
- execute(), which validates through the ToolType then routes by operation (create/update/delete)
- Private methods for each operation, handling the CancellationToken
- Automatic validation through SchemaValidator

### ToolType implementation
A class implementing ToolTypeContract with:
- getDisplayName(), getUsage(), getDefaultDisplayMode()
- getEntryFields(), getConfigSettings()
- getUsageScreen() @Composable
- getService(), getDatabaseEntities()

### enrichData pattern
**Principle**: automatic enrichment of the data before persistence (called by ToolDataService for all entries).

```kotlin
override fun enrichData(data: Map<String, Any>, context: Context): Map<String, Any> {
    // Automatic computations, validations, enrichments
    // Tracking example: raw value → formatted display field
    return enrichedData
}
```

**Use**: unified UI + AI, pre-persistence logic without manual interception.

### settleEntries pattern
**Principle**: a rule that bears on all the entries of an instance, not on a single one. `ToolDataService` calls it at each creation, update or deletion of an entry of the tooltype, with the means to read all the instance's entries as they will be after the write — read only if it asks for them, a tool type without a rule not paying for the reading —, and the id of the entry written (null after a deletion). It stores the entries returned in the same transaction as the write. Unified UI + AI, like enrichData.

**Manual order**: `ManualOrder` (core) keeps the positions 0, 1, 2… without gap or duplicate, under `state.position`, for a tool type whose entries the user orders by hand (Notes, List). Writing position p places the entry at p and shifts the following ones; an entry without a position goes last.

### Passive and active tooltypes

**Principle**: there are only two planes of data. The **config** is what the tool *is* (its definition: template, formula, schedule); **tool_data** is everything it *records or produces* (inputs, occurrences, results). No third plane — the technical audit of failures belongs to the logging system.

**Passive** (Tracking, Journal, Note): the user writes into tool_data.

**Active** (Messages, Goals): the system writes into tool_data, driven by the config. An active tooltype exposes an `execute` operation (`{tooltype}.execute` with `tool_instance_id`), declared in getOperations(), and usually a `getScheduler()`. Its occurrences are ordinary tool_data entries: queryable, open to statistics, migratable, visible to the AI, like any other entry.

**Occurrences with a life cycle**: an occurrence is not necessarily instantaneous. It can live (created → active → closed), in which case its data schema holds a `status` field and its requirements depend on it. The `status` filter of `tool_data.get` exists for these tooltypes.

### Registration
Added in ToolTypeScanner.getAllToolTypes() for automatic discovery.

## Examples of complete flows

### Nutrition flow
1. Food TRACKING (manual) → meals entered
2. Nutrition RECORDS (AI) → reference table of foods + RDA
3. Nutrition TRACKING (AI + #1 + #2) → automatic computations
4. JOURNAL (AI, based on #3) → daily reports
5. VARIABLES (core, based on #3) → totals of the day, periodic averages
6. CHART (App, based on #5) → visualisations vs RDA
7. ALERTS (App, criteria on #3) → deficiencies/excesses detected

### Configuration by the AI
**Principle**: the AI configures and manages complex tools automatically through JSON commands.
- **User**: defines the goal ("track my nutrition")
- **AI**: creates the chain of tools, configures the computations, defines the alerts

## Tool types available

### Tracking
**Use**: time-based quantitative/qualitative data
**Configuration**: value type (numeric, text, scale, choice, timer…), units (numeric: the list each entry takes its own from, a new unit entered joins it), predefined items (in numeric, a shortcut that has a value enters it with one touch: "Glass" = 1, "Half glass" = 0.5, "− Glass" = −1 for a count that goes down). A choice also shows each of its options, as the yes/no shows its answers: recorded with one touch (named by its label, the user's fields at their default value), or through the prefilled input window. Without shortcuts, a free entry is named after the tracking, changeable; with shortcuts, it notes another thing and its name starts empty
**Examples**: weight, mood on a 1-10 scale, free food log

### Goal
**Use**: success criteria with relative weights
**Structure**: 3 levels - goal → sub-goals → items
**Validation**: confirmation required before finalising

### Chart
**Use**: show the entries of other tools and variables, computing nothing: a total per day is a grid column or a variable, never an aggregation
**Configuration**: a subset of Vega-Lite, declared setting by setting (the form and the AI's schema are generated from it), its keys Vega-Lite's (`strokeDash` and `strokeWidth` included). `period`, the period shown, relative to the moment of display; `composition`: `layer` (one view), `vconcat`, `hconcat`, `concat` (views `{"title", "layer"}`, `columns` per row), `facet` (`facet.field`), `repeat` (columns, which the channels whose `field` is `repeat` take). A layer: `source` — `entries` (`selection` of a tool without its period, one row per entry: `timestamp`, `name`, its fields by path) or `grid` (calendar `step`, `columns`: a name and a term, variable or reading, read at the end of each step; a reading without a period reads its step; the row also has `timestamp`, `weekday`, `week`); `transform` (`fold` with `as`, `flatten`); `mark` (line, point, bar, area, text, arc, tick, rect, with its styles); `encoding` (x, y, x2, y2, color, size, shape, opacity, strokeDash, detail, order, text, theta, radius: `field` among the layer's columns, `type`, `scale` — `domain`, `zero`, `nice`, `reverse`, `range` in names of the palette —, `axis` — `grid`, `orient` left or right —, `stack` — `none`, `zero`, `normalize`, required on bars or areas split by a category —, `legend`, `condition` — the Condition brick on the row's columns — and `value`). `ChartCheck` refuses what the schema does not say: a column no source gives, a channel its mark lacks, two kinds of values on a shared axis, two units on the same side
**Data**: no entry of its own (`keepsEntries`). `ChartSources` reads one table per layer: the entries through `tool_data.get`, a grid step by step (`GridSteps`, the current step read at now) through `variables.evaluate` and `readings.read` with `at`, one column per call; a value in failure is a marked cell, drawn as a hole. The tool lays out the chart (`ChartSceneBuilder`: scales, ticks, stacking, legend) as a drawing of the core's shapes (`core/drawing`), which the theme draws. The screen: the chart, its legend, and what the finger touches (all the row's columns, or the cause of a hole and its entries, which open in their tool); read again when an entry or a tool changes. The tile (`rememberChartTile`): as summary the period and the last value of the first series; as body its marks as a band (EXTENDED), the chart reduced (SQUARE) or whole (FULL) (`ChartDetail`); a touch opens the tool

### Journal
**Use**: free text entries with dates
**Configuration**: entry template, suggested frequency

### List
**Use**: the present state of what remains to do (shopping, tasks, checklist), without history: what was done and when belongs to an "occurrence" tracking
**Configuration**: `remove_when_checked`, a checked item is deleted at once (shopping); `due_dates`, the items can bear a due date; what else an item bears beyond its name (a quantity) is a custom field
**Data**: one entry per item: its name, in `data` its due date `due_at` (declared only with `due_dates`), and in `state` its position (`ManualOrder`), `checked_at`, the instant it was checked, absent otherwise — unchecking erases it —, and `due_notified`, the due date already notified. The screen shows the unchecked items in the manual order, reordered by dragging, then, under a line, the checked ones in the order they were checked; "Uncheck all" acts as one batch. The tile checks without opening the tool (`ListTile`)
**Due dates**: a thing to do once before a moment (a fixed rhythm belongs to Messages). `ListDueScheduler` notifies once each past due date of an unchecked item and marks it in `due_notified`; an item marked and unchecked waits (the dot), the waiting conditions not knowing the time; `DueNotice` erases the mark when the due date it names changes. The screen shows the due date under the name, "Overdue" once past; the tile puts the overdue items first and counts them

### Goal
**Use**: a goal judged by counting its criteria, one attempt per period
**Configuration**: criteria and sub-goals (two levels), "at least N" (all by default) and "essential"; a criterion is a value — a variable, a tool's field reduced over the attempt's period (chosen among its fields, `SettingNode.Field.fieldOf`), or entered (yes/no, number, duration, scale) with "how to fill it in" — and a condition; its key is given at its creation and kept afterwards (`completeConfig`): renaming it keeps its values, deleting then recreating it makes another. As a routine's: an optional `schedule`, « Launch now » beside it, and an optional `duration`, an attempt without one ending at the next start; `enabled` (the schedule's switch), `expiry_delay` (7 days), `notify`, `ai_no_validation` ("Validation closed to the AI": `goal.validate` and `goal.reopen` refused to the AI, a routine and the connector, unticked by a person only)
**Data**: one entry per attempt, dated at the start of its period: `data.definition` (the copy that judged it), one field per entered criterion; `state`: `status` (active, to_validate, succeeded, failed, expired), `period_end`, and once validated `judgement` (values and verdict frozen), `validated_by` (the call's origin: a person or the AI, never a scheduler), `validated_at`, `reopened_at`. An open attempt follows the current definition; a validated or expired attempt keeps its copy and refuses any write (`refuseChange`), except `goal.reopen`. `GoalService`: `evaluate`, `validate` (refused while a value is missing), `reopen`, `launch` (`tool_instance_id`: an attempt opened now, by the screen's « Launch now », confirmed when one is open, a Task's step or the AI); `GoalScheduler`: to validate at the end of the period (a notification, the event), expired after the delay, the attempt of the last occurrence come since the last one (the goal's creation for the first) opened without catching up the others. Any start, the schedule's or a launch, sends the attempt open to be validated, its period ending there. The screen: the current attempt, its criteria facing their condition, entered on the spot, the count, the time left, the user's fields with a pencil opening them in a form (one write on saving, frozen with the judgement) and "Validate"; the attempts to validate; the history as pills, each attempt read again frozen with "Reopen". The tile: the count and the current verdict, the latest pills, the number to validate

### Questionnaire
**Use**: questions asked one per screen, on demand or at planned times, by the user or with the AI
**Configuration**: the questions are the user's fields (`extra_fields`); `schedule` (the invitations), `enabled`, `ai_message` ("Message to the AI", prefilled with an instruction that can be changed)
**Data**: one entry per sitting, dated at the moment it describes, without a name; `state.status` (to_fill, filled, ignored), `state.filled_at`. `QuestionnaireScheduler` creates at each planned time the entry "to fill", the times come during an absence included (60 at most, those after the last already created), and a notification. `QuestionnaireService`: `complete`, `ignore`, `ignore_all`. The screen: "Fill in now" (nothing is written before the end), "With the AI" (a chat opened with the message and the pointers to the tool and the entry, `ChatRequests`), "To fill (n)" with "Ignore all", the history where an entry is read again and changed; the sitting of a planned entry stores each answer and resumes at the first without an answer. An entry's title, name and moment in relative terms, is computed on display. The tile: the number to fill, otherwise the last answer

### Session (Sequence)
**Use**: steps prepared in advance then done by following the screen, a timer or "Done" per step (intervals, pomodoro, yoga, routine, recipe); it guides, it measures nothing: the figures go into a Tracking
**Configuration**: `steps`, steps and blocks (`kind`), two levels at most, the steps of a block within a block stored without `kind` (`SequencePlan`); a step: `name`, `instruction`, `images` and `media`, each a term of files (`SettingNode.Term` with a FILE `value`: images, or one sound or video): a fixed value the tool owns (`OwnedFiles`), or one entry's file field followed (`ConfigTerms`), read when a session starts (`SequenceMedia`) and kept with its run, where it came from noted with each step (`source`, a name or « Source deleted »); `length` (`none`, no time of its own; `fixed`, its `duration`; `media`, its sound or video's, ended by hand when there is nothing to play) and `auto_next`, whether the next step then comes by itself or the stopwatch goes on in overtime until « Done » (`StepLength`, the run's `StepEnd` following from both); a step with no time of its own that would pass by itself is refused (`refuseConfig`); the sound plays from the service that runs the session, screen on or off (`SequenceAudio`), the video on the session's screen, both following the step's clock, paused with the session, in a block `skip_last_round`; a block: `name`, `repeat`, `steps`. `step_signal`, `countdown`, `voice`; `schedule`, `enabled`
**Data**: one entry per session, dated at its start or at the planned time, without a name; `state`: `status` (planned, running, to_finish, done, stopped, ignored), `started_at`, `ended_at`, and while it runs `run` (`SequenceRun`: the unfolded course copied at the start, the steps left, the times on the session's clock, stopped during a pause); `data`, once over: `duration`, `paused`, `steps_done`, `steps_skipped`, `steps_not_done`, and `steps`, its steps as they ran (`SequenceSteps`: name, round, state, time), written by its operations alone (`systemWritten`). Removing a step from the config touches no entry. `SequenceService`: for the screen and the AI `complete` (a session done without the screen: the planned one `id`, or an unplanned one at the time given), `correct` (a finished session: time, duration, and the state of each step, `step_statuses`, for a session that keeps its steps, the counts following from them; the counts themselves, their sum kept, for a session finished before, `SequenceCorrection`; the state follows; from the screen, the user's fields under `extra`), `ignore`, `ignore_all`; for the screen and the notification only `start`, the gestures (`done`, `skip`, `back`, `restart`, `pause`, `resume`, `extend`, `stop`, `resume_interrupted`), computed by `SequenceGestures`, and `finish`. The last step passed or the session stopped, it is written whole and waits `to_finish` (the dot) for "Finish the session" (`finish`, the user's fields under `extra`), which sets it done or stopped as its steps say: a ritual, with or without user fields; until then no session starts in the app. `complete` and the AI write a session done at once. `SequenceRunService`, a foreground service (`specialUse`) under a wake lock, runs it with the screen off: the signals (`SequenceSounds`, three marimba notes, the vibration, Android's voice), the timers passed, its notification with "Done" and "Pause", left once the session is over as "Session over", which opens the tool, until it is finished. A single session running or to finish in the app. `SequenceScheduler` creates the planned sessions as a questionnaire's invitations. The screen: the running session (the step, its round, its time, its instruction, its images, the first large, the others small, each opened whole, the next, the gestures, the screen kept on), a session interrupted with the app to resume or stop, a session over to finish (its length and steps, the user's fields, "Finish the session"), or "Start", the planned sessions with "Done" and "Ignore", the course (a step's first image small at its line's end) and the history, in which a session opens to be corrected, the user's fields included. The tile: the current step and its time, the session to finish, the planned session, the next or the last; as body the latest sessions

### Agenda
**Use**: what takes place at a time and passes by itself (an appointment, a birthday, holidays), with nothing to check: neither a List's due date, nor a Messages reminder, nor a Tracking's thing done
**Configuration**: `reminders` and `all_day_reminders`, Instants relative to the start of an event (`SettingNode.Instants`, the edge `SAME` and the time as edge included); `series`, each a key given at its creation (`completeConfig`), a name, a schedule (`ScheduleSettings`), a duration or `all_day` and `days`
**Data**: one entry per event, its `timestamp` its start (for a whole day, the start of the app's day on its first date, where `settleEntries` puts it back whatever time is written); `data`: `duration`, or `all_day` and `days`, never together (`refuseEntry`); `state`: `status` (planned, cancelled, removed), a starting status of the core (`EntryStart`), changed by `agenda.cancel`, `agenda.remove` (a series occurrence only) and `agenda.restore`; `series` and `series_at` for an occurrence; `reminded_until` and `reminded_for`. A series occurrence is not deleted (`refuseDelete`). `AgendaScheduler` gives each series' occurrences 90 days ahead, the next one always, after the last given, and sends the reminders due, late too as long as the event is not over; its next reminder wakes the app (`nextAction`). A series' schedule changed deletes its occurrences to come, which the series gives again (`entriesRemovedByConfig`, the confirmation of the config change); a name, a duration or days changed go down to those that still had the former value (`entriesUpdatedByConfig`). The screen: Upcoming, Day, Week or Month (`SinglePeriodSelector`), the events per day, "Withdrawn (n)" folded; "+" creates an event or, with "Repeats", a series; an event's sheet, and for an occurrence, "Edit the series". The tile: the next event, then those to come, seven days in FULL

### Flashcards
**Use**: review by spaced repetition the rows of another tool (a lexicon, capitals): each card comes back just before being forgotten; the deck does not keep the words, it points to its source
**Configuration**: `source`, an Entry selection without a period (`SettingNode.Selection`); `group_by`, a field of the source each value of which makes a group; `directions`, each a key given at its creation (`completeConfig`), a name, its front and back fields (`front`, `back`), chosen among the source's (`FlashcardsRowFields`); `one_direction_after_another`, `new_per_day`, `reviews_per_day`, `desired_retention`. `refuseConfig` refuses a source that is not a tool keeping entries, or a deck, and a field it does not have; changing the source deletes the reviews, removing a direction deletes its own (`entriesRemovedByConfig`). No user fields (`takesUserFields`)
**Data**: one entry per review, its `timestamp` the instant; `data`: `source_entry` (a REFERENCE to the row), `direction`, `rating`, written by the app alone (`EntryStart` without a status); `state`: the card's FSRS state after it, `stability`, `difficulty`, `due`, `fsrs_version`. A review is not changed (`refuseUpdate`), only a card's last one is deleted (`refuseDelete`), which undoes it. `Fsrs`: FSRS-6 ported from py-fsrs 6.3.2, without learning steps or fuzz, tested against its suites (`FsrsTest`, `scripts/make_fsrs_reference.py`). `FlashcardsJournal` reads in SQL the first and the last review of each card, `FlashcardsSource` the source's filtered rows, and `FlashcardsDue` draws from them the cards to offer: due from the start of the day of their due date or failed, new in the order added, the day's caps in cards across the whole deck, two directions of a row never on the same day, one direction after the other (learnt = a stability of at least 7 days). `FlashcardsService`: `due` (a group, a direction, the faces), `counts` (All, each group by its label, each direction), `review`. The session (`FlashcardsSession`): a list fixed at the start, a failed card coming back after three cards and a minute, Hard, Good and Easy finishing a card, undoing by going back. The screen: All then one line per group, the start window per direction, the session (front, back under a line on touch, the four ratings, Back). The tile: "N to review · M new", one line per group, the dot counted by the deck (`countWaiting`)

### Records
**Use**: records made of the user's fields, each found again by its name (the foods and their calories, which a meal's REFERENCE reads)
**Configuration**: the columns are the user's fields (`extra_fields`); `table_columns`, how many of them the table shows after the name (2 by default)
**Data**: one entry per record: its name, required and unique in the tool ignoring case and the spaces around (`EntryFields.nameUnique`: `ToolDataService` refuses a duplicate in its transaction naming the existing record, a filter on `name` compares the same way), no date, `data` empty. The screen (`StructuredScreen`): a table sorted by touching a header, a record's screen where a swipe leads to the neighbours, a filter header common to both (search, filters, sort) folded into a line that sums them up with the position, kept for the length of the visit; a record is changed whole and written at once, a new one nothing before "Save". The tile counts the records

### Note
**Use**: a title and free content
**Configuration**: template, categories

### Message
**Use**: a notification message and its sends
**Configuration**: common title and body, priority, recurrence, days of advance creation, validity window
**Data**: one entry per send (`pending`, `sent`, `expired`, `cancelled`), holding the part written for that day and, once gone, the common part copied

## Display modes for tool cards

The tools of a group's section are laid on a grid of four columns of square cells (`SectionTiles`), with its variables among them, whose side the theme gives according to the width available (`gridCellPx`: a quarter, the grid at most 480 dp wide, in the default theme). Each tool sits there at `grid_x`/`grid_y` (`tool_instances`), placed by `SectionGrid` and `Grid`; its size in cells comes from its `display_mode`, which its config always holds (its look, `Looks`):

- **ICON** (1×1): the icon alone
- **MINIMAL** (2×1): the header, icon and name
- **LINE** (4×1): the header on the left, the summary (or the preview) on the right
- **CONDENSED** (2×2): the header at the top, the summary (or the preview) below
- **EXTENDED** (4×2): the header and the summary at the top, one row of body at most, the tile as high as its content (the header alone when the tool is empty), all its cells in editing
- **SQUARE** (4×4): likewise, three rows of body at most
- **FULL** (4 × its height): the header and the summary at the top, the body as high as its content, rounded to the cell

A cell is two lines, and every height in a tile is counted in lines. Only ICON, MINIMAL and CONDENSED share a row of the grid. Nothing scrolls in a tile: what does not fit is the tool type's business. A touchable element of the tile does its action, a touch elsewhere opens the tool (or the entry that waits), a long press its config; in editing, a touch selects. The AI neither sees nor changes the positions; changing a tool's mode through its config moves down what its tile covers.

## JSON Schema V3 validation

Unified validation for all tool types through SchemaValidator.

### Standard API
- ToolType fetched through ToolTypeManager.getToolType()
- Schema of a tool's entries: `BaseSchemas.getEntrySchema` (user fields included); config schema: `ToolConfigSettings.schema`
- The services validate every write (`ToolDataService`, `ToolInstanceService`)
- Result handling: isValid and errorMessage translated automatically

### Validation service pattern
The service's execute() validates automatically through the ToolType, then returns OperationResult.error() if validation fails.

### Configuration screen pattern
- States for the form's fields
- Optional real-time validation with remember(dependencies)
- Automatic toast for errors through a LaunchedEffect
- FormActions with the SAVE button enabled according to validation

## BaseSchemas and configuration

### Common settings (`ToolConfigSettings`)
- **name**: the instance's name
- **description**: description
- **display_mode**: display mode (ICON/MINIMAL/LINE/etc.)
- **validate_data**: "Protect this tool's data", the AI of the conversation and of the connector asks before writing its entries (default: no); its config is kept by its zone's protection (`docs/AI.md`, « Levels »), and only a person changes either one (`Protections`)

### always_send field (Level 2 AI)
```kotlin
"always_send": {
    "type": "boolean",
    "default": false,
    "description": "Always send the data to the AI (Level 2)"
}
```

**Use**: if `always_send = true`, the data of this tool instance is included systematically in Level 2 of the AI prompts, as permanent context.

**UI**: a common setting, declared in `ToolConfigSettings`.

## Robust parsing patterns

### The services return objects
`tool_data.*` returns `data` and `custom_fields` as a `Map`, and `tools.*` takes and returns `config` as an object — never as a string. JSON serialisation lives only at the edge of the database, where `tool_instances.config_json` keeps its name because the column does hold a string. A caller reads its keys directly, without parsing or try/catch.

### LaunchedEffect with complete dependencies
Include ALL the variables checked in the scope as dependencies to avoid stale states.

## Extension rules

### Pure discovery
- No hardcoded import in the Core
- Automatic registration through ToolTypeScanner
- Service and DAO discovered dynamically

### Consistency patterns
- Unified validation through SchemaValidator
- JSON configuration with a schema
- Event sourcing for all changes

### Interface contracts
- ToolTypeContract declares its fields and settings; the schemas are generated from them
- ExecutableService for the business logic
- SchemaValidator for UI/Service validation

---

*The tools architecture guarantees extensibility without changing the Core, and consistent patterns.*
