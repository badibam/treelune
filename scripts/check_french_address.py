#!/usr/bin/env python3
"""Check that the app's French texts address the user as tu.

The app's French texts (every `*-fr.xml` string source and the demo's `texts-fr.json`) address
the user as tu. README.fr.md, which speaks to whoever discovers the app, keeps vous, and so do
the English texts. A vous, votre or vos that slips back into a French text shows on a French
phone and nowhere else: not in a compile, not in a test run, not in an English screen. Nothing
would say so -- this check is the only net.

The check reads every French string source and the demo's French texts, and refuses the three
words as whole words, whatever their case. The exceptions below are the only allowed ones, each
with its reason; adding one is a decision that lives here.

Usage:
    ./scripts/check_french_address.py
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune"
DEMO = ROOT / "app" / "src" / "main" / "assets" / "demo" / "texts-fr.json"

STRING = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.DOTALL)
FORBIDDEN = re.compile(r"\b(vous|votre|vos)\b", re.IGNORECASE)

# The noun rendez-vous (an appointment): the vous inside it is not the pronoun, so the phrase
# is removed from a text before the words are looked for. An imperative « rendez-vous » would
# not be covered; a French text should say « rends-toi » anyway, which nothing here checks.
RENDEZ_VOUS = re.compile(r"rendez-vous", re.IGNORECASE)

# A key whose text is left out of the check entirely, with the reason. Named by the file's name
# and the key, because a key like « tagline » lives in many files.
EXCEPTED_KEYS = {
    "texts-fr.json": {
        "phrase_table": "the French translation of an Italian phrase in the demo's phrasebook: "
                        "the vous translates the polite « per favore », it is not the app "
                        "addressing the user",
        "phrase_bill": "the French translation of an Italian phrase in the demo's phrasebook: "
                       "the vous translates the polite « per favore », it is not the app "
                       "addressing the user",
    },
}


def texts_of(path):
    """Return {key: text} for one source file, XML strings or a JSON object of strings."""
    if path.suffix == ".json":
        data = json.loads(path.read_text(encoding="utf-8"))
        return {key: value for key, value in data.items() if isinstance(value, str)}
    return {m.group(1): m.group(2).strip() for m in STRING.finditer(path.read_text(encoding="utf-8"))}


def main():
    problems = []
    files = sorted(SOURCES.rglob("*-fr.xml")) + [DEMO]

    for path in files:
        excepted = EXCEPTED_KEYS.get(path.name, {})
        for key, text in texts_of(path).items():
            if key in excepted:
                continue
            cleaned = RENDEZ_VOUS.sub(" ", text)
            if FORBIDDEN.search(cleaned):
                problems.append(f"{path.relative_to(ROOT)}: {key}: {text}")

    if problems:
        print(f"{len(problems)} vous, votre or vos in the French texts:\n")
        for problem in problems:
            print(f"  {problem}")
        return 1

    print("The French texts address the user as tu")
    return 0


if __name__ == "__main__":
    sys.exit(main())
