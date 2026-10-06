package dev.theboysclash.hubeconomy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionHouse implements Listener {

    public static final String BROWSER_TITLE = "Auction House";
    public static final String CONFIRM_TITLE = "Buy listing?";

    private static final long LISTING_DURATION_MS = Duration.ofHours(48).toMillis();
    private static final int LISTINGS_PER_PAGE = 45;

    private final HubEconomyPlugin plugin;
    private final EconomyStore economy;
    private final File file;
    private final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();
    private final Object listingLock = new Object();

    public AuctionHouse(HubEconomyPlugin plugin, EconomyStore economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
    }

    public void load() {
        listings.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("listings");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                UUID seller = UUID.fromString(section.getString(key + ".seller"));
                String sellerName = section.getString(key + ".sellerName", "Unknown");
                long price = section.getLong(key + ".price");
                long expiresAt = section.getLong(key + ".expiresAt");
                ItemStack item = section.getItemStack(key + ".item");
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                listings.put(id, new AuctionListing(id, seller, sellerName, item.clone(), price, expiresAt));
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping invalid auction listing " + key + ": " + e.getMessage());
            }
        }
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        for (AuctionListing listing : listings.values()) {
            String path = "listings." + listing.id();
            config.set(path + ".seller", listing.sellerId().toString());
            config.set(path + ".sellerName", listing.sellerName());
            config.set(path + ".price", listing.price());
            config.set(path + ".expiresAt", listing.expiresAt());
            config.set(path + ".item", listing.item());
        }
        try {
            plugin.getDataFolder().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save auctions.yml: " + e.getMessage());
        }
    }

    public void openBrowser(Player player) {
        openBrowser(player, 0, false);
    }

    public void openBrowser(Player player, int page, boolean ownOnly) {
        BrowserSession session = new BrowserSession(player, page, ownOnly);
        player.openInventory(session.inventory);
    }

    public boolean sellFromHand(Player player, long price) {
        if (price < 1) {
            player.sendMessage(Messages.error("Minimum price is 1 coin."));
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(Messages.error("Hold an item in your main hand to list it."));
            return false;
        }
        UUID id = UUID.randomUUID();
        long expiresAt = System.currentTimeMillis() + LISTING_DURATION_MS;
        AuctionListing listing = new AuctionListing(
                id,
                player.getUniqueId(),
                player.getName(),
                hand.clone(),
                price,
                expiresAt);
        synchronized (listingLock) {
            listings.put(id, listing);
            save();
        }
        player.getInventory().setItemInMainHand(null);
        player.sendMessage(Messages.success(
                "Listed " + hand.getAmount() + " " + formatMaterial(hand.getType())
                        + " for " + MoneyFormat.formatCoins(price) + "."));
        return true;
    }

    public void collectExpired(Player player) {
        List<ItemStack> returns = new ArrayList<>();
        synchronized (listingLock) {
            List<UUID> toRemove = new ArrayList<>();
            for (AuctionListing listing : listings.values()) {
                if (!listing.sellerId().equals(player.getUniqueId())) {
                    continue;
                }
                returns.add(listing.item().clone());
                toRemove.add(listing.id());
            }
            for (UUID id : toRemove) {
                listings.remove(id);
            }
            if (!toRemove.isEmpty()) {
                save();
            }
        }
        if (returns.isEmpty()) {
            player.sendMessage(Messages.info("Nothing to collect."));
            return;
        }
        for (ItemStack stack : returns) {
            giveOrDrop(player, stack);
        }
        player.sendMessage(Messages.success("Collected " + returns.size() + " listing(s)."));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (holder instanceof BrowserSession browser) {
            event.setCancelled(true);
            handleBrowserClick(player, browser, event.getRawSlot());
            return;
        }
        if (holder instanceof ConfirmSession confirm) {
            event.setCancelled(true);
            handleConfirmClick(player, confirm, event.getRawSlot());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof BrowserSession || holder instanceof ConfirmSession) {
            event.setCancelled(true);
        }
    }

    private void handleBrowserClick(Player player, BrowserSession session, int rawSlot) {
        if (rawSlot == 45 && session.page > 0) {
            openBrowser(player, session.page - 1, session.ownOnly);
            return;
        }
        if (rawSlot == 53) {
            int maxPage = maxPage(session.ownOnly, player.getUniqueId());
            if (session.page < maxPage) {
                openBrowser(player, session.page + 1, session.ownOnly);
            }
            return;
        }
        if (rawSlot == 49) {
            openBrowser(player, 0, !session.ownOnly);
            return;
        }
        if (rawSlot >= 0 && rawSlot < LISTINGS_PER_PAGE) {
            UUID listingId = session.slotListingIds.get(rawSlot);
            if (listingId != null) {
                openConfirm(player, listingId, session.page, session.ownOnly);
            }
        }
    }

    private void handleConfirmClick(Player player, ConfirmSession session, int rawSlot) {
        if (rawSlot == 11) {
            openBrowser(player, session.returnPage, session.returnOwnOnly);
            return;
        }
        if (rawSlot == 15) {
            attemptPurchase(player, session.listingId);
            player.closeInventory();
        }
    }

    private void openConfirm(Player player, UUID listingId, int returnPage, boolean returnOwnOnly) {
        AuctionListing listing;
        synchronized (listingLock) {
            listing = listings.get(listingId);
        }
        if (listing == null || listing.isExpired()) {
            player.sendMessage(Messages.error("That listing is no longer available."));
            openBrowser(player, returnPage, returnOwnOnly);
            return;
        }
        ConfirmSession session = new ConfirmSession(listing, listingId, returnPage, returnOwnOnly);
        player.openInventory(session.inventory);
    }

    private void attemptPurchase(Player buyer, UUID listingId) {
        synchronized (listingLock) {
            AuctionListing listing = listings.get(listingId);
            if (listing == null || listing.isExpired()) {
                buyer.sendMessage(Messages.error("That listing is no longer available."));
                return;
            }
            if (listing.sellerId().equals(buyer.getUniqueId())) {
                buyer.sendMessage(Messages.error("You cannot buy your own listing."));
                return;
            }
            if (!economy.tryWithdraw(buyer.getUniqueId(), listing.price())) {
                buyer.sendMessage(Messages.error("You cannot afford that (" + MoneyFormat.formatCoins(listing.price()) + ")."));
                return;
            }
            listings.remove(listingId);
            save();
            economy.deposit(listing.sellerId(), listing.price());
            giveOrDrop(buyer, listing.item().clone());
            buyer.sendMessage(Messages.success(
                    "Purchased listing for " + MoneyFormat.formatCoins(listing.price()) + "."));
        }
    }

    private int maxPage(boolean ownOnly, UUID viewer) {
        int count = visibleListings(ownOnly, viewer).size();
        if (count == 0) {
            return 0;
        }
        return (count - 1) / LISTINGS_PER_PAGE;
    }

    private List<AuctionListing> visibleListings(boolean ownOnly, UUID viewer) {
        List<AuctionListing> visible = new ArrayList<>();
        synchronized (listingLock) {
            for (AuctionListing listing : listings.values()) {
                if (listing.isExpired()) {
                    continue;
                }
                if (ownOnly && !listing.sellerId().equals(viewer)) {
                    continue;
                }
                visible.add(listing);
            }
        }
        visible.sort(Comparator.comparingLong(AuctionListing::createdSortKey).reversed());
        return visible;
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(extra ->
                player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }

    private static String formatMaterial(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String formatTimeLeft(long expiresAt) {
        long ms = expiresAt - System.currentTimeMillis();
        if (ms <= 0) {
            return "Expired";
        }
        long hours = ms / (60 * 60 * 1000);
        long minutes = (ms / (60 * 1000)) % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m left";
        }
        return minutes + "m left";
    }

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack listingIcon(AuctionListing listing) {
        ItemStack icon = listing.item().clone();
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.text("Seller: " + listing.sellerName(), NamedTextColor.GRAY));
        lore.add(Component.text("Price: " + MoneyFormat.formatCoins(listing.price()), NamedTextColor.GOLD));
        lore.add(Component.text(formatTimeLeft(listing.expiresAt()), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private final class BrowserSession implements InventoryHolder {

        private final int page;
        private final boolean ownOnly;
        private final Map<Integer, UUID> slotListingIds = new ConcurrentHashMap<>();
        private final Inventory inventory;

        BrowserSession(Player player, int page, boolean ownOnly) {
            this.page = Math.max(0, page);
            this.ownOnly = ownOnly;
            this.inventory = Bukkit.createInventory(this, 54, Component.text(BROWSER_TITLE));
            layout(player);
        }

        private void layout(Player player) {
            ItemStack glass = filler();
            for (int slot = 45; slot <= 53; slot++) {
                if (slot != 45 && slot != 49 && slot != 53) {
                    inventory.setItem(slot, glass.clone());
                }
            }
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.displayName(Component.text("Previous page", NamedTextColor.YELLOW));
            prev.setItemMeta(prevMeta);
            inventory.setItem(45, prev);

            ItemStack your = new ItemStack(Material.BOOK);
            ItemMeta yourMeta = your.getItemMeta();
            yourMeta.displayName(Component.text(ownOnly ? "All listings" : "Your listings", NamedTextColor.AQUA));
            your.setItemMeta(yourMeta);
            inventory.setItem(49, your);

            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.displayName(Component.text("Next page", NamedTextColor.YELLOW));
            next.setItemMeta(nextMeta);
            inventory.setItem(53, next);

            List<AuctionListing> visible = visibleListings(ownOnly, player.getUniqueId());
            int start = this.page * LISTINGS_PER_PAGE;
            for (int i = 0; i < LISTINGS_PER_PAGE; i++) {
                int index = start + i;
                if (index >= visible.size()) {
                    break;
                }
                AuctionListing listing = visible.get(index);
                slotListingIds.put(i, listing.id());
                inventory.setItem(i, listingIcon(listing));
            }
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final class ConfirmSession implements InventoryHolder {

        private final UUID listingId;
        private final int returnPage;
        private final boolean returnOwnOnly;
        private final Inventory inventory;

        ConfirmSession(AuctionListing listing, UUID listingId, int returnPage, boolean returnOwnOnly) {
            this.listingId = listingId;
            this.returnPage = returnPage;
            this.returnOwnOnly = returnOwnOnly;
            this.inventory = Bukkit.createInventory(this, 27, Component.text(CONFIRM_TITLE));
            ItemStack glass = filler();
            for (int slot = 0; slot < 27; slot++) {
                inventory.setItem(slot, glass.clone());
            }
            inventory.setItem(13, listing.item().clone());
            ItemStack cancel = new ItemStack(Material.RED_WOOL);
            ItemMeta cancelMeta = cancel.getItemMeta();
            cancelMeta.displayName(Component.text("Cancel", NamedTextColor.RED));
            cancel.setItemMeta(cancelMeta);
            inventory.setItem(11, cancel);

            ItemStack confirm = new ItemStack(Material.LIME_WOOL);
            ItemMeta confirmMeta = confirm.getItemMeta();
            confirmMeta.displayName(Component.text("Confirm", NamedTextColor.GREEN));
            confirm.setItemMeta(confirmMeta);
            inventory.setItem(15, confirm);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record AuctionListing(
            UUID id,
            UUID sellerId,
            String sellerName,
            ItemStack item,
            long price,
            long expiresAt) {

        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }

        long createdSortKey() {
            return expiresAt - LISTING_DURATION_MS;
        }
    }
}
