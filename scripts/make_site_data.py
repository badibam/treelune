#!/usr/bin/env python3
"""Write under site/ what the site's pages read from the app, or check that it is up to date.

The site (docs/design/website.md) shows the app's tools, themes and demo, and says them with the
app's own words. What can be read from the sources is read here, never copied by hand:

    site/private/generated.json   the tool types (name, tagline, icon), the themes' names, the
                                  demo's Italian zone (its tools, its routine), the Lucide icons the
                                  page draws, the lowest Android, and the fingerprint of the demo's
                                  files
    site/public_html/fonts/       the theme fonts the opening band writes the themes' names in,
                                  each with its licence

The site's own texts (texts.php) also speak of the demo: the Italian zone's tools, the Sunday
lesson. The fingerprint is the demo as it was when they were written: once the demo changes, the
check says so, and the texts are read again by hand before the fingerprint is renewed by a write.

Usage:
    ./scripts/make_site_data.py           write the data and the fonts
    ./scripts/make_site_data.py --check   say what is stale, and fail if anything is; ./run
                                          release-tag runs it, and it never writes
"""

import hashlib
import json
import os
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune"
TOOL_REGISTRY = SOURCES / "core" / "tools" / "ToolTypeScanner.kt"
THEME_REGISTRY = SOURCES / "core" / "themes" / "ThemeScanner.kt"
DEMO = ROOT / "app" / "src" / "main" / "assets" / "demo"
FONTS = ROOT / "app" / "src" / "main" / "res" / "font"
FONT_LICENCES = ROOT / "app" / "src" / "main" / "assets" / "fonts"
LUCIDE = ROOT / "third_party" / "lucide" / "icons"
GRADLE = ROOT / "app" / "build.gradle.kts"

SITE = ROOT / "site"
LAYOUT = SITE / "private" / "layout.json"
GENERATED = SITE / "private" / "generated.json"
SITE_FONTS = SITE / "public_html" / "fonts"

# The demo's zone the site walks through, tool by tool.
ZONE = "demo-italian"

# A language of the site and the suffix of the strings files it reads.
LANGUAGES = {"en": "", "fr": "-fr"}

# The Android release each API level the app may require as its lowest.
ANDROID_RELEASES = {
    24: "7.0", 25: "7.1", 26: "8.0", 27: "8.1", 28: "9", 29: "10", 30: "11", 31: "12",
    32: "12L", 33: "13", 34: "14", 35: "15", 36: "16",
}

STRING = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)
REGISTRY_ENTRY = re.compile(r'"([a-z_]+)" to \w+')
DEFAULT_ICON = re.compile(r'getDefaultIconName\(\): String(?: = |\s*\{\s*return )"([a-z0-9-]+)"')


def strings(folder, suffix):
    """The strings of a tool's or a theme's folder in one language, unescaped as Android does."""
    text = (folder / f"strings{suffix}.xml").read_text(encoding="utf-8")
    return {name: value.replace("\\'", "'").replace('\\"', '"') for name, value in STRING.findall(text)}


def registry(path):
    """The ids a registry lists, in its order."""
    return REGISTRY_ENTRY.findall(path.read_text(encoding="utf-8"))


def default_icon(tool_id):
    """The Lucide name a tool type gives a new tool, read in its ToolType class."""
    for path in (SOURCES / "tools" / tool_id).glob("*ToolType.kt"):
        found = DEFAULT_ICON.search(path.read_text(encoding="utf-8"))
        if found:
            return found.group(1)
    raise SystemExit(f"make_site_data: no default icon for the tool type {tool_id}")


def tools():
    """The tool types, in the registry's order: name and tagline in each language, icon."""
    out = []
    for id_ in registry(TOOL_REGISTRY):
        texts = {lang: strings(SOURCES / "tools" / id_, suffix) for lang, suffix in LANGUAGES.items()}
        out.append({
            "id": id_,
            "icon": default_icon(id_),
            "name": {lang: t.get("display_name") or t["name"] for lang, t in texts.items()},
            "tagline": {lang: t["tagline"] for lang, t in texts.items()},
        })
    return out


def themes():
    """The themes, in the registry's order, by their name in each language."""
    return [
        {"id": id_, "name": {lang: strings(SOURCES / "themes" / id_, suffix)["name"] for lang, suffix in LANGUAGES.items()}}
        for id_ in registry(THEME_REGISTRY)
    ]


def lowest_android():
    """The lowest Android release the app installs on, from its minSdk."""
    min_sdk = int(re.search(r"minSdk = (\d+)", GRADLE.read_text(encoding="utf-8")).group(1))
    return ANDROID_RELEASES[min_sdk]


def demo_text(texts, value):
    """A demo value as the app shows it: "@key" is read in the language's texts."""
    if isinstance(value, str) and value.startswith("@"):
        return texts[value[1:]]
    return value


def zone():
    """The demo's Italian zone: its name, its tools in the demo's order, and its routine."""
    structure = json.loads((DEMO / "structure.json").read_text(encoding="utf-8"))
    texts = {lang: json.loads((DEMO / f"texts-{lang}.json").read_text(encoding="utf-8")) for lang in LANGUAGES}
    found = [z for z in structure["zones"] if z["id"] == ZONE]
    if not found:
        raise SystemExit(f"make_site_data: the demo has no zone {ZONE}")
    named = lambda value: {lang: demo_text(t, value) for lang, t in texts.items()}
    routines = [a for a in structure["automations"] if a["zone_id"] == ZONE]
    if len(routines) != 1:
        raise SystemExit(f"make_site_data: the zone {ZONE} has {len(routines)} routines, the site shows one")
    routine = routines[0]
    return {
        "id": ZONE,
        "name": named(found[0]["name"]),
        "tools": [
            {
                "id": t["id"],
                "type": t["tooltype"],
                "icon": t["config"]["icon_name"],
                "color": (t["config"].get("icon_color") or "").lower() or None,
                "name": named(t["config"]["name"]),
            }
            for t in structure["tools"] if t["zone_id"] == ZONE
        ],
        "routine": {
            "id": routine["id"],
            "icon": routine["icon_name"],
            "color": (routine.get("icon_color") or "").lower() or None,
            "name": named(routine["name"]),
        },
    }


def icon_body(name):
    """A Lucide icon's drawing: what its SVG holds inside the <svg> element."""
    path = LUCIDE / f"{name}.svg"
    if not path.exists():
        raise SystemExit(f"make_site_data: no Lucide icon {name}")
    svg = path.read_text(encoding="utf-8")
    body = re.search(r"<svg[^>]*>(.*)</svg>", svg, re.S).group(1)
    return " ".join(line.strip() for line in body.strip().splitlines())


def icons(layout, tool_types, demo_zone):
    """Every icon the page draws, by name."""
    names = {t["icon"] for t in tool_types}
    names |= {t["icon"] for t in demo_zone["tools"]} | {demo_zone["routine"]["icon"]}
    for section in ("privacy", "videos", "pieces"):
        names |= {item["icon"] for item in layout[section]}
    return {name: icon_body(name) for name in sorted(names)}


def fingerprint():
    """The demo's files as they are, one digest each."""
    return {
        str(path.relative_to(DEMO)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(DEMO.rglob("*")) if path.is_file()
    }


def fonts(layout):
    """Each theme font the page loads, with its licence: published path → source."""
    out = {}
    for theme in layout["themes"]:
        if theme["font"]:
            out[theme["font"]] = FONTS / theme["font"]
            out[f"{theme['licence']}-OFL.txt"] = FONT_LICENCES / theme["licence"] / "OFL.txt"
    return out


def layout_problems(layout, tool_types, demo_zone, theme_list):
    """What layout.json says that the app contradicts: a tool type or a theme it lacks or invents,
    a tile of a tool the zone no longer has."""
    problems = []
    app_tools = {t["id"] for t in tool_types}
    site_tools = [t["id"] for t in layout["tools"]]
    for id_ in sorted(app_tools - set(site_tools)):
        problems.append(f"layout.json: the tool type {id_} is missing from tools")
    for id_ in sorted(set(site_tools) - app_tools):
        problems.append(f"layout.json: tools names {id_}, which the app does not have")
    app_themes = {t["id"] for t in theme_list}
    site_themes = [t["id"] for t in layout["themes"]]
    for id_ in sorted(app_themes - set(site_themes)):
        problems.append(f"layout.json: the theme {id_} is missing from themes")
    for id_ in sorted(set(site_themes) - app_themes):
        problems.append(f"layout.json: themes names {id_}, which the app does not have")
    zone_tools = {t["id"] for t in demo_zone["tools"]}
    for id_ in sorted(zone_tools ^ set(layout["zone"]["tiles"])):
        problems.append(f"layout.json: zone.tiles and the demo's zone disagree on {id_}")
    for t in demo_zone["tools"]:
        if not t["color"] and t["id"] not in layout["zone"]["colors"]:
            problems.append(f"layout.json: {t['id']} has no colour in the demo, nor in zone.colors")
    return problems


def write(path, text):
    """Written aside then renamed: an interrupted write never leaves half a file."""
    partial = path.with_name(path.name + ".part")
    partial.write_text(text, encoding="utf-8")
    os.replace(partial, path)
    print(f"{path.relative_to(ROOT)}: written")


def main():
    check = "--check" in sys.argv[1:]
    layout = json.loads(LAYOUT.read_text(encoding="utf-8"))
    tool_types = tools()
    theme_list = themes()
    demo_zone = zone()
    data = {
        "_comment": "Written by scripts/make_site_data.py from the app's sources; never edited by hand.",
        "tools": tool_types,
        "themes": theme_list,
        "zone": demo_zone,
        "icons": icons(layout, tool_types, demo_zone),
        "android": lowest_android(),
        "demo_fingerprint": fingerprint(),
    }
    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    problems = layout_problems(layout, tool_types, demo_zone, theme_list)
    font_files = fonts(layout)

    if check:
        current = json.loads(GENERATED.read_text(encoding="utf-8")) if GENERATED.exists() else {}
        if current.get("demo_fingerprint") != data["demo_fingerprint"]:
            problems.append("the demo changed since the site was written: read site/private/texts.php again, then ./scripts/make_site_data.py")
        current.pop("demo_fingerprint", None)
        data.pop("demo_fingerprint")
        if current != data:
            problems.append("site/private/generated.json is stale: ./scripts/make_site_data.py")
        for name, source in font_files.items():
            published = SITE_FONTS / name
            if not published.exists() or published.read_bytes() != source.read_bytes():
                problems.append(f"site/public_html/fonts/{name} is stale: ./scripts/make_site_data.py")
        for problem in problems:
            print(problem)
        if problems:
            sys.exit(1)
        print("site data: up to date")
        return

    if problems:
        for problem in problems:
            print(problem)
        sys.exit(1)
    write(GENERATED, text)
    SITE_FONTS.mkdir(parents=True, exist_ok=True)
    for name, source in font_files.items():
        shutil.copyfile(source, SITE_FONTS / name)
    for stale in SITE_FONTS.iterdir():
        if stale.name not in font_files:
            stale.unlink()
    print(f"site/public_html/fonts/: {len(font_files)} files")


if __name__ == "__main__":
    main()
