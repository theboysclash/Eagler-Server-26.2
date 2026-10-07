const state = {
  page: "dashboard",
  pluginTab: "browse",
  consoleAfter: 0,
  filePath: "",
  status: null,
};

const main = document.getElementById("main");
const toast = document.getElementById("toast");

function showToast(message) {
  toast.hidden = false;
  toast.textContent = message;
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => { toast.hidden = true; }, 3200);
}

async function api(path, options) {
  const response = await fetch(path, options);
  const type = response.headers.get("content-type") || "";
  const body = type.includes("application/json") ? await response.json() : await response.text();
  if (!response.ok) {
    throw new Error(body.error || "Request failed");
  }
  return body;
}

function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (ch) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[ch]));
}

function bytes(value) {
  if (!value) return "0.0 GB";
  const gb = value / (1024 ** 3);
  if (gb >= 1) return gb.toFixed(1) + " GB";
  return (value / (1024 ** 2)).toFixed(0) + " MB";
}

function setPill(status) {
  const pill = document.getElementById("status-pill");
  const name = status.state || "stopped";
  pill.className = "pill " + name;
  pill.textContent = name.charAt(0).toUpperCase() + name.slice(1);
  document.getElementById("btn-start").disabled = name === "running" || name === "starting";
  document.getElementById("btn-stop").disabled = name === "stopped";
}

async function refreshStatus() {
  state.status = await api("/api/status");
  setPill(state.status);
  if (state.page === "dashboard") updateDashboardBits();
}

function updateDashboardBits() {
  const status = state.status;
  if (!status || !document.getElementById("stat-players")) return;
  document.getElementById("stat-players").textContent = `${status.players.length}/${status.maxPlayers || 0}`;
  document.getElementById("stat-cpu").textContent = `${Number(status.cpu || 0).toFixed(1)}%`;
  document.getElementById("stat-mem").textContent = bytes(status.memoryBytes);
  document.getElementById("stat-mem-sub").textContent = "of " + bytes(status.memoryMaxBytes);
  document.getElementById("stat-disk").textContent = bytes(status.storageBytes);
  document.getElementById("stat-disk-sub").textContent = bytes(status.diskFreeBytes) + " free on disk";
  document.getElementById("player-count").textContent = `${status.players.length}/${status.maxPlayers || 0}`;
  document.getElementById("console-badge").textContent = status.state === "running" ? "Connected" : "Idle";
  document.getElementById("dash-banner").hidden = status.state === "running";
  const players = document.getElementById("players");
  players.innerHTML = status.players.length
    ? status.players.map((name) => `<div class="player">${esc(name)}</div>`).join("")
    : '<div class="empty">No players online</div>';
}

document.getElementById("btn-start").onclick = () => power("start");
document.getElementById("btn-stop").onclick = () => power("stop");
document.getElementById("btn-restart").onclick = () => power("restart");
document.getElementById("btn-kill-all").onclick = () => {
  if (!confirm("Force-stop Minecraft, Caddy (wss), and any stuck KyleTurski server processes?\n\nThe dashboard stays open.")) {
    return;
  }
  power("kill-all");
};

async function power(action) {
  try {
    state.status = await api("/api/power", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action }),
    });
    setPill(state.status);
    if (action === "kill-all") {
      const list = (state.status.killed || []).join(", ");
      showToast(list ? "Killed: " + list : "No extra processes found (server stopped).");
    } else {
      showToast(action === "start" ? "Server starting" : "Sent " + action);
    }
    if (state.page === "dashboard" || state.page === "console") render();
  } catch (error) {
    showToast(error.message);
  }
}

document.querySelectorAll(".nav").forEach((button) => {
  button.onclick = () => {
    document.querySelectorAll(".nav").forEach((item) => item.classList.remove("active"));
    button.classList.add("active");
    state.page = button.dataset.page;
    location.hash = state.page;
    render();
  };
});

function render() {
  const pages = {
    dashboard: paintDashboard,
    console: paintConsole,
    settings: paintSettings,
    performance: paintPerformance,
    files: paintFiles,
    plugins: paintPlugins,
    network: paintNetwork,
    properties: paintProperties,
    backups: paintBackups,
    team: paintTeam,
    admin: paintAdmin,
  };
  (pages[state.page] || paintDashboard)();
}

function paintDashboard() {
  const status = state.status || { players: [], cpu: 0, memoryBytes: 0, memoryMaxBytes: 0, storageBytes: 0, diskFreeBytes: 0 };
  const warnings = [];
  if (!status.javaReady) warnings.push(status.javaHint || "Java 25+ required to start the server.");
  if (!status.hubEconomyReady) warnings.push("HubEconomy.jar missing — run launch\\build-plugin.bat then launch\\setup.bat. /sell and /shop will not work.");
  main.innerHTML = `
    ${warnings.length ? `<div class="banner">${warnings.map(esc).join(" ")}</div>` : ""}
    <div id="dash-banner" class="banner" ${status.state === "running" ? "hidden" : ""}>Server is stopped. Press Start to boot Paper 26.2 on this PC.</div>
    <div class="stats">
      <div class="card"><div class="label">Players</div><div class="value" id="stat-players"></div></div>
      <div class="card"><div class="label">CPU Usage</div><div class="value" id="stat-cpu"></div></div>
      <div class="card"><div class="label">Memory</div><div class="value" id="stat-mem"></div><div class="sub" id="stat-mem-sub"></div></div>
      <div class="card"><div class="label">Storage</div><div class="value" id="stat-disk"></div><div class="sub" id="stat-disk-sub"></div></div>
    </div>
    <div class="split">
      <section class="card">
        <div class="row"><strong>Server Console</strong><span class="badge" id="console-badge"></span></div>
        <div id="dash-console" class="console"></div>
        <form id="dash-command" class="command">
          <input id="dash-command-input" placeholder="Type a command" autocomplete="off">
          <button class="btn">Send</button>
        </form>
      </section>
      <section class="card">
        <div class="row"><strong>Players</strong><span id="player-count"></span></div>
        <div id="players"></div>
      </section>
    </div>`;
  updateDashboardBits();
  state.consoleAfter = 0;
  loadConsole(document.getElementById("dash-console"), true);
  document.getElementById("dash-command").onsubmit = (event) => {
    event.preventDefault();
    sendCommand(document.getElementById("dash-command-input"));
  };
}

async function loadConsole(target, reset) {
  if (!target) return;
  if (reset) state.consoleAfter = 0;
  const data = await api("/api/console?after=" + (reset ? 0 : state.consoleAfter));
  if (!data.lines.length) return;
  state.consoleAfter = data.lines[data.lines.length - 1].id;
  target.textContent += data.lines.map((line) => line.text).join("\n") + "\n";
  target.scrollTop = target.scrollHeight;
}

async function sendCommand(input) {
  const command = input.value.trim();
  if (!command) return;
  try {
    await api("/api/command", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ command }),
    });
    input.value = "";
  } catch (error) {
    showToast(error.message);
  }
}

function paintConsole() {
  main.innerHTML = `
    <h1>Console</h1>
    <section class="card">
      <div id="full-console" class="console" style="max-height:60vh"></div>
      <form id="full-command" class="command">
        <input id="full-command-input" placeholder="Type a command" autocomplete="off">
        <button class="btn primary">Send</button>
      </form>
    </section>`;
  const box = document.getElementById("full-console");
  state.consoleAfter = 0;
  loadConsole(box, true);
  document.getElementById("full-command").onsubmit = (event) => {
    event.preventDefault();
    sendCommand(document.getElementById("full-command-input"));
  };
}

async function paintSettings() {
  const settings = await api("/api/settings");
  main.innerHTML = `
    <h1>Settings</h1>
    <section class="card">
      <p class="desc">Memory applies the next time the server starts. Java 25 or newer is required.</p>
      <form id="settings-form" class="toolbar">
        <label>Min <input name="minMemory" value="${settings.minMemory}"></label>
        <label>Max <input name="maxMemory" value="${settings.maxMemory}"></label>
        <button class="btn primary">Save</button>
      </form>
    </section>`;
  document.getElementById("settings-form").onsubmit = async (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    try {
      await api("/api/settings", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ minMemory: form.get("minMemory"), maxMemory: form.get("maxMemory") }),
      });
      showToast("Saved. Restart the server to apply memory.");
    } catch (error) {
      showToast(error.message);
    }
  };
}

async function paintFiles() {
  const data = await api("/api/files?path=" + encodeURIComponent(state.filePath));
  main.innerHTML = `
    <h1>File Manager</h1>
    <div class="toolbar">
      <button class="btn" id="file-up">Up</button>
      <span class="meta">/${data.path}</span>
      <input id="file-upload" type="file">
    </div>
    <section class="card">
      <table>
        <thead><tr><th>Name</th><th>Size</th><th></th></tr></thead>
        <tbody>
          ${data.entries.map((entry) => `
            <tr>
              <td>${entry.dir ? `<button class="nav" data-dir="${esc(entry.name)}">${esc(entry.name)}/</button>` : esc(entry.name)}</td>
              <td>${entry.dir ? "" : bytes(entry.size)}</td>
              <td>${entry.dir ? "" : `<button class="btn" data-edit="${esc(entry.name)}">Edit</button>`}
                  <button class="btn" data-del="${esc(entry.name)}">Delete</button></td>
            </tr>`).join("")}
        </tbody>
      </table>
      <div id="editor"></div>
    </section>`;
  document.getElementById("file-up").onclick = () => {
    state.filePath = state.filePath.split("/").slice(0, -1).join("/");
    paintFiles();
  };
  main.querySelectorAll("[data-dir]").forEach((button) => {
    button.onclick = () => {
      state.filePath = [state.filePath, button.dataset.dir].filter(Boolean).join("/");
      paintFiles();
    };
  });
  main.querySelectorAll("[data-edit]").forEach((button) => {
    button.onclick = () => editFile([state.filePath, button.dataset.edit].filter(Boolean).join("/"));
  });
  main.querySelectorAll("[data-del]").forEach((button) => {
    button.onclick = async () => {
      const rel = [state.filePath, button.dataset.del].filter(Boolean).join("/");
      if (!confirm("Delete " + rel + "?")) return;
      await api("/api/files/delete", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ path: rel }) });
      paintFiles();
    };
  });
  document.getElementById("file-upload").onchange = async (event) => {
    const file = event.target.files[0];
    if (!file) return;
    await fetch("/api/files/upload?dir=" + encodeURIComponent(state.filePath), {
      method: "POST",
      headers: { "X-Filename": file.name },
      body: file,
    });
    showToast("Uploaded " + file.name);
    paintFiles();
  };
}

async function editFile(path) {
  const data = await api("/api/files/read?path=" + encodeURIComponent(path));
  const editor = document.getElementById("editor");
  editor.innerHTML = `<h2>${path}</h2><textarea id="file-text"></textarea><button class="btn primary" id="file-save">Save</button>`;
  document.getElementById("file-text").value = data.content;
  document.getElementById("file-save").onclick = async () => {
    await api("/api/files/write", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ path, content: document.getElementById("file-text").value }),
    });
    showToast("Saved " + path);
  };
}

async function paintPlugins() {
  main.innerHTML = `
    <div class="row"><h1>Plugin Manager</h1><span class="meta">Your server version: MC 26.2</span></div>
    <div class="tabs">
      <button class="tab ${state.pluginTab === "browse" ? "active" : ""}" data-tab="browse">Modrinth</button>
      <button class="tab ${state.pluginTab === "eagler" ? "active" : ""}" data-tab="eagler">Eagler add-ons</button>
      <button class="tab ${state.pluginTab === "installed" ? "active" : ""}" id="installed-tab" data-tab="installed">Installed</button>
    </div>
    <div class="toolbar" id="plugin-toolbar">
      <input id="plugin-search" placeholder="Search plugins on Modrinth" style="flex:1">
      <button class="btn" id="plugin-go">Search</button>
    </div>
    <div id="plugin-body" class="grid"></div>`;
  document.querySelectorAll(".tab").forEach((tab) => {
    tab.onclick = () => {
      state.pluginTab = tab.dataset.tab;
      paintPlugins();
    };
  });
  const toolbar = document.getElementById("plugin-toolbar");
  if (state.pluginTab === "eagler") {
    toolbar.hidden = true;
    loadEaglerAddons();
  } else if (state.pluginTab === "installed") {
    toolbar.hidden = true;
    loadInstalled();
  } else {
    toolbar.hidden = false;
    document.getElementById("plugin-go").onclick = () => loadModrinth(document.getElementById("plugin-search").value);
    document.getElementById("plugin-search").onkeydown = (event) => {
      if (event.key === "Enter") loadModrinth(event.target.value);
    };
    loadModrinth("");
  }
}

async function loadEaglerAddons() {
  const body = document.getElementById("plugin-body");
  body.className = "grid";
  body.innerHTML = '<div class="empty">Loading Eagler add-ons…</div>';
  try {
    const data = await api("/api/eaglerx/addons");
    const note = data.note
      ? `<p class="meta">${esc(data.note)} <a href="${esc(data.sourceUrl || "https://github.com/lax1dude/eaglerxserver/releases")}" target="_blank" rel="noopener">lax1dude releases</a> (${esc(data.release || "")}).</p>`
      : "";
    body.innerHTML = note + (data.addons || []).map((addon) => `
      <article class="card plugin-card">
        <div><strong>${esc(addon.title || addon.fileName)}</strong></div>
        <div class="desc">${esc(addon.description || "")}</div>
        <button class="btn primary" data-eagler="${esc(addon.id)}">Install</button>
      </article>`).join("");
    body.querySelectorAll("[data-eagler]").forEach((button) => {
      button.onclick = async () => {
        button.disabled = true;
        try {
          const result = await api("/api/eaglerx/install", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id: button.dataset.eagler }),
          });
          showToast("Installed " + (result.name || "add-on") + ". Restart the server.");
        } catch (error) {
          showToast(error.message);
          button.disabled = false;
        }
      };
    });
  } catch (error) {
    body.innerHTML = '<div class="empty">' + esc(error.message) + "</div>";
  }
}

async function loadInstalled() {
  const data = await api("/api/plugins");
  document.getElementById("installed-tab").textContent = "Installed (" + data.plugins.length + ")";
  const body = document.getElementById("plugin-body");
  body.className = "";
  if (!data.plugins.length) {
    body.innerHTML = '<div class="empty">No plugins in the plugins folder.</div>';
    return;
  }
  body.innerHTML = `<table><thead><tr><th>Plugin</th><th>State</th><th></th></tr></thead><tbody>
    ${data.plugins.map((plugin) => `<tr>
      <td>${esc(plugin.name)}</td>
      <td>${plugin.enabled ? "Enabled" : "Disabled"}</td>
      <td>
        <button class="btn" data-toggle="${esc(plugin.name)}">${plugin.enabled ? "Disable" : "Enable"}</button>
        <button class="btn" data-remove="${esc(plugin.name)}">Delete</button>
      </td>
    </tr>`).join("")}
  </tbody></table><p class="meta">Restart the server after changing plugins.</p>`;
  body.querySelectorAll("[data-toggle]").forEach((button) => {
    button.onclick = async () => {
      await api("/api/plugins/toggle", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name: button.dataset.toggle }) });
      showToast("Updated. Restart to apply.");
      loadInstalled();
    };
  });
  body.querySelectorAll("[data-remove]").forEach((button) => {
    button.onclick = async () => {
      if (!confirm("Delete " + button.dataset.remove + "?")) return;
      await api("/api/plugins/delete", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name: button.dataset.remove }) });
      loadInstalled();
    };
  });
}

async function loadModrinth(query) {
  const body = document.getElementById("plugin-body");
  body.className = "grid";
  body.innerHTML = '<div class="empty">Searching Modrinth…</div>';
  try {
    const data = await api("/api/modrinth/search?q=" + encodeURIComponent(query));
    if (!data.hits.length) {
      body.innerHTML = '<div class="empty">No Paper plugins for 26.2 matched that search.</div>';
      return;
    }
    body.innerHTML = data.hits.map((hit) => `
      <article class="card plugin-card">
        <div class="plugin-top">
          ${hit.icon ? `<img alt="" src="${hit.icon}">` : ""}
          <div><strong>${esc(hit.title)}</strong><div class="meta">${esc(hit.author || "")}</div></div>
        </div>
        <div class="desc">${esc(hit.description || "")}</div>
        <div class="meta">${Number(hit.downloads || 0).toLocaleString()} downloads</div>
        <button class="btn primary" data-install="${hit.id}">Install</button>
      </article>`).join("");
    body.querySelectorAll("[data-install]").forEach((button) => {
      button.onclick = async () => {
        button.disabled = true;
        try {
          const result = await api("/api/modrinth/install", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ projectId: button.dataset.install }),
          });
          showToast("Installed " + result.name + ". Restart the server.");
        } catch (error) {
          showToast(error.message);
          button.disabled = false;
        }
      };
    });
  } catch (error) {
    body.innerHTML = `<div class="empty">${error.message}</div>`;
  }
}

async function paintPerformance() {
  const data = await api("/api/performance");
  const targets = data.targets || {};
  main.innerHTML = `
    <h1>Performance &amp; FPS</h1>
    <p class="desc">Server plugins help TPS and how much the browser has to draw. They do not raise the Eagler FPS cap by themselves.</p>
    <section class="card">
      <div class="row"><strong>Eagler preset</strong><span>${data.matchesPreset ? "Applied" : "Not applied"}</span></div>
      <p class="meta">Targets: view ${targets["view-distance"]}, sim ${targets["simulation-distance"]}, entity range ${targets["entity-broadcast-range-percentage"]}%</p>
      <p class="meta">Current: view ${data.current["view-distance"] || "?"}, sim ${data.current["simulation-distance"] || "?"}, entity range ${data.current["entity-broadcast-range-percentage"] || "?"}%</p>
      <p class="meta">Paper config: ${data.paperConfigExists ? "found" : "start the server once, then apply again"}</p>
      <button class="btn primary" id="perf-apply">Apply Eagler preset</button>
      <p class="meta">Restart the server after applying.</p>
    </section>
    <section class="card" style="margin-top:12px">
      <div class="row"><strong>Chunky pregen</strong><span>${data.chunkyInstalled ? "Installed" : "Not installed"}</span></div>
      <p class="desc">Pre-generates chunks around survival so the first players do not hitch the server.</p>
      <div class="toolbar">
        <button class="btn" id="chunky-install">Install Chunky</button>
        <button class="btn" id="chunky-pregen">Run pregen commands</button>
      </div>
      <pre class="meta">${(data.chunkyCommands || []).join("\n")}</pre>
    </section>
    <section class="card" style="margin-top:12px">
      <strong>Spark (built into Paper)</strong>
      <p class="meta">Console: <code>${esc((data.sparkCommands || {}).check_tps || "spark tps")}</code> — if TPS is ~20, lag is client FPS. Use <code>${esc((data.sparkCommands || {}).profile || "spark profiler")}</code> before adding LagFixer.</p>
    </section>
    <section class="card" style="margin-top:12px">
      <strong>Eagler client tips</strong>
      <ul>${(data.clientTips || []).map((tip) => `<li>${esc(tip)}</li>`).join("")}</ul>
    </section>`;
  document.getElementById("perf-apply").onclick = async () => {
    try {
      const result = await api("/api/performance/apply", { method: "POST" });
      showToast("Preset applied. Restart the server.");
      if (result.warnings && result.warnings.length) showToast(result.warnings[0]);
      paintPerformance();
    } catch (error) {
      showToast(error.message);
    }
  };
  document.getElementById("chunky-install").onclick = async () => {
    try {
      const result = await api("/api/performance/chunky-install", { method: "POST" });
      showToast("Installed " + result.name + ". Restart, then run pregen.");
      paintPerformance();
    } catch (error) {
      showToast(error.message);
    }
  };
  document.getElementById("chunky-pregen").onclick = async () => {
    try {
      await api("/api/performance/chunky-pregen", { method: "POST" });
      showToast("Sent Chunky commands to the console.");
    } catch (error) {
      showToast(error.message);
    }
  };
}

async function paintNetwork() {
  const data = await api("/api/network");
  main.innerHTML = `
    <h1>Network</h1>
    <section class="card">
      <p>Java on this PC: <strong>${data.localJava}</strong></p>
      <p>Eaglercraft on this PC: <strong>${data.localEagler}</strong></p>
      <p>Free public Eagler address: <strong>${esc(data.tunnel && data.tunnel.eaglerUrl ? data.tunnel.eaglerUrl : "not started")}</strong></p>
      <p class="meta">Click the button, wait about 20 seconds, then paste that wss:// address into Eaglercraft Direct Connect. It changes every time you start it. Java players on this PC still use ${esc(data.localJava)}.</p>
      <div class="toolbar">
        <button class="btn primary" id="public-start">Start free public address</button>
        <button class="btn" id="public-stop">Stop public address</button>
      </div>
      <p class="meta">LAN addresses: ${data.lan.length ? data.lan.join(", ") : "none detected"}</p>
      ${data.clientTips && data.clientTips.length ? `<ul>${data.clientTips.map((tip) => `<li>${esc(tip)}</li>`).join("")}</ul>` : ""}
    </section>`;
  document.getElementById("public-start").onclick = async () => {
    showToast("Starting free public address…");
    try {
      const result = await api("/api/public", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: "start" }),
      });
      showToast(result.eaglerUrl || "Public address ready");
      paintNetwork();
    } catch (error) {
      showToast(error.message);
    }
  };
  document.getElementById("public-stop").onclick = async () => {
    await api("/api/public", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "stop" }),
    });
    showToast("Public address stopped");
    paintNetwork();
  };
}

async function paintProperties() {
  const data = await api("/api/properties");
  const keys = ["motd", "max-players", "server-port", "gamemode", "difficulty", "view-distance", "white-list", "online-mode", "pvp", "level-name"];
  main.innerHTML = `
    <h1>Properties</h1>
    <form id="props" class="card">
      ${keys.map((key) => `<label class="toolbar">${esc(key)}<input name="${esc(key)}" value="${esc(data.properties[key] || "")}"></label>`).join("")}
      <button class="btn primary">Save</button>
      <p class="meta">Restart the server after saving.</p>
    </form>`;
  document.getElementById("props").onsubmit = async (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    const properties = {};
    keys.forEach((key) => { properties[key] = form.get(key); });
    await api("/api/properties", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ properties }) });
    showToast("Properties saved");
  };
}

async function paintBackups() {
  const data = await api("/api/backups");
  main.innerHTML = `
    <h1>Backups</h1>
    <button class="btn primary" id="backup-now">Back up world</button>
    <section class="card" style="margin-top:12px">
      ${data.backups.length ? data.backups.map((item) => `<div class="row"><span>${item.name}</span><span>${bytes(item.size)} <a href="/api/backups/download?name=${encodeURIComponent(item.name)}">Download</a></span></div>`).join("") : '<div class="empty">No backups yet</div>'}
    </section>`;
  document.getElementById("backup-now").onclick = async () => {
    showToast("Creating backup…");
    try {
      const result = await api("/api/backups", { method: "POST" });
      showToast("Saved " + result.name);
      paintBackups();
    } catch (error) {
      showToast(error.message);
    }
  };
}

async function paintAdmin() {
  const status = state.status || await api("/api/status");
  const players = status.players || [];
  const options = players.map((name) => `<option value="${esc(name)}">${esc(name)}</option>`).join("");
  main.innerHTML = `
    <h1>Admin</h1>
    <section class="card">
      <p class="meta">Join the server first, then pick your name. Builder turns on creative mode and op so you can fly and use give. Hub blocks save on their own. Use Save world before you stop.</p>
      <label class="toolbar">Player
        <select id="admin-player">
          <option value="">Online players</option>
          ${options}
        </select>
        <input id="admin-name" placeholder="or type a name">
      </label>
      <div class="toolbar">
        <button class="btn primary" data-admin="builder">Make builder</button>
        <button class="btn" data-admin="survival">Survival mode</button>
        <button class="btn" data-admin="heal">Heal and feed</button>
        <button class="btn" data-admin="clear">Clear inventory</button>
        <button class="btn" data-admin="deop">Remove op</button>
        <button class="btn primary" data-admin="hubtool">Give hub tool</button>
      </div>
      <p class="meta">Hub tool: sneak and right-click to switch between hub spawn, survival spawn, and moving the Survival NPC. Right-click to apply it where you are standing. New NPCs can be added to that same tool later.</p>
      <label class="toolbar">Give item
        <input id="admin-item" value="stone" placeholder="oak_planks">
        <input id="admin-amount" value="64" style="width:70px">
        <button class="btn primary" data-admin="give">Give</button>
      </label>
      <button class="btn" data-admin="save">Save world</button>
    </section>`;
  main.querySelectorAll("[data-admin]").forEach((button) => {
    button.onclick = () => runAdmin(button.dataset.admin);
  });
}

function adminPlayerName() {
  const typed = document.getElementById("admin-name").value.trim();
  if (typed) return typed;
  return document.getElementById("admin-player").value;
}

async function runAdmin(action) {
  const body = {
    action,
    player: action === "save" ? "" : adminPlayerName(),
    item: document.getElementById("admin-item").value,
    amount: document.getElementById("admin-amount").value,
  };
  if (action !== "save" && !body.player) {
    showToast("Pick or type a player name");
    return;
  }
  try {
    await api("/api/admin", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    showToast("Sent " + action);
  } catch (error) {
    showToast(error.message);
  }
}

async function paintTeam() {
  const data = await api("/api/team");
  const names = (list) => list.length ? list.map((entry) => entry.name || entry.uuid || JSON.stringify(entry)).join(", ") : "none";
  main.innerHTML = `
    <h1>Team</h1>
    <section class="card">
      <p>Ops: ${names(data.ops)}</p>
      <p>Whitelist: ${names(data.whitelist)}</p>
      <p class="meta">Use the console commands op, deop, whitelist add, and whitelist remove. This PC dashboard is only for you.</p>
    </section>`;
}

setInterval(async () => {
  try {
    await refreshStatus();
    const box = document.getElementById("dash-console") || document.getElementById("full-console");
    if (box) await loadConsole(box, false);
  } catch (error) {
    /* dashboard may be restarting */
  }
}, 2000);

const hashPage = location.hash.replace("#", "");
if (hashPage) {
  state.page = hashPage;
  document.querySelectorAll(".nav").forEach((item) => {
    item.classList.toggle("active", item.dataset.page === hashPage);
  });
}
refreshStatus().then(render);
