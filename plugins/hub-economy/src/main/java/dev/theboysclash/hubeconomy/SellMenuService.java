package dev.theboysclash.hubeconomy;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SellMenuService implements Listener {

    public static final String TITLE = "Sell Items";
    public static final int DEPOSIT_START = 0;
    public static final int DEPOSIT_END = 44;
    public static final int SELL_SLOT = 49;
    public static final int CLOSE_SLOT = 53;

    private final HubEconomyPlugin plugin;
    private final EconomyStore economy;
    private final WorthCatalog worth;
    private final Map<UUID, SellSession> sessions = new HashMap<>();

    public SellMenuService(HubEconomyPlugin plugin, EconomyStore economy, WorthCatalog worth) {
        this.plugin = plugin;
        this.economy = economy;
        this.worth = worth;
    }

    public void open(Player player) {
        SellSession existing = sessions.get(player.getUniqueId());
        if (existing != null && player.getOpenInventory().getTopInventory() == existing.inventory) {
            updateSellButton(existing);
            return;
        }
        SellSession session = new SellSession(player);
        sessions.put(player.getUniqueId(), session);
        player.openInventory(session.inventory);
        updateSellButton(session);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SellSession session = sessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory) {
            return;
        }

        int rawSlot = event.getRawSlot();
        Inventory top = session.inventory;

        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == top) {
            if (isDepositSlot(rawSlot)) {
                event.setCancelled(false);
                Bukkit.getScheduler().runTask(plugin, () -> updateSellButton(session));
                return;
            }
            event.setCancelled(true);
            if (rawSlot == SELL_SLOT) {
                performSell(player, session);
            } else if (rawSlot == CLOSE_SLOT) {
                player.closeInventory();
            }
            return;
        }

        if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);
            ItemStack stack = event.getCurrentItem();
            if (stack == null || stack.getType().isAir()) {
                return;
            }
            ItemStack leftover = moveStackIntoDeposits(top, stack.clone());
            if (leftover.getAmount() <= 0) {
                event.setCurrentItem(null);
            } else {
                event.getCurrentItem().setAmount(leftover.getAmount());
            }
            Bukkit.getScheduler().runTask(plugin, () -> updateSellButton(session));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SellSession session = sessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < topSize(event) && !isDepositSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> updateSellButton(session));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        SellSession session = sessions.get(player.getUniqueId());
        if (session == null || event.getInventory() != session.inventory) {
            return;
        }
        sessions.remove(player.getUniqueId());
        returnItems(player, session.inventory);
        session.close();
    }

    private void performSell(Player player, SellSession session) {
        long total = 0L;
        List<ItemStack> unsellable = new ArrayList<>();
        for (int slot = DEPOSIT_START; slot <= DEPOSIT_END; slot++) {
            ItemStack stack = session.inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            long unit = worth.unitPrice(stack.getType());
            if (unit <= 0) {
                unsellable.add(stack.clone());
                session.inventory.setItem(slot, null);
                continue;
            }
            total += worth.stackTotalWorth(stack);
            session.inventory.setItem(slot, null);
        }
        if (!unsellable.isEmpty()) {
            player.sendMessage(Messages.error("Some items could not be sold and were returned."));
            giveOrDrop(player, unsellable);
        }
        if (total > 0) {
            economy.addBalance(player.getUniqueId(), total);
            long balance = economy.getBalance(player.getUniqueId());
            player.sendMessage(Messages.success(
                    "Sold items for " + MoneyFormat.format(total) + ". Balance: " + MoneyFormat.format(balance)));
        } else if (unsellable.isEmpty()) {
            player.sendMessage(Messages.info("Nothing to sell."));
        }
        updateSellButton(session);
    }

    private void updateSellButton(SellSession session) {
        long preview = 0L;
        for (int slot = DEPOSIT_START; slot <= DEPOSIT_END; slot++) {
            ItemStack stack = session.inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir()) {
                preview += worth.stackTotalWorth(stack);
            }
        }
        ItemStack sell = new ItemStack(Material.EMERALD);
        ItemMeta meta = sell.getItemMeta();
        meta.displayName(Component.text("Sell"));
        meta.lore(List.of(Component.text("Total: " + MoneyFormat.format(preview))));
        sell.setItemMeta(meta);
        session.inventory.setItem(SELL_SLOT, sell);
    }

    private static ItemStack moveStackIntoDeposits(Inventory top, ItemStack stack) {
        for (int slot = DEPOSIT_START; slot <= DEPOSIT_END; slot++) {
            ItemStack existing = top.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                top.setItem(slot, stack);
                return new ItemStack(Material.AIR);
            }
            if (existing.isSimilar(stack) && existing.getAmount() < existing.getMaxStackSize()) {
                int move = Math.min(stack.getAmount(), existing.getMaxStackSize() - existing.getAmount());
                existing.setAmount(existing.getAmount() + move);
                stack.setAmount(stack.getAmount() - move);
                if (stack.getAmount() <= 0) {
                    return new ItemStack(Material.AIR);
                }
            }
        }
        return stack;
    }

    private void returnItems(Player player, Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = DEPOSIT_START; slot <= DEPOSIT_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir()) {
                items.add(stack.clone());
                inventory.setItem(slot, null);
            }
        }
        giveOrDrop(player, items);
    }

    private static void giveOrDrop(Player player, List<ItemStack> items) {
        for (ItemStack stack : items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        }
    }

    private static boolean isDepositSlot(int rawSlot) {
        return rawSlot >= DEPOSIT_START && rawSlot <= DEPOSIT_END;
    }

    private static int topSize(InventoryDragEvent event) {
        return event.getView().getTopInventory().getSize();
    }

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    private final class SellSession implements InventoryHolder {

        private final Inventory inventory;

        SellSession(Player player) {
            this.inventory = Bukkit.createInventory(this, 54, Component.text(TITLE));
            layoutChrome();
        }

        private void layoutChrome() {
            ItemStack glass = filler();
            for (int slot = 45; slot <= 48; slot++) {
                inventory.setItem(slot, glass.clone());
            }
            for (int slot = 50; slot <= 52; slot++) {
                inventory.setItem(slot, glass.clone());
            }
            ItemStack close = new ItemStack(Material.BARRIER);
            ItemMeta closeMeta = close.getItemMeta();
            closeMeta.displayName(Component.text("Close"));
            close.setItemMeta(closeMeta);
            inventory.setItem(CLOSE_SLOT, close);
            updateSellButton(this);
        }

        void close() {
            // session lifecycle only
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
