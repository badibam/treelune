#!/usr/bin/env python3
"""Take the site's missing captures and videos in one language, on the phone (./run site-shots en|fr).

The phone holds the demo alone, never real data, and is set to the language taken (docs/design/
website.md, « Les captures »). What is missing is what site/bin/missing_media.php lists from
layout.json, the one declaration of every capture and video; for each, this says which screen to
put on the phone, then:

    a capture (.webp)       screencap, checked against layout.json's "screen" proportion, brought
                            down to 720 px wide, saved as WebP
    the zone's capture      the same, and in English each tool's tile framed from the screen's
                            layout (uiautomator), written into layout.json's zone.tiles
    a phone video (.mp4)    its gestures shown whole, then played (site/scenarios/<id>.txt): one
                            Enter per gesture made by hand, after which the script does at once what
                            follows on its own (texts typed by adb, filming started or stopped), then
                            hands back; filmed by screenrecord between start and stop, silent, 720 px
                            wide, H.264, its first image kept as the poster (.webp)
    the outside AI's video  a screen recording made on the computer, whose path is asked, its
                            scenario shown as what to record

layout.json's "screen" is the phone's, written by the first session; a phone of another size is
refused once captures or videos exist, the site's frames drawn to one proportion.

For the session, the status bar is in Android's demo mode (a fixed time, a full battery, no
notification), and while filming the touches show on screen; both are put back at the end.

A text adb types goes through `input text`, which takes ASCII alone; an accented one needs
ADBKeyBoard as the phone's keyboard, checked before it is typed.

Enter goes on, s skips, q stops. Nothing is committed: the files are left in site/public_html/ to
be looked at, then committed by hand.
"""

import base64
import io
import json
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ElementTree
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SITE = ROOT / "site"
PUBLIC = SITE / "public_html"
LAYOUT = SITE / "private" / "layout.json"
GENERATED = SITE / "private" / "generated.json"
SCENARIOS = SITE / "scenarios"
LANGUAGES = ("en", "fr")

WIDTH = 720
# A capture whose proportion differs from layout.json's "screen" by more than this is refused: the
# frames are drawn to that proportion, and a capture of another phone would be cropped.
RATIO_TOLERANCE = 0.01
# The pause between two typed words, the letters coming as fast as adb sends them.
WORD_PAUSE = 0.25
# screenrecord stops by itself after this many seconds.
RECORD_LIMIT = 180
ADB_KEYBOARD = "com.android.adbkeyboard/.AdbIME"


def missing(lang):
    """What missing_media.php lists in one language, as (path, description) pairs."""
    result = subprocess.run(["php", str(SITE / "bin" / "missing_media.php"), lang], capture_output=True, text=True)
    if result.returncode not in (0, 1):
        sys.exit(result.stderr or "missing_media.php failed")
    pairs = []
    for line in result.stdout.splitlines():
        path, _, what = line.partition("  (")
        pairs.append((path.strip(), what.rstrip(")")))
    return pairs


def ask(prompt):
    """Enter → True, s → False, q → quit (the phone put back as it was, by main's finally)."""
    answer = input(f"{prompt} [Enter: go, s: skip, q: quit] ").strip().lower()
    if answer == "q":
        raise KeyboardInterrupt
    return answer != "s"


def adb(*args, **kwargs):
    return subprocess.run(["adb", *args], check=True, **kwargs)


def adb_shell(command):
    """A command run by the phone's shell, its output as text."""
    return adb("shell", command, capture_output=True, text=True).stdout.strip()


def device_ready():
    out = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout
    devices = [line.split()[0] for line in out.splitlines()[1:] if line.strip().endswith("device")]
    if len(devices) != 1:
        sys.exit(f"site-shots needs exactly one phone ready over adb, found {len(devices)}")


# ---- The phone's state for the session ----

def demo_mode(on):
    """Android's demo mode for the status bar: a fixed time, a full battery, no notification."""
    if not on:
        adb_shell("am broadcast -a com.android.systemui.demo -e command exit")
        return
    adb_shell("settings put global sysui_demo_allowed 1")
    for extras in (
        "-e command enter",
        "-e command clock -e hhmm 1000",
        "-e command battery -e level 100 -e plugged false",
        "-e command network -e wifi show -e level 4 -e mobile show -e level 4",
        "-e command notifications -e visible false",
    ):
        adb_shell(f"am broadcast -a com.android.systemui.demo {extras}")


# The phone's own "Show taps" setting, read at the session's start and put back after each film.
touches_before = "0"


def show_touches(on):
    adb_shell(f"settings put system show_touches {1 if on else touches_before}")


# ---- Typing ----

def type_text(text):
    """Typed into the focused field letter by letter, a pause between words, so that it is seen
    being written: ASCII by `input text`, which replays one key per letter, the rest by ADBKeyBoard,
    sent one letter at a time to look the same."""
    if text.isascii():
        for i, word in enumerate(text.split(" ")):
            if i:
                adb_shell("input keyevent 62")
            adb_shell("input text " + shlex.quote(word))
            time.sleep(WORD_PAUSE)
        return
    if adb_shell("settings get secure default_input_method") != ADB_KEYBOARD:
        sys.exit(
            f"“{text}” is not ASCII: adb types it through ADBKeyBoard, which is not the phone's keyboard.\n"
            f"Install it, then: adb shell ime enable {ADB_KEYBOARD} && adb shell ime set {ADB_KEYBOARD}"
        )
    for letter in text:
        encoded = base64.b64encode(letter.encode("utf-8")).decode("ascii")
        adb_shell(f"am broadcast -a ADB_INPUT_B64 --es msg {encoded}")
        if letter == " ":
            time.sleep(WORD_PAUSE)


# ---- Captures ----

def screencap():
    """The phone's screen as a Pillow image."""
    from PIL import Image
    data = adb("exec-out", "screencap", "-p", capture_output=True).stdout
    return Image.open(io.BytesIO(data)).convert("RGB")


def save_shot(image, path, screen):
    """Checked against the declared proportion, brought to WIDTH, saved as WebP."""
    ratio = image.width / image.height
    expected = screen[0] / screen[1]
    if abs(ratio - expected) / expected > RATIO_TOLERANCE:
        sys.exit(
            f"The capture is {image.width}×{image.height}, the phone's screen {screen[0]}×{screen[1]}: "
            "is the phone held sideways, or was its resolution changed during the session?"
        )
    height = round(WIDTH * image.height / image.width)
    target = PUBLIC / path
    target.parent.mkdir(parents=True, exist_ok=True)
    image.resize((WIDTH, height)).save(target, "WEBP", quality=85, method=6)
    print(f"  {path}: {target.stat().st_size // 1024} KB")


def phone_screen():
    """The phone's screen size in pixels, held upright: its override if one is set, its physical size otherwise."""
    sizes = dict(re.findall(r"(Physical|Override) size: (\d+x\d+)", adb_shell("wm size")))
    width, height = map(int, sizes.get("Override", sizes["Physical"]).split("x"))
    return [min(width, height), max(width, height)]


def check_screen(layout):
    """layout.json's "screen" set to the phone's; refused if captures or videos taken on another exist."""
    screen = phone_screen()
    if layout["screen"] == screen:
        return
    taken = [p for folder in ("shots", "videos") if (PUBLIC / folder).exists() for p in (PUBLIC / folder).rglob("*") if p.is_file()]
    if taken:
        sys.exit(
            f"This phone's screen is {screen[0]}×{screen[1]}, layout.json's \"screen\" {layout['screen'][0]}×{layout['screen'][1]}, "
            f"the size of the {len(taken)} captures and videos already in site/public_html/: take them on that phone, "
            "or delete them all to take them again on this one"
        )
    text = LAYOUT.read_text(encoding="utf-8")
    text = re.sub(r'"screen": \[[^\]]*\]', f'"screen": {json.dumps(screen)}', text, count=1)
    partial = LAYOUT.with_name(LAYOUT.name + ".part")
    partial.write_text(text, encoding="utf-8")
    partial.replace(LAYOUT)
    print(f"layout.json: \"screen\" set to this phone's, {screen[0]}×{screen[1]}")
    layout["screen"] = screen


def tile_frames(screen_size):
    """Each zone tool's tile on the screen, as fractions: the clickable element holding its name."""
    generated = json.loads(GENERATED.read_text(encoding="utf-8"))
    adb("shell", "uiautomator", "dump", "/sdcard/site-ui.xml", capture_output=True)
    xml = adb("exec-out", "cat", "/sdcard/site-ui.xml", capture_output=True).stdout
    tree = ElementTree.fromstring(xml)
    parents = {child: parent for parent in tree.iter() for child in parent}
    width, height = screen_size
    frames = {}
    for tool in generated["zone"]["tools"]:
        names = set(tool["name"].values())
        node = next((n for n in tree.iter("node") if n.get("text") in names or n.get("content-desc") in names), None)
        while node is not None and node.get("clickable") != "true":
            node = parents.get(node)
        if node is None:
            print(f"  no tile found for {tool['id']}: its frame stays to be set by hand in layout.json")
            continue
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        frames[tool["id"]] = [round(x1 / width, 4), round(y1 / height, 4), round((x2 - x1) / width, 4), round((y2 - y1) / height, 4)]
    return frames


def write_tiles(frames):
    """The frames written into layout.json's zone.tiles, the rest of the file left as it is."""
    text = LAYOUT.read_text(encoding="utf-8")
    start = text.index('"tiles": {')
    end = text.index("}", start)
    block = text[start:end]
    for tool, frame in frames.items():
        block = re.sub(rf'("{re.escape(tool)}": )[^,\n]*', lambda m: m.group(1) + json.dumps(frame), block)
    partial = LAYOUT.with_name(LAYOUT.name + ".part")
    partial.write_text(text[:start] + block + text[end:], encoding="utf-8")
    partial.replace(LAYOUT)
    print(f"  layout.json: {len(frames)} tile frames written")


# ---- Videos ----

def scenario(video_id, lang):
    """A video's steps in one language, as (kind, text) pairs: setup, start, stop, do, type."""
    path = SCENARIOS / f"{video_id}.txt"
    if not path.exists():
        sys.exit(f"No scenario for the video {video_id}: write {path.relative_to(ROOT)} (site/scenarios/README.md)")
    steps = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line in ("start", "stop"):
            steps.append((line, ""))
            continue
        kind, _, text = line.partition(":")
        kind, text = kind.strip(), text.strip()
        if kind in ("setup", "do"):
            steps.append((kind, text))
        elif kind in (f"type {code}" for code in LANGUAGES):
            if kind == f"type {lang}":
                steps.append(("type", text))
        else:
            sys.exit(f"{path.relative_to(ROOT)}:{number}: unknown step “{line}”")
    return steps


# A step the user makes by hand, Enter once it is done; the others the script does on its own.
MANUAL = ("setup", "do")


def turns(steps):
    """The steps as the user's turns: each gesture made by hand with the steps the script then does
    on its own, until the next gesture; steps before the first gesture come under None."""
    groups = []
    for kind, text in steps:
        if kind in MANUAL:
            groups.append((text, []))
        else:
            if not groups:
                groups.append((None, []))
            groups[-1][1].append((kind, text))
    return groups


def what_follows(automatic, last):
    """What the script does after a turn's Enter, in a sentence."""
    actions = [{"start": "starts filming, touches shown", "stop": "stops filming", "type": f"types “{text}”"}[kind]
               for kind, text in automatic]
    if last:
        actions.append("saves the video and its first image")
    return "the script " + ", then ".join(actions) if actions else "nothing, the next step"


def encode_video(source, video_path):
    """Silent, WIDTH wide, H.264 ready to play while it loads."""
    target = PUBLIC / video_path
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([
        "ffmpeg", "-loglevel", "error", "-y", "-i", str(source), "-an",
        "-vf", f"scale={WIDTH}:-2", "-c:v", "libx264", "-preset", "slow", "-crf", "28",
        "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(target),
    ], check=True)
    print(f"  {video_path}: {target.stat().st_size // 1024} KB")


def save_poster(video_path, poster_path):
    from PIL import Image
    with tempfile.TemporaryDirectory() as scratch:
        frame = Path(scratch) / "first.png"
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(PUBLIC / video_path), "-frames:v", "1", str(frame)], check=True)
        target = PUBLIC / poster_path
        target.parent.mkdir(parents=True, exist_ok=True)
        Image.open(frame).convert("RGB").save(target, "WEBP", quality=85, method=6)
    print(f"  {poster_path}: first image of the video")


def play(steps, video_path):
    """The scenario turn by turn: the gesture, when to press Enter, what the script then does on its
    own, done at once before handing back; filmed between start and stop."""
    remote = "/sdcard/site-video.mp4"
    recorder = None
    groups = turns(steps)
    gestures = sum(1 for gesture, _ in groups if gesture is not None)
    number = 0
    for i, (gesture, automatic) in enumerate(groups):
        if gesture is not None:
            number += 1
            print(f"\n  [{number}/{gestures}] Do:   {gesture}")
            print("        Once done: Enter")
            print(f"        Then:  {what_follows(automatic, i == len(groups) - 1)}")
            input("  > ")
        for kind, text in automatic:
            if kind == "start":
                show_touches(True)
                recorder = subprocess.Popen(["adb", "shell", "screenrecord", "--bit-rate", "8000000",
                                             "--time-limit", str(RECORD_LIMIT), remote])
                print(f"        Filming (at most {RECORD_LIMIT} s).")
            elif kind == "stop":
                subprocess.run(["adb", "shell", "pkill", "-INT", "screenrecord"], check=False)
                recorder.wait()
                recorder = None
                show_touches(False)
                time.sleep(1)  # screenrecord finishes writing the file after its signal
                print("        Stopped.")
            elif kind == "type":
                print("        Typing…", end="", flush=True)
                type_text(text)
                print(" done.")
    if recorder is not None:
        sys.exit("The scenario has a start without a stop")
    with tempfile.TemporaryDirectory() as scratch:
        local = Path(scratch) / "raw.mp4"
        adb("pull", remote, str(local), capture_output=True)
        encode_video(local, video_path)


# ---- The session ----

def session(lang, layout):
    videos = {v["file"].replace("/", f"/{lang}/", 1): v for v in layout["videos"]}
    posters = {v["poster"].replace("/", f"/{lang}/", 1): v for v in layout["videos"]}
    zone_file = layout["zone"]["shot"]["file"].replace("/", f"/{lang}/", 1)
    for path, what in missing(lang):
        if path.startswith("layout.json"):
            continue  # tile frames come with the English zone capture
        if path in posters:
            video = posters[path]
            video_file = video["file"].replace("/", f"/{lang}/", 1)
            if (PUBLIC / video_file).exists():
                save_poster(video_file, path)
            continue  # otherwise it comes with its video
        if path in videos:
            video = videos[path]
            steps = scenario(video["id"], lang)
            # The gestures first, numbered, so that where it goes is known before it starts
            gestures = [gesture for gesture, _ in turns(steps) if gesture is not None]
            print(f"\n{what}, {len(gestures)} steps for you:")
            for number, gesture in enumerate(gestures, 1):
                print(f"  {number}. {gesture}")
            if video["frame"] == "computer":
                source = input("  Path of the computer's screen recording, 16:9 (empty: skip) ").strip()
                if not source:
                    continue
                encode_video(Path(source).expanduser(), path)
            else:
                if not ask("  Film it now?"):
                    continue
                play(steps, path)
            save_poster(path, video["poster"].replace("/", f"/{lang}/", 1))
            continue
        if not ask(f"\n{what}: put the phone on this screen"):
            continue
        image = screencap()
        save_shot(image, path, layout["screen"])
        if path == zone_file and lang == "en":
            write_tiles(tile_frames(image.size))


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in LANGUAGES:
        sys.exit("Usage: ./run site-shots en|fr  (the phone set to that language, holding the demo alone)")
    lang = sys.argv[1]
    if not shutil.which("adb") or not shutil.which("ffmpeg"):
        sys.exit("site-shots needs adb and ffmpeg")
    layout = json.loads(LAYOUT.read_text(encoding="utf-8"))
    todo = missing(lang)
    if not todo:
        print(f"Nothing is missing in {lang}: every capture, video and tile frame is there.")
        return
    print(f"{len(todo)} missing in {lang}. The phone is set to that language and holds the demo alone.")
    device_ready()
    check_screen(layout)
    global touches_before
    touches_before = adb_shell("settings get system show_touches") or "0"
    demo_mode(True)
    try:
        session(lang, layout)
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        subprocess.run(["adb", "shell", "pkill", "-INT", "screenrecord"], capture_output=True, check=False)
        show_touches(False)
        demo_mode(False)
    left = missing(lang)
    print(f"\n{len(left)} still missing in {lang}. Look at the files in site/public_html/, then commit them.")


if __name__ == "__main__":
    main()
