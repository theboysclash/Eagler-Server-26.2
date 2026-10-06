"""Apply the Eagler performance preset from launch/performance-preset.yml."""

from __future__ import annotations

import json
import re
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

import yaml

PRESET_PATH = Path(__file__).resolve().parent / "performance-preset.yml"
MODRINTH = "https://api.modrinth.com/v2"
MODRINTH_UA = "KyleTurskiMC-performance/1.0"


def load_preset() -> dict[str, Any]:
    if not PRESET_PATH.is_file():
        raise FileNotFoundError("performance-preset.yml is missing")
    return yaml.safe_load(PRESET_PATH.read_text(encoding="utf-8")) or {}


def read_properties(server_dir: Path) -> dict[str, str]:
    path = server_dir / "server.properties"
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key] = value
    return values


def write_properties(server_dir: Path, updates: dict[str, str]) -> None:
    path = server_dir / "server.properties"
    if not path.is_file():
        raise FileNotFoundError("server.properties is missing. Run launch/setup.py and start the server once.")
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines(keepends=True)
    seen: set[str] = set()
    new_lines: list[str] = []
    for line in lines:
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key = stripped.split("=", 1)[0]
            if key in updates:
                newline = "\n" if line.endswith("\n") else ""
                new_lines.append(f"{key}={updates[key]}{newline}")
                seen.add(key)
                continue
        new_lines.append(line)
    for key, value in updates.items():
        if key not in seen:
            new_lines.append(f"{key}={value}\n")
    path.write_text("".join(new_lines), encoding="utf-8")


def deep_merge(base: dict, patch: dict) -> dict:
    for key, value in patch.items():
        if isinstance(value, dict) and isinstance(base.get(key), dict):
            deep_merge(base[key], value)
        else:
            base[key] = value
    return base


def apply_paper_yaml(path: Path, patch: dict) -> bool:
    if not patch:
        return False
    if path.is_file():
        data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        if not isinstance(data, dict):
            data = {}
    else:
        data = {}
        path.parent.mkdir(parents=True, exist_ok=True)
    deep_merge(data, patch)
    path.write_text(yaml.dump(data, default_flow_style=False, sort_keys=False), encoding="utf-8")
    return True


def list_plugins(server_dir: Path) -> list[str]:
    plugins_dir = server_dir / "plugins"
    if not plugins_dir.is_dir():
        return []
    names: list[str] = []
    for path in plugins_dir.iterdir():
        if path.suffix == ".jar" or path.name.endswith(".jar.disabled"):
            names.append(path.name.replace(".disabled", ""))
    return names


def modrinth_install_chunky(server_dir: Path) -> dict[str, str]:
    preset = load_preset()
    slug = preset.get("chunky", {}).get("modrinth_project", "chunky")
    project = modrinth_get(f"/project/{slug}")
    project_id = project.get("id") or project.get("project_id")
    if not project_id:
        raise ValueError("Could not resolve Chunky on Modrinth")
    query = urllib.parse.urlencode({
        "loaders": json.dumps(["paper", "spigot", "bukkit"]),
        "game_versions": json.dumps(["26.2"]),
    })
    versions = modrinth_get(f"/project/{project_id}/version?{query}")
    if not isinstance(versions, list) or not versions:
        raise ValueError("No Chunky build for Minecraft 26.2")
    chosen = versions[0]
    files = chosen.get("files") or []
    primary = next((item for item in files if item.get("primary")), files[0] if files else None)
    if not primary:
        raise ValueError("Chunky version has no file")
    url = primary.get("url", "")
    filename = Path(primary.get("filename", "Chunky.jar")).name
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or "modrinth.com" not in parsed.netloc:
        raise ValueError("Unexpected download URL")
    plugins_dir = server_dir / "plugins"
    plugins_dir.mkdir(parents=True, exist_ok=True)
    destination = plugins_dir / filename
    request = urllib.request.Request(url, headers={"User-Agent": MODRINTH_UA})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    if len(data) > 80 * 1024 * 1024:
        raise ValueError("Download too large")
    destination.write_bytes(data)
    return {"name": filename, "version": str(chosen.get("version_number", ""))}


def modrinth_get(path: str) -> object:
    request = urllib.request.Request(
        MODRINTH + path,
        headers={"User-Agent": MODRINTH_UA, "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def apply_eagler_preset(server_dir: Path) -> dict[str, Any]:
    preset = load_preset()
    props = preset.get("server_properties") or {}
    if not isinstance(props, dict):
        props = {}
    clean_props = {str(k): str(v) for k, v in props.items()}
    write_properties(server_dir, clean_props)

    paper_patch = preset.get("paper_world_defaults") or {}
    paper_path = server_dir / "config" / "paper-world-defaults.yml"
    paper_applied = apply_paper_yaml(paper_path, paper_patch if isinstance(paper_patch, dict) else {})

    warnings: list[str] = []
    if not (server_dir / "config").is_dir() and paper_applied:
        warnings.append("Paper created config on next boot; you can apply again after the first start for extra tuning.")

    return {
        "appliedProperties": clean_props,
        "paperConfig": str(paper_path.relative_to(server_dir)) if paper_applied else None,
        "warnings": warnings,
        "restartRequired": True,
    }


def performance_status(server_dir: Path) -> dict[str, Any]:
    preset = load_preset()
    targets = preset.get("server_properties") or {}
    current = read_properties(server_dir)
    plugins = list_plugins(server_dir)
    chunky = any("chunky" in name.lower() for name in plugins)
    paper_defaults = server_dir / "config" / "paper-world-defaults.yml"
    return {
        "presetName": preset.get("name", "eagler"),
        "targets": targets,
        "current": {
            "view-distance": current.get("view-distance"),
            "simulation-distance": current.get("simulation-distance"),
            "entity-broadcast-range-percentage": current.get("entity-broadcast-range-percentage"),
        },
        "matchesPreset": all(str(current.get(k)) == str(v) for k, v in targets.items()),
        "paperConfigExists": paper_defaults.is_file(),
        "chunkyInstalled": chunky,
        "clientTips": preset.get("clientTips") or preset.get("client_tips") or [],
        "chunkyCommands": preset.get("chunky", {}).get("pregen_commands", []),
        "sparkCommands": preset.get("spark_commands", {}),
    }


def main() -> int:
    import sys

    root = Path(__file__).resolve().parent.parent
    server = root / "server-26.2"
    if len(sys.argv) > 1 and sys.argv[1] == "status":
        print(json.dumps(performance_status(server), indent=2))
        return 0
    result = apply_eagler_preset(server)
    print(json.dumps(result, indent=2))
    print("Restart the server for changes to take effect.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
