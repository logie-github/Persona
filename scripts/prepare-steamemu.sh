#!/bin/sh
# Puts the Goldberg Steam emulator's Windows libraries (steamemu-version.env) into app/steamemu/assets/steamemu, for the app to swap into the
# games' folders (SteamEmu), and its loader with the experimental client libraries into .../coldclient (ColdClient): needs curl and 7z (p7zip).
# Does nothing when that version is already there.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1090
. "$ROOT/steamemu-version.env"
OUT="$ROOT/app/steamemu/assets/steamemu"
if [ -f "$OUT/VERSION" ] && [ -d "$OUT/coldclient" ] && [ "$(cat "$OUT/VERSION")" = "$GBE_TAG" ]; then echo "gbe_fork $GBE_TAG already in $OUT"; exit 0; fi
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
# The release's Windows build: its asset named in steamemu-version.env, else whichever of its assets is the Windows release 7z (the name has
# changed between releases), found through GitHub's API (with the workflow's token when there is one).
api="https://api.github.com/repos/${GBE_REPO#https://github.com/}/releases/tags/$GBE_TAG"
url=$(curl -fsSL --retry 4 ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} "$api" | python3 -c '
import json, re, sys
want = sys.argv[1]
assets = json.load(sys.stdin).get("assets", [])
names = [a["name"] for a in assets]
pick = next((a for a in assets if a["name"] == want), None) or next((a for a in assets if re.search(r"(?i)win", a["name"]) and re.search(r"(?i)release", a["name"]) and a["name"].lower().endswith(".7z")), None)
if not pick: sys.exit("no Windows release 7z among " + ", ".join(names))
print(pick["browser_download_url"])
' "$GBE_ASSET")
echo "gbe_fork $GBE_TAG: $url"
curl -fsSL --retry 4 -o "$tmp/emu.7z" "$url"
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
# The loader way (ColdClient, GameNative's default): the experimental client libraries and the loaders, from the release's steamclient_experimental
# folder. The app starts a game through them when they are there, else it swaps the game's library as above.
mkdir -p "$OUT/coldclient"
for name in steamclient.dll steamclient64.dll steamclient_loader_x32.exe steamclient_loader_x64.exe GameOverlayRenderer.dll GameOverlayRenderer64.dll; do
	f=$(find "$tmp/x" -ipath '*steamclient_experimental*' -iname "$name" -not -ipath '*extra_dlls*' | head -n 1)
	if [ -n "$f" ]; then cp "$f" "$OUT/coldclient/$name"; echo "  coldclient: ${f#$tmp/x/}"; else echo "  coldclient: no $name in the release" >&2; fi
done
printf '%s' "$GBE_TAG" > "$OUT/VERSION"
echo "gbe_fork $GBE_TAG in $OUT"
