#!/bin/bash
# Drives the phone over adb for a pass on the device (docs/THEMES.md, La passe sur le téléphone):
#   shot NAME          a screenshot into tmp/phone_ui/NAME.png, brought down to 720 px wide
#   dump               the texts on screen, each with its x,y
#   tap X Y            a touch
#   text "label"       a touch on the element showing that text or description
#   type "words"       words typed one by one, spaces between them
#   swipe X1 Y1 X2 Y2  a drag
#   back               the back key
# Coordinates are in the 720 px wide space of the shots, for a phone 1440 px wide (twice it).
S="$(cd "$(dirname "$0")/.." && pwd)/tmp/phone_ui"; mkdir -p "$S"
case "$1" in
 shot) adb exec-out screencap -p > $S/$2.png; python3 -c "
from PIL import Image; im=Image.open('$S/$2.png'); im.resize((720,1520)).save('$S/$2.png')";;
 tap) adb shell input tap $(( $2 * 2 )) $(( $3 * 2 ));;
 swipe) adb shell input swipe $(( $2 * 2 )) $(( $3 * 2 )) $(( $4 * 2 )) $(( $5 * 2 )) ${6:-300};;
 back) adb shell input keyevent 4;;
 dump) adb shell uiautomator dump /sdcard/u.xml >/dev/null && adb exec-out cat /sdcard/u.xml > $S/u.xml && python3 - <<P
import re, html
x=open('$S/u.xml').read()
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x):
    t,d,a,b,c,e=m.groups(); t,d=html.unescape(t),html.unescape(d)
    if t or d: print(f"{(int(a)+int(c))//4},{(int(b)+int(e))//4}  {t!r} {d!r}")
P
;;
esac
if [ "$1" = text ]; then
  c=$($0 dump | grep -F "'$2'" | head -1 | cut -d' ' -f1)
  [ -z "$c" ] && { echo "not found: $2"; exit 1; }
  $0 tap ${c%,*} ${c#*,}
fi
if [ "$1" = type ]; then
  first=1
  for w in $2; do
    [ $first = 0 ] && adb shell input keyevent 62
    first=0
    e=$(printf '%s' "$w" | sed -e "s/'/\\\\'/g" -e 's/[()&<>|;"?]/\\&/g')
    adb shell "input text $e"
  done
fi
