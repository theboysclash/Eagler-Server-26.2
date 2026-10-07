package dev.theboysclash.hubeconomy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public final class RtpMenu implements CommandExecutor, Listener {

    private final HubEconomyPlugin plugin;
    private final WorldService worlds;

    public RtpMenu(HubEconomyPlugin plugin, WorldService worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Random teleport"));
        holder.inventory = inventory;
        inventory.setItem(11, button(Material.GRASS_BLOCK, "Overworld", "Random spot in the survival world"));
        inventory.setItem(13, button(Material.NETHERRACK, "Nether", "Random spot in the nether"));
        inventory.setItem(15, button(Material.END_STONE, "The End", "Random spot in the end"));
        player.openInventory(inventory);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        if (!player.hasPermission("hubeconomy.use")) {
            player.sendMessage(Messages.error("No permission."));
            return true;
        }
        open(player);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null) {
            return;
        }
        World.Environment environment = switch (item.getType()) {
            case GRASS_BLOCK -> World.Environment.NORMAL;
            case NETHERRACK -> World.Environment.NETHER;
            case END_STONE -> World.Environment.THE_END;
            default -> null;
        };
        if (environment == null) {
            return;
        }
        player.closeInventory();
        teleport(player, environment);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void teleport(Player player, World.Environment environment) {
        World world = worlds.rtpWorld(environment);
        if (world == null) {
            player.sendMessage(Messages.error("That world is not loaded."));
            return;
        }
        player.sendMessage(Messages.info("Looking for a safe spot..."));
        int radius = environment == World.Environment.NORMAL ? 2000 : 400;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location spot = worlds.findRandomSafe(world, radius);
            if (spot == null) {
                player.sendMessage(Messages.error("No safe spot found. Try again."));
                return;
            }
            player.teleport(spot);
            player.setGameMode(GameMode.SURVIVAL);
            player.setFallDistance(0f);
            player.sendMessage(Messages.success("Teleported."));
        });
    }

    private static ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GREEN));
        meta.lore(java.util.List.of(Component.text(lore, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
