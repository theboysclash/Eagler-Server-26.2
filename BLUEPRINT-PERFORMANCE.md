# Plan: performance and FPS for KyleTurski MC

This plan is for the Paper 26.2 server in this repo. Browser FPS and server lag are different problems. A plugin can make the server send less work to Eaglercraft. It cannot raise the frame cap inside the browser port.

## What actually moves FPS

Eaglercraft 26.2 draws the world in the browser. FPS drops when the client has too many chunks, entities, and particles to render. The server settings that help are the ones that send fewer of those:

- Lower view distance
- Lower simulation distance
- Shorter entity tracking range
- Fewer mobs and dropped items near players

Server TPS drops when chunks are generating, mob farms are huge, or hoppers and redstone tick constantly. That feels like lag even when the client FPS number is fine.

## Do this first

Paper 26.2 already bundles Spark. Use it before adding jars.

1. Start the server and join.
2. In the console run `/spark tps` while someone is in the survival world.
3. If TPS is near 20, the stutter is client FPS. Change view distance and entity range. Do not add a pile of "booster" plugins.
4. If TPS is under 19, profile with `/spark profiler` and only then add the plugin that matches the cause.

## Phases

### Phase 1 — Safe Paper preset

After the first boot, Paper writes `server-26.2/config/paper-world-defaults.yml` and `paper-global.yml`. Set a small "Eagler" preset and restart.

| Setting | Target | Why |
|---|---|---|
| `view-distance` in `server.properties` | 7 | Fewer chunks for the browser to draw |
| `simulation-distance` | 5 | Less ticking away from the player |
| `entity-broadcast-range-percentage` | 50 | Mobs and items stop rendering sooner |
| Mob spawn caps | Paper defaults, then lower only if Spark shows mob ticks | Fewer entities on screen |
| Hopper and redstone checks | Paper's alternate redstone and hopper cooldown, if those keys exist in the 26.2 config | Less idle farm lag |

Keep the hub world as it is. Apply the tighter distances to the survival world if the config allows a per-world override. The hub is already a small platform.

Acceptance: a restart still boots, `/spark tps` stays near 20 with one player, and walking in survival does not load a 10-chunk radius anymore.

### Phase 2 — Chunky, once

Install **Chunky** from Modrinth. It has a Paper build for Minecraft 26.2 (`Chunky-Bukkit-1.5.3.jar` as of this plan).

1. Install it with the dashboard plugin manager, or drop the jar in `server-26.2/plugins/`.
2. Restart.
3. Pregenerate the survival spawn only, about a 500-block radius: `chunky world survival`, `chunky radius 500`, `chunky start`.
4. When it finishes, leave Chunky installed but do not keep a pregen running while people play.

This removes the hitch when the first players explore. It does not increase the FPS cap.

Acceptance: flying around the pregenerated area does not spike TPS from chunk generation.

### Phase 3 — LagFixer only if Spark says items or entities are the problem

**LagFixer** has Paper 26.2 builds. Its listing claims to be a general performance jar. Treat it as optional.

Use it only when `/spark profiler` shows dropped items, excess mobs, or similar. Turn on item-clear and mob-limits. Do not enable any option that deletes items from chests or player inventories.

If Spark does not show that problem, skip this phase. Stacking extra "performance" jars on modern Paper usually adds plugin overhead instead of removing it.

Acceptance: item piles despawn on a timer, chests are unchanged, and TPS is higher than the Spark reading from phase 1.

### Phase 4 — Client FPS, no plugin

Tell players, in the hub or on the dashboard Network page, to use these in the Eagler 26.2 client:

- Render distance at or below the server view distance
- Particles on minimal
- Smooth lighting off if the client has that toggle
- A desktop browser when they can, because the 26.2 WASM build is still heavy

No server plugin can fix a client that is already using a lot of RAM or missing features such as sound.

## Plugins to leave out

- ClearLagg, old React builds, and random "ServerBooster" jars. Paper already does the useful parts, and many of those jars are not built for 26.2.
- ViaVersion or ViaBackwards on this server. The backend is already 26.2. Those plugins translate old clients. They do not add FPS.
- A second Spark jar. Paper already ships Spark.
- Lithium or other Fabric mods. This server is Paper.

## Later implementation in this repo

When this plan is approved, the coding work is:

1. A `launch/performance-preset.yml` comment list, plus a small script or dashboard button that writes the phase 1 values into `server.properties` and the Paper config keys that exist after first boot.
2. Document Chunky commands in the README performance section.
3. Do not vendor the plugin jars in git. Install Chunky through the dashboard Modrinth installer so the file stays in `server-26.2/plugins/`.
4. Re-check `/spark tps` after each phase and stop if TPS gets worse.

## Implemented in this repo

- `launch/performance-preset.yml` — target values
- `launch/performance_preset.py` — CLI apply and status (`python launch/performance_preset.py`)
- Dashboard **Performance** page — apply preset, install Chunky, run pregen console commands, client FPS tips
- New installs from `launch/setup.py` use view distance **7** in the server template

## Out of scope

- Rewriting the Eagler 26.2 client renderer
- Changing `wss://KyleTurski.MC`
- Promising a specific FPS number
