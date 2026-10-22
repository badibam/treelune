# Video scenarios

One file per video of `site/private/layout.json`, named by its id. `./run site-shots en` (then `fr`) plays it turn by turn: for each gesture made by hand, what to do, Enter once it is done, and what the script then does on its own (type a text, start or stop filming), done at once before it hands back:

- `setup: …` the screen to start from, before filming
- `start` / `stop` the filming, touches shown on screen in between
- `do: …` a gesture made by hand, like `setup`; Enter once it is done
- `type en: …` / `type fr: …` a text adb types, word by word, into the field already touched; the other language's line is skipped
- `#` a comment

The `do:` steps say what the demo shows; a step that no longer matches the app is corrected here.

What a scenario asks the AI to create is absent from the demo: the phone holds the demo alone, and a demo tool other tools use (Sleep, read by two charts and the discussion) cannot be deleted to make room for it.
