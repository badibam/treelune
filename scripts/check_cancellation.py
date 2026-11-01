#!/usr/bin/env python3
"""Check that no coroutine code catches its own cancellation as an error.

A coroutine is stopped by a CancellationException: a screen closed, a Task stopped, a timeout
reached. A `catch (e: Exception)` or `catch (e: Throwable)` around a suspending call catches it
too, and then logs an ERROR, answers a failure and lets the code go on, where the coroutine should
have stopped. Such a catch is preceded, in the same try, by a catch that lets the cancellation
through (`catch (e: CancellationException) { throw e }`), or rethrows it first in its own body
(`if (e is CancellationException) throw e`).

Where it counts: the body of a `suspend fun`, and the block of a coroutine builder or a
suspending scope (launch, async, LaunchedEffect, withContext, withTimeout…). A catch outside
them, in plain code, catches no cancellation. A lambda inside these blocks is counted with them:
reading Kotlin's types is beyond this script, and a catch there costs one line at worst.

Usage:
    ./scripts/check_cancellation.py           list what lets no cancellation through
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java"

BROAD = re.compile(r"\bcatch\s*\(\s*\w+\s*:\s*(?:kotlin\.|java\.lang\.)?(?:Exception|Throwable)\s*\)")
CANCEL_CATCH = re.compile(r"catch\s*\(\s*\w+\s*:\s*[\w.]*CancellationException\s*\)\s*$")
RETHROWN = re.compile(r"^\s*\{\s*(?://[^\n]*\n\s*)*if\s*\(\s*\w+\s+is\s+[\w.]*CancellationException\s*\)\s*throw\b")
SUSPEND_FUN = re.compile(r"\bsuspend\s+fun\b")
BUILDERS = re.compile(r"\b(?:launch|async|LaunchedEffect|withContext|withTimeout|withTimeoutOrNull|coroutineScope|"
                      r"supervisorScope|runBlocking|produceState|repeatOnLifecycle|flow|channelFlow|callbackFlow)\b")


def blanked(text):
    """[text] with its comments and strings blanked, same length: braces in them count for nothing."""
    out = list(text)
    i, n = 0, len(text)
    while i < n:
        if text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
            out[i:j] = " " * (j - i)
            i = j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out[i:j] = [c if c == "\n" else " " for c in text[i:j]]
            i = j
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out[i + 3:j - 3] = [c if c == "\n" else " " for c in text[i + 3:j - 3]]
            i = j
        elif text[i] == '"':
            j = i + 1
            while j < n and text[j] != '"' and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            out[i + 1:j] = " " * (j - i - 1)
            i = j + 1
        elif text[i] == "'":
            # A character literal, on its line and short ('a', '\\n', '\\u00e9'); an apostrophe
            # anything else (in a string a template reopened) is left as it is
            end = text.find("'", i + 1 + (text[i + 1] == "\\" if i + 1 < n else 0))
            if 0 < end - i <= 8 and "\n" not in text[i:end]:
                out[i + 1:end] = " " * (end - i - 1)
                i = end + 1
            else:
                i += 1
        else:
            i += 1
    return "".join(out)


def matching(code, open_at):
    """The index of the brace closing the one at [open_at]."""
    depth = 0
    for i in range(open_at, len(code)):
        if code[i] == "{":
            depth += 1
        elif code[i] == "}":
            depth -= 1
            if depth == 0:
                return i
    return len(code)


def opening(code, close_at):
    """The index of the brace opening the one closed at [close_at]."""
    depth = 0
    for i in range(close_at, -1, -1):
        if code[i] == "}":
            depth += 1
        elif code[i] == "{":
            depth -= 1
            if depth == 0:
                return i
    return 0


def body_after(code, start):
    """The brace opening the body that follows [start] (a signature, a builder's arguments), or None."""
    depth = 0
    for i in range(start, len(code)):
        c = code[i]
        if c in "([":
            depth += 1
        elif c in ")]":
            depth -= 1
        elif c == "{" and depth <= 0:
            return i
        elif c == "\n" and depth <= 0 and code[start:i].rstrip().endswith(")") and SUSPEND_FUN.match(code, start) is None:
            # A builder named without a block (a reference, a call with no lambda)
            return None
    return None


def regions(code):
    """The spans of suspending code: suspend functions' bodies and coroutine blocks."""
    spans = []
    for pattern in (SUSPEND_FUN, BUILDERS):
        for m in pattern.finditer(code):
            brace = body_after(code, m.end())
            if brace is None:
                continue
            # A builder's block opens on its own line, never after a statement in between
            between = code[m.end():brace]
            if pattern is BUILDERS and (";" in between or between.count("\n") > 3):
                continue
            spans.append((brace, matching(code, brace)))
    return spans


def lets_cancellation_through(code, catch_at):
    """Whether the catch at [catch_at] lets a cancellation through: a catch for it before, in the same try, or a rethrow first in its body."""
    body = code.find("{", catch_at)
    if body >= 0 and RETHROWN.match(code, body):
        return True
    i = catch_at - 1
    while True:
        while i >= 0 and code[i].isspace():
            i -= 1
        if i < 0 or code[i] != "}":
            return False
        block = opening(code, i)
        before = code[:block].rstrip()
        if before.endswith("try"):
            return False
        clause = before[before.rfind("catch"):] if "catch" in before[-200:] else ""
        if CANCEL_CATCH.search(clause):
            return True
        if not clause:
            return False
        i = before.rfind("catch") - 1


def problems(path):
    text = path.read_text(encoding="utf-8")
    code = blanked(text)
    spans = regions(code)
    found = []
    for m in BROAD.finditer(code):
        if any(start < m.start() < end for start, end in spans) and not lets_cancellation_through(code, m.start()):
            found.append(code.count("\n", 0, m.start()) + 1)
    return found


def main():
    found = []
    for path in sorted(SOURCES.rglob("*.kt")):
        for line in problems(path):
            found.append(f"{path.relative_to(ROOT)}:{line}")
    if found:
        print("A catch of every exception in coroutine code lets no cancellation through:")
        for place in found:
            print(f"  {place}")
        print(f"{len(found)} place(s): put `catch (e: kotlinx.coroutines.CancellationException) {{ throw e }}` before it.")
        return 1
    print("Every catch of every exception in coroutine code lets the cancellation through.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
