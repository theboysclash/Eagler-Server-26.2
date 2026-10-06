#!/usr/bin/env python3
"""Download Paper 26.2 + EaglerXPaper and prepare a local server directory."""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO_ROOT = ROOT.parent
MANIFEST_PATH = ROOT / "manifest.json"
SERVER_DIR = REPO_ROOT / "server-26.2"
PLUGINS_DIR = SERVER_DIR / "plugins"
TEMPLATES = ROOT / "templates"


def load_manifest() -> dict:
    with MANIFEST_PATH.open(encoding="utf-8") as f:
        return json.load(f)


def java_major_version() -> int | None:
    try:
        out = subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT, text=True)
    except (OSError, subprocess.CalledProcessError):
        return None
    # e.g. 'openjdk version "25.0.1" ...'
    for token in out.replace('"', " ").split():
        if token[0:1].isdigit():
            return int(token.split(".")[0])
    return None


def download(url: str, dest: Path, expected_sha256: str | None) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    print(f"Downloading {dest.name} ...")
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "Eagler-Server-26.2-setup/1.0 (+https://github.com/theboysclash/Eagler-Server-26.2)"},
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = resp.read()
    tmp.write_bytes(data)
    if expected_sha256:
        digest = hashlib.sha256(data).hexdigest()
        if digest.lower() != expected_sha256.lower():
            tmp.unlink(missing_ok=True)
            raise SystemExit(
                f"SHA256 mismatch for {dest.name}\n"
                f"  expected: {expected_sha256}\n"
                f"  got:      {digest}"
            )
    tmp.replace(dest)
    print(f"  saved to {dest}")


def ensure_server_properties(port: int) -> None:
    dest = SERVER_DIR / "server.properties"
    if dest.exists():
        return
    template = (TEMPLATES / "server.properties").read_text(encoding="utf-8")
    dest.write_text(template.replace("server-port=25565", f"server-port={port}"), encoding="utf-8")


def ensure_eula() -> None:
    dest = SERVER_DIR / "eula.txt"
    if dest.exists():
        return
    dest.write_text(
        "# By changing the setting below to TRUE you are indicating your agreement to Mojang's EULA.\n"
        "# https://aka.ms/MinecraftEULA\n"
        "eula=true\n",
        encoding="utf-8",
    )


def main() -> int:
    manifest = load_manifest()
    min_java = manifest["java"]["minimumMajor"]
    skip_java = os.environ.get("EAGLER_SETUP_SKIP_JAVA", "").lower() in ("1", "true", "yes")
    major = java_major_version()
    if major is None and not skip_java:
        print("Java was not found on PATH.", file=sys.stderr)
        print(f"Install Java {min_java}+ from {manifest['java']['downloadHint']}", file=sys.stderr)
        return 1
    if major is not None and major < min_java and not skip_java:
        print(f"Java {major} detected; Paper 26.2 needs Java {min_java}+.", file=sys.stderr)
        print(f"Install from {manifest['java']['downloadHint']}", file=sys.stderr)
        return 1
    if major is None:
        print("Warning: Java not found; continuing because EAGLER_SETUP_SKIP_JAVA is set.")
    else:
        print(f"Using Java {major}")

    SERVER_DIR.mkdir(parents=True, exist_ok=True)
    PLUGINS_DIR.mkdir(parents=True, exist_ok=True)

    paper = manifest["paper"]
    paper_jar = SERVER_DIR / paper["fileName"]
    if not paper_jar.exists():
        download(paper["url"], paper_jar, paper.get("sha256"))

    plugin = manifest["eaglerXPaper"]
    plugin_jar = PLUGINS_DIR / plugin["fileName"]
    if not plugin_jar.exists():
        download(plugin["url"], plugin_jar, plugin.get("sha256"))

    # Symlink or copy a stable name for launch scripts
    stable_paper = SERVER_DIR / "paper.jar"
    if stable_paper.exists() or stable_paper.is_symlink():
        stable_paper.unlink()
    try:
        os.symlink(paper_jar.name, stable_paper)
    except OSError:
        shutil.copy2(paper_jar, stable_paper)

    port = manifest["defaults"]["serverPort"]
    ensure_eula()
    ensure_server_properties(port)

    hub_jar = REPO_ROOT / "plugins" / "hub-economy" / "build" / "libs" / "HubEconomy.jar"
    if hub_jar.is_file():
        shutil.copy2(hub_jar, PLUGINS_DIR / "HubEconomy.jar")
        print(f"  copied {hub_jar.name} to {PLUGINS_DIR}")

    print()
    print("Setup complete.")
    print(f"  Server files: {SERVER_DIR}")
    print()
    print("Start the server:")
    if sys.platform == "win32":
        print("  launch\\start-server.bat")
    else:
        print("  ./launch/start-server.sh")
    print()
    print("Eaglercraft 26.2 (offline HTML): Direct Connect →")
    print(f"  ws://127.0.0.1:{port}/")
    print("Java Edition 26.2 on the same PC:")
    print(f"  127.0.0.1:{port}")
    print()
    print("For friends on the internet, expose port 25565 or use ngrok / Cloudflare Tunnel")
    print("(see README — Creating a 26.2 Server).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
