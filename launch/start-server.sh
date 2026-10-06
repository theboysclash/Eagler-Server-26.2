#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="$ROOT/server-26.2"

if [[ ! -f "$SERVER_DIR/paper.jar" ]]; then
  echo "Server not set up yet. Run: python3 launch/setup.py"
  exit 1
fi

echo "Opening the KyleTurski MC dashboard. Leave this window open."
exec python3 "$ROOT/dashboard/app.py" --open --autostart
