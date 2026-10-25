#!/usr/bin/env python3
"""Check that every screen the chat's button floats over leaves room for it at its end.

The chat's button floats at the bottom right of the places one reads (Place.chatButton): the
home screen, a zone, a tool, the Guide, an automation. A screen of such a place puts
`.chatButtonSpace()` after its `verticalScroll`, or `chatButtonEnd()` in a lazy list's bottom
content padding; without it, its last line hides under the button, and only a phone shows it.

The places marked in Place.kt are mapped to their screens' files below; a place marked and not
mapped fails, so that a new place gets its screen named here. A tool place stands for every tool
type's screen, found from the tool type's getUsageScreen, and for the screens it opens in its
place (the Journal's entry, a Flashcards session): every composable of the tool's package it
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
    "automation": [APP / "core" / "ai" / "ui" / "automation" / "AutomationScreen.kt"],
    "execution": [APP / "core" / "ai" / "ui" / "automation" / "ExecutionDetailScreen.kt"],
    "task": [APP / "core" / "tasks" / "ui" / "TaskScreen.kt"],
    "task_run": [APP / "core" / "tasks" / "ui" / "TaskRunScreen.kt"],
}

ROOM = re.compile(r"\.chatButtonSpace\(\)|chatButtonEnd\(\)")
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
    """Each tool type's screens under the button: the composable its getUsageScreen calls, and
    the composables of its package reached from it, dialogs left out; by (file, name, body)."""
    screens = []
    for tool_type in sorted(TOOLS.glob("*/*ToolType.kt")):
        text = tool_type.read_text(encoding="utf-8")
        body = text[text.index("override fun getUsageScreen"):]
        called = re.search(r"\b([A-Z]\w+Screen)\(", body).group(1)
        known = functions(tool_type.parent)
        if called not in known:
            raise SystemExit(f"check_chat_space: no file holds {called}, the screen of {tool_type.parent.name}")
        seen, todo = set(), [called]
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
        if kind == "tool":
            for path, name, body in tool_screens():
                checked.add(path)
                if SCROLLS.search(body) and not ROOM.search(body) and "HideChatButton" not in body:
                    problems.append(f"{path.relative_to(ROOT)}: {name} scrolls with no room for the chat's button (chatButtonSpace / chatButtonEnd)")
            continue
        files = SCREENS.get(kind)
        if files is None:
            problems.append(f"place '{kind}' has the chat's button and no screen named in check_chat_space.py")
            continue
        for path in files:
            if path in checked:
                continue
            checked.add(path)
            if not ROOM.search(path.read_text(encoding="utf-8")):
                problems.append(f"{path.relative_to(ROOT)}: no room for the chat's button (chatButtonSpace / chatButtonEnd)")
    for problem in problems:
        print(problem)
    if problems:
        print(f"{len(problems)} screen(s) without room for the chat's button")
        return 1
    print(f"Every screen under the chat's button leaves it room ({len(checked)} files).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
