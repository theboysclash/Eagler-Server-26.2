# Blueprint: coins, shop, auction house, sidebar

Author: Grok 4.7 (plan only). Implementation owner: Composer 2.5.

This extends HubEconomy. Keep the hub, Survival villager, and `/sell` chest. Change the economy so it feels like a public survival shop server: whole coins, a sidebar on the right, an instant buy shop, and an auction house. Prices are original. Dirt is the anchor. Do not copy another server's price sheet or branding.

## Coin rules

- One dirt sells for **1 coin**. A stack of 64 dirt sells for **64 coins**.
- Every `Material` where `isItem()` is true, except `AIR`, has a sell price of at least **1 coin**.
- Prices are whole coins. Round enchant bonuses to the nearest coin, and never below the base total.
- Store balances as whole coins (`long`), not dollars. Display `1,234` with no `$`.
- Buy prices are always higher than sell prices so buying an item and selling it back loses coins.

## Phases

Implement in this order. The project must still compile at the end of each phase.

### Phase 1 — Reprice every item

Edit `WorthCatalog`.

- Bulk blocks sell for **1** coin: dirt, coarse dirt, rooted dirt, mud, sand, red sand, gravel, cobblestone, netherrack, stone, deepslate, tuff, andesite, diorite, granite, and the other common overworld filler blocks.
- Building blocks (planks, glass, wool, terracotta, concrete, logs) sell for **2 to 8**.
- Food and crops sell for **2 to 12**.
- Mob drops sell for **4 to 40**. Ender pearls stay at the top of that range.
- Ores and minerals sell for more than the blocks they come from:

| Item | Sell coins |
|---|---|
| Coal ore / deepslate coal ore | 8 / 10 |
| Copper ore / deepslate copper ore | 6 / 8 |
| Iron ore / deepslate iron ore | 18 / 22 |
| Gold ore / deepslate gold ore / nether gold ore | 36 / 44 / 30 |
| Redstone ore / deepslate redstone ore | 14 / 18 |
| Lapis ore / deepslate lapis ore | 16 / 20 |
| Diamond ore / deepslate diamond ore | 160 / 200 |
| Emerald ore / deepslate emerald ore | 130 / 160 |
| Nether quartz ore | 12 |
| Ancient debris | 550 |
| Coal / charcoal | 8 / 6 |
| Raw copper / copper ingot | 5 / 8 |
| Raw iron / iron ingot | 14 / 24 |
| Raw gold / gold ingot | 28 / 48 |
| Redstone dust | 12 |
| Lapis lazuli | 14 |
| Diamond | 220 |
| Emerald | 180 |
| Netherite scrap | 500 |
| Netherite ingot | 2,200 |

- Gear stays above the material it is made from. Wood tools 8, stone 16, iron 64, gold 80, diamond 500, netherite 2,400. Armor is 4× the matching tool. Bows follow the same tier as the closest material, defaulting to 16.
- Rare items: elytra 8,000, dragon egg 15,000, beacon 4,000, nether star 5,000, enchanted golden apple 1,500, totem 1,200.
- Anything not matched still sells for **1** coin.
- Write `prices-version: 2` at the top of `values.yml`. If the file is missing or the version is below 2, rewrite the prices. Admins can edit the file after that. `/hubeconomy reload` reloads it.

`/sell` already multiplies unit price by stack size. 64 dirt must pay exactly 64 coins. Change `MoneyFormat` and `EconomyStore` to whole coins. Round any old decimal balances in `balances.yml` to the nearest coin when loading.

### Phase 2 — Sidebar on the right

Add `CoinSidebar`.

- On join, give the player a scoreboard whose display slot is the sidebar (the right side of the screen).
- Title: `KyleTurski MC`
- Lines, top to bottom:

```
Coins
<balance>
```

- Use a blank spacer line between the title area and the word Coins if the scoreboard API needs unique entries.
- Refresh the balance line whenever coins are added or spent, and when the player joins.
- Remove the board on quit. Do not flash a second board over it.
- Paper 26.2: prefer `Score.customName(Component)` for the text. Fall back to team prefixes only if that method is missing.

### Phase 3 — `/shop`

Add `ShopMenu`.

`/shop` opens a 54-slot chest titled `Shop`. The top row is category buttons. The rest of the chest is items in the selected category. Default category is Blocks.

Categories and example contents (include the obvious siblings of each item, such as every wood type for planks and logs):

| Category | Sells |
|---|---|
| Blocks | dirt, cobblestone, stone, deepslate, sand, gravel, oak planks, oak log, glass, white wool |
| Ores | coal, raw iron, iron ingot, raw gold, gold ingot, diamond, emerald, ancient debris, netherite scrap |
| Food | bread, cooked beef, cooked chicken, golden carrot, golden apple |
| Farming | wheat, wheat seeds, carrot, potato, bone meal, oak sapling |
| Combat | stone sword, iron sword, bow, arrow, shield, iron helmet, iron chestplate, diamond sword |
| Misc | ender pearl, blaze rod, ender chest, shulker box, totem of undying, elytra |

- Left-click buys **1**. Shift-click buys **one full stack** (the item's max stack size).
- Buy price for one item is `max(2, sellPrice * 3)`.
- Show the buy price on the item lore: `Buy: 3 coins` and `Shift-click: stack`.
- If the player cannot afford it, send an error and take nothing.
- Take the coins first, then give the items. Drop overflow at their feet.
- Category buttons are not purchased. Clicking one only changes the page.
- Fill unused slots with gray glass that cannot be taken.
- Cancel every click that would move a shop item into the player inventory as a real item. The bought item is a new stack, not the display icon.

### Phase 4 — Auction house

Add `AuctionHouse` and persist `plugins/HubEconomy/auctions.yml`.

Commands:

| Command | Effect |
|---|---|
| `/ah` | Open the auction browser |
| `/ah sell <coins>` | List the main-hand stack for that many coins. Minimum price is 1. Remove the stack from the hand only after the listing is saved. |
| `/ah collect` | Return every expired or unsold listing that belongs to the player |

Browser GUI, 54 slots, title `Auction House`:

- Slots 0–44: listings, 45 per page, newest first.
- Slot 45: previous page. Slot 53: next page.
- Slot 49: button `Your listings`.
- Other bottom slots: gray glass.

Each listing icon is a copy of the item. Lore shows seller name, price in coins, and time left. Listings last **48 hours**.

Buying:

- Click a listing to open a 27-slot confirm chest titled `Buy listing?`.
- Center slot is the item. A green wool `Confirm` buys it. A red wool `Cancel` goes back.
- Refuse the purchase if the listing is gone, expired, or owned by the buyer.
- Charge the buyer, pay the seller (even if they are offline), delete the listing, then give the item. If two players confirm the same listing, only the first save wins.
- Save `auctions.yml` after every list, buy, collect, and on disable.

### Phase 5 — Commands, reload, docs

- Register `/shop` and `/ah` in `plugin.yml` under `hubeconomy.use`.
- `/ah sell` tab-completes nothing required. `/ah` with no args opens the browser. Unknown `/ah` usage prints the three forms above.
- `/hubeconomy reload` reloads worth prices and does not wipe balances or auctions.
- README hub section: dirt is 1 coin, the sidebar shows coins, `/shop` buys, `/ah` lists and buys player items.

## Acceptance

1. `./gradlew build` in `plugins/hub-economy` succeeds.
2. `/worth` on dirt reports `1` coin. Selling 64 dirt pays exactly 64 coins and the sidebar updates.
3. Every normal item can be sold. None pay 0.
4. A diamond ore sells for 160 coins. A deepslate diamond ore sells for 200.
5. `/shop` buys 1 dirt for 3 coins and does not let the player take the display icon.
6. `/ah sell 100` with a diamond in hand creates a listing and clears the hand. Another player can buy it. The seller's balance increases by 100 even if they are offline.
7. Restarting the server keeps balances, shop prices, and unsold auctions.
8. Hub, Survival villager, and `/sell` still work.

## Out of scope

- Shards, crates, order books, or another server's exact scoreboard text.
- A website for the auction house.
- Changing the `wss://KyleTurski.MC` join address.
