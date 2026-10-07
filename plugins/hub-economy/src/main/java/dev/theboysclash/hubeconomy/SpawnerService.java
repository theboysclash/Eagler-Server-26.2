package dev.theboysclash.hubeconomy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.TileState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SpawnerService implements Listener {

    private final HubEconomyPlugin plugin;
    private NamespacedKey stackKey;
    private NamespacedKey typeKey;
    private NamespacedKey hologramKey;
    private NamespacedKey shopKey;

    public SpawnerService(HubEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        stackKey = new NamespacedKey(plugin, "spawner_stack");
        typeKey = new NamespacedKey(plugin, "spawner_type");
        hologramKey = new NamespacedKey(plugin, "spawner_label");
        shopKey = new NamespacedKey(plugin, "shop_spawner");
    }

    public long price(EntityType type) {
        return switch (type) {
            case ZOMBIE -> 1_000_000L;
            case SKELETON -> 2_000_000L;
            case BLAZE -> 3_000_000L;
            default -> 0L;
        };
    }

    public List<ItemStack> shopDisplays() {
        return List.of(
                shopDisplay(EntityType.ZOMBIE),
                shopDisplay(EntityType.SKELETON),
                shopDisplay(EntityType.BLAZE));
    }

    public EntityType shopType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return EntityType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public ItemStack createItem(EntityType type, int stack) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        if (meta.getBlockState() instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(type);
            meta.setBlockState(spawner);
        }
        meta.displayName(Component.text(pretty(type) + " Spawner", NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("Stack: " + stack, NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, Math.max(1, stack));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER || !(block.getState() instanceof CreatureSpawner spawner)) {
            return;
        }
        removeHologram(spawner);
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) <= 0) {
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        EntityType type = spawner.getSpawnedType();
        int stack = readStack(spawner);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createItem(type, stack));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) {
            return;
        }
        EntityType type = typeOf(event.getItemInHand());
        int adding = stackOf(event.getItemInHand());
        if (type == null) {
            return;
        }
        Block below = event.getBlockPlaced().getRelative(0, -1, 0);
        if (below.getState() instanceof CreatureSpawner lower && lower.getSpawnedType() == type) {
            int merged = readStack(lower) + adding;
            writeStack(lower, merged);
            lower.update(true);
            event.getBlockPlaced().setType(Material.AIR);
            refreshHologram(lower);
            return;
        }
        if (event.getBlockPlaced().getState() instanceof CreatureSpawner placed) {
            placed.setSpawnedType(type);
            writeStack(placed, adding);
            placed.update(true);
            refreshHologram(placed);
        }
    }

    @EventHandler
    public void onStackClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() != Material.SPAWNER) {
            return;
        }
        ItemStack hand = event.getItem();
        if (hand == null || hand.getType() != Material.SPAWNER) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof CreatureSpawner spawner)) {
            return;
        }
        EntityType held = typeOf(hand);
        if (held == null || held != spawner.getSpawnedType()) {
            return;
        }
        event.setCancelled(true);
        int merged = readStack(spawner) + stackOf(hand);
        writeStack(spawner, merged);
        spawner.update(true);
        refreshHologram(spawner);
        hand.setAmount(hand.getAmount() - 1);
        event.getPlayer().sendMessage(Messages.success(pretty(held) + " spawner stack is now " + merged + "."));
    }

    @EventHandler
    public void onSpawn(SpawnerSpawnEvent event) {
        if (!(event.getSpawner() instanceof CreatureSpawner spawner)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        int stack = readStack(spawner);
        var health = living.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            double base = health.getBaseValue();
            double scaled = base * stack;
            health.setBaseValue(scaled);
            living.setHealth(Math.min(scaled, health.getValue()));
        }
        living.customName(Component.text(stack + "x " + plural(spawner.getSpawnedType()), NamedTextColor.RED));
        living.setCustomNameVisible(true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (var state : event.getChunk().getTileEntities()) {
            if (state instanceof CreatureSpawner spawner && readStack(spawner) > 0) {
                refreshHologram(spawner);
            }
        }
    }

    private ItemStack shopDisplay(EntityType type) {
        ItemStack item = createItem(type, 1);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                Component.text("Buy: " + MoneyFormat.formatCoins(price(type)), NamedTextColor.GRAY),
                Component.text("Right-click a matching spawner to stack it", NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    private EntityType typeOf(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (raw != null) {
            try {
                return EntityType.valueOf(raw);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (item.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof CreatureSpawner spawner) {
            return spawner.getSpawnedType();
        }
        return null;
    }

    private int stackOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 1;
        }
        Integer stack = item.getItemMeta().getPersistentDataContainer().get(stackKey, PersistentDataType.INTEGER);
        return stack == null || stack < 1 ? 1 : stack;
    }

    private int readStack(CreatureSpawner spawner) {
        Integer stack = spawner.getPersistentDataContainer().get(stackKey, PersistentDataType.INTEGER);
        return stack == null || stack < 1 ? 1 : stack;
    }

    private void writeStack(CreatureSpawner spawner, int stack) {
        spawner.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, Math.max(1, stack));
    }

    private void refreshHologram(CreatureSpawner spawner) {
        removeHologram(spawner);
        Location loc = spawner.getLocation().add(0.5, 1.15, 0.5);
        TextDisplay label = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        label.setBillboard(Display.Billboard.CENTER);
        label.setSeeThrough(false);
        label.setViewRange(3.0f);
        label.setPersistent(true);
        label.setInvulnerable(true);
        int stack = readStack(spawner);
        label.text(Component.text(stack + "x " + pretty(spawner.getSpawnedType()) + " Spawners", NamedTextColor.YELLOW));
        spawner.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, label.getUniqueId().toString());
        spawner.update();
    }

    private void removeHologram(TileState state) {
        String raw = state.getPersistentDataContainer().get(hologramKey, PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        try {
            var entity = plugin.getServer().getEntity(UUID.fromString(raw));
            if (entity != null) {
                entity.remove();
            }
        } catch (IllegalArgumentException ignored) {
            // Old label id can be ignored.
        }
    }

    private static String pretty(EntityType type) {
        String lower = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String plural(EntityType type) {
        return switch (type) {
            case ZOMBIE -> "Zombies";
            case SKELETON -> "Skeletons";
            case BLAZE -> "Blazes";
            default -> pretty(type) + "s";
        };
    }
}
