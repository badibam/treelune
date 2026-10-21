# Data management

Technical guide to navigating, validating and handling data in the Treelune architecture.

##
## Hierarchical navigation

### Navigating to a thing

A single path leads to a thing of the app, for the pointer as for a REFERENCE field: `ThingBrowser`, the breadcrumb App › zone › tool › entry (`ThingPath`), each step a way back, and the places one level down, among those that lead to what the target accepts (`ReferenceTarget`). It reads them through `references.choices`: at the app, the zones — or, when only the entries of certain tools are accepted, these tools directly; in a zone, its tools; in a tool, its entries, searched by their label. The browser only moves the path; what the place reached is worth belongs to whoever uses it.

### The pointer

A POINTER block of a message holds a selection of the core (`EntrySelection`: a reference, never a name, its period, its filters, its fields) and says what goes with the message (`PointerConfig`, `{"selection", "attach"}`): the config, the entries, or nothing (a mention). For a tool, a period, filters by value and a choice of fields narrow its entries, attached or only mentioned; a mention of narrowed entries gives the AI the query that reads them. On sending, `EnrichmentProcessor` draws ordinary `tool_data.get` queries from it; an automation's period is relative, computed again at each run. The screen is `PointerSelector`: the browser (zone or tool), the checkboxes and what narrows the entries, and the sentence that says what will go.

##
## Fields and entries

Every value of the app is a field of a field type (`core/fields`, `FieldType`): what is entered in an entry, the state the app and its actions write there, a setting, a question of the AI. The field type holds the value's form, its constraints, its schema, its input (`FieldInput`), its display (`FieldValue`, wherever a value shows) and its description for the AI; a tool holds only its quick ways to create an entry and its computations over several entries.

- **Storage by author**: `name` and `timestamp`, declared by the core, live in columns; the tool type's fields in `data`; the user's in `extra`; the state in `state`. Two authors who each evolve their fields on their own never share an object. `id`, `tool_instance_id`, `tooltype` serve to find the entry and are not fields.
- **Use of `name` and `timestamp`**: each tool type declares them required, optional or absent, according to the config when needed (a note has a name, its title, only if its config asks for one); absent, nothing shows them and the schema refuses a value. In an update, a `name` sent as null erases the name, as a key of `data`, `extra` or `state` does. On creation, and for an object written whole (a config, settings), a key at null is a value not given: the write removes it before validating and storing (`JsonNulls`), and the validator checks what it is given, nothing less — what it checks is what is stored. The elements of a list stay as sent.
- **State**: described by fields for its labels and its filters, never entered. A tool type whose entries live by a status declares how they start (`EntryStart`, in `EntryFields`): whoever creates an entry, a screen, the AI, an MCP client, gives its status alone among those declared, and the tool type sets the rest of the state (`EntryStart.decide`, called by `ToolDataService`); what the app writes itself (a scheduler, the tool's operation, the demo) keeps the state it gives; an entry without a status is refused, wherever it comes from. Messages starts "to send" (set by hand), Questionnaire "to fill" or "filled"; Goal and Session refuse any other creation, their attempts and sessions being made by the tool; Flashcards too, whose reviews have no status (`withStatus` false): the app alone writes them, with the state it gives. Afterwards, the status moves only through the tool's operations and through the app: an update coming from elsewhere that changes it is refused (`EntryStart.changesStatus`), the rest of the state (read, archived) staying changeable. A DURATION field can be "running": `start_duration` writes the instant in `state.running.<data|extra>.<field>`, `stop_duration` adds the time elapsed to the value. The truth is in the database, a stopwatch survives the app being killed.
- **Default value**: a suggestion, applied by whoever acts — the form prefills it, a quick action applies it, the AI reads it in the schema. A field definition holds it (`default_value`, all types except DATE, DATETIME, REFERENCE and IMAGE), checked against the field itself by `FieldConfigValidator`; the config enters it with the input of the field being defined (`SettingNode.Field.valueOfDefined`). It prefills a new entry (`CustomFieldsInput` with `newEntry`), a tracking's shortcut touched, a communication module, and the value a migration asks for. The service never writes a value it was not given: an absent field means "no answer", and the input shows it so (yes/no with no button chosen, slider without a handle, empty boxes).
- **Read only for the AI**: a user's field, and the entered field of a Goal's criterion, can be set `ai_read_only` ("Read only for the AI", off by default): the person enters it, the AI reads it and never writes it. A write coming from the AI (a conversation, a routine, an import it starts) or from the connector that holds such a field — erased by `null`, a stopwatch started or stopped included — is refused whole, the error naming the fields (`AiReadOnly`, in `ToolDataService`); the schema the AI reads says it. Anyone can tick it; only the person unticks it on a field kept under the same name (`tools.update`), a config rewritten whole without the key keeping it. A Goal's verdict closes to the AI by a setting of the Goal, under the same rule.
- **Display of the user's fields**: a single component, `CustomFieldsDisplay`, which every tool uses. It shows the fields that have a value and those set "Always show" ("No value" when empty), with their name unless the config says `show_field_labels: false` — a setting common to all the fields of a config, whose default comes from the tool type (`getDefaultShowFieldLabels`). The screen chooses the layout by its place: `EXPANDED` (each field as a block, the name large above), `LINE` (one field per line, the name large then the value on the rest of the line) or `COMPACT` (one per line, "Name: value" with the name small, a long value wrapping across the whole width).
- **Copy of the config into an entry**: a fact when the entry must keep saying what it said (the unit of a numeric tracking, the title of a message sent); otherwise a derivation, computed on reading and never stored.
- **CHOICE**: its options are groups `{value, label, color}`, a colour of the theme's palette making a pill. Multiple, ordered (a ranking, entered by drag and drop) or open: a new value joins the options in the transaction that writes the entry — those of a user's field in `extra_fields`, those of a tool type's field where its config keeps them (`configWithOptionsAdded`); the tool's screen then reads its config again.
- **Unit**: the fixed unit (`unit`, "km") is not a setting of the value but a setting apart (`FieldTypeSettings.unit`), held by a user's number or range when all its values share it. A numeric tracking has none: each entry holds its own (`data.unit`), an open choice over the list `units`.
- **DURATION**: milliseconds; the units of the config (`DurationUnits`, from the largest to the smallest: days hours, hours minutes…) decide only the input and the display. Days are for a delay on the calendar (a goal's attempt, an event), which `TimeResolver.after` adds at the same time of the clock; hours for time spent, whose total reads wrong in days.
- **REFERENCE**: another thing of the app by its kind and its id, `{"kind": "ENTRY", "id": …}` (`Reference`, without id for the app), never by its name. The config says what it accepts, `target: {kinds, tool_instances?}` (`ReferenceTarget`), the tools narrowing an entry to theirs; each is itself a REFERENCE to a tool instance. The name is read on display through the core's service `references` (`names`), by the screen as by the AI, which receives it with `name` or `"deleted": true` and sends it back without (`ModelValues`); a deleted target keeps its address. The input (`ReferencePicker`) goes through the browser and is confirmed when the place reached is of an accepted kind; narrowed to the entries of a single tool, it opens in that tool. On writing, `ToolDataService` checks that a new or changed value names a thing that exists, and for an entry an accepted tool; narrowing the target removes the values that no longer fit, under `confirm_migration` (`FieldChange.ReferenceTargetNarrowed`). A filter compares it by the id it holds (`=`, `absent`, `present`).
- **IMAGE**: always an ordered list of image ids, even for one, without a caption; `max` bounds their number (no limit by default). Each id names a row of the table `images` (`ImageEntity`), shared by the images of messages and of entries, which the entry owns; the file, in its kept format (JPEG or PNG), lives in `files/attachments/entries/`. The input (`ImageInput`) prepares a photo when it is added (`files.prepare_image`, a file without a row in `attachments/pending/`); on writing, `ToolDataService` compares the field's ids with the entry's images (`EntryImages`): a prepared image becomes its own, one of another owner (a photo of the conversation, another entry) is copied under a new id written in its place, an image removed is deleted, an unknown id refuses the write. An entry that goes takes its rows by cascade, its files at the startup sweep (INFO), which also removes the images their entry no longer names (a field removed). A filter knows only `absent` and `present`; a Chart does not draw it, a reading of the core does not reduce it. The AI reads each id with its size, never the image (`ModelValues.withImageSizes`), and asks for the images through `IMAGES`.
- **Reading filtered entries**: `tool_data.get` takes `filters`, conditions put on each entry (`Conditions`, `{"left": {"field"}, "op", "right": {"constant"}}`, a pair of constants for `between`), which must all be true. The field is a path of the schema (`timestamp`, `data.x`, `extra.x`, a filterable state key); the conditions depend on its type (`EntryFilters.operatorsFor`, no `=` on an instant), and `absent` / `present`, without `right`, say whether it has an answer. A filter compares only with written values: a variable or a reading is read first. A period arrives there as filters on `timestamp`. A written date of a filter or a bound of a period is fixed (its stored form) or relative, an object that says its edge (`{"relative": {"unit": "DAY", "offset": -1, "edge": "START"}}`, `{"relative": "NOW"}`, `TimePoint`), resolved by `TimeResolver` on the context's reference; the operator never chooses an edge. The filters run in SQL (`json_extract`), so a page and its count are those of the filtered entries.
- **Reading of the core**: `readings.read` draws a value from the entries of a single tool instance — a `selection` (its period and its filters, the relative dates resolved on the `reference` the reader gives, in milliseconds), a `field` (absent to count) and a `reduction` its type allows (`Reduction.forType`: last, sum, average, min, max for a number or a duration; last, earliest, latest for a date or a time; count without a field). The result keeps its source's type and settings, the count is a whole number (`FieldReading`). Without an entry, sum and count are 0 and the rest fails; an entry without an answer to the field makes it fail, naming it — the `present` filter sets it aside if that is what is meant; a deleted source fails too. A failure is an answer (`failure`: reason, field, entries, text), which each reader decides on. A running DURATION counts up to the instant read: its stored value plus its stopwatch's time. With `at` in place of `reference`, the reading is made at several instants (a Chart's grid, one step each) and returns `values`, a value or a failure per instant: without a relative date in the filters, a single reading of the entries serves them all, each instant keeping those of its period (`EntrySelection.filtersMove`).
- **Variables**: a named value that any reader reads, without a tool type between them (`core/variables`, table `variables`, service `variables`). It lives in a zone (deleted with it, stored in a group like an automation) and reads any zone; its name is unique in the app and is written in a formula. A constant (`{"kind": "CONSTANT", "value", "field"}`) or a formula (`{"kind": "FORMULA", "formula", "terms", "field"}`): the text (`Formula`, `+ - × ÷`, parentheses, `hours()`, `minutes()`, `seconds()`, English names stored and written by the AI, shown and accepted on screen in the app's language) bears on the names of its terms — a reading of the core without a test, possibly a per-entry formula that reads through a REFERENCE (`quantity × food.kcal_100g / 100`), a constant, another variable — and on other variables named directly, stored by their id (`{var:ID}`) and shown under their current name. The field (NUMERIC, SCALE or DURATION) is deduced from what it reads when not given (`FormulaType`). The service refuses a name taken or unreadable, an unknown name in the formula, an unreadable term, a loop (naming the path). Nothing of the value is stored: `variables.evaluate` (or `readings.read` with `variable` and `at`) computes it at each instant asked (`VariableEvaluator`), each period relative to that instant; a term in failure, a division by zero, a deleted variable make it fail, with their causes.
- **Import**: the core's service `imports`, for any tool, reads rows of text values named by their column (a CSV: `CsvReader`, separator `,` `;` or tab), given as text or by the id of a file attached to a message (service `files`, table `attached_files`) — which is how the AI imports, with `IMPORT_PLAN` and `IMPORT_DATA`, without reading the rows. Each field type holds the closed, named list of its writings (`Writing`: decimal point or comma, day/month/year, `1h25`, `85 min`, yes/no…), which reads a cell or says why not; a writing absent from the list is accepted nowhere. `detect` proposes a declaration by reading the whole file (`ImportPlanner.detect`): the name as key where the names are unique, an existing field by its name or its label — among those `tool_data` says it writes (`tool_data.writable`: the name, the date, the data fields outside `system_managed`, the user's); a column that names a field the app writes itself (`created_at`, `updated_at`, the state, a `system_managed` data) is set aside, saying so —, otherwise a new field of the most demanding type one of whose writings reads all the cells (TEXT last, CHOICE when the values are few, its options drawn from the file; a header `kcal [NUMERIC]` fixes the type); two writings that read everything differently are not chosen. When no writing of the type reads every cell, the one that reads the most is proposed, and the cells it does not read refuse their rows alone; two that read as many cells differently are not chosen. It also says what its proposal lacks as it stands (`missing`), with `apply`'s checks: a column to settle, no column for the name when the tool type requires one — by its key if its names are unique, by the field `name` otherwise —, a new field that would bear the name of a field the app writes. `apply` applies only a complete declaration, otherwise naming what is missing, and refuses a duplicate key in the file: the new fields are created in the order of the columns, then the rows are written through `tool_data`, in two batches — updated those whose key finds an entry, created the others. An empty cell is an absence of answer; an unreadable cell or a row the service refuses refuses its row alone, with the reason; the report says created, updated, refused. The new fields and all the rows are written in a single transaction: a failure on the way writes nothing; a row the service refuses is an answer, counted, which cancels none of the others. The screen (`ImportDialog`) shows the declaration column by column with an example read, the ambiguities to settle and the rows that would be refused; it runs these checks again at each change, states the gaps at the top, and imports only a complete declaration.
- **Export**: the core's service `exports`, `csv`, writes the entries of a tool that its filters keep (as `tool_data.get` takes them, a period being filters on timestamp), with the fields it names or all of them, into the app's folder (`AppSettings.DATA_FOLDER`, the automatic backups' too), without asking anything; no folder chosen, or its right lost, refuses it saying so. The file (`EntriesCsv`) is made to be read, not imported again: RFC 4180, UTF-8 after its byte order mark (a spreadsheet opened by a double tap reads the accents right), the fields' labels in the app's language on its first line (a label borne twice followed by its path), then each type in a fixed form whatever the language — a point for decimals, an instant in ISO 8601 in the app's zone to the second, a duration `1:25:00`, options parted by `;`, a range in two columns, a reference by the name it shows, an image field by its number of images. The columns: the name and the date when the tool type uses them, the tool type's fields, the user's, the state (not the app's own notes, `bookkeeping`), `created_at`, `updated_at`. Named after the tool and the minute through `FileNames`, the one cleaning of a name into a file's, `_2`, `_3`… when taken, it is written under `.part` then renamed.
- **Values of a text field**: `tool_data.values` (`tool_instance_id`, `field`, `limit` 20 by default) returns the values a text field takes in a tool's entries, the most frequent first, without the absent or empty answers (`EntryFilters.values`). It is what a filter offers to choose instead of typing; a field of another type is refused, its input already having its values.

A setting is declared with the same fields, assembled by a fixed set of forms (`SettingNode`): field, group, list, variant (settings according to the value of a CHOICE, stored flat beside it), section (of the display alone), and the bricks a config keeps whole, each stored in its brick's form and entered through its selector: condition (judged once, a term on each side, `SettingNode.Condition`), term (`SettingNode.Term`, among the kinds the context takes), entry selection of a tool without its period (`SettingNode.Selection`, `SelectionSetting`) and period (`SettingNode.Period`). A setting can name a field of the rows the config describes where it stands (`rowField`, and a condition `onRow` put on each row, which compares these fields with each other or with a written value): the form's owner gives these fields according to the place (`RowFields`) — the columns of a Chart's layer. An optional group filled in is removed with a button. A setting declares its default value, which is also the meaning of its absence; it can be secret (entered masked, never sent to the AI nor logged, and sealed in the database: `SecretSettings`, an AES key of Android's Keystore that does not leave the phone, `SecretBox`). The service that writes the config seals it, the one that reads it opens it; a secret flat only (field, section, case of a variant), never in a group or a list. A backup carries it sealed: restored on another phone, it does not open, the reading leaves it aside and names it (`unreadable_secrets`), and the screen, the call to the AI and the external access say so. A config's screen is the form of its declaration (`SettingsForm`), where the core plugs its own editors on a few settings (the icon, the group, a schedule). A list of groups declares its summary (`summary`), the settings of an element that stand for it: the form shows each element closed, on a line of these values, and opens it when that line is touched; an element added opens. A group or an element that holds a group, a list or a brick opens on a page of its own, full width, under its title and a button that goes back up one page and names it ("Back to the configuration", "Back to the field “Tags”"); a field's definition is named as a field, by its name and its type ("Field “Tags” (Choice type)", "New field"), and the page of its type's settings by that type ("Choice type settings"); its line in its parent's page also says what its terms and its selections read, and counts its lists. The phone's back goes up one page; all the pages change the same draft. Save, Create, Cancel and Delete stand on the root page alone (`SettingsPages.atRoot`). What the form judges by itself (a required setting empty, a value out of its bounds, a list under its minimum, a condition or a term half written: `SettingProblems`) marks its line "To complete or correct", and each line above it up to the root.

##
## Schema validation

No schema is written by hand nor named in a piece of data: they are generated from declarations.

- **Entries**: the schema is generated from the fields the tool type declares (`getEntryFields`) and the user's fields (`extra_fields`), by `BaseSchemas.getEntrySchema`. `ToolDataService` validates every write, whoever the caller, after rounding the numbers to their decimals (`NumericPrecision`); `FieldValueValidator` adds what a schema cannot say (a range's start before its end).
- **Tool configs**: the schema is generated from the common part (`ToolConfigSettings`) and the tool type's part (`getConfigSettings`), by `SettingsSchemaGenerator`; a variant (a tracking's `type`) becomes a `oneOf`. `ToolInstanceService` validates every write of a config.
- **Config change**: what it does to the entries is deduced from the entry fields the old and the new config give, `data` as `extra` (`EntryMigration`): a value the new setting can no longer hold is removed (field removed, type changed, option removed), a value it can hold is converted (scale bounds that contain it, an answer become a list of one answer, a list of one answer become an answer), an entry that loses a required value of `data` is deleted, and the whole is written in the same transaction as the config. An entry that keeps its past (`keepsItsPast`: a Goal's attempt closed or reopened, judged again on its own copy of the definition) is not touched; a finished Session keeps its steps copied from its plan (`SequenceSteps`: name, done, skipped or not reached, time), corrected through a step's state, the hour and the total duration, a session done without the screen taking the plan's steps of the moment, all done. Removing values, deleting entries or reading kept values differently (`reinterpreted`) is refused without `confirm_migration: true`, the counts under `migration`, with each reason a tool type's own rule gives for the entries it removes (`entriesRemovedByConfig`, `RemovedByConfig`: a deck's direction removed takes its reviews) apart from the entries deleted for a required value (`deleted_without_value`); the screen, the reason the AI's change waits for approval and the refusal sent to it word them alike (`MigrationCounts`); for a change of meaning (`changed_meaning`) the screen offers a new field instead: the changed field leaves without its key and receives a new one, the former stays with its values, renamed (`NewFieldInstead`). An entry without a value for a field become required takes the one the caller gives in `fill_values` (`{"data": {"value": 3}}`), otherwise the change is refused. An option removed or renamed that a filter of another tool compares the field with ("moment = lunch" in a Chart) would leave that filter finding nothing, without an error: these tools are named under `migration.aimed_by` and ask for the same agreement (`FiltersOnOptions`, which walks each other tool's config through `ConfigConditions`). A field removed is not counted there: the filter that names it is refused on reading, which shows. A message's pointers and the variables are not walked.
- **Zones**: the schema `zone_config` is generated from `ZoneSettings` (name, description, icon, group of the home screen, tool groups), and `ZoneService` validates every write; the zone screen is the form of this declaration.
- **Groups**: an element stored has as its group `null` or a group that exists (`Groups`) — a zone, a group of the home screen; a tool, an automation or a variable, a tool group of its zone. The services refuse an unknown group and the deletion of a group still held, naming what holds it; a renaming arrives with the list (`renames`, which the list editor draws from its original names, `ListOrigins`) and follows into everything that holds the former name; changing zone without a group given empties the group.
- **App settings**: each category (format, AI limits, validation, home screen, demo, interface) is declared in `AppSettings`; `app_config.get` and `app_config.set` read and write a whole category, `AppConfigService` checks it against the generated schema, and each category's screen is the form of its declaration (`AppSettingsScreen`). A bounded setting is declared there as SCALE, set with the slider.
- **Automations**: declared in `AutomationSettings` (see `docs/AI.md`).
- **Reading**: a config is read through `ToolConfigSettings.read` (`SettingValues`); an absent setting is worth its declared default, never a fallback written on the spot.
- **Facing the AI**: a schema is asked for by `tooltype` (config), `tool_instance_id` (entries) or `id` (the others: `zone_config`, `field_type_TEXT`…). Its computed name (`tracking_config`, `tracking_data`) serves only not to send the same schema twice.
- **What the AI reads of a schema**: not the JSON Schema, which serves only to validate, but its notation (`SchemaNotation`): one line per value, with its label and its description once only, and the common settings of a variant written once instead of once per option. A list of field definitions refers there to the prompt's Fields section, which describes it once. A keyword the notation cannot write makes the sending fail rather than vanish.

A field marked `"system_managed": true` is the app's to produce, never the caller's. In `data`, the service removes every marked field from what a screen or the AI sends, and keeps it when the app itself writes — a service from its own operation, a scheduler, the origin `SYSTEM` (`Origin.byTheApp`, `SystemManagedFields`); an import does not offer these fields to its columns. At the root, it reads only named parameters.

The validation the screens do before calling the service serves to answer early in the form; it is not the guard.

**Field `id`**: not `system_managed`, since an update names it to designate the entry. A creation generates it; only the app itself gives one, prefixed `demo-`, for the demo (`GivenId`) — from a screen or the AI, an `id` given to a creation is refused.

##
## Propagation of changes

A write goes through the service, which writes to the database via its DAO then signals the change with `DataChangeNotifier` — the screens that depend on it reload. There is no event log: no table records the changes, and the state in the database is the only source. What the user and the AI read as a history are the entries themselves and the sessions, not a replayable sequence of events.

### Verbalisation

A system of templates for actions, states and results:

```kotlin
// Template
"[source] [verb] the title [old_value] to [new_value]"

// Result
"The AI changed the title Blup to Blip"
```

**Use**: history, user validation, AI feedback.

##
## History of writes

Every write of the database is kept in a history, to undo it step by step (`core/history`, service `history`, Settings › History).

- **Covered**: zones, tools (their config), entries, variables, automations, identities, the connector's clients' masks, the app's settings, and what belongs to them (an entry's images, a routine's message: its seed session, its messages and their images). Not covered: the conversations, their messages and images, the providers (a key is not copied), the connector's tokens, the Guide's progress, the log, the files attached to a session. A secret in a covered setting is never copied, left as it is by an undo. The history goes back on the database only: a notification sent, an AI call billed stay done.
- **A step** is an operation someone asks for (a gesture on screen, a command of the AI, of a connector's client), with every row it writes, through the operations it calls on the way too; the coordinator opens one only if none is open in the coroutine's context, where the step's number travels like the origin. A write outside any operation is an error. What the app writes by itself (a scheduler's entries, a status it moves on, its reminders) is no step, neither shown nor undone, but noted so that a refusal to undo names it ("changed by the app on 20 October at 8:00"). A step keeps its origin, time, session id when it comes from the AI, its sentence (written by the service's `verbalize()` when it happens, frozen), its state, and for each row touched the table, the id, the tool and zone, the whole row before and after.
- **One door**: every write of a covered table goes through a single class (`Writes`), which reads the row before, writes, then writes the history in the same transaction; `scripts/check_history_writes.py` refuses any other write of these tables. The cascades of SQLite are read before a deletion, so that what goes with it can be restored, parents before children; `CascadeDescentTest` compares this descent with the schema's foreign keys. No SQLite trigger: it would need a transaction open for the whole operation, through an AI call. An entry image's file stays on disk while a step names its row, the startup sweep deleting only the files neither the database nor the history names.
- **Undo and redo** (`history.undo`, `history.redo`, by a step's id; `history.list` for the screen and the AI): a step is undone whole or not at all, after checking that each row is still as the step left it, otherwise refused naming the step that changed it since — never a merge nor an overwrite. An undo is not a step. The fields the app holds alone stay out (`system_managed`, the core's columns like `created_at`, and the reminders each tool or settings category declares: an Agenda reminder sent, a List due date notified): an undo leaves them in their present state, so that a renamed event got back keeps its reminder marked sent. An undo is not validated by schema again: the row was valid when written, and the migrations kept it up to date (`HistoryCopies.rewrite`).
- **Depth**: 30 days and 500 MB, set in the app's settings; the `CoreScheduler` tick deletes the older steps, then the oldest while the total goes beyond, never the most recent. The export does not carry the history; a restore empties it, saying so.
- The screen: the steps from the most recent, Undo and Redo at the top, a step unfolding its rows as the forms show them (`ReadableChanges`), undone or redone alone.

## Versioning and migrations

### Single sources of truth

**build.gradle.kts**:
- `versionCode` = monotonic integer (10, 11, 12...) for comparisons
- `versionName` = SemVer string ("0.3.0", "0.4.0"...) for the user's display
- Reachable through `BuildConfig.VERSION_CODE` and `BuildConfig.VERSION_NAME`

**AppDatabase**:
- `@Database(version = 10)` = SQL schema version
- `companion object { const val VERSION = 10 }` = reachable at runtime
- Independent of versionCode (can stay stable between releases)

### Kinds of migrations

#### SQL migration (Type 1)

**When**: the SQL schema changes (ALTER TABLE, CREATE TABLE, etc.)

**Handled by**: manual Room migrations in AppDatabase.kt. The database runs on the app's SQLite (`BundledSQLiteDriver`), not on the phone's: Room gives a migration a connection, which `ConnectionMigration` presents to it as a `MigrationDatabase` (`execSQL`, and `query`, whose rows are read whole before being returned).

**Example**:
```kotlin
private val MIGRATION_10_11 = object : ConnectionMigration(10, 11) {
    override fun migrate(database: MigrationDatabase) {
        database.execSQL("ALTER TABLE zones ADD COLUMN icon TEXT")
    }
}
```

**Flow**:
1. Increment `@Database(version = 11)` and `const val VERSION = 11`
2. Write the SQL migration in AppDatabase's companion object
3. Add it to `.addMigrations()` in getDatabase()
4. Room detects and runs it automatically at startup

#### JSON migration (Type 2)

**When**: a JSON format that is not backward compatible (fields renamed, restructuring)

**Handled by**: the centralised JsonTransformers

**Example**: `{"unit": "kg"}` → `{"measurement_unit": "kg"}`

**Flow**:
1. Write the transformation in JsonTransformers.kt
2. Applied automatically on a backup import
3. Optionally callable in a Room migration when needed

### JsonTransformers - centralised architecture

**Single file**: `core/versioning/JsonTransformers.kt`

**Public functions**:
```kotlin
transformToolConfig(json, tooltype, fromVersion, toVersion): String
transformToolData(json, tooltype, fromVersion, toVersion): String
transformAppConfig(json, fromVersion, toVersion): String
```

**Sequential application**: a loop `for (v in fromVersion until toVersion)` applies all the intermediate transformers

**Example**:
```kotlin
private fun transformTrackingConfig(json: JSONObject, version: Int): JSONObject {
    return when (version) {
        10 -> {
            // Migrate from v10 to v11
            if (json.has("unit")) {
                json.put("measurement_unit", json.getString("unit"))
                json.remove("unit")
            }
            json
        }
        else -> json // No migration for this version
    }
}
```

**Reuse**: the same transformers are used by:
- **Room migration**: transforms the JSONs in the database at startup (once per version)
- **Backup import**: transforms the JSONs of old backups
- **Part import**: a part of a backup taken back (`backup.import_part`); a part holds only some tables, so a transformation reads each table as optional (`optJSONArray`), never as required

**Application at startup**: a change of JSON format REQUIRES a Room migration to transform the existing data. The transformers are called in the migration to update the stored JSONs.

**Lifecycle**: transformers kept indefinitely (support for old backups)

### Export/import format

**The file**: a zip, `backup.json` (the tables, below) and `images/<id>.jpg`, the images attached to messages copied file by file, never in the JSON. `BackupService` writes the export into the app's folder (`AppFolder`, chosen once, the automatic backups' too), under `treelune_backup` and the minute, and the export's screen (`ExportScreen`, Settings › Data › Export…) hands it on through Share; the import reads the file chosen (its `uri`), opened on the import's screen (`ImportScreen`, Settings › Data › Import…), where a whole backup is restored or a part of it taken back. An image whose file is missing is an error the backup copies as it is: the export goes ahead anyway, its row without a file, named in `missing_images`, and says so; the import accepts a row without a file that it names, and refuses any other, the sign of a damaged backup. The import also accepts the old `.json` alone; it lays the zip's images apart (`files/attachments.import/`), and replaces the images' folder in the same move as the tables.

**A part of the app**: the same file cut down to what is ticked (`BackupPart`, `PartSelection`: zones, and in them tools, each with its entries or not, variables, routines, and the identities the zone or its routines speak as), `"partial": true` in its metadata. It never holds the settings, the AI providers, the conversations or the files joined to them; a routine keeps its seed session and its message, without its provider or runs. An identity is listed at the top of each zone that speaks as it, or one of whose routines does, ticked with the zone or a routine; unticked, the zone and the routine are set to none, a link that only warns (`share_link_zone_identity`, `share_link_routine_identity`). One that does not make an identity file (a language with resources and no text, the language it speaks without one) refuses the export, naming it. `backup.export` given a `selection` writes it into the app's folder (`AppFolder`, the CSV export's too), named after its zone when it holds one, `treelune_share` otherwise, and the minute. An element kept that names one left out blocks the file, a routine's only warns (`PartLinks`, its ids found as whole words, `IdWords`). `backup.tree` gives the export's screen what the app holds, its zones and their elements with every link between them (`PartTree`), which the screen filters as its boxes change.

**Taking a part back** (`backup.import_part`, `PartImport`): a whole backup or a part, cut to a `selection`, each of its zones going into a new zone or into one of the app's (`arrivals`). Everything is added through the ordinary services as the app itself, one step of the history: each thing gets a new id first, replaced in the whole part at once, which the services take as given (`GivenId.Made`); the tools and variables are created after what they read, the entries after those they designate. A variable whose name is taken arrives under `_2`; a routine arrives off, without provider; an identity keeps its id, shared with whoever wrote it: one the app lacks is imported as its identity file (`IdentityFile.write`, then `identities.import`) before the zones and routines that speak as it, one it holds is kept, or replaced last when `replace_identities` names it, the screen offering the choice only when the two differ (`Identity.sameAs`: name and texts, wherever each comes from), keep by default; a missing home or tool group is added; an image missing at the export is taken out of its entry. A step refused removes what was written before it. A part is never restored whole (`backup.import` refuses it). The import's screen (`ImportScreen`, Settings › Data › Import…) reads the file through `backup.tree` given its `uri`, the same tree as the export's, with whether it is a part, the variables' names already taken and the identities the app holds, `same` or `different`.

**Metadata in `backup.json`**:
```json
{
  "metadata": {
    "export_version": 10,
    "export_timestamp": 1234567890,
    "db_schema_version": 10
  },
  "data": { ... }
}
```

**Import process**:
1. Check `export_version` against `BuildConfig.VERSION_CODE`
2. If `export_version > VERSION_CODE` → error "version too recent"
3. If `export_version < VERSION_CODE` → apply JsonTransformers
4. Insert the transformed data, then the images' folder replaced by the backup's

**In the database**: pure JSONs without metadata (version in the export files only)

### Automatic backup

The export's zip, written without asking anything into a folder of the phone chosen once in Settings › Data (`OpenDocumentTree`, a lasting write permission kept by Android; settings category `data`, `folder` and `auto_backup`). The scheduler's round (`CoreScheduler.tick`, every 10 minutes, app closed included) asks `AutoBackup`: a copy is due when the hour's is missing or is an hour old; it is the long operation `backup.auto`, never beside an import, the demo or an export, and is written only if the data differs from the last copy (SHA-256 fingerprint of `data`, kept on the phone in `files/auto_backup.json`, outside the database the copy would hold); data found unchanged is not read again before an hour (`checked_at`, same file), the comparison reading the whole database. Four files, `treelune_auto_<hour|day|week|month>_<date>.zip`: the new copy takes the hour, and the older one moves down a step when the step below has passed its duration (a day, a week, thirty days), otherwise it is erased (`AutoBackupRotation`); the state is read in the folder's names, nothing else is kept. The new copy is written before the rotation: a failure on the way leaves the folder as it was. A folder whose permission is lost (uninstall, folder moved, backup restored on another phone) stops the copies and says so in Settings. No folder chosen, or the folder lost, marks the way to Settings › Data (`WaitingMark` on the home screen's Settings button and on the Data tile, `AutoBackup.pending`) until the screen is opened: the case seen is kept (`seen`), another case marks again.

### Development rules

**Versioning**:
- `BuildConfig.VERSION_CODE` = single source of the app's version
- `AppDatabase.VERSION` = database version (independent of the app's version)
- No metadata in the database (version in the export files only)

**SQL migrations**:
- Explicit manual migrations in AppDatabase
- No fallbackToDestructiveMigration (migrations required)
- Migrations kept historically
- The history of writes (« History of writes ») keeps whole copies of the rows: a migration that renames or removes a column of a covered table, or transforms the JSON it holds, transforms its copies too, with the same transformation (`HistoryCopies.rewrite`). A column added asks nothing of it.

**JSON transformations**:
- Centralised in JsonTransformers.kt
- Reused everywhere (imports + Room migrations)
- Kept indefinitely (complete history)
- Automatic sequential application

##
## Development rules

### Implementation patterns

#### Service implementation
- Inherit `ExecutableService`
- Validation by the service against the generated schema (see "Schema validation")
- Explicit error logs and handling of token cancellation

#### Discovery pattern
- Never hardcoded imports in the Core
- Services discovered through ToolTypeManager
- Automatic extension by adding to the Scanner
- ToolTypes declare `getEntryFields` and `getConfigSettings`; their schemas are generated

#### Data consistency
- Write by the service, then notification through `DataChangeNotifier` (no event log)
- Validation centralised in the services, against generated schemas

---

*The data architecture guarantees consistency, automatic validation and extensibility through decoupled patterns.*
