#!/bin/sh
# Puts the Goldberg Steam emulator's Windows libraries (steamemu-version.env) into app/steamemu/assets/steamemu, for the app to swap into the
# games' folders (SteamEmu): needs curl and 7z (p7zip). Does nothing when that version is already there.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1090
. "$ROOT/steamemu-version.env"
OUT="$ROOT/app/steamemu/assets/steamemu"
if [ -f "$OUT/VERSION" ] && [ "$(cat "$OUT/VERSION")" = "$GBE_TAG" ]; then echo "gbe_fork $GBE_TAG already in $OUT"; exit 0; fi
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
curl -fsSL --retry 4 -o "$tmp/emu.7z" "$GBE_REPO/releases/download/$GBE_TAG/$GBE_ASSET"
7z x -y -o"$tmp/x" "$tmp/emu.7z" > /dev/null
pick() {   # the emulator's regular build of a library (not the experimental one), else any copy
	f=$(find "$tmp/x" -ipath '*/regular/*' -iname "$1" | head -n 1)
	[ -n "$f" ] || f=$(find "$tmp/x" -iname "$1" | head -n 1)
	[ -n "$f" ] || { echo "No $1 in $GBE_ASSET" >&2; exit 1; }
	printf '%s' "$f"
}
rm -rf "$OUT"; mkdir -p "$OUT"
cp "$(pick steam_api.dll)" "$OUT/steam_api.dll"
cp "$(pick steam_api64.dll)" "$OUT/steam_api64.dll"
printf '%s' "$GBE_TAG" > "$OUT/VERSION"
echo "gbe_fork $GBE_TAG in $OUT"
