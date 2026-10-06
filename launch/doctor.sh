#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
echo "=== KyleTurski MC doctor ==="
echo
command -v python3 >/dev/null && python3 --version || echo "MISSING python3"
echo
echo "Java:"
java -version 2>/dev/null || echo "  java not on PATH"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  "$JAVA_HOME/bin/java" -version 2>/dev/null || true
fi
echo
[[ -f server-26.2/paper.jar ]] && echo "OK paper.jar" || echo "MISSING server — run: python3 launch/setup.py"
[[ -f server-26.2/plugins/HubEconomy.jar ]] && echo "OK HubEconomy.jar" || echo "MISSING HubEconomy — run launch/build-plugin.sh then setup.py"
[[ -f server-26.2/hub/level.dat ]] && echo "OK custom hub world" || echo "Hub: default platform or import-worlds/hub + launch/import-hub.sh"
echo
echo "Start: ./launch/start-server.sh"
