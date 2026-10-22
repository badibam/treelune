#!/usr/bin/env python3
"""Take the site's missing captures and videos on the phone, one by one (./run site-shots).

The phone holds the demo alone, never real data (docs/design/website.md, « Les captures »). What is
missing is what site/bin/missing_media.php lists from layout.json, the one declaration of every
capture and video; for each, this says which screen to put on the phone, then:

    a capture (.webp)       screencap, checked against layout.json's "screen" proportion,
                            brought down to 720 px wide, saved as WebP
    the zone's capture      the same, and each tool's tile framed from the screen's layout
                            (uiautomator), written into layout.json's zone.tiles
    a phone video (.mp4)    filmed by screenrecord between two Enter, silent, 720 px wide, H.264,
                            its first image kept as the poster (.webp)
    the outside AI's video  a screen recording made on the computer, whose path is asked

Enter takes, s skips, q stops. Nothing is committed: the files are left in site/public_html/ to be
looked at, then committed by hand.
"""

import json
import re
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

WIDTH = 720
# A capture whose proportion differs from layout.json's "screen" by more than this is refused: the
# frames are drawn to that proportion, and a capture of another phone would be cropped.
RATIO_TOLERANCE = 0.01


def missing():
    """What missing_media.php lists, as (path, description) pairs."""
    result = subprocess.run(["php", str(SITE / "bin" / "missing_media.php")], capture_output=True, text=True)
    if result.returncode not in (0, 1):
        sys.exit(result.stderr or "missing_media.php failed")
    pairs = []
    for line in result.stdout.splitlines():
        path, _, what = line.partition("  (")
        pairs.append((path.strip(), what.rstrip(")")))
    return pairs


def ask(prompt):
    """Enter → True, s → False, q → quit."""
    answer = input(f"{prompt} [Enter: go, s: skip, q: quit] ").strip().lower()
    if answer == "q":
        sys.exit(0)
    return answer != "s"


def adb(*args, **kwargs):
    return subprocess.run(["adb", *args], check=True, **kwargs)


def device_ready():
    out = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout
    devices = [line.split()[0] for line in out.splitlines()[1:] if line.strip().endswith("device")]
    if len(devices) != 1:
        sys.exit(f"site-shots needs exactly one phone ready over adb, found {len(devices)}")


def screencap():
    """The phone's screen as a Pillow image."""
    from PIL import Image
    import io
    data = adb("exec-out", "screencap", "-p", capture_output=True).stdout
    return Image.open(io.BytesIO(data)).convert("RGB")


def save_shot(image, path, screen):
    """Checked against the declared proportion, brought to WIDTH, saved as WebP."""
    ratio = image.width / image.height
    expected = screen[0] / screen[1]
    if abs(ratio - expected) / expected > RATIO_TOLERANCE:
        sys.exit(
            f"The capture is {image.width}×{image.height}, layout.json's \"screen\" says {screen[0]}×{screen[1]}: "
            "set \"screen\" to this phone's, once, and run site-shots again"
        )
    height = round(WIDTH * image.height / image.width)
    target = PUBLIC / path
    target.parent.mkdir(parents=True, exist_ok=True)
    image.resize((WIDTH, height)).save(target, "WEBP", quality=85, method=6)
    print(f"  {path}: {target.stat().st_size // 1024} KB")


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


def encode_video(source, video_path):
    """Silent, WIDTH wide, H.264 ready to play while it loads; its first image kept as the poster."""
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
        Image.open(frame).convert("RGB").save(PUBLIC / poster_path, "WEBP", quality=85, method=6)
    print(f"  {poster_path}: first image of the video")


def film(video_path):
    """screenrecord between two Enter, pulled and encoded."""
    remote = "/sdcard/site-video.mp4"
    recorder = subprocess.Popen(["adb", "shell", "screenrecord", "--bit-rate", "8000000", remote])
    input("  Filming… Enter to stop. ")
    subprocess.run(["adb", "shell", "pkill", "-INT", "screenrecord"], check=False)
    recorder.wait()
    time.sleep(1)  # screenrecord finishes writing the file after its signal
    with tempfile.TemporaryDirectory() as scratch:
        local = Path(scratch) / "raw.mp4"
        adb("pull", remote, str(local), capture_output=True)
        encode_video(local, video_path)


def main():
    if not shutil.which("adb") or not shutil.which("ffmpeg"):
        sys.exit("site-shots needs adb and ffmpeg")
    layout = json.loads(LAYOUT.read_text(encoding="utf-8"))
    videos = {v["file"]: v for v in layout["videos"]}
    posters = {v["poster"]: v for v in layout["videos"]}
    todo = missing()
    if not todo:
        print("Nothing is missing: every capture, video and tile frame is there.")
        return
    print(f"{len(todo)} missing. The phone holds the demo alone.\n")
    device_ready()
    zone_file = layout["zone"]["shot"]["file"]
    for path, what in todo:
        if path.startswith("layout.json"):
            continue  # tile frames come with the zone's capture
        if path in posters:
            video = posters[path]
            if (PUBLIC / video["file"]).exists():
                save_poster(video["file"], path)
            continue  # otherwise it comes with its video
        if path in videos:
            video = videos[path]
            if video["frame"] == "computer":
                source = input(f"{what}: path of the computer's screen recording (empty: skip) ").strip()
                if not source:
                    continue
                encode_video(Path(source).expanduser(), path)
            else:
                if not ask(f"{what}: put the phone on the start of the scene"):
                    continue
                film(path)
            save_poster(path, video["poster"])
            continue
        if not ask(f"{what}: put the phone on this screen"):
            continue
        image = screencap()
        save_shot(image, path, layout["screen"])
        if path == zone_file:
            write_tiles(tile_frames(image.size))
    left = missing()
    print(f"\nDone; {len(left)} still missing. Look at the files in site/public_html/, then commit them.")


if __name__ == "__main__":
    main()
