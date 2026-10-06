#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v caddy >/dev/null 2>&1; then
  echo "Caddy is required for wss://KyleTurski.MC"
  echo "Install it from https://caddyserver.com/docs/install"
  exit 1
fi

echo "Eaglercraft join address: wss://KyleTurski.MC"
echo "DNS for KyleTurski.MC must point at this computer. Ports 80 and 443 must be open."
echo "Start the Minecraft server first (./launch/start-server.sh)."
cd "$ROOT"
exec caddy run --config "$ROOT/launch/Caddyfile"
