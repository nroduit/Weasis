#!/bin/bash
# Repackages the jpackage .deb as an AppImage with linuxdeploy.
#
# Usage: package-appimage.sh --deb <file.deb> --arch x86_64|aarch64 --out <dir> --tools <dir>
# <dir> for --tools must contain linuxdeploy-<arch>.AppImage, linuxdeploy-plugin-appimage-<arch>.AppImage
# and runtime-<arch> (the AppImage type2 runtime).

set -euo pipefail

die() {
  echo "ERROR: $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --deb) DEB="$2"; shift 2 ;;
    --arch) ARCH="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --tools) TOOLS="$2"; shift 2 ;;
    *) die "Unknown option: $1" ;;
  esac
done
: "${DEB:?--deb is required}" "${ARCH:?--arch is required}" "${OUT:?--out is required}" "${TOOLS:?--tools is required}"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
APPDIR="$WORK/AppDir"
VERSION=$(dpkg-deb -f "$DEB" Version | cut -d- -f1)

dpkg-deb -x "$DEB" "$WORK/deb"
APP="$WORK/deb/opt/weasis"
[ -x "$APP/bin/Weasis" ] || die "bin/Weasis not found in $DEB"

# Relative Exec/Icon: the AppImage is relocatable, unlike the /opt/weasis paths of the deb
sed -e 's|^Exec=.*|Exec=Weasis %U|' -e 's|^Icon=.*|Icon=weasis|' \
  "$APP/lib/weasis-Weasis.desktop" > "$WORK/weasis.desktop"
cp "$APP/lib/Weasis.png" "$WORK/weasis.png"

export APPIMAGE_EXTRACT_AND_RUN=1 ARCH
export LDAI_RUNTIME_FILE="$TOOLS/runtime-$ARCH"
export OUTPUT="$OUT/Weasis-$VERSION-$ARCH.AppImage"
mkdir -p "$OUT" "$APPDIR/usr/bin"

# Stage 1 uses a placeholder launcher: linuxdeploy fails when it scans the bundled JRE (libjvm.so)
printf '#!/bin/sh\n' > "$APPDIR/usr/bin/Weasis"
chmod +x "$APPDIR/usr/bin/Weasis"
"$TOOLS/linuxdeploy-$ARCH.AppImage" --appdir "$APPDIR" -d "$WORK/weasis.desktop" -i "$WORK/weasis.png"

# Stage 2: the real jpackage tree replaces the placeholder, then the AppDir is packed
cp -a "$APP/." "$APPDIR/usr/"
ln -sfn usr/bin/Weasis "$APPDIR/AppRun"
"$TOOLS/linuxdeploy-plugin-appimage-$ARCH.AppImage" --appdir "$APPDIR"

echo "Created $OUTPUT"
