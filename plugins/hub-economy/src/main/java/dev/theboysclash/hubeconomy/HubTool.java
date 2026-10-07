package dev.theboysclash.hubeconomy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class HubTool implements Listener {

    private static final List<String> MODES = List.of("hub-spawn", "survival-spawn", "npc:survival", "delete-npc");

    private final HubEconomyPlugin plugin;
    private final PluginConfig config;
    private final HubNpc hubNpc;
    private NamespacedKey toolKey;
    private NamespacedKey modeKey;

    public HubTool(HubEconomyPlugin plugin, PluginConfig config, HubNpc hubNpc) {
        this.plugin = plugin;
        this.config = config;
        this.hubNpc = hubNpc;
    }

    public void init() {
        toolKey = new NamespacedKey(plugin, "hub_tool");
        modeKey = new NamespacedKey(plugin, "hub_tool_mode");
    }

    public void give(Player player) {
        player.getInventory().addItem(createItem(MODES.getFirst()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isTool(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("hubeconomy.admin")) {
            player.sendMessage(Messages.error("No permission."));
            return;
        }
        String mode = modeOf(item);
        if (player.isSneaking()) {
            String next = nextMode(mode);
            writeMode(item, next);
            player.sendMessage(Messages.info("Hub tool: " + label(next)));
            return;
        }
        apply(player, mode);
    }

    private void apply(Player player, String mode) {
        if ("hub-spawn".equals(mode)) {
            config.setHubSpawn(player.getLocation());
            player.sendMessage(Messages.success("Hub spawn set to where you are standing."));
            return;
        }
        if ("survival-spawn".equals(mode)) {
            config.setSurvivalSpawn(player.getLocation());
            player.sendMessage(Messages.success("Survival spawn set to where you are standing."));
            return;
        }
        if ("delete-npc".equals(mode)) {
            Entity target = player.getTargetEntity(6);
            if (target == null || !hubNpc.isHubNpc(target)) {
                player.sendMessage(Messages.error("Look at an NPC and right-click to delete it."));
                return;
            }
            hubNpc.deleteNpc(target);
            player.sendMessage(Messages.success("NPC deleted. It will not come back after a restart."));
            return;
        }
        if (mode.startsWith("npc:")) {
            String id = mode.substring("npc:".length());
            if (!HubNpc.NPC_VALUE_SURVIVAL.equals(id)) {
                player.sendMessage(Messages.error("That NPC is not set up yet."));
                return;
            }
            hubNpc.moveSurvivalNpc(player.getLocation());
            player.sendMessage(Messages.success("Survival NPC moved here."));
            return;
        }
        player.sendMessage(Messages.error("Unknown tool mode. Sneak + right-click to reset it."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeleteClick(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isTool(item) || !"delete-npc".equals(modeOf(item))) {
            return;
        }
        if (!player.hasPermission("hubeconomy.admin")) {
            return;
        }
        if (!hubNpc.isHubNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        hubNpc.deleteNpc(event.getRightClicked());
        player.sendMessage(Messages.success("NPC deleted. It will not come back after a restart."));
    }

    private ItemStack createItem(String mode) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        writeMode(item, mode);
        return item;
    }

    private void writeMode(ItemStack item, String mode) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Hub Tool", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text(label(mode), NamedTextColor.GREEN),
                Component.text("Right-click to use", NamedTextColor.GRAY),
                Component.text("Sneak + right-click to change mode", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode);
        item.setItemMeta(meta);
    }

    private boolean isTool(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) {
            return false;
        }
        Byte mark = item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.BYTE);
        return mark != null && mark == 1;
    }

    private String modeOf(ItemStack item) {
        String mode = item.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        if (mode == null || !MODES.contains(mode)) {
            return MODES.getFirst();
        }
        return mode;
    }

    private static String nextMode(String mode) {
        int index = MODES.indexOf(mode);
        if (index < 0 || index + 1 >= MODES.size()) {
            return MODES.getFirst();
        }
        return MODES.get(index + 1);
    }

    private static String label(String mode) {
        return switch (mode) {
            case "hub-spawn" -> "Mode: set hub spawn";
            case "survival-spawn" -> "Mode: set survival spawn";
            case "npc:survival" -> "Mode: move Survival NPC";
            case "delete-npc" -> "Mode: delete the NPC you are looking at";
            default -> "Mode: " + mode;
        };
    }
}
