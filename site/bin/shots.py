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
    a phone video (.mp4)    its story and its gestures shown whole, then played (site/scenarios/
                            <id>.txt): one Enter per gesture made by hand, after which the script
                            does at once what follows on its own (texts typed by adb, filming
                            started or stopped), then hands back; filmed on the computer by scrcpy
                            between start and stop, its window showing the phone, at 720 px wide;
                            every still longer than STILL_MAX cut to STILL_KEPT, silent, H.264, its
                            first image kept as the poster (.webp), the recording as made kept in
                            tmp/site-videos/
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
import signal
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
# A still of the screen longer than STILL_MAX seconds (a wait for the AI, the time to read the
# next step) is cut to STILL_KEPT: nothing changes on screen in what is cut.
STILL_MAX = 2.0
STILL_KEPT = 1.0
# The site's videos play at this rate, a still repeating its frame.
FPS = 30
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
    """A video's steps in one language, as (kind, text) pairs: story, setup, start, stop, do, type."""
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
        if kind in ("story", "setup", "do"):
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
    """The steps as the user's turns: the gestures made by hand with the steps the script then does
    on its own; gestures following one another with nothing for the script between are one turn,
    since an Enter is only worth pressing for what the script does next. Steps before the first
    gesture come under None."""
    groups = []
    for kind, text in steps:
        if kind == "story":
            continue
        if kind in MANUAL:
            if groups and groups[-1][0] is not None and not groups[-1][1]:
                groups[-1][0].append(text)
            else:
                groups.append(([text], []))
        else:
            if not groups:
                groups.append((None, []))
            groups[-1][1].append((kind, text))
    return groups


def what_follows(automatic, following):
    """What comes after a turn's Enter, in a sentence: what the script does on its own, then the
    next gesture, or the video saved when there is none."""
    actions = [{"start": "the script starts filming, touches shown", "stop": "the script stops filming",
                "type": f"the script types “{text}”"}[kind] for kind, text in automatic]
    actions.append(f"you: {'; then '.join(following)}" if following is not None else "the script saves the video and its first image")
    return ", then ".join(actions)


def encode_video(source, video_path, spans=None):
    """Silent, WIDTH wide, FPS a second, H.264 ready to play while it loads; only [spans] of
    [source] when given, (start, end) in seconds, end None for the rest, joined."""
    target = PUBLIC / video_path
    target.parent.mkdir(parents=True, exist_ok=True)
    spans = spans or [(0.0, None)]
    cuts = "".join(
        f"[0:v]trim=start={start}" + (f":end={end}" if end is not None else "") + f",setpts=PTS-STARTPTS[s{i}];"
        for i, (start, end) in enumerate(spans)
    )
    joined = "".join(f"[s{i}]" for i in range(len(spans))) + f"concat=n={len(spans)}:v=1:a=0"
    subprocess.run([
        "ffmpeg", "-loglevel", "error", "-y", "-i", str(source), "-an",
        "-filter_complex", f"{cuts}{joined},fps={FPS},scale={WIDTH}:-2[v]", "-map", "[v]",
        "-c:v", "libx264", "-preset", "slow", "-crf", "28",
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


class Recorder:
    """scrcpy filming the phone into a file on the computer, without a window, which would take the
    focus from the terminal: 30 to 60 frames a second while typing and scrolling (measured on
    2026-10-22, a Galaxy S10+)."""

    def __init__(self):
        self.process = None
        self.started = None

    def start(self, target):
        show_touches(True)
        width, height = phone_screen()
        # --max-size bounds the longer side: the height that makes the width WIDTH
        self.process = subprocess.Popen(
            ["scrcpy", "--max-size", str(round(height * WIDTH / width)), "--no-audio", "--no-window", "--record", str(target)],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        self.started = time.monotonic()

    def stop(self):
        """scrcpy writes the end of its file when it is interrupted, as by Ctrl+C."""
        if self.process is None:
            return
        self.process.send_signal(signal.SIGINT)
        self.process.wait()
        self.process = None
        self.started = None
        show_touches(False)

    def state(self):
        """What a prompt starts with: whether it is filming, and for how long."""
        if self.started is None:
            return "○ not filming"
        elapsed = int(time.monotonic() - self.started)
        return f"● REC {elapsed // 60}:{elapsed % 60:02d}"


recorder = Recorder()


def play(steps, video_path):
    """The scenario turn by turn: the gestures, when to press Enter, what follows, done at once by the
    script when it is its own before handing back; filmed between start and stop. The first turn's
    Enter can be s, to skip the video. Whether it was filmed."""
    groups = turns(steps)
    gestures = sum(1 for gesture, _ in groups if gesture is not None)
    number = 0
    with tempfile.TemporaryDirectory() as scratch:
        local = Path(scratch) / "raw.mp4"
        for i, (gesture, automatic) in enumerate(groups):
            if gesture is not None:
                number += 1
                following = next((g for g, _ in groups[i + 1:] if g is not None), None)
                print(f"\n  {recorder.state()}  [{number}/{gestures}] Do:")
                for letter, text in zip("abcdefghij", gesture):
                    print(f"        {letter}. {text}" if len(gesture) > 1 else f"        {text}")
                print(f"        Then:  {what_follows(automatic, following)}")
                if number == 1:
                    if not ask("  Once done:"):
                        return False
                else:
                    if input("  Once done: Enter [q: quit] ").strip().lower() == "q":
                        raise KeyboardInterrupt
            for kind, text in automatic:
                if kind == "start":
                    recorder.start(local)
                    print(f"        {recorder.state()}  filming")
                elif kind == "stop":
                    recorder.stop()
                    print(f"        {recorder.state()}  stopped")
                elif kind == "type":
                    print("        Typing…", end="", flush=True)
                    type_text(text)
                    print(" done.")
        if recorder.started is not None:
            recorder.stop()
            sys.exit("The scenario has a start without a stop")
        # The recording as made, to measure its frames (tmp/ is not versioned)
        raw = ROOT / "tmp" / "site-videos" / video_path
        raw.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(local, raw)
        encode_video(local, video_path, kept_spans(local))
    return True


def frame_times(source):
    """When each frame of [source] appears, in seconds: a recording holds a frame only when the
    screen changes, so a long gap between two is a still."""
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "frame=pts_time",
                          "-of", "csv=p=0", str(source)], capture_output=True, text=True, check=True).stdout
    return [float(line.split(",")[0]) for line in out.splitlines() if line.strip()]


def kept_spans(source):
    """The spans of [source] kept, as (start, end) in seconds: all of it but the end of each still
    longer than STILL_MAX, which is cut to STILL_KEPT."""
    times = frame_times(source)
    spans, start = [], 0.0
    for current, following in zip(times, times[1:]):
        if following - current > STILL_MAX:
            spans.append((start, current + STILL_KEPT))
            start = following
    spans.append((start, None))
    return spans


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
            print(f"\n{what}")
            for kind, text in steps:
                if kind == "story":
                    print(f"  {text}")
            print(f"  {len(gestures)} steps for you:")
            for number, gesture in enumerate(gestures, 1):
                print(f"  {number}. {'; then '.join(gesture)}")
            if video["frame"] == "computer":
                source = input("  Path of the computer's screen recording, 16:9 (empty: skip) ").strip()
                if not source:
                    continue
                encode_video(Path(source).expanduser(), path)
            elif not play(steps, path):
                continue
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
    if not all(shutil.which(tool) for tool in ("adb", "ffmpeg", "scrcpy")):
        sys.exit("site-shots needs adb, ffmpeg and scrcpy (trixie-backports: sudo apt install -t trixie-backports scrcpy)")
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
        recorder.stop()
        show_touches(False)
        demo_mode(False)
    left = missing(lang)
    print(f"\n{len(left)} still missing in {lang}. Look at the files in site/public_html/, then commit them.")


if __name__ == "__main__":
    main()
