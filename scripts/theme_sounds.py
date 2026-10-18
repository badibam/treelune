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
                 (tmp/sound-library/catalogue.json): each sound with its takes when it records
                 several gestures (find_takes), and the sound each theme plays now for each
                 signal. A source already there is kept.
    make <id>    write the theme's FLAC files and its sounds.json from its choice: each sound
                 decoded, given the theme's finish when it has one (FINISH) and brought to its
                 level, measured, or else to the theme's raw gain; plus its signal's offset; its
                 measures printed beside it; written on 16 bits
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


def themes():
    """Every theme of the app, silent ones included: a folder of themes/ holding its <Name>Theme.kt."""
    return sorted(p.name for p in THEMES.iterdir() if p.is_dir() and any(p.glob("*Theme.kt")))


def current():
    """The sound each theme plays now for each signal, to compare on the bench."""
    return {s: {p.name[:-len(f"_{s}.flac")]: p.relative_to(ROOT).as_posix()
                for p in sorted(RAW.glob(f"*_{s}.flac")) if (THEMES / p.name[:-len(f"_{s}.flac")]).is_dir()}
            for s in signals()}


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
        entry = {"id": id_, "source": s["source"], "family": s["family"],
                 "file": s["file"].relative_to(ROOT).as_posix(), "seconds": duration(s["file"])}
        takes = find_takes(s["file"]) if entry["seconds"] > 2 * TAKE_GAP else []
        if takes:
            entry["takes"] = takes
        entries.append(entry)
    for theme, choice in choices().items():
        for name, recipe in choice.get("recipes", {}).items():
            out = recipe_file(theme, name)
            render(recipe, sounds, out)
            entries.append({"id": f"{RECIPE}{SEP}{name}", "source": f"{theme} {RECIPE}", "family": f"{theme} {RECIPE}s",
                            "theme": theme, "file": out.relative_to(ROOT).as_posix(), "seconds": duration(out)})
    write_json(CATALOGUE, {"signals": signals(), "themes": themes(), "current": current(), "sounds": entries})
    print(f"{len(entries)} sounds in {CATALOGUE.relative_to(ROOT)}")
    return 0


# ----------------------------------------------------------------------------------------------
# A theme's finish
# ----------------------------------------------------------------------------------------------

# What a finish may hold, each step named and applied only when there: what holds a theme's
# sounds together whatever their source (docs/design/theme-sounds.md).
FINISH = {
    "mono": "one channel",
    "rate": "the sampling rate, in Hz",
    "highpass_hz": "the bass cut below it: the room, the handling",
    "trim_head_db": "the silence before the sound cut, under this level in dB",
    "bass_db": "the bass raised or lowered",
    "treble_db": "the treble raised or lowered",
    "reverb": "a small room: reverberance, room_scale and wet_db, as sox's reverb",
    "max_ms": "the longest a sound lasts, its end faded",
    "fade_ms": "the fade at the end",
    "loudness_db": "the level each sound is brought to: its loudest 50 ms, in dB RMS",
}
# The loudest a sample may be once brought to its level: above, the sound clips.
CEILING_DB = -0.5
WINDOW = 0.05


def check_finish(choice):
    """A theme's finish, or None; a ValueError when it names a step there is not, or keeps the raw
    gain beside the level it brings each sound to."""
    finish = choice.get("finish")
    if finish is None:
        return None
    unknown = set(finish) - set(FINISH)
    if unknown:
        raise ValueError(f"unknown finish steps {sorted(unknown)} (known: {', '.join(FINISH)})")
    if "loudness_db" not in finish:
        raise ValueError("a finish brings every sound to one level, loudness_db")
    if "gain_db" in choice:
        raise ValueError("gain_db applies to the raw sound, loudness_db to the finished one: keep one")
    if "fade_ms" in finish and "max_ms" not in finish:
        raise ValueError("fade_ms fades the end that max_ms sets")
    return finish


def finish_effects(finish):
    """The sox effects of a finish, in the order they apply; the level comes after, measured."""
    effects = []
    if finish.get("mono"):
        effects += ["remix", "-"]
    if "rate" in finish:
        effects += ["rate", "-v", finish["rate"]]
    if "highpass_hz" in finish:
        effects += ["highpass", finish["highpass_hz"]]
    if "trim_head_db" in finish:
        effects += ["silence", "1", "0.002", f"{finish['trim_head_db']}d"]
    if "bass_db" in finish:
        effects += ["bass", finish["bass_db"]]
    if "treble_db" in finish:
        effects += ["treble", finish["treble_db"]]
    if "reverb" in finish:
        r = finish["reverb"]
        # Room for the tail, cut again by max_ms when there is one.
        effects += ["pad", "0", "0.4", "reverb", r["reverberance"], "50", r["room_scale"], "100", "0", r["wet_db"]]
    if "max_ms" in finish:
        effects += ["trim", "0", finish["max_ms"] / 1000]
        if finish.get("fade_ms"):
            # Faded in backwards: the end of a sound of any length, never padded to max_ms.
            effects += ["reverse", "fade", "q", finish["fade_ms"] / 1000, "reverse"]
    # Room above, so that a step raising the sound never clips before the level is set.
    return ["gain", "-12", *effects]


def render_finished(source, finish, out):
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["sox", "-G", str(source), "-b", "32", "-e", "floating-point", str(out),
                    *map(str, finish_effects(finish))], check=True)


def samples(path):
    """The sound's samples, its channels mixed, as floats; and its rate."""
    import numpy
    rate = int(float(subprocess.run(["soxi", "-r", str(path)], capture_output=True, text=True, check=True).stdout))
    raw = subprocess.run(["sox", str(path), "-t", "f32", "-c", "1", "-"], capture_output=True, check=True).stdout
    return numpy.frombuffer(raw, dtype=numpy.float32), rate


def measure(path):
    """What tells a sound from its neighbours: its length, its loudest and quietest 50 ms (the
    second its noise floor), its peak, and its colour, the centre of its spectrum in Hz. A
    measure, not a verdict: the ear decides."""
    import numpy
    x, rate = samples(path)
    n = max(1, int(rate * WINDOW))
    windows = [x[i:i + n] for i in range(0, max(1, len(x) - n + 1), n)] or [x]
    rms = numpy.array([numpy.sqrt(numpy.mean(w.astype(numpy.float64) ** 2)) for w in windows])
    db = lambda v: round(20 * float(numpy.log10(max(float(v), 1e-9))), 1)
    spectrum = numpy.abs(numpy.fft.rfft(x.astype(numpy.float64)))
    freqs = numpy.fft.rfftfreq(len(x), 1 / rate)
    centre = float((spectrum * freqs).sum() / spectrum.sum()) if spectrum.sum() else 0.0
    return {"seconds": round(len(x) / rate, 3), "loud_db": db(rms.max()), "floor_db": db(rms.min()),
            "peak_db": db(numpy.abs(x).max()), "centre_hz": round(centre)}


TAKE_FRAME = 0.01
# A take: where a recording rises above its floor, from its start to where it has died away.
TAKE_RISE_DB = 15
TAKE_GAP = 0.12
TAKE_SHORTEST = 0.02
TAKE_BEFORE = 0.01
TAKES_AT_MOST = 60


def find_takes(path):
    """The takes of a recording of several gestures (a stapler slow then fast, five pages turned):
    each span where it rises above its floor, joined to the next when the gap between them is
    shorter than TAKE_GAP, a little room kept before. A proposal for the bench, not a verdict: the
    ear keeps a take, which becomes a recipe that cuts it. None for a sound of one gesture."""
    import numpy
    x, rate = samples(path)
    n = int(rate * TAKE_FRAME)
    frames = len(x) // n
    if frames < 3:
        return []
    rms = numpy.sqrt(numpy.mean(x[:frames * n].astype(numpy.float64).reshape(frames, n) ** 2, axis=1))
    db = 20 * numpy.log10(numpy.maximum(rms, 1e-9))
    floor = float(numpy.percentile(db, 10))
    above = db > floor + TAKE_RISE_DB
    spans, start = [], None
    for i, on in enumerate(above):
        if on and start is None:
            start = i
        elif not on and start is not None:
            spans.append([start, i]); start = None
    if start is not None:
        spans.append([start, frames])
    joined = []
    for s in spans:
        if joined and (s[0] - joined[-1][1]) * TAKE_FRAME < TAKE_GAP:
            joined[-1][1] = s[1]
        else:
            joined.append(s)
    takes = [[round(max(0, s * TAKE_FRAME - TAKE_BEFORE), 3), round(e * TAKE_FRAME, 3)]
             for s, e in joined if (e - s) * TAKE_FRAME >= TAKE_SHORTEST]
    return takes[:TAKES_AT_MOST] if len(takes) > 1 else []


def level(measured, finish, offset_db):
    """The gain that brings a finished sound to the theme's level and its signal's offset;
    refused when its peak would clip."""
    gain = float(finish["loudness_db"]) + float(offset_db) - measured["loud_db"]
    return round(gain, 2), measured["peak_db"] + gain > CEILING_DB



def hear(theme, choice, id_, offset_db, sounds, take=None):
    """A sound as the bench plays it, with the finish of [choice] even unsaved, or one take of it
    ([start, end] in seconds): finished, brought to its level and written in
    tmp/sound-library/finished/<theme>/hear/; its measures, the gain its level asked and whether it
    clips. A ValueError for a finish refused or a sound unknown."""
    finish = check_finish(choice)
    if finish is None:
        raise ValueError("no finish: the bench plays the raw sound at the theme's gain")
    source = sound_file(theme, choice, id_, sounds)
    recipe = choice.get("recipes", {}).get(id_[len(RECIPE + SEP):]) if id_.startswith(RECIPE + SEP) else None
    key = hashlib.sha256(json.dumps([id_, recipe, take, finish, offset_db], sort_keys=True).encode()).hexdigest()[:16]
    folder = LIBRARY / "finished" / theme / "hear"
    done, out = folder / f"{key}.finished.wav", folder / f"{key}.wav"
    if take is not None:
        start, end = (float(v) for v in take)
        cut = folder / f"{key}.take.wav"
        folder.mkdir(parents=True, exist_ok=True)
        subprocess.run(["sox", str(source), str(cut), "trim", f"{start:.3f}", f"{end - start:.3f}"], check=True)
        source = cut
    render_finished(source, finish, done)
    measured = measure(done)
    gain, clips = level(measured, finish, offset_db)
    subprocess.run(["sox", str(done), str(out), "gain", str(gain)], check=True, capture_output=True)
    return out, {"measured": measured, "gain_db": gain, "clips": clips}

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


def sound_file(theme, choice, id_, sounds):
    """The file a sound id plays: a sound of the library, or the theme's recipe, rendered again."""
    if id_.startswith(RECIPE + SEP):
        name = id_[len(RECIPE + SEP):]
        if name not in choice.get("recipes", {}):
            raise ValueError(f"{id_!r}: no such recipe in the choice")
        source = recipe_file(theme, name)
        render(choice["recipes"][name], sounds, source)
        return source
    if id_ not in sounds:
        raise ValueError(f"{id_!r} is not in the library")
    return sounds[id_]["file"]


def task_make(theme):
    path = THEMES / theme / "sounds-choice.json"
    if not path.is_file():
        sys.exit(f"no {path.relative_to(ROOT)}")
    choice = load(path)
    if not CATALOGUE.is_file():
        sys.exit("no library: run scripts/theme_sounds.py library first")
    sounds = library_sounds()
    try:
        finish = check_finish(choice)
    except ValueError as error:
        sys.exit(f"{theme}: {error}")
    gain = float(choice.get("gain_db", 0))
    document = credits(theme, choice)  # refuses an unknown source or a licence before writing
    for signal in signals():
        target = RAW / f"{theme}_{signal}.flac"
        entry = choice.get("signals", {}).get(signal, {})
        id_ = entry.get("choice")
        if not id_:
            target.unlink(missing_ok=True)
            continue
        try:
            source = sound_file(theme, choice, id_, sounds)
        except ValueError as error:
            sys.exit(f"{signal}: {error}")
        offset = float(entry.get("offset_db", 0))
        if finish is None:
            level_db, measured = gain + offset, None
        else:
            # Finished first, then brought to the theme's level as measured on the finished sound.
            done = LIBRARY / "finished" / theme / f"{signal}.wav"
            render_finished(source, finish, done)
            measured = measure(done)
            level_db, clips = level(measured, finish, offset)
            if clips:
                sys.exit(f"{signal}: {id_} would clip at {finish['loudness_db'] + offset:g} dB (peak {measured['peak_db']} dB, loudest 50 ms {measured['loud_db']} dB)")
            source = done
        part = target.with_name(target.stem + ".part.flac")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(source), "-af", f"volume={level_db:g}dB",
                        "-c:a", "flac", "-sample_fmt", "s16", str(part)], check=True)
        os.replace(part, target)
        print(f"{target.relative_to(ROOT)}  {id_}  {level_db:g} dB")
        if measured:
            print("    " + "  ".join(f"{k} {v}" for k, v in measure(target).items()))
    write_json(THEMES / theme / "sounds.json", document)
    # The bench compares with what each theme plays now: the files just written among them.
    catalogue = load(CATALOGUE)
    catalogue["themes"], catalogue["current"] = themes(), current()
    write_json(CATALOGUE, catalogue)
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
