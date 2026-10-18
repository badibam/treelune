#!/usr/bin/env python3
"""The themes' interface sounds: the library they are chosen from, the files the app plays, and
the check that both agree (docs/THEMES.md, Les sons).

A theme answers each UISignal with a sound or none. Its choice is a file of its own,
themes/<id>/sounds-choice.json, written by the sound bench (./run themes): the families kept for
the theme, the sounds shortlisted for each signal, the one chosen, a gain for the whole theme in
dB and an offset for a signal, and the theme's recipes -- a sound of the library retouched, several
mixed, or one made from nothing by sox. The sounds themselves are never committed: the sources
are listed in themes/sound-sources.json -- an archive taken whole, or files listed sound by sound,
each with its own page and, where they differ from its source's, its author and licence -- and
downloaded here, into tmp/sound-library/. What the app
plays is committed: res/raw/<id>_<signal>.flac, and their credits in themes/<id>/sounds.json.

Commands:
    library      download every source (each archive checked against its sha256), render each
                 theme's recipes, and write the catalogue the bench plays
                 (tmp/sound-library/catalogue.json), with the sound each theme plays now for
                 each signal. A source already there is kept.
    make <id>    write the theme's FLAC files and its sounds.json from its choice: each sound
                 decoded, brought to the theme's gain plus its signal's offset, written on 16 bits
                 at its own rate and in its own channels, which is what the phone's decoder hands
                 back. A signal without a choice has no file: the theme leaves it silent.
    check        without the library: for each theme with a choice, its sounds.json is the one
                 its choice gives, every sound chosen comes from a source of an accepted licence,
                 and its res/raw files are those of the signals chosen. Run by ./run test.

Needs ffmpeg and sox for library and make; check needs neither.
"""

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THEMES = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune" / "themes"
SOURCES = THEMES / "sound-sources.json"
RAW = ROOT / "app" / "src" / "main" / "res" / "raw"
SIGNALS_KT = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune" / "core" / "ui" / "sound" / "UISignal.kt"
LIBRARY = ROOT / "tmp" / "sound-library"
CATALOGUE = LIBRARY / "catalogue.json"

# Free for F-Droid; CC BY asks only for the credit sounds.json gives.
LICENCES = {"CC0 1.0", "CC BY 4.0"}
AUDIO = {".ogg", ".wav", ".flac", ".mp3"}
# A sound's id is its source's title and its file name; a recipe's, this prefix and its name.
SEP = " · "
RECIPE = "recipe"
# Some sites refuse the default agent of urllib.
AGENT = "treelune-theme-sounds/1.0 (the interface sounds of the app's themes)"


def signals():
    """The signals' file names (confirm, scroll_end...), read from UISignal itself: one list."""
    body = SIGNALS_KT.read_text(encoding="utf-8").split("enum class UISignal", 1)[1].split("}", 1)[0]
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    return [name.lower() for name in re.findall(r"\b([A-Z][A-Z_]+)\b", body)]


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path, document):
    """Replaced whole, so that a write cut short leaves the former file."""
    part = path.with_name(path.name + ".part")
    part.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    os.replace(part, path)


def sources():
    return {s["title"]: s for s in load(SOURCES)["sources"]}


def slug(title):
    return re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")


def choices():
    """Each theme with a choice, by id."""
    return {p.parent.name: load(p) for p in sorted(THEMES.glob("*/sounds-choice.json"))}


# ----------------------------------------------------------------------------------------------
# The library
# ----------------------------------------------------------------------------------------------

def fetch(source):
    """The source's sounds in tmp/sound-library/<slug>/: an archive's audio folder, or each file
    of a source listed sound by sound."""
    target = LIBRARY / slug(source["title"])
    if "archive" in source:
        return fetch_archive(source, target)
    return fetch_files(source, target)


def fetch_archive(source, target):
    """An archive's audio folder, made in a .part folder renamed at the end, so that the folder's
    presence says it is complete."""
    if target.is_dir():
        return target
    print(f"downloading {source['title']}")
    with tempfile.TemporaryDirectory() as tmp:
        archive = Path(tmp) / "archive.zip"
        with urllib.request.urlopen(source["archive"]) as response, open(archive, "wb") as out:
            shutil.copyfileobj(response, out)
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        if digest != source["sha256"]:
            sys.exit(f"{source['title']}: the archive's sha256 is {digest}, not the one listed")
        part = target.with_name(target.name + ".part")
        shutil.rmtree(part, ignore_errors=True)
        with zipfile.ZipFile(archive) as z:
            for name in z.namelist():
                inner = Path(name)
                if not name.startswith(source["audio"]) or inner.suffix.lower() not in AUDIO:
                    continue
                # Only the part under the audio folder, never a path climbing out of it.
                relative = Path(name[len(source["audio"]):])
                if ".." in relative.parts or relative.is_absolute():
                    sys.exit(f"{source['title']}: refused path {name}")
                out = part / relative
                out.parent.mkdir(parents=True, exist_ok=True)
                out.write_bytes(z.read(name))
        os.replace(part, target)
    return target


def fetch_files(source, target):
    """Each listed file as <name><its extension>, checked against its sha256 and written whole, so
    that a file's presence says it is complete: a file added to the list later is the only one
    downloaded."""
    target.mkdir(parents=True, exist_ok=True)
    for one in source["files"]:
        if not re.fullmatch(r"[a-z0-9_-]+", one["name"]):
            sys.exit(f"{source['title']}: refused name {one['name']!r}")
        path = target / (one["name"] + Path(urllib.parse.urlparse(one["url"]).path).suffix.lower())
        if path.suffix not in AUDIO:
            sys.exit(f"{source['title']}: {one['name']} is not a sound file ({one['url']})")
        if path.is_file():
            continue
        print(f"downloading {source['title']}{SEP}{one['name']}")
        request = urllib.request.Request(one["url"], headers={"User-Agent": AGENT})
        with urllib.request.urlopen(request, timeout=120) as response:
            data = response.read()
        digest = hashlib.sha256(data).hexdigest()
        if digest != one["sha256"]:
            sys.exit(f"{source['title']}{SEP}{one['name']}: its sha256 is {digest}, not the one listed")
        part = path.with_name(path.name + ".part")
        part.write_bytes(data)
        os.replace(part, path)
    return target


def licence(source, name):
    """A sound's licence: its file's own in a source listed sound by sound, else its source's."""
    for one in source.get("files", []):
        if one["name"] == name:
            return one.get("licence", source.get("licence"))
    return source.get("licence")


def family(source, relative):
    if source["family"] == "folder":
        return relative.parent.name or source["title"]
    return re.sub(r"[_-]?\d+$", "", relative.stem)


def duration(path):
    out = subprocess.run(["soxi", "-D", str(path)], capture_output=True, text=True, check=True).stdout
    return round(float(out), 3)


def library_sounds():
    """Every sound of the downloaded sources, by id: its file, its source, its family."""
    found = {}
    for title, source in sources().items():
        folder = LIBRARY / slug(title)
        for path in sorted(p for p in folder.rglob("*") if p.suffix.lower() in AUDIO):
            relative = path.relative_to(folder)
            found[f"{title}{SEP}{path.stem}"] = {"file": path, "source": title, "family": family(source, relative)}
    return found


def recipe_file(theme, name):
    return LIBRARY / "recipes" / theme / f"{name}.wav"


def render(recipe, sounds, out):
    """A recipe written to [out]: its inputs mixed (none for a sound made from nothing), then its
    sox effects."""
    inputs = recipe.get("inputs", [])
    for one in inputs:
        if one not in sounds:
            sys.exit(f"recipe input {one!r} is not in the library")
    head = ["-n"] if not inputs else (["-m"] if len(inputs) > 1 else []) + [str(sounds[i]["file"]) for i in inputs]
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["sox", *head, str(out), *map(str, recipe.get("effects", []))], check=True)


def task_library():
    LIBRARY.mkdir(parents=True, exist_ok=True)
    for source in sources().values():
        names = [one["name"] for one in source.get("files", [])] or [None]
        for name in names:
            if licence(source, name) not in LICENCES:
                what = source["title"] + (f"{SEP}{name}" if name else "")
                sys.exit(f"{what}: licence {licence(source, name)} is not one the app takes ({', '.join(sorted(LICENCES))})")
        fetch(source)
    sounds = library_sounds()
    entries = []
    for id_, s in sounds.items():
        entries.append({"id": id_, "source": s["source"], "family": s["family"],
                        "file": s["file"].relative_to(ROOT).as_posix(), "seconds": duration(s["file"])})
    for theme, choice in choices().items():
        for name, recipe in choice.get("recipes", {}).items():
            out = recipe_file(theme, name)
            render(recipe, sounds, out)
            entries.append({"id": f"{RECIPE}{SEP}{name}", "source": f"{theme} {RECIPE}", "family": f"{theme} {RECIPE}s",
                            "theme": theme, "file": out.relative_to(ROOT).as_posix(), "seconds": duration(out)})
    # The sound each theme plays now for each signal, to compare on the bench.
    current = {s: {p.name[:-len(f"_{s}.flac")]: p.relative_to(ROOT).as_posix()
                   for p in sorted(RAW.glob(f"*_{s}.flac")) if (THEMES / p.name[:-len(f"_{s}.flac")]).is_dir()}
               for s in signals()}
    write_json(CATALOGUE, {"signals": signals(), "current": current, "sounds": entries})
    print(f"{len(entries)} sounds in {CATALOGUE.relative_to(ROOT)}")
    return 0


# ----------------------------------------------------------------------------------------------
# A theme's files
# ----------------------------------------------------------------------------------------------

def credit(id_, choice, by_title):
    """sounds.json's entry for a sound: its source's credit, or for a recipe, how it was made and
    the credits of what it was made from."""
    if id_.startswith(RECIPE + SEP):
        recipe = choice.get("recipes", {}).get(id_[len(RECIPE + SEP):])
        if recipe is None:
            raise ValueError(f"{id_}: no such recipe in the choice")
        made = " ".join(map(str, recipe.get("effects", [])))
        return {"made": f"sox {made}".strip(), "from": [credit(i, choice, by_title) for i in recipe.get("inputs", [])]}
    title, name = id_.split(SEP, 1)
    source = by_title.get(title)
    if source is None:
        raise ValueError(f"{id_}: no source titled {title!r} in sound-sources.json")
    # A source listed sound by sound credits each sound by its own page, and its own author and
    # licence where they are not the source's.
    one = next((f for f in source.get("files", []) if f["name"] == name), {})
    if source.get("files") and not one:
        raise ValueError(f"{id_}: no file named {name!r} in {title}")
    if licence(source, name) not in LICENCES:
        raise ValueError(f"{id_}: licence {licence(source, name)} refused")
    return {"title": id_, "author": one.get("author", source.get("author")), "licence": licence(source, name),
            "page": one.get("page", source["page"])}


def credits(theme, choice):
    by_title = sources()
    chosen = {s: v["choice"] for s, v in choice.get("signals", {}).items() if v.get("choice")}
    return {
        "_about": f"The {theme} theme's interface sounds, one per UISignal chosen, made by scripts/theme_sounds.py from sounds-choice.json beside this file (docs/THEMES.md); their source and licence.",
        "sounds": {f"res/raw/{theme}_{s}.flac": credit(chosen[s], choice, by_title) for s in signals() if s in chosen},
    }


def task_make(theme):
    path = THEMES / theme / "sounds-choice.json"
    if not path.is_file():
        sys.exit(f"no {path.relative_to(ROOT)}")
    choice = load(path)
    if not CATALOGUE.is_file():
        sys.exit("no library: run scripts/theme_sounds.py library first")
    sounds = library_sounds()
    gain = float(choice.get("gain_db", 0))
    document = credits(theme, choice)  # refuses an unknown source or a licence before writing
    for signal in signals():
        target = RAW / f"{theme}_{signal}.flac"
        entry = choice.get("signals", {}).get(signal, {})
        id_ = entry.get("choice")
        if not id_:
            target.unlink(missing_ok=True)
            continue
        if id_.startswith(RECIPE + SEP):
            source = recipe_file(theme, id_[len(RECIPE + SEP):])
            render(choice["recipes"][id_[len(RECIPE + SEP):]], sounds, source)
        elif id_ in sounds:
            source = sounds[id_]["file"]
        else:
            sys.exit(f"{signal}: {id_!r} is not in the library")
        level = gain + float(entry.get("offset_db", 0))
        part = target.with_name(target.stem + ".part.flac")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(source), "-af", f"volume={level:g}dB",
                        "-c:a", "flac", "-sample_fmt", "s16", str(part)], check=True)
        os.replace(part, target)
        print(f"{target.relative_to(ROOT)}  {id_}  {level:g} dB")
    write_json(THEMES / theme / "sounds.json", document)
    return 0


def task_check():
    problems = []
    for theme, choice in choices().items():
        try:
            expected = credits(theme, choice)
        except ValueError as error:
            problems.append(f"{theme}: {error}")
            continue
        written = THEMES / theme / "sounds.json"
        if not written.is_file() or load(written) != expected:
            problems.append(f"{theme}: sounds.json is not what sounds-choice.json gives -- run scripts/theme_sounds.py make {theme}")
        files = {p.name for p in RAW.glob(f"{theme}_*.flac")}
        wanted = {Path(k).name for k in expected["sounds"]}
        if files != wanted:
            problems.append(f"{theme}: res/raw has {sorted(files - wanted)} beyond its choice and lacks {sorted(wanted - files)}")
    for problem in problems:
        print(problem)
    print(f"theme sounds: {'FAIL' if problems else 'ok'}")
    return 1 if problems else 0


def main():
    args = sys.argv[1:]
    if args == ["library"]:
        return task_library()
    if len(args) == 2 and args[0] == "make":
        return task_make(args[1])
    if args == ["check"]:
        return task_check()
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())
