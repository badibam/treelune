#!/usr/bin/env python3
"""Check that every name the design guide cites exists in the code.

The design guide (`ai_design_guide` in ai_prompt_chunks.xml, read by the AI with DESIGN_GUIDE)
tells the AI which pieces answer a need and how they connect, citing the app's own names between
backticks: commands, Task step kinds and keys, trigger kinds, tool operations, formula functions,
values a filter compares. A name renamed or removed in the code would leave the guide sending the
AI to something that no longer exists, and nothing else reads the guide to say so.

Each name between backticks must be one of:
- a command the AI may send (AICommands.kt), written in capitals: `DESIGN_GUIDE`;
- a tool type's operation, `tooltype.operation`, declared by its ToolOperation: `messages.execute`;
- a formula function, `name()`, its canonical name in Formula.Function: `now()`;
- a string literal of the app's Kotlin sources, as the code writes it: `create_entry`, `filled`.

Usage:
    ./scripts/check_design_guide.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune"
CHUNKS = SOURCES / "core" / "strings" / "sources" / "ai_prompt_chunks.xml"
COMMANDS = SOURCES / "core" / "ai" / "data" / "AICommands.kt"
FORMULA = SOURCES / "core" / "variables" / "Formula.kt"
TOOLS = SOURCES / "tools"

GUIDE = re.compile(r'<string name="ai_design_guide"><!\[CDATA\[(.*?)\]\]></string>', re.DOTALL)
CITED = re.compile(r"`([^`]+)`")


def guide_text():
    match = GUIDE.search(CHUNKS.read_text(encoding="utf-8"))
    if match is None:
        sys.exit(f"no ai_design_guide in {CHUNKS.relative_to(ROOT)}")
    return match.group(1)


def commands():
    return set(re.findall(r'(?:query|action)\(\s*"([A-Z_]+)"', COMMANDS.read_text(encoding="utf-8")))


def operations():
    """Each tool type's operations, as `tooltype.operation`; the tool type is its folder."""
    found = set()
    for folder in TOOLS.iterdir():
        for source in folder.glob("*ToolType.kt"):
            text = source.read_text(encoding="utf-8")
            for name in re.findall(r'ToolOperation\(\s*(?:name\s*=\s*)?"([a-z_]+)"', text):
                found.add(f"{folder.name}.{name}")
    return found


def functions():
    text = FORMULA.read_text(encoding="utf-8")
    block = text[text.index("enum class Function"):]
    return {f"{name}()" for name in re.findall(r'[A-Z]+\("([a-z]+)"', block[:block.index(";")])}


def literals():
    found = set()
    for source in SOURCES.rglob("*.kt"):
        found.update(re.findall(r'"([A-Za-z0-9_.]+)"', source.read_text(encoding="utf-8")))
    return found


def main():
    known_commands, known_operations, known_functions, known_literals = commands(), operations(), functions(), literals()
    unknown = []
    for name in sorted(set(CITED.findall(guide_text()))):
        if re.fullmatch(r"[A-Z_]+", name):
            ok = name in known_commands
        elif name.endswith("()"):
            ok = name in known_functions
        elif re.fullmatch(r"[a-z]+\.[a-z_]+", name) and (TOOLS / name.split(".")[0]).is_dir():
            ok = name in known_operations
        else:
            ok = name in known_literals
        if not ok:
            unknown.append(name)
    if unknown:
        print("The design guide cites names the code does not have:")
        for name in unknown:
            print(f"  `{name}`")
        return 1
    print("Every name the design guide cites exists in the code.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
