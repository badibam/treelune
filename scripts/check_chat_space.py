#!/usr/bin/env python3
"""Check that every screen the chat's button floats over leaves room for it at its end, and hands
its scroll to the « back to top » button facing it.

The chat's button floats at the bottom right of the places one reads (Place.chatButton): the
home screen, a zone, a tool, the Guide, a routine. A screen of such a place scrolls by
`.pageScroll()`, which leaves the room and hands the scroll over, or is a lazy list holding
`rememberPageListState()` with `chatButtonEnd()` in its bottom content padding; without it, its last
line hides under the button, or « back to top » never shows, and only a phone shows it.

The places marked in Place.kt are mapped to their screens' files below; a place marked and not
mapped fails, so that a new place gets its screen named here. A tool place and a page inside a
tool stand for every tool type's screen, found from the tool type's getUsageScreen and its
PartScreen, and for the screens it opens in its place (the Journal's entry, a Flashcards
session): every composable of the tool's package it
calls, and those they call, is read, and one that scrolls on its own needs the room too. A dialog
(a function named …Dialog) floats above the button and is not followed; a function that hides
the button (HideChatButton) needs no room.

Usage:
    ./scripts/check_chat_space.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune"
PLACE = APP / "core" / "navigation" / "Place.kt"
TOOLS = APP / "tools"

# A place's kind, and the files of the screens drawn for it
SCREENS = {
    "home": [APP / "core" / "ui" / "screens" / "MainScreen.kt"],
    "zone": [APP / "core" / "ui" / "screens" / "ZoneScreen.kt"],
    "guide": [APP / "core" / "guide" / "ui" / "GuideScreens.kt"],
    "chapter": [APP / "core" / "guide" / "ui" / "GuideScreens.kt"],
    "routine": [APP / "core" / "ai" / "ui" / "routine" / "RoutineScreen.kt"],
    "execution": [APP / "core" / "ai" / "ui" / "routine" / "ExecutionDetailScreen.kt"],
    "task": [APP / "core" / "tasks" / "ui" / "TaskScreen.kt"],
    "task_run": [APP / "core" / "tasks" / "ui" / "TaskRunScreen.kt"],
}

PAGE_SCROLL = re.compile(r"\.pageScroll\(")
PAGE_LIST = re.compile(r"rememberPageListState\(\)")
LIST_ROOM = re.compile(r"chatButtonEnd\(\)")


def has_room(body):
    """Whether [body] scrolls as a page of a place one reads: by pageScroll, or a lazy list handed over and leaving room."""
    return bool(PAGE_SCROLL.search(body) or (PAGE_LIST.search(body) and LIST_ROOM.search(body)))
SCROLLS = re.compile(r"\.verticalScroll\(|\bLazyColumn\(|\bLazyVerticalGrid\(")
TOP_FUN = re.compile(r"^(?:(?:private|internal) )?fun (?:[\w.]+\.)?([A-Z]\w*)\(", re.M)


def functions(package):
    """The top-level composables of a tool's package, by name: their file and their body, which
    runs to the next line closing at the margin."""
    found = {}
    for path in sorted(package.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for match in TOP_FUN.finditer(text):
            end = text.find("\n}", match.end())
            found[match.group(1)] = (path, text[match.start():end if end != -1 else len(text)])
    return found


def tool_screens():
    """Each tool type's screens under the button: the composable its getUsageScreen calls, those
    its PartScreen calls (its pages, Place.ToolPart), and the composables of its package reached
    from them, dialogs left out; by (file, name, body)."""
    screens = []
    for tool_type in sorted(TOOLS.glob("*/*ToolType.kt")):
        text = tool_type.read_text(encoding="utf-8")
        body = text[text.index("override fun getUsageScreen"):]
        called = re.search(r"\b([A-Z]\w+Screen)\(", body).group(1)
        known = functions(tool_type.parent)
        if called not in known:
            raise SystemExit(f"check_chat_space: no file holds {called}, the screen of {tool_type.parent.name}")
        roots = [called]
        if "override fun PartScreen" in text:
            part = text[text.index("override fun PartScreen"):]
            part = part[:part.find("\n    }")]
            roots += [n for n in re.findall(r"\b([A-Z]\w*)\(", part[len("override fun PartScreen("):]) if n in known]
        seen, todo = set(), roots
        while todo:
            name = todo.pop()
            if name in seen or name.endswith("Dialog"):
                continue
            seen.add(name)
            path, fun_body = known[name]
            screens.append((path, name, fun_body))
            todo += [n for n in re.findall(r"\b([A-Z]\w*)\(", fun_body[len(name) + 5:]) if n in known]
    return screens


def marked_kinds():
    """The kinds of the places Place.kt marks with the chat's button."""
    text = PLACE.read_text(encoding="utf-8")
    kinds = []
    for block in re.split(r"\n    (?=(?:data class|object) )", text):
        kind = re.search(r'override val kind = "([a-z_]+)"', block)
        if kind and "override val chatButton = true" in block:
            kinds.append(kind.group(1))
    return kinds


def main():
    problems = []
    checked = set()
    for kind in marked_kinds():
        # A tool's pages are read with its screens, from its PartScreen
        if kind == "tool_part":
            continue
        if kind == "tool":
            for path, name, body in tool_screens():
                checked.add(path)
                if SCROLLS.search(body) and not has_room(body) and "HideChatButton" not in body:
                    problems.append(f"{path.relative_to(ROOT)}: {name} scrolls with no room for the chat's button (pageScroll, or rememberPageListState with chatButtonEnd)")
            continue
        files = SCREENS.get(kind)
        if files is None:
            problems.append(f"place '{kind}' has the chat's button and no screen named in check_chat_space.py")
            continue
        for path in files:
            if path in checked:
                continue
            checked.add(path)
            if not has_room(path.read_text(encoding="utf-8")):
                problems.append(f"{path.relative_to(ROOT)}: no room for the chat's button (pageScroll, or rememberPageListState with chatButtonEnd)")
    for problem in problems:
        print(problem)
    if problems:
        print(f"{len(problems)} screen(s) without room for the chat's button")
        return 1
    print(f"Every screen under the chat's button leaves it room ({len(checked)} files).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
