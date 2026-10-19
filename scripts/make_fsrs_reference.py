#!/usr/bin/env python3
"""Write the reference suites of reviews the Kotlin port of FSRS is tested against (FsrsTest).

Each suite is a card reviewed several times, each review a rating at an instant; the reference
implementation, py-fsrs, gives the card's state after each one: its stability, its difficulty and
when it is due. The suites cover a first review of each rating, reviews on time, late and early,
several in one day (FSRS's short-term formula), long gaps, three desired retentions, and random
suites from a fixed seed.

The scheduler is the one the flashcards tool runs (docs/design/flashcards.md): FSRS's default
parameters, no learning nor relearning steps — a session brings a failed card back itself, every
review giving a due date in days — and no fuzz, so that one suite always gives the same dates.

The output, app/src/test/resources/fsrs/reference.json, is committed: the tests never need this
script nor py-fsrs. Moving to another FSRS = installing its py-fsrs, setting PY_FSRS below and
Fsrs.VERSION, running this script again, committing.

Usage (py-fsrs at the version below, in a virtualenv for instance):
    pip install fsrs==6.3.2
    ./scripts/make_fsrs_reference.py
"""

import json
import random
from datetime import datetime, timedelta, timezone
from importlib.metadata import version
from pathlib import Path

from fsrs import Card, Rating, Scheduler

PY_FSRS = "6.3.2"
ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "app" / "src" / "test" / "resources" / "fsrs" / "reference.json"

START = datetime(2026, 1, 5, 9, 30, tzinfo=timezone.utc)
MINUTE = timedelta(minutes=1)
HOUR = timedelta(hours=1)
DAY = timedelta(days=1)


def millis(moment):
    return int(moment.timestamp() * 1000)


def run(retention, reviews):
    """reviews: (offset from the previous review, or from START for the first; rating)."""
    scheduler = Scheduler(desired_retention=retention, learning_steps=(), relearning_steps=(), enable_fuzzing=False)
    card = Card()
    at = START
    out = []
    for offset, rating in reviews:
        at = at + offset
        card, _ = scheduler.review_card(card, Rating[rating], review_datetime=at)
        out.append({
            "at": millis(at),
            "rating": rating.upper(),
            "stability": card.stability,
            "difficulty": card.difficulty,
            "due": millis(card.due),
        })
    return {"desired_retention": retention, "reviews": out}


def written():
    suites = []
    ratings = ["Again", "Hard", "Good", "Easy"]

    # A first review of each rating, then a review a day later of each rating
    for first in ratings:
        for second in ratings:
            suites.append(run(0.9, [(timedelta(0), first), (DAY, second)]))

    # Several reviews in one day: the short-term formula, a failed card repeated in a session
    suites.append(run(0.9, [(timedelta(0), "Again"), (2 * MINUTE, "Again"), (3 * MINUTE, "Good"), (5 * MINUTE, "Good")]))
    suites.append(run(0.9, [(timedelta(0), "Good"), (10 * MINUTE, "Easy"), (HOUR, "Hard")]))
    suites.append(run(0.9, [(timedelta(0), "Good"), (3 * DAY, "Good"), (MINUTE, "Again"), (2 * MINUTE, "Good")]))

    # Reviews late, early and on long gaps
    suites.append(run(0.9, [(timedelta(0), "Good"), (10 * DAY, "Good"), (60 * DAY, "Good"), (400 * DAY, "Again"), (DAY, "Good")]))
    suites.append(run(0.9, [(timedelta(0), "Easy"), (12 * HOUR, "Good"), (25 * HOUR, "Hard"), (2 * DAY, "Easy")]))
    suites.append(run(0.9, [(timedelta(0), "Hard"), (5 * DAY + 7 * HOUR, "Hard"), (17 * DAY, "Again"), (DAY, "Hard"), (3 * DAY, "Good")]))

    # Reviews each on their due date, for each desired retention
    for retention in (0.8, 0.9, 0.95):
        for rating in ratings[1:]:
            scheduler = Scheduler(desired_retention=retention, learning_steps=(), relearning_steps=(), enable_fuzzing=False)
            card = Card()
            at = START
            reviews = []
            for _ in range(8):
                card, _ = scheduler.review_card(card, Rating[rating], review_datetime=at)
                reviews.append({
                    "at": millis(at), "rating": rating.upper(),
                    "stability": card.stability, "difficulty": card.difficulty, "due": millis(card.due),
                })
                at = card.due
            suites.append({"desired_retention": retention, "reviews": reviews})

    # Random suites, from a fixed seed
    pick = random.Random(20261019)
    for _ in range(40):
        retention = pick.choice((0.8, 0.85, 0.9, 0.95))
        reviews = []
        for i in range(pick.randint(2, 15)):
            offset = timedelta(0) if i == 0 else pick.choice((
                pick.randint(1, 50) * MINUTE,
                pick.randint(1, 30) * HOUR,
                pick.randint(1, 90) * DAY + pick.randint(0, 23) * HOUR,
            ))
            reviews.append((offset, pick.choice(ratings)))
        suites.append(run(retention, reviews))
    return suites


def main():
    found = version("fsrs")
    if found != PY_FSRS:
        raise SystemExit(f"py-fsrs {found} installed, the reference is written with {PY_FSRS}")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    document = {"py_fsrs": PY_FSRS, "suites": written()}
    temporary = OUTPUT.with_suffix(".json.part")
    temporary.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    temporary.replace(OUTPUT)
    print(f"{OUTPUT.relative_to(ROOT)}: {len(document['suites'])} suites")


if __name__ == "__main__":
    main()
