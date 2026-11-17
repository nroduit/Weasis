#!/bin/bash
# Copies the jpackage resources of one platform and removes the weasis:// URL scheme registration
# from the copy, for a pre-release build (a version with a suffix: 5.0.0-beta.1).
#
# A preview coexists with the installed release of the workstation: its installers register the
# scheme neither on macOS (Info.plist, CFBundleURLTypes), nor on Linux (.desktop MIME type and the
# prerm hook), nor on Windows (the URIHandlerRegistry component of the MSI), so a viewer hub or a
# browser launch keeps opening the release. Everything else of the resources is kept as is.
#
# Usage: prerelease-resources.sh <resources dir> <output dir>
#   <resources dir>  script/resources/<platform> (macosx, linux or windows)
#   <output dir>     created or emptied; the caller passes it to jpackage as --resource-dir
# Prints one line per removal; exits 1 on a missing argument or an unreadable directory.

set -euo pipefail

SRC="${1:-}"
OUT="${2:-}"
[ -n "$SRC" ] && [ -n "$OUT" ] || { echo "Usage: $(basename "$0") <resources dir> <output dir>" >&2; exit 1; }
[ -d "$SRC" ] || { echo "ERROR: $SRC is not a directory" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT"
cp -R "$SRC/." "$OUT/"

removed=0

# macOS: the CFBundleURLTypes key and its array
PLIST="$OUT/Info.plist"
if [ -f "$PLIST" ]; then
  python3 - "$PLIST" <<'PY'
import re, sys
p = sys.argv[1]
t = open(p, encoding="utf-8").read()
key = re.search(r"\s*<key>CFBundleURLTypes</key>\s*<array>", t)
t2 = t
if key:
    # the value is an array of dicts that hold an array themselves: walk to the matching close
    depth, pos = 1, key.end()
    for m in re.finditer(r"</?array>", t[pos:]):
        depth += 1 if m.group() == "<array>" else -1
        if depth == 0:
            t2 = t[: key.start()] + t[pos + m.end() :]
            break
open(p, "w", encoding="utf-8").write(t2)
print("Info.plist: URL types removed" if t2 != t else "Info.plist: nothing to remove")
PY
  removed=$((removed + 1))
fi

# Linux: the scheme handler of the desktop entry and its uninstall in the prerm hook
DESKTOP="$OUT/Weasis.desktop"
if [ -f "$DESKTOP" ] && grep -q 'x-scheme-handler/weasis' "$DESKTOP"; then
  sed -i.bak 's#x-scheme-handler/weasis;##' "$DESKTOP" && rm -f "$DESKTOP.bak"
  echo "Weasis.desktop: scheme handler removed"
  removed=$((removed + 1))
fi
PRERM="$OUT/prerm"
if [ -f "$PRERM" ] && grep -q 'x-scheme-handler/weasis' "$PRERM"; then
  # awk, not python3: this part runs in the Linux packaging image, which has no Python
  awk 'state == 0 && /^if command -v desktop_uninstall_default_mime_handler/ { state = 1; next }
       state == 1 { if ($0 == "fi") state = 2; next }
       { print }' "$PRERM" > "$PRERM.tmp" && mv "$PRERM.tmp" "$PRERM"
  echo "prerm: scheme handler uninstall removed"
  removed=$((removed + 1))
fi

# Windows: the URIHandlerRegistry component of each MSI resource set
for WXS in "$OUT"/msi/*/main.wxs; do
  [ -f "$WXS" ] || continue
  python3 - "$WXS" <<'PY'
import re, sys
p = sys.argv[1]
t = open(p, encoding="utf-8").read()
t2 = re.sub(r'\s*<Component Id="URIHandlerRegistry".*?</Component>', "", t, count=1, flags=re.S)
t2 = re.sub(r'\s*<ComponentRef Id="URIHandlerRegistry"\s*/>', "", t2)
open(p, "w", encoding="utf-8").write(t2)
print(p + (": URI handler removed" if t2 != t else ": nothing to remove"))
PY
  removed=$((removed + 1))
done

[ "$removed" -gt 0 ] || echo "No URL scheme registration found under $SRC"
echo "Pre-release resources: $OUT"
