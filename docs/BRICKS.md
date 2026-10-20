# Bricks

A brick is a notion that the user, the AI and the code handle in several places: an instant, a thing of the app, a field, a condition… It has **a model** (a class of the core), **a stored form** read by **a single parser**, and **a selector** on screen. What varies from one use to another is the context that supplies it; never a copy of the brick.

## The rule

- Every input, every storage, every reading of a notion that has its brick goes through it: its model, its parser, its selector. A screen does not rewrite a list of fields, a choice of operator or a date input.
- A new notion becomes a brick before its first use, and enters this catalogue.
- A composed brick is written with the bricks it is made of, never beside them.

## The compositions

Base bricks: **Instant**, **Thing**, **Field**, **Reduction**, **Value of a field**, **Schedule**. The others are written with them:

```
Period              = Instant + Instant
Reading             = Entry selection + Field (or none, to count) + Reduction
Term                = Value of a field (a constant) | Thing (a variable) | Reading
Side                = Term | Field (only when the condition is put on each entry)
Condition           = Side + operator + Side
Entry selection     = Thing (a tool, or a zone) + Period + Conditions put on each entry + Fields kept
```

A filter is not a brick of its own: it is a Condition put on each entry.

## What the context supplies

A brick does not know the screen that uses it; the screen gives it what it needs:

- **the reference**: the instant of reading, against which relative dates, the periods of Readings and the variables resolve — the planned time of a run, now, "now, or the end of the attempt once over" for a Goal, the instant of its row for the Chart. The selector shows it ("Relative to: the instant read"), it never makes the user choose it. Without a reference (the chat, the input of an entry), the clock resolves at the moment of choosing and the date obtained is what is stored;
- **the entry judged**, when a condition is put on each entry: its fields become possible sides of the condition;
- **the kinds allowed** (a zone, a tool, a variable, an entry…);
- **the types allowed** (a numeric field, a date…) and **the precision** (day or instant);
- **the tool** whose field is chosen.

## Term and condition

- **A term is a value**: a constant (of any type: a number, a duration, a date — relative included —, options, a yes/no, a text), a variable, or a Reading. Its relative periods resolve against the reference. A field is not a term: it is a place in an entry, which has a value only once the entry is given.
- **A condition is side, operator, side.** A side is a term; when the condition is put on each entry, a side may also be a field of the entry judged. Both sides are of comparable types, and that type says the operators allowed (`EntryFilters.operatorsFor`): `<` `<=` `=` `>=` `>` on a value of that type, `between` on two, `in` on options, `contains` on a text, `absent` and `present` with nothing opposite.
- **Both sides are read at the same instant**, the context's reference: a side that must look at another period says so in its own term ("weight, average, the previous month"), never through another instant of reading.
- **A condition is judged in two ways:**
  - **once**, both sides being terms: a read criterion of a Goal ("Sleep tracking › duration, last `≥` 7 h"), the waiting ("count of sittings to fill `>` 0"), the Alert ("`average_weight_7d` `>` 80");
  - **on each entry**, at least one side being a field: a filter (a Condition put on each entry of a Selection) ("`eaten` `>` `planned`", "`duration` `<` 6 h"), an entered criterion, put on the attempt ("`sleep` `≥` 7 h"). A filter is evaluated in SQL and today compares a field only with written values (`EntryFilters.parse` refuses a variable or a Reading opposite); the day it takes them, they will be read first, once, at the context's reference — never at the instant of each entry, which the database could not compute row by row. An entered criterion is judged in Kotlin, on the attempt alone.
- **A single stored form**: `{"left", "op", "right"}`, each side being a term (`{"constant": …}`, `{"variable": …}`, `{"reading": …}`) or a field (`{"field": "data.duration"}`), `right` absent for `absent` and `present`, a pair of sides for `between`. "duration < 6 h": `{"left": {"field": "data.duration"}, "op": "<", "right": {"constant": 21600000}}`.

## Exceptions and separations on purpose

- **A variable's formula** is the only exception assumed: computing takes a language (`Formula`: `+ - × ÷`, parentheses, functions), which is not an assembly of bricks. Its names are Terms; its per-entry variant (`per_entry`, `quantity × food.kcal_100g / 100`) reads the fields of each entry inside a Reading.
- **The Period stays beside the Conditions**, though it could be written as two conditions on `timestamp`: it also applies to a whole zone, to each of its tools, where conditions on fields make no sense (decided on 2026-09-29). It is not a duplicate to merge.
- **The Chart writes its conditions with the Condition**, not with Vega-Lite's predicates: the columns of the row drawn are its Fields. The rest of its drawing config is the subset of Vega-Lite described in `docs/design/missing-tools.md`.

## Catalogue

| Brick | What it chooses | Stored form | Model, parser | Selector | State |
|---|---|---|---|---|---|
| **Instant** | a relative date (unit, offset, start or end), a custom date, now, without limit; where the context allows, a relative date that keeps its place in the unit (the same moment, the minute included; by the day, a time: "the day before at 18:00") | the field's stored form (milliseconds, `"2026-09-15"`), `{"relative": {"unit", "offset", "edge"}}`, `edge` also worth `"SAME"` or `{"at": "18:00"}`, `{"relative": "NOW"}`, absent for without limit | `TimePoint` (`Shift` for the place kept), `TimePoint.read`, resolved by `TimeResolver`; `SettingNode.Instants` for a list of relative instants in a config | `InstantPicker` (`shifts`, `relativeOnly`) | complete |
| **Period** | two Instants | `{"start", "end"}`, each bound optional | `EntryPeriod`; `SettingNode.Period` to declare it in a config | `PeriodPicker` | complete |
| **Thing** | through the breadcrumb App › zone › tool or variable › entry | `{"kind", "id"}`; what a field accepts: `{"kinds", "tool_instances"}` | `Reference`, `ReferenceTarget`, `ThingPath`; service `references` | `ThingBrowser`, a zone shown group by group as its screen shows it | complete |
| **Value of a field** | a value of a field type | the type's (`FieldType`) | `FieldValueSchema`, `FieldValueValidator` | `FieldInput` (input), `FieldValue` (display) | complete |
| **Schedule** | a recurrence: daily, weekly, monthly, yearly, precise dates | `ScheduleConfig` | `ScheduleSettings.group` | `ScheduleConfigEditor`; `ScheduleSettingEditor` on a settings form | complete |
| **Field** | a field of a tool's entries, or none to count | a path: `timestamp`, `name`, `data.x`, `extra.x`, a filterable state key | `ToolFields.filterable` | `FieldPicker`: the name alone, the path as well when two fields bear the same name; kept by its path | complete |
| **Reduction** | last, sum, average, min, max, count, earliest, latest | `"SUM"` | `Reduction`, `Reduction.forType` | `ReductionPicker`: a single one allowed is stated without a list; the Goal's settings keep a CHOICE until its criteria are rewritten | complete |
| **Term** | a constant, a variable, a Reading | `{"constant"}`, `{"variable"}`, `{"reading"}`; the constant without a type of its own, read in that of what it is compared with (a number in a formula) | `Term`, in `core/terms`; `SettingNode.Term` to declare it in a config, among the kinds the context takes | `TermPicker` | complete; read once by `TermReader`, its type known without reading data by `TermFields` |
| **Condition** | side, operator, side | `{"left", "op", "right"}`, `right` a pair for `between` | `Conditions` (stored form), `Condition` (its sides), `ConditionJudge` (judged once), `EntryFilters` (put on each entry, in SQL); `SettingNode.Condition` to declare it in a config; at the writing of any config, `ConfigConditions` finds it through the declaration, `ConditionCheck` checks it, `ConditionValues` translates the AI's constants | `ConditionPicker` (a filter); `ConditionSetting` (judged once, a term on each side); `RowConditionSetting` (put on each row of a table, a field of the row or a written value on each side) | complete |
| **Filter** (a Condition put on each entry) | a field, an operator, a written value | the Condition, `{"left": {"field"}, "op", "right": {"constant"}}` | `EntryFilters`, evaluated in SQL | `PointerFiltersDialog`, made of `ConditionPicker` | complete |
| **Entry selection** | a Thing (a tool, or a zone for the period alone), a Period, Filters, the fields kept | `{"target", "period", "filters", "fields"}` | `EntrySelection`; `SettingNode.Selection` to declare it in a config, without its period | `SelectionPicker`: the browser folded into one line once the thing is chosen; `SelectionSetting` in a settings form | complete |
| **Reading** | an Entry selection, a Field, a Reduction | `{"selection", "field", "reduction"}` (+ `"per_entry"` in a term) | `readings.read`, `FieldReading` (a failure is an answer) | `SelectionPicker`: the browser folded into one line once the thing is chosen | complete |

## Who assembles what

| Use | Assembly | State |
|---|---|---|
| REFERENCE field of an entry | Thing | done |
| DATE or DATETIME field of an entry | Instant, without reference | done |
| Pointer of a message to the AI | Thing (zone, tool) + Period + Filters + fields + attach or mention | done, its selection assembled by hand |
| Access mask of an automation or of a connector's client | a list of Things (zone, tool), each with a level (`AccessMask`, `{"target", "level"}`) | done, chosen by the REFERENCE field's selector |
| Term of a variable | Term, the period of its Reading relative to the instant read | done, with its own selectors |
| Read criterion of a Goal | Condition judged once, "Relative to: the end of the attempt (now while it runs)"; a Reading without a period reads the attempt's, a period chosen replaces it | done |
| Entered criterion of a Goal | Condition put on the attempt: the entered field on one side | done |
| Waiting, what belongs to a zone (the mark of a tile or a card, what the tile and the notification open) | the conditions of a tool's entries that wait (`getWaiting`, `tools.waiting`), or the count a tool type makes itself (`countWaiting`, a deck's due cards) and a routine's runs finished and not read yet (`seen_at` empty, `automations.waiting`), counted by thing and by zone (`Waiting`); a dot of the theme (`WaitingMark`) at the icon's corner or beside the name; the tile, and the notification that names the tool, open it on the oldest (`getUsageScreen(openEntry)`); a run's notification opens it | done |
| Waiting, what is unique in the app (the mark of a button) | a named source (`WaitingSource`: the active conversation, the automatic backup, the Guide) that says continuously whether it waits and keeps its own rule of "seen"; the button names the sources it leads to (`waiting(...)`) and bears the dot when one waits | done |
| Chart | per layer, an Entry selection or a grid of Terms, each row read at its instant; a Period shown; its drawing conditions, Conditions on the row's columns | done (`tools/chart`, drawn by `core/drawing`) |
| Goal attempts, Questionnaire invitations, Messages sends, automations | Schedule | done |
| Reading recorded (direct automation) | Term + Thing (a Tracking) + Field + Instant | to design |
| Alert | Condition judged once | to design |
