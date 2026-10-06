# Blueprint: Hub, Survival warp, and Donut-style sell economy

Author: Grok 4.7 (plan only). Implementation owner: Composer 2.5.

This is an original hub-and-worth design inspired by the public shape of Donut-style SMPs (lobby, click a character, chest sell menu, every item has a price). It does not copy any private plugin, price sheet, or branding.

Server target: Paper 26.2 (Java 25), plugin jar dropped into `server-26.2/plugins/` next to EaglerXPaper.

## Player experience

1. Join the server and land in the **hub**.
2. A named character stands in front of spawn. **Right-click** them to go to the **survival** world.
3. Run **`/sell`** to open a chest menu. Put items in, click the sell button, and get money from each item's worth.
4. Run **`/bal`** to see money and **`/worth`** while holding an item to see its unit price.
5. Run **`/hub`** to go back to the hub.

## Architecture

```
plugins/hub-economy/          Gradle Paper plugin
server-26.2/plugins/          runtime jars (gitignored server folder)
HubEconomy.jar
  HubEconomyPlugin            lifecycle, worlds, NPC, commands
  EconomyStore                balances.yml
  WorthCatalog                values.yml + category defaults
  SellMenu                    chest GUI
  HubNpc                      clickable villager
```

Money is a server balance stored by UUID. It is not a physical item.

## Phases

Implement in this order. Each phase should compile before the next one starts.

### Phase 1 — Plugin project

Create `plugins/hub-economy/` as a Gradle project.

- Java toolchain **25**
- `compileOnly("io.papermc.paper:paper-api:26.2.build.130-stable")` if that version exists, otherwise the newest `26.2.build.*-stable` that resolves from `https://repo.papermc.io/repository/maven-public/`
- `plugin.yml`:
  - name: `HubEconomy`
  - main: `dev.theboysclash.hubeconomy.HubEconomyPlugin`
  - version: `1.0.0`
  - api-version: `26.2` (if the server rejects that, use the api-version string Paper 26.2 documents)
  - commands: `sell`, `bal`, `balance`, `worth`, `hub`, `survival`, `sethub`, `setsurvival`
  - permissions: `hubeconomy.use` (default true), `hubeconomy.admin` (default op)
- Shadow or plain jar is fine. Output name: `HubEconomy.jar`
- If the machine has no Java 25, install a Temurin/OpenJDK 25 JDK and point Gradle at it. Do not lower the toolchain below 25; Paper 26.2 will not load the plugin otherwise.

### Phase 2 — Hub and survival worlds

On enable:

- Load or create world `hub` as a superflat void-style lobby (stone platform at y=64 is acceptable if a void generator is awkward). Keep the hub peaceful: difficulty peaceful, doDaylightCycle false, doMobSpawning false, doWeatherCycle false.
- Load or create world `survival` as a normal world.
- Remember hub spawn and survival spawn in `plugins/HubEconomy/config.yml`.
- First join and every respawn in the hub: teleport to hub spawn, adventure mode, clear fall damage.
- Deaths in survival respawn in survival.
- `/hub` (permission `hubeconomy.use`): teleport to hub spawn, adventure mode.
- `/survival` (same permission): teleport to survival spawn, survival mode. This is the command backup for the NPC.
- `/sethub` and `/setsurvival` (permission `hubeconomy.admin`): save the sender's location as that spawn and respawn the NPC when the hub spawn changes.

New players get adventure mode only in the hub so they cannot break the lobby. Survival is a normal survival world.

### Phase 3 — Clickable hub character

Spawn one persistent villager at the hub spawn, two blocks in front of the spawn facing the player.

- Custom name: `Survival` (green), name visible
- No AI, silent, invulnerable, no gravity, collidable, persistent
- Mark it with a persistent data key `hubeconomy:npc` = `survival`
- Remove duplicate NPCs with that key on startup, then spawn exactly one
- `PlayerInteractEntityEvent`: right-click sends the player through the same path as `/survival`
- The villager must not take damage, trade, or despawn

No Citizens dependency.

### Phase 4 — Balances and item worth

`EconomyStore`

- File: `plugins/HubEconomy/balances.yml`
- Map UUID string to a double balance
- Save on change and on disable
- Starting balance: `0`
- Format money as `$1,234.50`

`WorthCatalog`

- On first run, write `plugins/HubEconomy/values.yml` with a unit price for every `Material` where `isItem()` is true, except `AIR`
- Prices are **per single item**, before stack size
- Admins can edit the file; reload with `/hubeconomy reload` (admin) or server restart. Add command `hubeconomy`.
- Never leave a normal item at a missing price. Unknown materials fall back to `$1`

Default price bands (original numbers, not a copy of any server list):

| Band | Examples | Unit price |
|---|---|---|
| Bulk | dirt, cobblestone, netherrack, sand, gravel, deepslate | $0.25 |
| Building | stone, planks, glass, terracotta, wool, concrete | $1 |
| Farming | logs, crops, seeds, common food | $2 to $8 |
| Mob drops | bone, string, gunpowder, rotten flesh, ender pearl | $4 to $40 |
| Minerals | coal $6, copper $4, iron $12, gold $24, redstone $8, lapis $10, diamond $120, emerald $90, ancient debris $400, netherite ingot $1,500 | as listed |
| Gear | wood tools $8, stone $16, iron $48, gold $64, diamond $400, netherite $2,000; armor about 4× the matching tool | as listed |
| Rare | elytra $5,000, dragon egg $10,000, beacon $2,500, nether star $3,000, enchanted golden apple $1,000, totem $750 | as listed |
| Default | anything not matched | $1 |

Enchanted stacks get a small bonus: `+5%` of base price per enchantment level, capped at `+50%`. Damage on tools does not reduce the price in v1.

### Phase 5 — `/sell` chest menu

`/sell` opens a 54-slot chest inventory titled `Sell Items`.

Layout:

- Slots `0`–`44`: deposit area
- Slot `49`: emerald named `Sell` with lore showing the current total
- Slot `53`: barrier named `Close`
- Other bottom-row slots (`45`–`48`, `50`–`52`): gray glass panes, not takeable

Behavior:

- Players may put in and take out items only in slots `0`–`44`
- Shift-click from the player inventory moves stacks into the deposit area
- Clicking `Sell` prices every deposit stack as `unitWorth * amount`, plus the enchant bonus, adds the total to the balance, clears sold slots, and messages the player: how much they earned and their new balance
- Stacks whose material has no positive price are returned to the player (or left in place with an error). With Phase 4, normal items all have a positive price
- Clicking `Close`, or closing the inventory, returns leftover deposit items to the player and drops overflow at their feet
- Update the emerald lore whenever the deposit area changes
- Cancel any click that would move the glass, emerald, or barrier into the player inventory
- One sell menu per player. Opening `/sell` again refreshes that player's menu

### Phase 6 — Commands and messages

| Command | Who | Effect |
|---|---|---|
| `/sell` | everyone | open the chest menu |
| `/bal`, `/balance` | everyone | show own balance. `/bal <player>` for admins |
| `/worth` | everyone | price of the item in the main hand, including enchant bonus for that stack |
| `/hub` | everyone | hub spawn, adventure |
| `/survival` | everyone | survival spawn, survival mode |
| `/sethub`, `/setsurvival` | admin | save spawn |
| `/hubeconomy reload` | admin | reload `values.yml` and `config.yml` without wiping balances |

Chat messages use short green/gold/red text. No clickable chat ads, no links.

### Phase 7 — Wire into this repo

- `.gitignore`: ignore `plugins/hub-economy/build/`, `.gradle/`
- `launch/setup.py`: after the Paper download, if `HubEconomy.jar` already exists in `plugins/hub-economy/build/libs/`, copy it into `server-26.2/plugins/`
- Add `launch/build-plugin.sh` and `launch/build-plugin.bat` that run `./gradlew build` (Windows: `gradlew.bat`) and copy the jar into `server-26.2/plugins/` when that folder exists
- README: a short "Hub and sell shop" section under the 26.2 guide with the commands above
- Do not download or commit Minecraft client files, Eagler HTML, or the Paper jar

## Acceptance checks

1. `./gradlew build` in `plugins/hub-economy` produces `HubEconomy.jar`
2. Plugin loads on Paper 26.2 with no exception in `latest.log`
3. A new player appears in the hub in adventure mode
4. Right-clicking the Survival villager places them in the survival world in survival mode
5. `/hub` brings them back
6. `/sell` opens a chest. Putting in 64 dirt and clicking Sell pays `64 * 0.25 = $16.00` and empties those slots
7. Closing the menu returns unsold items
8. Restarting the server keeps the balance and does not spawn a second NPC
9. `/worth` on a diamond reports `$120.00`

## Out of scope

- Auction house, shards, crates, ranks, scoreboard, or anti-cheat
- Per-player survival islands
- Changing EaglerXPaper or the Minecraft protocol
- A Windows `.exe` (the existing `.cmd` launcher stays the start path)
