#!/usr/bin/env python3
"""Check that no code cuts a day or a week behind the user's calendar's back.

The app has one calendar: the hour its days start at, the first day of its weeks, as the user
set them (docs/reference.md, « Un seul calendrier »). A day, a week, a month and their bounds are
the app's: TimeResolver gives them, from the periods of core/selection/Periods.kt. With days
starting at 4:00, midnight belongs to the day before; a bound made by hand at midnight, or a week
made to start on Monday, is right only for the user whose calendar happens to say so -- and a
test run at 15:00 never sees the difference. This check keeps those bounds from coming back,
one spelling at a time.

Allowed: the files where those calculations live, and the migrations, which compute as their
version did, from the calendar stored then. A few lines are allowed by what they say, each for
its reason: content of the demo, which places what it tells on the calendar.

Usage:
    ./scripts/check_day_bounds.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java"

ALLOWED_FILES = {
    "app/treelune/core/selection/TimeResolver.kt",
    "app/treelune/core/selection/Periods.kt",
    "app/treelune/core/utils/DateUtils.kt",
}
ALLOWED_DIRS = ("app/treelune/core/versioning/",)

# (file, text the line holds, why it may)
ALLOWED_LINES = [
    ("app/treelune/core/demo/DemoData.kt", "fun day(weeksBack: Int, weekday: Int)",
     "an entry written by hand names its ISO weekday, placed within the week of the app"),
    ("app/treelune/core/demo/DemoData.kt", '"next_saturday" ->',
     "a task of the demo due next Saturday, a content of the demo"),
]

# Each cuts a day or a week by hand, or reads a calendar the user did not set.
FORBIDDEN = [
    (re.compile(r"\batStartOfDay\b"), "a day's start at midnight -- TimeResolver.startOf, or DateUtils for a day shown"),
    (re.compile(r"\bLocalTime\.MIDNIGHT\b"), "midnight as a day's start -- TimeResolver.startOf"),
    (re.compile(r"\btruncatedTo\(\s*ChronoUnit\.DAYS\s*\)"), "a day cut at midnight -- TimeResolver.startOf / dateOf"),
    (re.compile(r"\.atTime\(\s*\d"), "an hour written by hand on a day -- TimeResolver.startOf for a day's start"),
    (re.compile(r"\bDayOfWeek\.(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\b"), "a week's first day written by hand -- DateUtils.weekStart, or a week's start from TimeResolver"),
    (re.compile(r"\b(previousOrSame|nextOrSame)\b"), "a week's start made by hand -- TimeResolver, Relative(WEEK, 0, START)"),
    (re.compile(r"\bWeekFields\b"), "the week of the phone's language -- DateUtils.weekStart"),
    (re.compile(r"\bLocalDate\.now\("), "today read by hand -- TimeResolver.today for the day of the app, DateUtils.calendarToday for an instant's"),
    (re.compile(r"(?<![\w.])TimeResolver\("), "a calendar built by hand -- TimeResolver.at, which reads the one the user set"),
]


def main():
    found = []
    for path in sorted(SOURCES.rglob("*.kt")):
        relative = path.relative_to(SOURCES).as_posix()
        if relative in ALLOWED_FILES or relative.startswith(ALLOWED_DIRS):
            continue
        allowed = [text for file, text, _ in ALLOWED_LINES if file == relative]
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if line.lstrip().startswith(("//", "*", "/*")):
                continue
            if any(text in line for text in allowed):
                continue
            for pattern, advice in FORBIDDEN:
                if pattern.search(line):
                    found.append(f"{relative}:{number}: {advice}")

    # An allowed line that no longer exists is an exception left behind
    for file, text, _ in ALLOWED_LINES:
        if text not in (SOURCES / file).read_text(encoding="utf-8"):
            found.append(f"{file}: the allowed line holding {text!r} is gone -- take it out of ALLOWED_LINES")

    for entry in found:
        print(entry)
    if found:
        print(f"{len(found)} day or week bound(s) made outside the user's calendar.")
        return 1
    print("Every day and week is cut by the user's calendar.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
