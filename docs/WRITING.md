# Writing for the user

Every text the user reads follows these rules: the app's texts (`shared`, tools, themes, Guide, notifications), the demo (`texts-*.json`), the READMEs, the release notes and the F-Droid listing. The reader is discovering the app and does not know its code: nothing of the register of the internal docs and the commits.

## The sentence

- One sentence, one idea, with a real verb. Never a thing acting in someone's place: not "the routine keeps a message", nor "the message opens a conversation", but "a message is prepared once", "touch the bubble to start a conversation".
- No avalanche of you and your (in French tu, ton, ta, tes, or vous, votre, vos): an article is enough when it is clear who is meant ("a first zone", "the list"), and "one" or a passive turn lightens the sentence.
- The why before the how: what it is for, then where to touch. A button is named in a sentence that says why to touch it.
- A colon announces a list or an example, never half a sentence; no semicolon.
- A list of examples says that it is one (« comme », « par exemple », « ou autre chose », “such as”, “or anything else”): bare after a colon (« Des fiches : aliments, livres, plantes »), it reads as everything there is.
- A word of the app (entry, zone, pointer, attempt) is explained where it first appears, then stays the same: "configuration", not "config".
- No precision against a misunderstanding the reader does not have yet.
- No repeated formula ("Then Next."): the button can be seen.
- Each language's quotation marks: « » in French, “ ” in English.

## Who we talk to

- In French, the user is addressed as tu everywhere: the app's texts (the `*-fr.xml`), the demo (`texts-fr.json`) and `README.fr.md`, which shows the presentation sentences of the tools and the themes among its own. `scripts/check_french_address.py` refuses vous, votre and vos in these texts at every `./run test`. English says you.
- French is written first; English is its translation.
