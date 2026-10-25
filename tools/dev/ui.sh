#!/usr/bin/env bash
# Tiny adb helpers for driving the emulator in manual checks.
#   tools/dev/ui.sh tap "Texto visible"   — taps the first element with that exact text
#   tools/dev/ui.sh shot name             — saves a screenshot to $SHOTS/name.png
#   tools/dev/ui.sh texts                 — lists visible texts
set -euo pipefail
A=${ADB:-$HOME/Android/Sdk/platform-tools/adb}
SHOTS=${SHOTS:-/tmp}
dump() { $A shell uiautomator dump /sdcard/u.xml >/dev/null; $A exec-out cat /sdcard/u.xml; }
case "$1" in
  tap)
    b=$(dump | python3 -c "
import sys,re,html
t=sys.argv[1]
for m in re.finditer(r'<node [^>]*>',sys.stdin.read()):
  n=m.group(0); tx=re.search(r' text=\"([^\"]*)\"',n); cd=re.search(r'content-desc=\"([^\"]*)\"',n)
  if (tx and html.unescape(tx.group(1))==t) or (cd and html.unescape(cd.group(1))==t):
    a=list(map(int,re.findall(r'\d+',re.search(r'bounds=\"([^\"]*)\"',n).group(1)))); print((a[0]+a[2])//2,(a[1]+a[3])//2); break
" "$2")
    [ -n "$b" ] || { echo "not found: $2" >&2; exit 1; }
    $A shell input tap $b ;;
  shot) $A exec-out screencap -p > "$SHOTS/$2.png" ;;
  texts) dump | python3 -c "import sys,re,html;[print(html.unescape(t)) for t in re.findall(r' text=\"([^\"]+)\"',sys.stdin.read())]" ;;
esac
