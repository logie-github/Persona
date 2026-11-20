#!/bin/sh
# Builds the session's keeper (tools/borderless/borderless.c, the Blizzard launcher's) into app/keeper/assets/borderless.exe (git-ignored),
# with Zig (keeper.env: its release and SHA-256), for x86_64 Windows, the Windows subsystem (no console). Every Windows game is started
# through it (Winlator patch 0010: the exec_wrapper extra).
#
# KEEPER_WORK is where to keep Zig (default build/keeper).
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT/keeper.env"
WORK="${KEEPER_WORK:-$ROOT/build/keeper}"
OUT="$ROOT/app/keeper/assets"
mkdir -p "$WORK" "$OUT"
if [ ! -f "$WORK/zig.tar.xz" ] || ! echo "$ZIG_SHA256  $WORK/zig.tar.xz" | sha256sum -c - > /dev/null 2>&1; then
	curl -fsSL --retry 4 -o "$WORK/zig.tar.xz" "$ZIG_URL"
	echo "$ZIG_SHA256  $WORK/zig.tar.xz" | sha256sum -c - > /dev/null || { echo "zig: wrong SHA-256" >&2; exit 1; }
	rm -rf "$WORK/zig"
fi
if [ ! -x "$WORK/zig/zig" ]; then
	mkdir -p "$WORK/zig"
	tar -xJf "$WORK/zig.tar.xz" -C "$WORK/zig" --strip-components 1
fi
"$WORK/zig/zig" cc -target x86_64-windows-gnu -O2 -s -Wl,--subsystem,windows "$ROOT/tools/borderless/borderless.c" -luser32 -lkernel32 -o "$OUT/borderless.exe" 2> "$WORK/borderless.log" ||
	{ tail -40 "$WORK/borderless.log"; exit 1; }
echo "borderless.exe in $OUT ($(wc -c < "$OUT/borderless.exe") bytes)"
