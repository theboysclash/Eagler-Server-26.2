#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="$ROOT/server-26.2"
MANIFEST="$ROOT/launch/manifest.json"

if [[ ! -f "$SERVER_DIR/paper.jar" ]]; then
  echo "Server not set up yet. Run: python3 launch/setup.py"
  exit 1
fi

MIN_MEM="$(python3 -c "import json; print(json.load(open('$MANIFEST'))['defaults']['minMemory'])")"
MAX_MEM="$(python3 -c "import json; print(json.load(open('$MANIFEST'))['defaults']['maxMemory'])")"

cd "$SERVER_DIR"
exec java -Xms"$MIN_MEM" -Xmx"$MAX_MEM" -jar paper.jar nogui
