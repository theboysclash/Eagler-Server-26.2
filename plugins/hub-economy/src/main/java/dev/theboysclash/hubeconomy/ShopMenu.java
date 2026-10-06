package dev.theboysclash.hubeconomy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
public final class ShopMenu implements Listener {

    public static final String TITLE = "Shop";

    private final HubEconomyPlugin plugin;
    private final EconomyStore economy;
    private final WorthCatalog worth;

    public ShopMenu(HubEconomyPlugin plugin, EconomyStore economy, WorthCatalog worth) {
        this.plugin = plugin;
        this.economy = economy;
        this.worth = worth;
    }

    public void open(Player player) {
        ShopSession session = new ShopSession(player, ShopCategory.BLOCKS);
        player.openInventory(session.inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ShopSession session)) {
            return;
        }
        event.setCancelled(true);

        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }

        if (rawSlot < 9) {
            ShopCategory category = ShopCategory.fromSlot(rawSlot);
            if (category != null && category != session.category) {
                session.switchCategory(category);
            }
            return;
        }

        ItemStack display = top.getItem(rawSlot);
        if (display == null || display.getType().isAir() || isFiller(display)) {
            return;
        }

        Material material = display.getType();
        int amount = event.isShiftClick() ? material.getMaxStackSize() : 1;
        long unitBuy = worth.buyPrice(material);
        long totalCost = unitBuy * amount;

        if (economy.getBalance(player.getUniqueId()) < totalCost) {
            player.sendMessage(Messages.error("You cannot afford that (" + MoneyFormat.formatCoins(totalCost) + ")."));
            return;
        }

        if (!economy.tryWithdraw(player.getUniqueId(), totalCost)) {
            player.sendMessage(Messages.error("You cannot afford that (" + MoneyFormat.formatCoins(totalCost) + ")."));
            return;
        }

        ItemStack bought = new ItemStack(material, amount);
        giveOrDrop(player, bought);
        player.sendMessage(Messages.success(
                "Bought " + amount + " " + formatMaterial(material) + " for " + MoneyFormat.formatCoins(totalCost) + "."));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopSession) {
            event.setCancelled(true);
        }
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(extra ->
                player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }

    private static String formatMaterial(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static boolean isFiller(ItemStack stack) {
        return stack.getType() == Material.GRAY_STAINED_GLASS_PANE;
    }

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack categoryButton(ShopCategory category, boolean selected) {
        ItemStack item = new ItemStack(category.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(category.label(), selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack shopDisplay(Material material, long buyPrice) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                Component.text("Buy: " + MoneyFormat.formatCoins(buyPrice), NamedTextColor.GRAY),
                Component.text("Shift-click: stack", NamedTextColor.DARK_GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private enum ShopCategory {
        BLOCKS(0, "Blocks", Material.DIRT),
        ORES(1, "Ores", Material.COAL),
        FOOD(2, "Food", Material.BREAD),
        FARMING(3, "Farming", Material.WHEAT),
        COMBAT(4, "Combat", Material.IRON_SWORD),
        MISC(5, "Misc", Material.ENDER_PEARL);

        private final int slot;
        private final String label;
        private final Material icon;

        ShopCategory(int slot, String label, Material icon) {
            this.slot = slot;
            this.label = label;
            this.icon = icon;
        }

        int slot() {
            return slot;
        }

        String label() {
            return label;
        }

        Material icon() {
            return icon;
        }

        static ShopCategory fromSlot(int slot) {
            for (ShopCategory c : values()) {
                if (c.slot == slot) {
                    return c;
                }
            }
            return null;
        }

        List<Material> materials() {
            return switch (this) {
                case BLOCKS -> blocksCategory();
                case ORES -> List.of(
                        Material.COAL, Material.RAW_IRON, Material.IRON_INGOT,
                        Material.RAW_GOLD, Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD,
                        Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
                case FOOD -> List.of(
                        Material.BREAD, Material.COOKED_BEEF, Material.COOKED_CHICKEN,
                        Material.GOLDEN_CARROT, Material.GOLDEN_APPLE);
                case FARMING -> farmingCategory();
                case COMBAT -> List.of(
                        Material.STONE_SWORD, Material.IRON_SWORD, Material.BOW, Material.ARROW,
                        Material.SHIELD, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.DIAMOND_SWORD);
                case MISC -> List.of(
                        Material.ENDER_PEARL, Material.BLAZE_ROD, Material.ENDER_CHEST,
                        Material.SHULKER_BOX, Material.TOTEM_OF_UNDYING, Material.ELYTRA);
            };
        }
    }

    private static List<Material> blocksCategory() {
        List<Material> list = new ArrayList<>();
        list.add(Material.DIRT);
        list.add(Material.COBBLESTONE);
        list.add(Material.STONE);
        list.add(Material.DEEPSLATE);
        list.add(Material.SAND);
        list.add(Material.GRAVEL);
        list.addAll(woodPlanks());
        list.addAll(woodLogs());
        list.add(Material.GLASS);
        list.addAll(woolColors());
        return list;
    }

    private static List<Material> farmingCategory() {
        List<Material> list = new ArrayList<>();
        list.add(Material.WHEAT);
        list.add(Material.WHEAT_SEEDS);
        list.add(Material.CARROT);
        list.add(Material.POTATO);
        list.add(Material.BONE_MEAL);
        list.addAll(saplings());
        return list;
    }

    private static List<Material> woodPlanks() {
        List<Material> planks = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.name().endsWith("_PLANKS")) {
                planks.add(m);
            }
        }
        planks.sort((a, b) -> a.name().compareTo(b.name()));
        return planks;
    }

    private static List<Material> woodLogs() {
        return List.of(
                Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
                Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
                Material.CRIMSON_STEM, Material.WARPED_STEM);
    }

    private static List<Material> woolColors() {
        List<Material> wool = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.name().endsWith("_WOOL")) {
                wool.add(m);
            }
        }
        wool.sort((a, b) -> a.name().compareTo(b.name()));
        return wool;
    }

    private static List<Material> saplings() {
        List<Material> saplings = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.name().endsWith("_SAPLING") || m.name().endsWith("_FUNGUS")) {
                saplings.add(m);
            }
        }
        saplings.sort((a, b) -> a.name().compareTo(b.name()));
        return saplings;
    }

    private final class ShopSession implements InventoryHolder {

        private ShopCategory category;
        private final Inventory inventory;

        ShopSession(Player player, ShopCategory category) {
            this.category = category;
            this.inventory = Bukkit.createInventory(this, 54, Component.text(TITLE));
            render();
        }

        void switchCategory(ShopCategory newCategory) {
            this.category = newCategory;
            render();
        }

        private void render() {
            ItemStack glass = filler();
            for (int slot = 0; slot < 54; slot++) {
                inventory.setItem(slot, glass.clone());
            }
            for (ShopCategory cat : ShopCategory.values()) {
                inventory.setItem(cat.slot(), categoryButton(cat, cat == category));
            }
            List<Material> items = category.materials();
            int index = 0;
            for (int slot = 9; slot < 54 && index < items.size(); slot++) {
                Material material = items.get(index++);
                long buy = worth.buyPrice(material);
                inventory.setItem(slot, shopDisplay(material, buy));
            }
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
