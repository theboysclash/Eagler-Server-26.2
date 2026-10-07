#!/usr/bin/env python3
"""Local KyleTurski MC dashboard. Binds to 127.0.0.1 only."""

from __future__ import annotations

import json
import os
import re
import shutil
import signal
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
import importlib.util
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PERFORMANCE_PRESET = ROOT / "launch" / "performance_preset.py"


def load_performance_module():
    spec = importlib.util.spec_from_file_location("performance_preset", PERFORMANCE_PRESET)
    if spec is None or spec.loader is None:
        raise RuntimeError("performance_preset.py could not be loaded")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

SERVER_DIR = ROOT / "server-26.2"
PLUGINS_DIR = SERVER_DIR / "plugins"
STATIC_DIR = Path(__file__).resolve().parent / "static"
DATA_DIR = Path(__file__).resolve().parent / "data"
SETTINGS_PATH = DATA_DIR / "settings.json"
MANIFEST_PATH = ROOT / "launch" / "manifest.json"
EAGLERX_ADDONS_PATH = ROOT / "launch" / "eaglerx-addons.json"
BACKUPS_DIR = SERVER_DIR / "backups"
HOST = "127.0.0.1"
DEFAULT_PORT = 8765
MODRINTH = "https://api.modrinth.com/v2"
MODRINTH_UA = "KyleTurskiMC-Dashboard/1.0 (local server panel)"
JOIN_RE = re.compile(r"\]: ([A-Za-z0-9_]{1,16}) joined the game")
LEAVE_RE = re.compile(r"\]: ([A-Za-z0-9_]{1,16}) left the game")
JVM_SERVER_MARKER = "-Dkyleturski.mc=1"

runtime = None  # type: ignore


def load_manifest() -> dict:
    if not MANIFEST_PATH.is_file():
        return {}
    return json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))


def load_settings() -> dict:
    manifest = load_manifest()
    defaults = manifest.get("defaults", {})
    settings = {
        "minMemory": defaults.get("minMemory", "2G"),
        "maxMemory": defaults.get("maxMemory", "4G"),
    }
    if SETTINGS_PATH.is_file():
        try:
            stored = json.loads(SETTINGS_PATH.read_text(encoding="utf-8"))
            if isinstance(stored, dict):
                settings.update({k: stored[k] for k in ("minMemory", "maxMemory") if k in stored})
        except json.JSONDecodeError:
            pass
    return settings


def save_settings(settings: dict) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    SETTINGS_PATH.write_text(json.dumps(settings, indent=2), encoding="utf-8")


def parse_memory(value: str) -> int:
    text = value.strip().upper()
    match = re.fullmatch(r"(\d+)([GMK])", text)
    if not match:
        return 0
    amount = int(match.group(1))
    unit = match.group(2)
    if unit == "G":
        return amount * 1024 ** 3
    if unit == "M":
        return amount * 1024 ** 2
    return amount * 1024


def java_major(binary: str) -> int | None:
    try:
        out = subprocess.check_output([binary, "-version"], stderr=subprocess.STDOUT, text=True)
    except (OSError, subprocess.CalledProcessError):
        return None
    for token in out.replace('"', " ").split():
        if token[:1].isdigit():
            try:
                return int(token.split(".")[0])
            except ValueError:
                return None
    return None


def find_java() -> str | None:
    candidates: list[str] = []
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        name = "java.exe" if os.name == "nt" else "java"
        candidates.append(str(Path(java_home) / "bin" / name))
    which = shutil.which("java")
    if which:
        candidates.append(which)
    opt = Path("/opt")
    if opt.is_dir():
        candidates.extend(str(path) for path in sorted(opt.glob("jdk-25*/bin/java")))
    if os.name == "nt":
        for pattern in (
            Path(r"C:\Program Files\Eclipse Adoptium"),
            Path(r"C:\Program Files\Java"),
            Path(r"C:\Program Files\Microsoft"),
        ):
            if pattern.is_dir():
                candidates.extend(
                    str(path / "bin" / "java.exe")
                    for path in sorted(pattern.glob("jdk-25*"))
                    if (path / "bin" / "java.exe").is_file()
                )
    for candidate in candidates:
        major = java_major(candidate)
        if major is not None and major >= 25:
            return candidate
    return None


def safe_server_path(relative: str) -> Path:
    relative = relative.replace("\\", "/").lstrip("/")
    root = SERVER_DIR.resolve()
    target = (root / relative).resolve()
    if target != root and root not in target.parents:
        raise ValueError("Path escapes the server folder")
    return target


def read_properties() -> dict[str, str]:
    path = SERVER_DIR / "server.properties"
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key] = value
    return values


def write_properties(updates: dict[str, str]) -> None:
    path = SERVER_DIR / "server.properties"
    if not path.is_file():
        raise FileNotFoundError("server.properties is missing. Run launch/setup.py first.")
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines(keepends=True)
    seen = set()
    new_lines = []
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


def folder_size(path: Path) -> int:
    total = 0
    if not path.exists():
        return 0
    for root, dirs, files in os.walk(path):
        dirs[:] = [name for name in dirs if name not in {".git"}]
        for name in files:
            try:
                total += (Path(root) / name).stat().st_size
            except OSError:
                pass
    return total


def modrinth_get(path: str) -> object:
    request = urllib.request.Request(
        MODRINTH + path,
        headers={"User-Agent": MODRINTH_UA, "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


class PublicTunnel:
    """Cloudflare quick tunnel: free https URL, no domain signup."""

    def __init__(self) -> None:
        self.proc: subprocess.Popen | None = None
        self.https_url = ""
        self.error = ""
        self.lines: list[str] = []

    def running(self) -> bool:
        return self.proc is not None and self.proc.poll() is None

    def eagler_url(self) -> str:
        if self.https_url.startswith("https://"):
            return "wss://" + self.https_url[len("https://"):]
        return ""

    def binary_path(self) -> Path:
        name = "cloudflared.exe" if os.name == "nt" else "cloudflared"
        return SERVER_DIR / name

    def ensure_binary(self) -> Path:
        dest = self.binary_path()
        if dest.is_file() and dest.stat().st_size > 1_000_000:
            return dest
        url = (
            "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
            if os.name == "nt"
            else "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64"
        )
        if runtime is not None:
            runtime.append("Downloading free public tunnel (cloudflared)...")
        dest.parent.mkdir(parents=True, exist_ok=True)
        request = urllib.request.Request(url, headers={"User-Agent": MODRINTH_UA})
        with urllib.request.urlopen(request, timeout=180) as response:
            dest.write_bytes(response.read())
        if os.name != "nt":
            dest.chmod(0o755)
        return dest

    def start(self, port: int) -> str | None:
        if self.running() and self.eagler_url():
            return None
        self.stop()
        try:
            binary = self.ensure_binary()
        except Exception as exc:  # noqa: BLE001
            self.error = f"Could not download cloudflared: {exc}"
            return self.error
        self.https_url = ""
        self.error = ""
        self.lines.clear()
        self.proc = subprocess.Popen(
            [str(binary), "tunnel", "--url", f"http://127.0.0.1:{port}", "--no-autoupdate"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        threading.Thread(target=self._read, daemon=True).start()
        for _ in range(50):
            if self.eagler_url():
                if runtime is not None:
                    runtime.append("Public Eagler address: " + self.eagler_url())
                return None
            if self.proc.poll() is not None:
                tail = " ".join(self.lines[-4:])
                self.error = "Public tunnel stopped. " + tail
                return self.error
            time.sleep(0.5)
        self.error = "Timed out waiting for a public address."
        return self.error

    def stop(self) -> None:
        proc = self.proc
        self.proc = None
        self.https_url = ""
        if proc is not None and proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()

    def status(self) -> dict:
        return {
            "running": self.running(),
            "eaglerUrl": self.eagler_url(),
            "httpsUrl": self.https_url,
            "error": self.error,
        }

    def _read(self) -> None:
        proc = self.proc
        if proc is None or proc.stdout is None:
            return
        for line in proc.stdout:
            text = line.rstrip()
            self.lines.append(text)
            self.lines = self.lines[-40:]
            match = re.search(r"https://[A-Za-z0-9-]+\.trycloudflare\.com", text)
            if match:
                self.https_url = match.group(0)
            if runtime is not None and ("trycloudflare" in text or "ERR" in text):
                runtime.append(text)


public_tunnel = PublicTunnel()


def kill_related_server_processes(runtime: "MinecraftRuntime") -> list[str]:
    """End Paper, Caddy, the free public tunnel, and other server processes (not this dashboard)."""
    public_tunnel.stop()
    runtime.force_stop()
    killed: list[str] = []
    my_pid = os.getpid()
    server_path = str(SERVER_DIR.resolve())
    marker = JVM_SERVER_MARKER

    if os.name == "nt":
        server_escaped = server_path.replace("'", "''")
        script = (
            f"$mine = {my_pid}; "
            f"$marker = '{marker}'; "
            f"$server = '{server_escaped}'; "
            "Get-CimInstance Win32_Process | ForEach-Object { "
            "  $procId = $_.ProcessId; "
            "  if ($procId -eq $mine) { return }; "
            "  $cmd = $_.CommandLine; "
            "  if (-not $cmd) { return }; "
            "  $hit = $false; "
            "  $name = $_.Name; "
            "  if ($cmd -like ('*' + $marker + '*')) { $hit = $true; $name = 'Minecraft' } "
            "  elseif (($cmd -like '*caddy*') -and ($cmd -like '*Caddyfile*')) { $hit = $true; $name = 'Caddy' } "
            "  elseif ($cmd -like '*cloudflared*') { $hit = $true; $name = 'Public tunnel' } "
            "  elseif (($cmd -like '*paper.jar*') -and ($cmd -like ('*' + $server + '*'))) { $hit = $true; $name = 'Minecraft' }; "
            "  if ($hit) { "
            "    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue; "
            "    Write-Output ($name + ' (pid ' + $procId + ')') "
            "  } "
            "}"
        )
        try:
            out = subprocess.check_output(
                ["powershell", "-NoProfile", "-Command", script],
                text=True,
                timeout=30,
            )
            killed = [line.strip() for line in out.splitlines() if line.strip()]
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
            pass
        return killed

    for entry in Path("/proc").iterdir():
        if not entry.name.isdigit():
            continue
        pid = int(entry.name)
        if pid == my_pid:
            continue
        try:
            cmdline = (entry / "cmdline").read_bytes().replace(b"\0", b" ").decode("utf-8", errors="replace")
        except OSError:
            continue
        label = None
        if marker in cmdline:
            label = f"Minecraft (pid {pid})"
        elif "caddy" in cmdline and "Caddyfile" in cmdline:
            label = f"Caddy (pid {pid})"
        elif "cloudflared" in cmdline:
            label = f"Public tunnel (pid {pid})"
        elif "paper.jar" in cmdline and server_path in cmdline:
            label = f"Minecraft (pid {pid})"
        if not label:
            continue
        try:
            os.kill(pid, signal.SIGTERM)
            killed.append(label)
        except ProcessLookupError:
            continue
    time.sleep(0.4)
    for entry in Path("/proc").iterdir():
        if not entry.name.isdigit():
            continue
        pid = int(entry.name)
        if pid == my_pid:
            continue
        try:
            cmdline = (entry / "cmdline").read_bytes().replace(b"\0", b" ").decode("utf-8", errors="replace")
        except OSError:
            continue
        if marker not in cmdline and "cloudflared" not in cmdline and not (
            "caddy" in cmdline and "Caddyfile" in cmdline
        ) and not ("paper.jar" in cmdline and server_path in cmdline):
            continue
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    return killed


class MinecraftRuntime:
    def __init__(self) -> None:
        self.proc: subprocess.Popen | None = None
        self.lines: list[dict] = []
        self.seq = 0
        self.players: set[str] = set()
        self.lock = threading.Lock()
        self.cpu_percent = 0.0
        self.memory_bytes = 0
        self._cpu_sample: tuple[float, int] | None = None
        self._storage_cache = (0.0, 0)
        self.saved_notice = False
        self.state = "stopped"

    def append(self, text: str) -> None:
        text = text.rstrip("\r\n")
        if "Saved the game" in text:
            self.saved_notice = True
        joined = JOIN_RE.search(text)
        if joined:
            self.players.add(joined.group(1))
        left = LEAVE_RE.search(text)
        if left:
            self.players.discard(left.group(1))
        with self.lock:
            self.seq += 1
            self.lines.append({"id": self.seq, "text": text})
            if len(self.lines) > 800:
                self.lines = self.lines[-800:]

    def snapshot(self, after: int) -> list[dict]:
        with self.lock:
            return [line for line in self.lines if line["id"] > after]

    def running(self) -> bool:
        return self.proc is not None and self.proc.poll() is None

    def start(self) -> str | None:
        if self.running():
            return "Server is already running."
        if not (SERVER_DIR / "paper.jar").is_file():
            return "Server files are missing. Run python launch/setup.py first."
        java = find_java()
        if not java:
            return "Java 25+ was not found. Install Temurin 25 and try again."
        settings = load_settings()
        SERVER_DIR.mkdir(parents=True, exist_ok=True)
        command = [
            java,
            JVM_SERVER_MARKER,
            f"-Xms{settings['minMemory']}",
            f"-Xmx{settings['maxMemory']}",
            "-jar",
            "paper.jar",
            "nogui",
        ]
        self.players.clear()
        self.state = "starting"
        self.append("Starting KyleTurski MC...")
        self.proc = subprocess.Popen(
            command,
            cwd=SERVER_DIR,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            bufsize=1,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        threading.Thread(target=self._read_output, daemon=True).start()
        threading.Thread(target=self._watch_stats, daemon=True).start()
        return None

    def _read_output(self) -> None:
        proc = self.proc
        if proc is None or proc.stdout is None:
            return
        for line in proc.stdout:
            self.append(line)
            if "Done (" in line:
                self.state = "running"
        code = proc.wait()
        self.state = "stopped"
        self.players.clear()
        self.cpu_percent = 0.0
        self.memory_bytes = 0
        self.append(f"Server stopped (exit {code}).")

    def send(self, command: str) -> str | None:
        if not self.running() or self.proc is None or self.proc.stdin is None:
            return "Server is not running."
        text = command.strip()
        if not text or "\n" in text or "\r" in text:
            return "Enter one command."
        self.proc.stdin.write(text + "\n")
        self.proc.stdin.flush()
        self.append("> " + text)
        return None

    def flush_save(self) -> None:
        self.saved_notice = False
        self.send("save-all flush")
        for _ in range(40):
            if self.saved_notice or not self.running():
                return
            time.sleep(0.5)

    def stop(self) -> str | None:
        if not self.running():
            self.state = "stopped"
            return "Server is already stopped."
        self.state = "stopping"
        self.flush_save()
        error = self.send("stop")
        if error:
            return error
        return None

    def restart(self) -> str | None:
        if self.running():
            error = self.stop()
            if error and "already" not in error:
                return error
            for _ in range(120):
                if not self.running():
                    break
                time.sleep(0.5)
            if self.running() and self.proc is not None:
                self.proc.kill()
        return self.start()

    def force_stop(self) -> None:
        proc = self.proc
        if proc is not None and proc.poll() is None:
            self.flush_save()
            try:
                if proc.stdin is not None:
                    proc.stdin.write("stop\n")
                    proc.stdin.flush()
            except OSError:
                pass
            for _ in range(16):
                if proc.poll() is not None:
                    break
                time.sleep(0.5)
            if proc.poll() is None:
                proc.kill()
                try:
                    proc.wait(timeout=8)
                except subprocess.TimeoutExpired:
                    proc.kill()
        self.proc = None
        self.state = "stopped"
        self.players.clear()
        self.cpu_percent = 0.0
        self.memory_bytes = 0
        self.append("Server process ended (force stop).")

    def _watch_stats(self) -> None:
        while self.running() and self.proc is not None:
            self._sample_process(self.proc.pid)
            time.sleep(2)

    def _sample_process(self, pid: int) -> None:
        if os.name == "nt":
            self._sample_windows(pid)
        else:
            self._sample_linux(pid)

    def _sample_linux(self, pid: int) -> None:
        try:
            stat = Path(f"/proc/{pid}/stat").read_text(encoding="utf-8")
            status = Path(f"/proc/{pid}/status").read_text(encoding="utf-8")
        except OSError:
            return
        parts = stat.split()
        if len(parts) < 17:
            return
        ticks = int(parts[13]) + int(parts[14])
        now = time.time()
        if self._cpu_sample is not None:
            last_time, last_ticks = self._cpu_sample
            elapsed = now - last_time
            if elapsed > 0:
                cpu = ((ticks - last_ticks) / os.sysconf("SC_CLK_TCK")) / elapsed * 100
                self.cpu_percent = max(0.0, round(cpu, 1))
        self._cpu_sample = (now, ticks)
        for line in status.splitlines():
            if line.startswith("VmRSS:"):
                kb = int(line.split()[1])
                self.memory_bytes = kb * 1024
                break

    def _sample_windows(self, pid: int) -> None:
        script = (
            f"$p = Get-Process -Id {pid} -ErrorAction SilentlyContinue; "
            "if ($p) { Write-Output ($p.WorkingSet64.ToString() + ' ' + $p.CPU) }"
        )
        try:
            out = subprocess.check_output(
                ["powershell", "-NoProfile", "-Command", script],
                text=True,
                timeout=5,
            ).strip()
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
            return
        bits = out.split()
        if not bits:
            return
        try:
            self.memory_bytes = int(float(bits[0]))
        except ValueError:
            return

    def storage_bytes(self) -> int:
        now = time.time()
        cached_at, size = self._storage_cache
        if now - cached_at < 30:
            return size
        size = folder_size(SERVER_DIR)
        self._storage_cache = (now, size)
        return size

    def status(self) -> dict:
        props = read_properties()
        settings = load_settings()
        disk = shutil.disk_usage(SERVER_DIR if SERVER_DIR.exists() else ROOT)
        state = "running" if self.running() else self.state
        if state == "starting" and not self.running() and self.proc is not None and self.proc.poll() is not None:
            state = "stopped"
        return {
            "state": state if self.running() or state == "stopping" else "stopped",
            "players": sorted(self.players),
            "maxPlayers": int(props.get("max-players", "20") or 20),
            "cpu": self.cpu_percent if self.running() else 0.0,
            "memoryBytes": self.memory_bytes if self.running() else 0,
            "memoryMaxBytes": parse_memory(settings.get("maxMemory", "4G")),
            "storageBytes": self.storage_bytes(),
            "diskFreeBytes": disk.free,
            "diskTotalBytes": disk.total,
            "version": load_manifest().get("minecraftVersion", "26.2"),
            "motd": props.get("motd", "KyleTurski MC"),
            "port": int(props.get("server-port", "25565") or 25565),
            "javaReady": find_java() is not None,
            "javaHint": "Install Temurin Java 25 and restart the dashboard, or set JAVA_HOME to jdk-25.",
            "hubEconomyReady": (PLUGINS_DIR / "HubEconomy.jar").is_file(),
            "setupReady": (SERVER_DIR / "paper.jar").is_file(),
        }


def admin_console_command(data: dict, action: str) -> str:
    if action == "save":
        return "save-all flush"
    name = str(data.get("player", "")).strip()
    if not re.fullmatch(r"[A-Za-z0-9_]{1,16}", name):
        raise ValueError("Enter the player's exact Minecraft name.")
    if action == "heal":
        return f"effect give {name} minecraft:instant_health 1 10 true"
    if action == "feed":
        return f"effect give {name} minecraft:saturation 1 10 true"
    if action == "creative":
        return f"gamemode creative {name}"
    if action == "survival":
        return f"gamemode survival {name}"
    if action == "op":
        return f"op {name}"
    if action == "deop":
        return f"deop {name}"
    if action == "clear":
        return f"clear {name}"
    if action == "hubtool":
        return f"hubeconomy tool {name}"
    if action == "give":
        item = str(data.get("item", "stone")).strip().lower()
        if item.startswith("minecraft:"):
            item = item.split(":", 1)[1]
        if not re.fullmatch(r"[a-z0-9_]+", item):
            raise ValueError("Use an item id like stone or oak_planks.")
        try:
            amount = int(data.get("amount", 1))
        except (TypeError, ValueError) as exc:
            raise ValueError("Amount must be a number.") from exc
        if amount < 1 or amount > 64:
            raise ValueError("Amount must be 1 to 64.")
        return f"give {name} minecraft:{item} {amount}"
    raise ValueError("Unknown admin action.")


def list_plugins() -> list[dict]:
    PLUGINS_DIR.mkdir(parents=True, exist_ok=True)
    plugins = []
    for path in sorted(PLUGINS_DIR.iterdir(), key=lambda item: item.name.lower()):
        name = path.name
        if name.endswith(".jar"):
            plugins.append({"name": name, "enabled": True, "size": path.stat().st_size})
        elif name.endswith(".jar.disabled"):
            plugins.append({
                "name": name[: -len(".disabled")],
                "enabled": False,
                "size": path.stat().st_size,
            })
    return plugins


def plugin_path(name: str, enabled: bool) -> Path:
    if "/" in name or "\\" in name or name.startswith("."):
        raise ValueError("Invalid plugin name")
    if not name.endswith(".jar"):
        raise ValueError("Plugin must be a .jar")
    filename = name if enabled else name + ".disabled"
    return safe_server_path("plugins/" + filename)


def install_modrinth(project_id: str, version_id: str | None) -> dict:
    if not re.fullmatch(r"[A-Za-z0-9]+", project_id):
        raise ValueError("Invalid project id")
    if version_id:
        if not re.fullmatch(r"[A-Za-z0-9]+", version_id):
            raise ValueError("Invalid version id")
        version = modrinth_get("/version/" + version_id)
        versions = [version]
    else:
        versions = None
        for game_version in ("26.2", "26.1", "1.21.11", None):
            params = {"loaders": json.dumps(["paper", "spigot", "bukkit"])}
            if game_version:
                params["game_versions"] = json.dumps([game_version])
            found = modrinth_get(f"/project/{project_id}/version?{urllib.parse.urlencode(params)}")
            if isinstance(found, list) and found:
                versions = found
                break
    if not isinstance(versions, list) or not versions:
        raise ValueError("No Paper, Spigot, or Bukkit download was found for that plugin.")
    chosen = versions[0]
    files = chosen.get("files") or []
    primary = next((item for item in files if item.get("primary")), files[0] if files else None)
    if not primary:
        raise ValueError("That version has no downloadable file.")
    url = primary.get("url", "")
    filename = Path(primary.get("filename", "plugin.jar")).name
    if not filename.endswith(".jar"):
        raise ValueError("Refusing a non-jar download.")
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or not parsed.netloc.endswith("modrinth.com"):
        raise ValueError("Refusing a download outside Modrinth.")
    destination = plugin_path(filename, True)
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": MODRINTH_UA})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    if len(data) > 80 * 1024 * 1024:
        raise ValueError("Plugin file is too large.")
    destination.write_bytes(data)
    disabled = destination.with_name(destination.name + ".disabled")
    if disabled.exists():
        disabled.unlink()
    return {"name": destination.name, "version": chosen.get("version_number", "")}


def load_eaglerx_addons() -> dict:
    if not EAGLERX_ADDONS_PATH.is_file():
        return {"release": "", "sourceUrl": "", "note": "", "addons": []}
    data = json.loads(EAGLERX_ADDONS_PATH.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("Invalid eaglerx-addons.json")
    addons = data.get("addons") or []
    if not isinstance(addons, list):
        raise ValueError("Invalid eaglerx-addons.json addons list")
    return data


def install_eaglerx_addon(addon_id: str) -> dict:
    if not re.fullmatch(r"[a-z0-9-]+", addon_id):
        raise ValueError("Invalid add-on id")
    catalog = load_eaglerx_addons()
    entry = next((item for item in catalog.get("addons", []) if item.get("id") == addon_id), None)
    if not entry:
        raise ValueError("Unknown Eagler add-on")
    filename = str(entry.get("fileName", ""))
    url = str(entry.get("url", ""))
    if not filename.endswith(".jar"):
        raise ValueError("Invalid add-on file name")
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or parsed.netloc != "github.com":
        raise ValueError("Refusing add-on download outside GitHub releases")
    if not parsed.path.startswith("/lax1dude/eaglerxserver/releases/download/"):
        raise ValueError("Refusing non-EaglerXServer release URL")
    destination = plugin_path(filename, True)
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": MODRINTH_UA})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    if len(data) > 80 * 1024 * 1024:
        raise ValueError("Add-on file is too large.")
    destination.write_bytes(data)
    disabled = destination.with_name(destination.name + ".disabled")
    if disabled.exists():
        disabled.unlink()
    return {"name": destination.name, "title": entry.get("title", filename)}


def create_backup() -> str:
    world = SERVER_DIR / "world"
    if not world.is_dir():
        raise FileNotFoundError("No world folder yet. Start the server once first.")
    BACKUPS_DIR.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    archive = BACKUPS_DIR / f"world-{stamp}.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
        for path in world.rglob("*"):
            if path.is_file():
                bundle.write(path, path.relative_to(SERVER_DIR).as_posix())
    return archive.name


class Handler(BaseHTTPRequestHandler):
    server_version = "KyleTurskiDashboard/1.0"

    def log_message(self, fmt: str, *args) -> None:
        return

    def _send(self, code: int, payload: object, content_type: str = "application/json") -> None:
        if content_type == "application/json":
            body = json.dumps(payload).encode("utf-8")
        elif isinstance(payload, bytes):
            body = payload
        else:
            body = str(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _error(self, code: int, message: str) -> None:
        self._send(code, {"error": message})

    def _body(self) -> dict:
        length = int(self.headers.get("Content-Length", "0") or 0)
        if length <= 0:
            return {}
        if length > 2_000_000:
            raise ValueError("Request is too large.")
        raw = self.rfile.read(length)
        if not raw:
            return {}
        data = json.loads(raw.decode("utf-8"))
        if not isinstance(data, dict):
            raise ValueError("Expected a JSON object.")
        return data

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)
        try:
            if path in ("/", "/index.html"):
                self._file(STATIC_DIR / "index.html", "text/html; charset=utf-8")
                return
            if path.startswith("/static/"):
                self._static(path[len("/static/"):])
                return
            if path == "/api/status":
                self._send(200, runtime.status())
                return
            if path == "/api/console":
                after = int(query.get("after", ["0"])[0])
                self._send(200, {"lines": runtime.snapshot(after)})
                return
            if path == "/api/plugins":
                self._send(200, {"plugins": list_plugins(), "version": "26.2"})
                return
            if path == "/api/modrinth/search":
                self._modrinth_search(query)
                return
            if path == "/api/eaglerx/addons":
                self._send(200, load_eaglerx_addons())
                return
            if path == "/api/files":
                self._list_files(query.get("path", [""])[0])
                return
            if path == "/api/files/read":
                self._read_file(query.get("path", [""])[0])
                return
            if path == "/api/properties":
                self._send(200, {"properties": read_properties()})
                return
            if path == "/api/network":
                self._network()
                return
            if path == "/api/public":
                self._send(200, public_tunnel.status())
                return
            if path == "/api/settings":
                self._send(200, load_settings())
                return
            if path == "/api/backups":
                self._list_backups()
                return
            if path == "/api/backups/download":
                self._download_backup(query.get("name", [""])[0])
                return
            if path == "/api/team":
                self._team()
                return
            if path == "/api/performance":
                perf = load_performance_module()
                self._send(200, perf.performance_status(SERVER_DIR))
                return
            self._error(404, "Not found")
        except Exception as exc:  # noqa: BLE001 - surface a safe message to the local UI
            self._error(400, str(exc))

    def do_POST(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        try:
            if parsed.path == "/api/files/upload":
                self._upload(urllib.parse.parse_qs(parsed.query))
                return
            data = self._body()
            path = parsed.path
            if path == "/api/power":
                self._power(str(data.get("action", "")))
                return
            if path == "/api/public":
                self._public(str(data.get("action", "")))
                return
            if path == "/api/admin":
                self._admin(data)
                return
            if path == "/api/command":
                error = runtime.send(str(data.get("command", "")))
                if error:
                    self._error(400, error)
                else:
                    self._send(200, {"ok": True})
                return
            if path == "/api/plugins/toggle":
                self._toggle_plugin(str(data.get("name", "")))
                return
            if path == "/api/plugins/delete":
                self._delete_plugin(str(data.get("name", "")))
                return
            if path == "/api/modrinth/install":
                version = data.get("versionId")
                result = install_modrinth(str(data.get("projectId", "")), str(version) if version else None)
                self._send(200, result)
                return
            if path == "/api/eaglerx/install":
                result = install_eaglerx_addon(str(data.get("id", "")))
                self._send(200, result)
                return
            if path == "/api/files/write":
                self._write_file(str(data.get("path", "")), str(data.get("content", "")))
                return
            if path == "/api/files/mkdir":
                target = safe_server_path(str(data.get("path", "")))
                target.mkdir(parents=True, exist_ok=True)
                self._send(200, {"ok": True})
                return
            if path == "/api/files/delete":
                self._delete_path(str(data.get("path", "")))
                return
            if path == "/api/properties":
                updates = data.get("properties", {})
                if not isinstance(updates, dict):
                    raise ValueError("properties must be an object")
                clean = {str(key): str(value) for key, value in updates.items() if re.fullmatch(r"[a-z0-9.-]+", str(key))}
                write_properties(clean)
                self._send(200, {"ok": True})
                return
            if path == "/api/settings":
                settings = load_settings()
                for key in ("minMemory", "maxMemory"):
                    if key in data:
                        value = str(data[key]).strip().upper()
                        if not re.fullmatch(r"\d+[GMK]", value):
                            raise ValueError("Memory must look like 2G or 512M.")
                        settings[key] = value
                save_settings(settings)
                self._send(200, settings)
                return
            if path == "/api/backups":
                name = create_backup()
                self._send(200, {"name": name})
                return
            if path == "/api/performance/apply":
                perf = load_performance_module()
                result = perf.apply_eagler_preset(SERVER_DIR)
                self._send(200, result)
                return
            if path == "/api/performance/chunky-install":
                perf = load_performance_module()
                result = perf.modrinth_install_chunky(SERVER_DIR)
                self._send(200, result)
                return
            if path == "/api/performance/chunky-pregen":
                perf = load_performance_module()
                preset = perf.load_preset()
                commands = preset.get("chunky", {}).get("pregen_commands", [])
                if not runtime.running():
                    self._error(400, "Start the server before running Chunky pregen.")
                    return
                for command in commands:
                    error = runtime.send(str(command))
                    if error:
                        self._error(400, error)
                        return
                self._send(200, {"ok": True, "commands": commands})
                return
            self._error(404, "Not found")
        except Exception as exc:  # noqa: BLE001
            self._error(400, str(exc))

    def _power(self, action: str) -> None:
        if action == "kill-all":
            killed = kill_related_server_processes(runtime)
            payload = runtime.status()
            payload["killed"] = killed
            self._send(200, payload)
            return
        if action == "start":
            error = runtime.start()
        elif action == "stop":
            error = runtime.stop()
        elif action == "restart":
            error = runtime.restart()
        else:
            self._error(400, "Unknown action")
            return
        if error:
            self._error(400, error)
        else:
            self._send(200, runtime.status())

    def _file(self, path: Path, content_type: str) -> None:
        if not path.is_file():
            self._error(404, "Missing file")
            return
        self._send(200, path.read_bytes(), content_type)

    def _static(self, relative: str) -> None:
        target = (STATIC_DIR / relative).resolve()
        if STATIC_DIR.resolve() not in target.parents and target != STATIC_DIR.resolve():
            self._error(403, "Forbidden")
            return
        if not target.is_file():
            self._error(404, "Not found")
            return
        kind = "text/plain; charset=utf-8"
        if target.suffix == ".css":
            kind = "text/css; charset=utf-8"
        elif target.suffix == ".js":
            kind = "text/javascript; charset=utf-8"
        self._file(target, kind)

    def _modrinth_search(self, query: dict) -> None:
        text = query.get("q", [""])[0]
        offset = query.get("offset", ["0"])[0]
        category = query.get("category", [""])[0]
        allowed = {
            "optimization", "economy", "utility", "management", "worldgen",
            "adventure", "mobs", "social", "storage", "equipment",
        }
        facet_groups = [
            ["project_type:plugin"],
            ["versions:26.2", "versions:26.1", "versions:1.21.11"],
            ["categories:paper", "categories:spigot", "categories:bukkit"],
        ]
        if category in allowed:
            facet_groups.append([f"categories:{category}"])
        facets = json.dumps(facet_groups)
        params = urllib.parse.urlencode({
            "query": text,
            "limit": "18",
            "offset": offset,
            "index": "relevance",
            "facets": facets,
        })
        try:
            result = modrinth_get("/search?" + params)
        except urllib.error.URLError as exc:
            raise ValueError(f"Modrinth search failed: {exc}") from exc
        hits = []
        for hit in result.get("hits", []) if isinstance(result, dict) else []:
            hits.append({
                "id": hit.get("project_id"),
                "title": hit.get("title"),
                "description": hit.get("description"),
                "author": hit.get("author"),
                "icon": hit.get("icon_url"),
                "downloads": hit.get("downloads", 0),
                "updated": hit.get("date_modified", ""),
            })
        self._send(200, {"hits": hits, "total": result.get("total_hits", 0) if isinstance(result, dict) else 0})

    def _list_files(self, relative: str) -> None:
        target = safe_server_path(relative)
        if not target.exists():
            raise ValueError("Folder not found")
        if not target.is_dir():
            raise ValueError("Not a folder")
        entries = []
        for child in sorted(target.iterdir(), key=lambda item: (not item.is_dir(), item.name.lower())):
            size = child.stat().st_size if child.is_file() else 0
            entries.append({"name": child.name, "dir": child.is_dir(), "size": size})
        shown = "" if target == SERVER_DIR.resolve() else target.relative_to(SERVER_DIR.resolve()).as_posix()
        self._send(200, {"path": shown, "entries": entries})

    def _read_file(self, relative: str) -> None:
        target = safe_server_path(relative)
        if not target.is_file():
            raise ValueError("File not found")
        if target.stat().st_size > 256_000:
            raise ValueError("File is too large to edit here.")
        text = target.read_text(encoding="utf-8", errors="replace")
        self._send(200, {"path": relative, "content": text})

    def _write_file(self, relative: str, content: str) -> None:
        target = safe_server_path(relative)
        if target.is_dir():
            raise ValueError("Cannot write a folder")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        self._send(200, {"ok": True})

    def _delete_path(self, relative: str) -> None:
        target = safe_server_path(relative)
        if target == SERVER_DIR.resolve():
            raise ValueError("Refusing to delete the server folder")
        if target.is_dir():
            shutil.rmtree(target)
        elif target.exists():
            target.unlink()
        self._send(200, {"ok": True})

    def _upload(self, query: dict) -> None:
        folder = query.get("dir", [""])[0]
        filename = Path(self.headers.get("X-Filename", "")).name
        if not filename or filename in {".", ".."}:
            raise ValueError("Missing file name")
        length = int(self.headers.get("Content-Length", "0") or 0)
        if length <= 0 or length > 80 * 1024 * 1024:
            raise ValueError("Upload is empty or too large.")
        data = self.rfile.read(length)
        target = safe_server_path(str(Path(folder) / filename))
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        self._send(200, {"ok": True, "name": filename})

    def _toggle_plugin(self, name: str) -> None:
        enabled = plugin_path(name, True)
        disabled = plugin_path(name, False)
        if enabled.exists():
            enabled.rename(disabled)
            now = False
        elif disabled.exists():
            disabled.rename(enabled)
            now = True
        else:
            raise ValueError("Plugin not found")
        self._send(200, {"name": name, "enabled": now})

    def _delete_plugin(self, name: str) -> None:
        removed = False
        for enabled in (True, False):
            path = plugin_path(name, enabled)
            if path.exists():
                path.unlink()
                removed = True
        if not removed:
            raise ValueError("Plugin not found")
        self._send(200, {"ok": True})

    def _network(self) -> None:
        manifest = load_manifest()
        public = manifest.get("publicJoin", {})
        props = read_properties()
        addresses = []
        try:
            hostname = socket.gethostname()
            for info in socket.getaddrinfo(hostname, None):
                ip = info[4][0]
                if ":" in ip or ip.startswith("127."):
                    continue
                if ip not in addresses:
                    addresses.append(ip)
        except OSError:
            pass
        tips = [
            "Keep the Eagler render distance at or below the server view distance.",
            "Turn particles down in the browser client if the game stutters.",
        ]
        try:
            loaded = load_performance_module().load_preset()
            tips = loaded.get("client_tips") or loaded.get("clientTips") or tips
        except Exception:
            pass
        self._send(200, {
            "port": int(props.get("server-port", "25565") or 25565),
            "localJava": f"127.0.0.1:{props.get('server-port', '25565')}",
            "localEagler": f"ws://127.0.0.1:{props.get('server-port', '25565')}/",
            "publicEagler": public_tunnel.eagler_url() or public.get("url", ""),
            "tunnel": public_tunnel.status(),
            "lan": addresses,
            "clientTips": tips,
        })

    def _admin(self, data: dict) -> None:
        if not runtime.running():
            self._error(400, "Start the server first.")
            return
        action = str(data.get("action", ""))
        commands: list[str] = []
        if action == "builder":
            commands.append(admin_console_command(data, "op"))
            commands.append(admin_console_command(data, "creative"))
        elif action == "heal":
            commands.append(admin_console_command(data, "heal"))
            commands.append(admin_console_command(data, "feed"))
        else:
            commands.append(admin_console_command(data, action))
        for command in commands:
            error = runtime.send(command)
            if error:
                self._error(400, error)
                return
        self._send(200, {"ok": True, "commands": commands})

    def _public(self, action: str) -> None:
        if action == "stop":
            public_tunnel.stop()
            self._send(200, public_tunnel.status())
            return
        if action != "start":
            self._error(400, "Unknown action")
            return
        props = read_properties()
        try:
            port = int(props.get("server-port", "25565") or 25565)
        except ValueError:
            port = 25565
        error = public_tunnel.start(port)
        if error:
            self._error(400, error)
            return
        self._send(200, public_tunnel.status())

    def _list_backups(self) -> None:
        BACKUPS_DIR.mkdir(parents=True, exist_ok=True)
        items = []
        for path in sorted(BACKUPS_DIR.glob("*.zip"), reverse=True):
            items.append({"name": path.name, "size": path.stat().st_size})
        self._send(200, {"backups": items})

    def _download_backup(self, name: str) -> None:
        if not re.fullmatch(r"world-\d{8}-\d{6}\.zip", name):
            raise ValueError("Unknown backup")
        path = BACKUPS_DIR / name
        if not path.is_file():
            raise ValueError("Backup not found")
        self._send(200, path.read_bytes(), "application/zip")

    def _team(self) -> None:
        def read_json(name: str) -> list:
            path = SERVER_DIR / name
            if not path.is_file():
                return []
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                return []
            return data if isinstance(data, list) else []

        self._send(200, {"ops": read_json("ops.json"), "whitelist": read_json("whitelist.json")})


def open_dashboard(url: str) -> None:
    print(f"Opening dashboard in your browser: {url}")
    if os.name == "nt":
        try:
            os.startfile(url)
            return
        except OSError as exc:
            print(f"Could not open the browser: {exc}")
    try:
        webbrowser.open(url)
    except OSError as exc:
        print(f"Could not open the browser: {exc}")
        print(f"Open this address yourself: {url}")


def serve(port: int, open_browser: bool, autostart: bool) -> None:
    global runtime
    runtime = MinecraftRuntime()
    httpd = ThreadingHTTPServer((HOST, port), Handler)
    url = f"http://{HOST}:{port}"
    print(f"KyleTurski MC dashboard: {url}")
    print("Leave this window open. Closing it stops the dashboard.")
    if autostart:
        error = runtime.start()
        if error:
            print(error)
    if open_browser:
        open_dashboard(url)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping dashboard.")
        if runtime.running():
            runtime.stop()
        httpd.shutdown()


def main() -> int:
    open_browser = "--open" in sys.argv
    autostart = "--autostart" in sys.argv
    port = DEFAULT_PORT
    for _ in range(5):
        try:
            serve(port, open_browser, autostart)
            return 0
        except OSError as exc:
            if getattr(exc, "errno", None) not in {98, 48, 10048}:
                raise
            if open_browser:
                open_dashboard(f"http://{HOST}:{port}")
                print(f"Dashboard already running at http://{HOST}:{port}")
                print("Close the other dashboard command window, or use that tab in your browser.")
                try:
                    input("Press Enter to close this launcher window...")
                except EOFError:
                    pass
                return 0
            port += 1
    print("Could not bind a dashboard port.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
