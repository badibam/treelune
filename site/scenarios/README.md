# Video scenarios

One file per video of `site/private/layout.json`, named by its id. `./run site-shots en` (then `fr`) shows its story, then plays it turn by turn: for each gesture made by hand, whether it is filming and for how long, what to do, Enter once it is done, and what follows (what the script does on its own, typing a text, starting or stopping the filming, done at once before it hands back, then the next gesture). The filming is scrcpy's, on the computer, its window showing the phone; a still longer than 2 s is cut to 1 s when the video is encoded:

- `story: …` what the video shows, from its first screen to its last, read before it starts
- `setup: …` the screen to start from, before filming
- `start` / `stop` the filming, touches shown on screen in between
- `do: …` a gesture made by hand, like `setup`; Enter once it is done
- `type en: …` / `type fr: …` a text adb types, word by word, into the field already touched; the other language's line is skipped
- `#` a comment

The `do:` steps say what the demo shows; a step that no longer matches the app is corrected here.

What a scenario asks the AI to create is absent from the demo: the phone holds the demo alone, and a demo tool other tools use (Sleep, read by two charts and the discussion) cannot be deleted to make room for it.
