# The user's journey — design

Designed 2026-10-08. What a person discovering the app sees, from the README to their first tools, then to the AI and the routines. The aimed public: a curious person who arrives through F-Droid or GitHub, at ease with a phone, not a developer, without an API key. The AI makes the app smooth and is part of the journey, but it is not asked for before there is something to give it.

## The promise

They open the README, the F-Droid listing and the home screen, in this order:

1. Follow what matters (sport, meals, tasks, mood) with tools you assemble yourself, and an AI that can read them, fill them in and set them with you.
2. The example: you note your meals, and the app draws from them the day's calories, compares them to a target and draws them in a chart.

The AI that acts with the same gestures as the user, which sets the app apart, comes next: up front, it only speaks to those who have already understood the rest.

## The README

- `README.md` in English, `README.fr.md` in French, each linked to the other on the first line. The French is written first, the English is its translation.
- Plan, in the order of the questions of whoever discovers the app:
  1. Header: icon, name, the promise and its example.
  2. Three or four screenshots side by side: the home screen, a zone of the demo, a chart, a conversation with the AI.
  3. What you can do, in four short blocks: Note (the tool types, one line each), Connect (variables, goals, charts), With an AI (it reads, fills in, sets, asks for your approval, runs on a schedule; your key; Claude connected from outside through the connector), To your taste (zones, layout, themes).
  4. Start: install, then the demo and the Guide at the first launch.
  5. Your data: everything stays on the phone, nothing leaves except to the chosen AI provider when one talks to it or a routine runs, no telemetry, backup and export.
  6. Project state: version, active development, minimal Android, languages.
  7. For developers: a paragraph, the link to `docs/reference.md`, `./run`.
  8. Licence: GPL-3.0.
- Out of the README: the three principles up front (their content passes into 1 and 3) and the technical list, which `docs/` already carries.
- The screenshots live in `fastlane/metadata/android/en-US/images/phoneScreenshots/`, where F-Droid expects them: the listing and the README share the same images. Made by hand; screenshots generated without a phone (Roborazzi) the day they go stale too fast.
- Generated blocks: the version (`versionName`), the tool types (name and description of one line, drawn from their texts in each language), the AI providers, the themes, the languages and the minimal Android (`minSdk`). A script (`scripts/make_readme.py`) fills them between comments (`<!-- tools -->` … `<!-- /tools -->`); `./run test` checks that they are up to date and fails otherwise, without ever rewriting the README. No badge.

## The first launch

After the demo's installation, one screen, once only: the icon and the name, the promise, a line about the demo ("the life of Camille over twelve weeks, to see what it looks like once built"), and two buttons:

- "Start: First steps" launches the band;
- "Explore on my own" leads to the home screen, where the Guide's icon carries its dot, and a brief message says where the Guide is.

No slideshow, nothing about the AI.

## The words

- A **chapter** of the Guide: an introduction, steps, a conclusion. A chapter that has steps to do is a **tutorial**.
- A **step**: a goal, an explanation, a target screen, what it waits for.
- The **path**: the gestures that lead to the target screen, from the open screen (band) or from the home screen (Guide).

## The language

That of every text the user reads: `docs/WRITING.md`.

## The start of a tutorial

A tutorial that starts at its first step (from the home screen, from a tutorial's end, from a chapter, from "Start again") opens a dialog: its title, its introduction, and the band shown ("it gives the step to do, and unfolds on a touch to explain it"). "Let's go"; "Later" hides the tutorial and says where it waits. A resumption does not open it.

## The band

Laid above all the screens, at the bottom, in the place `LongOperationBar` holds for a long operation.

- Folded, two lines and an arrow: the tutorial's title; "2/6 · Add a run in the Tracking". The arrow says it unfolds; the whole band is touched.
- Unfolded, in addition: the step's explanation; the path from the open screen ("From here: open the zone Running", or "You are in Kitchen: go back to the home screen"); "Hide the tutorial".
- No button that takes to the screen: the person does the gestures themselves. A button that cannot be found in use may blink, case by case.
- A done step ticks itself, the next one takes its place.

## The path

Each kind of screen of the stack knows its parent and the sentence that leads there (a zone: the home screen, "open the zone {name}"; a tool: its zone, "touch {name}"; the AI providers: the Settings). From the open screen, the path goes back up to the screen common with the target, then back down, one sentence per screen. In the Guide, it always starts from the home screen ("Home › Demo › Running › Runs › +"). The same data for both. In a screen, a button is named as it looks: "the gear at the top right".

## The steps

Three kinds:

- **go**: done when the target screen is at the top of the stack;
- **do**: done when the expected operation passes through the dispatcher, with its origin (`currentOrigin()`) — `tool_data.create` on a tool, of user origin; "ask the AI to create a tool" waits for `tools.create` of AI origin, and does not tick if the user creates it themselves; or when the user sends the AI the expected message, which goes through `AIOrchestrator`, not the dispatcher (`SentMessages`): any message (`"await": {"message": "any"}`), or one carrying a pointer to a tool (`"pointer_tool"`);
- **read**: an explanation and "Next".

A step waits for an operation done while it is underway, not a state already there. It can:

- target what a previous step created (the entry in the tool created two steps earlier);
- ask that its target be outside the demo (a zone created in the Demo group disappears at the next update).

A step is written as data, not code. A check of `./run test` verifies that each expected operation exists in the service registry and each target screen in the stack.

## The Guide

- A page, opened by a fixed icon at the top of the home screen (`book-open`). It reads in a single scroll, in parts (`GuidePart`): "Discovery", the tour of what the app does, whose chapters are numbered and form the journey; "Getting started", each feature to use it for real; "The tools", one chapter per tool type, all together; "Going further", the subtleties. A part without a chapter does not show: Going further waits for its first chapters.
- Each chapter has the same line in all the parts: icon, title, duration, state ("To do", "3/6 · resume", "Done"). Open, it reads whole (all its steps, paths from the home screen), with "Do it on the app" if it has steps to do and "Start again" if it is finished.
- Nothing is locked: every chapter opens at any time.
- While a journey tutorial is proposed and not done, or a tutorial is hidden in progress, the Guide's icon carries `WaitingMark`'s dot.

## Journey (Discovery)

1. **First steps**, without an AI.
2. **Connect an AI**: choose a provider, paste their key, ask for an overview without attaching anything, then point at their tool with a pointer in a simple mention, each gesture named (the target, the zone, the tool, Confirm).
3. **Build with the AI**: have it create a tool, validate what it proposes, let it link two tools (a variable, a Goal).
4. **Routines**: its two uses, launch it (the AI alone) or discuss it (the bubble); create it, write its message (a step that waits for the editor's save, refused without a message), launch it, read its execution, then the bubble.

The content of tutorials 2 to 4 is written at their building: it names screens the stack does not have yet.

### First steps

1. **Go**: open the Demo group, then the zone Running. The explanation presents Camille, their zone, their tools sorted by groups.
2. **Do**: add a run in *Runs* (`tool_data.create` on `demo-course-runs`, of user origin).
3. **Go, then read**: open *Km per week* — your run is there, and the goal *Training week* counted it. The tools feed each other.
4. **Do**: create your own zone, outside the demo: the explanation sends to the "Ungrouped" section, at the very bottom of the home screen, and to its +, the Demo group being Camille's.
5. **Do**: add a tool to it; the explanation guides to a List, where an entry is only a name to tick, and says that only the name is to fill in (the Tracking and its shortcuts are seen at step 2, in the demo; without a shortcut, its entry intimidates).
6. **Do**: add a first entry to it, in step 5's tool: an item of the List.

## Getting started

One chapter per subject, read in any order one wants, tutorial optional:

- **Configure a tool**, at the head of Getting started, as a tutorial, without an AI: the configuration opened by a long press, the icon, its colour and the display mode, an extra field filled in an entry, what the validation and "Always send" change for the AI, then the export of its entries as CSV.

- Organize: groups of zones on the home screen, groups of tools in a zone, the layout of the tiles — the same gesture at two levels, in a single chapter.
- The tools, in their own part after Getting started, which is also the glance at the tools: each line gives the type's `tagline` under its name, and the part has for subtitle the advice to look at what the right tool provides before assembling several of them. One chapter per type, provided by the tool type in its folder (`tools/<type>/`), beside its texts, and discovered as the tool is; a new tool arrives with its chapter. Its text is the type's `usage` (what it is for, what it already does, not for), the same the AI reads.
- Connect tools: variables, readings, pointers.
- Talk to the AI: the pointer, the questions and validations, the cost.
- Appearance: the User interface screen alone (theme, mode, hue, size, one column, sounds); an icon's colour is set in its zone's or its tool's configuration.
- Your data: the Data management screen (automatic backup, export, restore, reset) and the History, where a change is undone; the import of a table belongs to the Structured data chapter.
- External access: the connector.
- When something goes wrong: logs, bug report.

A Getting started chapter covers its screen and nothing else: what another screen shows goes in that screen's chapter.

A Getting started chapter names everything the screens it covers show: a button, a mention. The Discovery, a journey, stays short and is not held to it.

- A form's settings (a tool's config, the Settings, an AI provider) are declared (`SettingNode`), so checked: the chapter declares the forms it covers (`"covers"` in `chapters.json`), and `scripts/check_guide.py` refuses a setting of these forms whose label appears in none of its texts, in each language. An added setting breaks `./run test` until the Guide talks about it.
- A screen's buttons are declared nowhere: when writing the chapter, one goes through the screen button by button.
- A form a chapter can cover is named in `FORMS` (`scripts/check_guide.py`): its file, and the place where its declaration begins; a label is read there at its place, just after the setting's name. A new form is added there with the first chapter that covers it.

## The flow

- While the journey is not finished, the end of every tutorial (a Getting started chapter done ahead included) opens a dialog that proposes the journey's next tutorial: "Continue: Connect an AI" / "Later".
- The journey finished, the end of each tutorial proposes "Open the Guide", which opens the page at the Getting started title, or "Later".
- "Later" says where the rest is: "in the Guide, with the book at the top of the home screen".

## The demo

- At the launch of a tutorial that needs it, if it is absent: reinstall it in one button, or skip its steps. No version without the demo of these steps.
- The dialog that removes it names the tutorials that use it, says that their steps in the demo will be skipped, and that it reinstalls from Settings › Demo.
- In the Guide, while it is absent, a chapter that needs it carries "Requires the demo" and "Reinstall".
- A chapter needs the demo if one of its steps targets a screen or a tool of the demo: deduced, never declared.
- No demo tool is marked "Always send to the AI" (`always_send`): its data would go out in every prompt of whoever installs it, at their expense and without their having chosen it. The setting shows in a tutorial, on a user's tool. `DemoContentTest` checks it (decided 2026-10-17).

## The progress

In the app's settings: for each chapter, the step underway or "Done"; the band hidden or not. It survives the updates and the demo's reinstallation, leaves with the backup, disappears at the reset (which restarts First steps). "Start again" per chapter, no global reset.

## Where it lives

- The chapters: `assets/guide/chapters.json`, one step per object (kind, address of the place, expected operation with its origin and its parameters, what it keeps). "Outside the demo" has no form of its own: a step targets the zone created at the step before (`{zone}`), never a demo zone. One chapter per tool type is added in the The tools part, drawn from the tool's name, `tagline`, `usage` and Guide texts; "The tools at a glance" is added at the head of Getting started.
- The texts: `core/strings/sources/guide.xml` and `guide-fr.xml`, named after the chapter and the step; a tool chapter's, `usage`, `guide_config` and `guide_screen`, in the tool's texts.
- A Going further chapter is a text to read, without steps: `"text": true` in `chapters.json`, its body `guide_<chapitre>_body`, written in the same formatting. First subject: the relative dates (`relative_dates`).
- Their formatting (`Markup`, shown by `MarkupText`), a bit of Markdown: `## ` a subtitle, `- ` a list line, `{nom}` at the head of a list line the Lucide icon of the button it names, never elsewhere; short sentences under subtitles rather than blocks. `scripts/check_guide.py` checks it. A chapter's parts (steps, config, screen) are title tiles with their icon.
- The engine: `core/guide/Guide.kt`, which listens to the successful operations asked from outside (`PassedOperations`, emitted by the coordinator) and the stack; the progress in the settings category `guide`.
- The check: `scripts/check_guide.py`, in `./run test`.
