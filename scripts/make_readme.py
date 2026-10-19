#!/usr/bin/env python3
"""Fill the READMEs' generated blocks and the store listing's list of tools from the sources, or
check that they are up to date.

Each README (README.md in English, README.fr.md in French) is written by hand, except for a few
blocks between HTML comments that say what the code says and would go stale otherwise:

    <!-- tools -->      the tool types, in the registry's order, each with its name and tagline
    <!-- providers -->  the AI providers one brings a key for, by their name
    <!-- themes -->     the themes, in the registry's order, each with its name and tagline
    <!-- status -->     the version, the lowest Android, the languages

A block ends at its closing comment (<!-- /tools -->). Names and taglines come from the tools'
and the themes' own strings, in the README's language; a tool or a theme added to its registry
appears without touching the README.

The store listing's long description (fastlane/metadata/android/<locale>/full_description.txt)
is written by hand down to its line that announces the tools ("The pieces:"): everything after it
is the tool types, one line each with its tagline, in plain text. The file is published as it is,
so it carries no comment to mark the block.

The OpenAI-compatible provider is left out of the providers' block: it is a way of talking, not
a vendor, and the README's text names it.

Usage:
    ./scripts/make_readme.py           write the blocks
    ./scripts/make_readme.py --check   say which blocks are stale, and fail if any is; ./run test
                                       runs this, and never writes a README or a listing on its own
"""

import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune"
TOOL_REGISTRY = SOURCES / "core" / "tools" / "ToolTypeScanner.kt"
THEME_REGISTRY = SOURCES / "core" / "themes" / "ThemeScanner.kt"
PROVIDER_KINDS = SOURCES / "core" / "ai" / "providers" / "AIProviderKinds.kt"
PROVIDERS = SOURCES / "core" / "ai" / "providers"
GRADLE = ROOT / "app" / "build.gradle.kts"
STRING_SOURCES = SOURCES / "core" / "strings" / "sources"

# A README and the language its strings are read in: the suffix of the strings file.
READMES = {
    ROOT / "README.md": "",
    ROOT / "README.fr.md": "-fr",
}

# A store listing's long description, the language its strings are read in, and its line after
# which the tools are listed.
LISTING = ROOT / "fastlane" / "metadata" / "android"
LISTINGS = {
    LISTING / "en-US" / "full_description.txt": ("", "The pieces:"),
    LISTING / "fr-FR" / "full_description.txt": ("-fr", "Les pièces :"),
}

LISTING_LIMITS = {"title.txt": 50, "short_description.txt": 80, "full_description.txt": 4000}

# The Android release each API level the app may require as its lowest.
ANDROID_RELEASES = {
    24: "7.0", 25: "7.1", 26: "8.0", 27: "8.1", 28: "9", 29: "10", 30: "11", 31: "12",
    32: "12L", 33: "13", 34: "14", 35: "15", 36: "16",
}

# The words the status block is made of, in each README's language.
WORDS = {
    "": {
        "version": "Version", "android": "Android", "or_later": "or later",
        "languages": "Languages:", "lang": {"en": "English", "fr": "French"},
    },
    "-fr": {
        "version": "Version", "android": "Android", "or_later": "ou plus récent",
        "languages": "Langues :", "lang": {"en": "anglais", "fr": "français"},
    },
}

STRING = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)
REGISTRY_ENTRY = re.compile(r'"([a-z_]+)" to \w+')


def strings(folder, suffix):
    """The strings of a tool's or a theme's folder in one language, unescaped as Android does."""
    text = (folder / f"strings{suffix}.xml").read_text(encoding="utf-8")
    return {name: value.replace("\\'", "'").replace('\\"', '"') for name, value in STRING.findall(text)}


def registry(path):
    """The ids a registry lists, in its order."""
    return REGISTRY_ENTRY.findall(path.read_text(encoding="utf-8"))


def named_lines(folder_root, ids, suffix):
    """One line per id: its name in bold and its tagline."""
    lines = []
    for id_ in ids:
        texts = strings(folder_root / id_, suffix)
        name = texts.get("display_name") or texts["name"]
        lines.append(f"- **{name}** — {texts['tagline']}")
    return lines


def listing_tools(suffix):
    """One line per tool type, in plain text: a store listing shows no Markdown."""
    return [line.replace("**", "") for line in named_lines(SOURCES / "tools", registry(TOOL_REGISTRY), suffix)]


def fill_listing(text, heading):
    """The listing with everything after its heading line replaced by the tools; None without it."""
    lines = text.split("\n")
    if heading not in lines:
        return None
    return lines, len(lines) - 1 - lines[::-1].index(heading)


def providers(suffix):
    """The vendors' names, in one language: each kind the app knows, in its order, but the server of
    the user's, which the README names apart; a kind's name is its string ai_provider_kind_<kind>."""
    texts = (STRING_SOURCES / f"shared{suffix}.xml").read_text(encoding="utf-8")
    shared = {name: value.replace("\\'", "'") for name, value in STRING.findall(texts)}
    names = []
    for cls in re.findall(r"\{ (\w+Provider)\(context\) \}", PROVIDER_KINDS.read_text(encoding="utf-8")):
        for path in PROVIDERS.glob("*.kt"):
            text = path.read_text(encoding="utf-8")
            start = text.find(f"class {cls}(")
            if start < 0:
                continue
            kind = re.search(r'const val KIND = "([a-z]+)"', text[start:]).group(1)
            if kind != "compatible":
                names.append(shared[f"ai_provider_kind_{kind}"])
            break
        else:
            raise SystemExit(f"make_readme: no class {cls} under {PROVIDERS}")
    return names


def status(suffix):
    """The version, the lowest Android and the languages, one line each."""
    gradle = GRADLE.read_text(encoding="utf-8")
    version = re.search(r'versionName = "([^"]+)"', gradle).group(1)
    min_sdk = int(re.search(r"minSdk = (\d+)", gradle).group(1))
    words = WORDS[suffix]
    # English is the default strings' language; every shared-xx.xml adds one.
    codes = ["en"] + sorted(p.stem.split("-")[1] for p in STRING_SOURCES.glob("shared-??.xml"))
    languages = ", ".join(words["lang"][code] for code in codes)
    return [
        f"- {words['version']} {version}",
        f"- {words['android']} {ANDROID_RELEASES[min_sdk]} {words['or_later']}",
        f"- {words['languages']} {languages}",
    ]


def blocks(suffix):
    """Each block's content in one language."""
    return {
        "tools": named_lines(SOURCES / "tools", registry(TOOL_REGISTRY), suffix),
        "providers": [", ".join(f"**{name}**" for name in providers(suffix))],
        "themes": named_lines(SOURCES / "themes", registry(THEME_REGISTRY), suffix),
        "status": status(suffix),
    }


def fill(text, contents):
    """The README with each block's content replaced; the names of the blocks it lacks."""
    missing = []
    for name, lines in contents.items():
        pattern = re.compile(rf"(<!-- {name} -->\n)(.*?)(<!-- /{name} -->)", re.S)
        if not pattern.search(text):
            missing.append(name)
            continue
        body = "\n".join(lines) + "\n"
        text = pattern.sub(lambda m: m.group(1) + body + m.group(3), text)
    return text, missing


def write(path, text):
    """Written aside then renamed: an interrupted write never leaves half a file."""
    partial = path.with_name(path.name + ".part")
    partial.write_text(text, encoding="utf-8")
    os.replace(partial, path)
    print(f"{path.relative_to(ROOT)}: written")


def main():
    check = "--check" in sys.argv[1:]
    failed = False
    for readme, suffix in READMES.items():
        current = readme.read_text(encoding="utf-8")
        contents = blocks(suffix)
        filled, missing = fill(current, contents)
        for name in missing:
            print(f"{readme.name}: no block <!-- {name} -->")
            failed = True
        if filled == current:
            continue
        if check:
            stale = [name for name in contents if name not in missing and fill(current, {name: contents[name]})[0] != current]
            print(f"{readme.name}: stale blocks {', '.join(stale)} — run ./scripts/make_readme.py, then read the result")
            failed = True
        else:
            write(readme, filled)
    for listing, (suffix, heading) in LISTINGS.items():
        current = listing.read_text(encoding="utf-8")
        found = fill_listing(current, heading)
        if found is None:
            print(f"{listing.relative_to(ROOT)}: no line “{heading}” to list the tools after")
            failed = True
            continue
        lines, at = found
        filled = "\n".join(lines[:at + 1] + listing_tools(suffix)) + "\n"
        if filled == current:
            continue
        if check:
            print(f"{listing.relative_to(ROOT)}: stale list of tools — run ./scripts/make_readme.py, then read the result")
            failed = True
        else:
            write(listing, filled)
    # F-Droid's limits on a listing's texts, in characters
    for locale in sorted(LISTING.iterdir()):
        for name, limit in LISTING_LIMITS.items():
            length = len((locale / name).read_text(encoding="utf-8").strip())
            if length > limit:
                print(f"{(locale / name).relative_to(ROOT)}: {length} characters, more than {limit}")
                failed = True
    if check and not failed:
        print("READMEs and store listings: generated blocks up to date")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
