# Video scenarios

One file per video of `site/private/layout.json`, named by its id. `./run site-shots en` (then `fr`) plays it, one step per Enter, the next step always shown:

- `setup: …` the screen to start from, before filming
- `start` / `stop` the filming, touches shown on screen in between
- `do: …` a gesture made by hand; Enter once it is done
- `type en: …` / `type fr: …` a text adb types, word by word, into the field already touched; the other language's line is skipped
- `#` a comment

The `do:` steps say what the demo shows; a step that no longer matches the app is corrected here.
