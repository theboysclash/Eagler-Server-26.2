#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PLUGIN_DIR="$ROOT/plugins/hub-economy"
JAR="$PLUGIN_DIR/build/libs/HubEconomy.jar"
DEST="$ROOT/server-26.2/plugins"

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

cd "$PLUGIN_DIR"
./gradlew build

if [[ -d "$DEST" ]]; then
  cp -f "$JAR" "$DEST/"
  echo "Copied HubEconomy.jar to $DEST"
fi
