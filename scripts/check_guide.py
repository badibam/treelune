#!/usr/bin/env python3
"""Check the Guide's chapters (assets/guide/chapters.json) against the code they name.

A tutorial waits for operations and sends the user to places: an operation renamed, a place
gone, a text forgotten, and the step never ends, with nothing else in the build to say so. For
each chapter and step:

- an operation waited for is `resource.operation`, the resource registered (ServiceRegistry)
  and the operation answered by its service (a `"operation" ->` branch in its file);
- a message waited for says what it carries as MessageWait names it (GuideChapters.kt);
- an origin is a Source;
- a place's address begins with a kind Place.of reads, a name in braces kept by an earlier step;
- every text the Guide reads by name exists in English and in French (guide.xml, guide-fr.xml):
  a chapter's title and introduction, a tutorial's end, each step's goal and explanation, a read
  chapter's body ("text": true);
- every demo id a step names, in its place or in what it waits for, is a zone, a tool or a Task
  the demo installs (assets/demo/structure.json), and a tool's or a Task's place names the zone
  the demo puts it in:
  the demo rewritten, a tutorial would send the user to a place that is not there;
- the Guide's texts, and the tool types' texts it shows (usage, guide_config, guide_screen), keep
  to its markup (core/markup/Markup.kt): an icon only as `{name}` at the head of a list line, a name of the
  icon vocabulary (assets/icons/index.json), and no brace anywhere else;
- a chapter that covers a form ("covers", a name of FORMS) names each of its settings: the label of
  every setting the form declares appears in the chapter's texts, in each language. The labels are
  read from the form's declaration (its SettingNode list), a setting added there breaking the check
  until the chapter speaks of it.

Usage:
    ./scripts/check_guide.py
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHAPTERS = ROOT / "app" / "src" / "main" / "assets" / "guide" / "chapters.json"
CORE = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune" / "core"
REGISTRY = CORE / "coordinator" / "ServiceRegistry.kt"
PLACE = CORE / "navigation" / "Place.kt"
SOURCE = CORE / "coordinator" / "Source.kt"
GUIDE_CHAPTERS = CORE / "guide" / "GuideChapters.kt"
TEXTS = {"English": CORE / "strings" / "sources" / "guide.xml", "French": CORE / "strings" / "sources" / "guide-fr.xml"}
SOURCES = ROOT / "app" / "src" / "main" / "java"
DEMO = ROOT / "app" / "src" / "main" / "assets" / "demo" / "structure.json"
DEMO_ID = re.compile(r"demo-[a-z0-9-]+")

KEPT = re.compile(r"\{([a-z_]+)\}")
ICONS = ROOT / "app" / "src" / "main" / "assets" / "icons" / "index.json"
TOOLS = CORE.parent / "tools"
TOOL_TEXTS = ("usage", "guide_config", "guide_screen")
LIST_ICON = re.compile(r"^- \{([a-z0-9-]+)\} ")

# The forms a chapter may cover: the file declaring its settings, and where the declaration begins,
# right before the bracket that opens it
FORMS = {
    "home_config": (CORE / "config" / "AppSettings.kt", r"AppSettingCategories\.MAIN_SCREEN -> listOf"),
    "zone_config": (CORE / "schemas" / "ZoneSettings.kt", r"fun nodes\(context: Context\): List<SettingNode> "),
    "ui_settings": (CORE / "config" / "AppSettings.kt", r"AppSettingCategories\.UI -> "),
    "external_access": (CORE / "config" / "AppSettings.kt", r"AppSettingCategories\.EXTERNAL_ACCESS -> listOf"),
}
SHARED = {"English": CORE / "strings" / "sources" / "shared.xml", "French": CORE / "strings" / "sources" / "shared-fr.xml"}
# A setting's label, the text given right after its name: FieldDefinition("name", s.shared("label")…),
# SettingNode.ListOf("name", text("label")…), and ZoneSettings' field("name", "label"…)
LABEL_CALL = re.compile(r'\b\w+\(\s*(?:"[^"]*"|[A-Za-z_][\w.]*)\s*,\s*(?:s\.shared|text)\("([a-z0-9_]+)"\)')
LABEL_RAW = re.compile(r'\bfield\(\s*"[^"]+"\s*,\s*"([a-z0-9_]+)"')
# A look's settings (core/look/Look.kt, Looks.nodes), spread into a declaration: its name, labelled by
# the owner's spec (nameLabel = "…", in the same file), then the labels every look shares
LOOK_NODES = re.compile(r"\bLooks\.nodes\(")
LOOK_NAME = re.compile(r'\bnameLabel\s*=\s*"([a-z0-9_]+)"')
LOOK_LABELS = ["label_description", "label_icon", "label_icon_color", "label_texture", "tools_config_label_display_mode"]


def markup_problems(where, text, icons):
    """What breaks the Guide's markup in one text, as its XML source writes it."""
    found = []
    for line in text.replace("\\n", "\n").split("\n"):
        line = line.strip()
        head = LIST_ICON.match(line)
        if head and head.group(1) not in icons:
            found.append(f"{where}: no icon '{head.group(1)}'")
        rest = line[head.end():] if head else line
        if "{" in rest or "}" in rest:
            found.append(f"{where}: a brace outside the head of a list line: « {line} »")
    return found


def declaration(path, start):
    """The text of a form's declaration: from the bracket after [start] to the one closing it."""
    source = path.read_text(encoding="utf-8")
    found = re.search(start, source)
    if not found:
        return None
    opening = found.end()
    close = {"(": ")", "{": "}"}[source[opening]]
    depth, i, quoted = 0, opening, False
    while i < len(source):
        c = source[i]
        if quoted:
            if c == "\\":
                i += 1
            elif c == '"':
                quoted = False
        elif c == '"':
            quoted = True
        elif c == source[opening]:
            depth += 1
        elif c == close:
            depth -= 1
            if depth == 0:
                return source[opening:i + 1]
        i += 1
    return None


def strings(path):
    """Each text of a strings file by its key, as the screen shows it."""
    return {k: v.replace("\\'", "'").replace('\\"', '"') for k, v in
            re.findall(r'<string name="([^"]+)">(.*?)</string>', path.read_text(encoding="utf-8"), re.S)}


def services():
    """Each registered resource and the file of its service."""
    registry = REGISTRY.read_text(encoding="utf-8")
    found = {}
    for resource, cls in re.findall(r'"([a-z_]+)" to ::(\w+)', registry):
        files = [p for p in SOURCES.rglob("*.kt") if re.search(rf"\bclass {cls}\b", p.read_text(encoding="utf-8"))]
        found[resource] = files[0] if files else None
    return found


def main():
    chapters = json.loads(CHAPTERS.read_text(encoding="utf-8"))["chapters"]
    registered = services()
    kinds = set(re.findall(r'^\s+"([a-z_]+)" ->', PLACE.read_text(encoding="utf-8"), re.M))
    enum = re.search(r"enum class MessageWait\(.*?\n\}", GUIDE_CHAPTERS.read_text(encoding="utf-8"), re.S).group(0)
    message_waits = set(re.findall(r'^\s+[A-Z_]+\("([a-z_]+)"\)', enum, re.M))
    origins = set(re.findall(r"^\s+([A-Z]+),?$", SOURCE.read_text(encoding="utf-8"), re.M))
    texts = {lang: set(re.findall(r'<string name="([^"]+)"', path.read_text(encoding="utf-8"))) for lang, path in TEXTS.items()}
    demo = json.loads(DEMO.read_text(encoding="utf-8"))
    demo_zones = {z["id"] for z in demo["zones"]}
    demo_tools = {t["id"]: t["zone_id"] for t in demo["tools"]}
    demo_tasks = {t["id"]: t["zone_id"] for t in demo["tasks"]}
    problems = []

    icons = {icon["name"] for icon in json.loads(ICONS.read_text(encoding="utf-8"))["icons"]}
    sources = list(TEXTS.values()) + sorted(TOOLS.glob("*/strings*.xml"))
    for path in sources:
        for key, text in re.findall(r'<string name="([^"]+)">(.*?)</string>', path.read_text(encoding="utf-8"), re.S):
            if path in TEXTS.values() or key in TOOL_TEXTS:
                problems += markup_problems(f"{path.relative_to(ROOT)} {key}", text, icons)

    shared = {lang: strings(path) for lang, path in SHARED.items()}
    guide = {lang: strings(path) for lang, path in TEXTS.items()}
    for chapter in chapters:
        cid = chapter["id"]
        for form in chapter.get("covers", []):
            if form not in FORMS:
                problems.append(f"{cid}: no form '{form}' to cover")
                continue
            body = declaration(*FORMS[form])
            if body is None:
                problems.append(f"{cid}: the declaration of '{form}' is not found where FORMS says")
                continue
            labels = LABEL_CALL.findall(body) + LABEL_RAW.findall(body)
            if LOOK_NODES.search(body):
                labels += LOOK_NAME.findall(FORMS[form][0].read_text(encoding="utf-8")) + LOOK_LABELS
            if not labels:
                problems.append(f"{cid}: no setting read in the declaration of '{form}'")
            for lang in TEXTS:
                said = " ".join(v for k, v in guide[lang].items() if k.startswith(f"guide_{cid}_")).casefold()
                for key in labels:
                    label = shared[lang].get(key)
                    if label is None:
                        problems.append(f"{cid}: no {lang} text '{key}' for a setting of '{form}'")
                    elif label.casefold() not in said:
                        problems.append(f"{cid}: '{form}' setting « {label} » named in no {lang} text of the chapter")
        keys = [f"guide_{cid}_title", f"guide_{cid}_intro"]
        kept = set()
        for n, step in enumerate(chapter["steps"], start=1):
            where = f"{cid}, step {n}"
            keys += [f"guide_{cid}_{n}_goal", f"guide_{cid}_{n}_text"]
            target = step.get("target")
            named = [target or ""] + list((step.get("await") or {}).get("params", {}).values())
            for demo_id in (i for text in named for i in DEMO_ID.findall(text)):
                if demo_id not in demo_zones and demo_id not in demo_tools and demo_id not in demo_tasks:
                    problems.append(f"{where}: '{demo_id}' is no zone, tool or Task of the demo")
            # A tool's place and a Task's end on their zone; a Task's run's on its Task, then its zone
            placed = {"tool": demo_tools, "task": demo_tasks, "task_run": demo_tasks}
            if target is not None and target.split("/")[0] in placed:
                parts = target.split("/")
                owner, zone = parts[-2], parts[-1]
                where_put = placed[parts[0]]
                if owner in where_put and where_put[owner] != zone:
                    problems.append(f"{where}: the demo puts '{owner}' in '{where_put[owner]}', not '{zone}'")
            if target is not None:
                if target.split("/")[0] not in kinds:
                    problems.append(f"{where}: no place of kind '{target.split('/')[0]}'")
                for name in KEPT.findall(target):
                    if name not in kept:
                        problems.append(f"{where}: '{{{name}}}' kept by no earlier step")
            wait = step.get("await")
            if (step["kind"] == "do") != (wait is not None):
                problems.append(f"{where}: a do step waits for an operation or a message, and only it")
            if wait and "message" in wait:
                if wait["message"] not in message_waits:
                    problems.append(f"{where}: no message wait '{wait['message']}'")
            elif wait:
                resource, _, operation = wait["operation"].partition(".")
                service = registered.get(resource)
                if resource not in registered:
                    problems.append(f"{where}: no service for resource '{resource}'")
                elif service is None or f'"{operation}" ->' not in service.read_text(encoding="utf-8"):
                    problems.append(f"{where}: '{resource}' answers no operation '{operation}'")
                if wait["origin"] not in origins:
                    problems.append(f"{where}: no origin '{wait['origin']}'")
                for value in wait.get("params", {}).values():
                    for name in KEPT.findall(value):
                        if name not in kept:
                            problems.append(f"{where}: '{{{name}}}' kept by no earlier step")
            kept |= set(step.get("keep", {}).keys())
        if chapter["steps"]:
            keys.append(f"guide_{cid}_outro")
        if chapter.get("text"):
            if chapter["steps"]:
                problems.append(f"{cid}: a read chapter has no steps")
            keys.append(f"guide_{cid}_body")
        for lang, have in texts.items():
            for key in keys:
                if key not in have:
                    problems.append(f"{cid}: no {lang} text '{key}'")

    for problem in problems:
        print(problem)
    if problems:
        print(f"{len(problems)} problem(s) in the Guide's chapters")
        return 1
    print(f"The Guide's {len(chapters)} chapters agree with the code.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
