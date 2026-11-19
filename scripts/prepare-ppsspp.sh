#!/bin/sh
# Brings the PSP emulator's source (the tag in ppsspp-version.env, with its submodules) into build/ppsspp-src and applies patches/ppsspp/:
#
#     scripts/prepare-ppsspp.sh
#
# Safe to run again: a tree that already has the patches is left as it is.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1090
. "$ROOT/ppsspp-version.env"
SRC="$ROOT/build/ppsspp-src"
if [ ! -d "$SRC/.git" ]; then
	mkdir -p "$ROOT/build"
	git clone --depth 1 --branch "$PPSSPP_TAG" --recurse-submodules --shallow-submodules --jobs 4 "$PPSSPP_REPO" "$SRC"
fi
for p in "$ROOT"/patches/ppsspp/*.patch; do
	[ -f "$p" ] || continue
	if git -C "$SRC" apply --check -R "$p" 2> /dev/null; then continue; fi        # already applied
	git -C "$SRC" apply "$p"
done
echo "PPSSPP $PPSSPP_TAG is in $SRC"
