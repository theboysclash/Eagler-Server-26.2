#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/import-worlds/hub"
DEST="$ROOT/server-26.2/hub"

if [[ ! -f "$SRC/level.dat" ]]; then
  echo "Put your hub in import-worlds/hub/ (level.dat + region/)"
  exit 1
fi
if [[ ! -d "$ROOT/server-26.2" ]]; then
  echo "Run: python3 launch/setup.py"
  exit 1
fi
rm -rf "$DEST"
mkdir -p "$DEST"
cp -a "$SRC/." "$DEST/"
echo "Done. Restart the server and run /sethub at spawn once."
