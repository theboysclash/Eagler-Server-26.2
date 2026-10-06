package dev.theboysclash.hubeconomy;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class WorthCatalog {

    private static final int PRICES_VERSION = 2;

    private final HubEconomyPlugin plugin;
    private final File file;
    private final Map<Material, Long> prices = new EnumMap<>(Material.class);

    public WorthCatalog(HubEconomyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "values.yml");
    }

    public void loadOrCreate() {
        plugin.getDataFolder().mkdirs();
        if (!file.exists() || needsRewrite()) {
            writeDefaults();
        }
        reloadFromDisk();
    }

    private boolean needsRewrite() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getInt("prices-version", 0) < PRICES_VERSION;
    }

    public void reloadFromDisk() {
        prices.clear();
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.getConfigurationSection("prices") != null) {
            for (String key : config.getConfigurationSection("prices").getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    continue;
                }
                long price = config.getLong("prices." + key, config.getInt("prices." + key, 1));
                if (price < 1) {
                    price = 1;
                }
                prices.put(material, price);
            }
        }
        for (Material material : Material.values()) {
            if (!material.isItem() || material == Material.AIR) {
                continue;
            }
            prices.putIfAbsent(material, defaultPriceFor(material));
        }
    }

    private void writeDefaults() {
        FileConfiguration config = new YamlConfiguration();
        config.set("prices-version", PRICES_VERSION);
        for (Material material : Material.values()) {
            if (!material.isItem() || material == Material.AIR) {
                continue;
            }
            config.set("prices." + material.name(), defaultPriceFor(material));
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not write values.yml: " + e.getMessage());
        }
    }

    public long unitPrice(Material material) {
        if (material == null || !material.isItem() || material == Material.AIR) {
            return 0L;
        }
        return prices.getOrDefault(material, 1L);
    }

    public long buyPrice(Material material) {
        long sell = unitPrice(material);
        if (sell <= 0) {
            return 2L;
        }
        return Math.max(2L, sell * 3L);
    }

    public long stackUnitWorthWithEnchants(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0L;
        }
        long unit = unitPrice(stack.getType());
        double withEnchant = unit * enchantMultiplier(stack);
        long rounded = Math.round(withEnchant);
        return Math.max(unit, rounded);
    }

    public long stackTotalWorth(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0L;
        }
        return stackUnitWorthWithEnchants(stack) * stack.getAmount();
    }

    public static double enchantMultiplier(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 1.0;
        }
        int totalLevels = 0;
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            totalLevels += entry.getValue();
        }
        double bonus = Math.min(totalLevels * 0.05, 0.50);
        return 1.0 + bonus;
    }

    static long defaultPriceFor(Material material) {
        String name = material.name();
        Long explicit = explicitPrices.get(name);
        if (explicit != null) {
            return explicit;
        }
        if (isBulk(material)) {
            return 1L;
        }
        if (isBuilding(material)) {
            return buildingPrice(material);
        }
        Long farming = farmingPrice(material);
        if (farming != null) {
            return farming;
        }
        Long mob = mobDropPrice(material);
        if (mob != null) {
            return mob;
        }
        Long gear = gearPrice(material);
        if (gear != null) {
            return gear;
        }
        return 1L;
    }

    private static boolean isBulk(Material material) {
        String n = material.name();
        if (n.equals("DIRT") || n.equals("COARSE_DIRT") || n.equals("ROOTED_DIRT")
                || n.equals("MUD") || n.equals("COBBLESTONE") || n.equals("NETHERRACK")
                || n.equals("SAND") || n.equals("RED_SAND") || n.equals("GRAVEL")
                || n.equals("STONE") || n.equals("TUFF") || n.equals("ANDESITE")
                || n.equals("DIORITE") || n.equals("GRANITE") || n.equals("CALCITE")
                || n.equals("DRIPSTONE_BLOCK") || n.equals("MOSS_BLOCK")) {
            return true;
        }
        if (n.contains("DEEPSLATE") && !n.contains("ORE") && !n.contains("INGOT")
                && !n.contains("BRICK") && !n.contains("TILE")) {
            return true;
        }
        return n.equals("DEEPSLATE") || n.equals("COBBLED_DEEPSLATE");
    }

    private static long buildingPrice(Material material) {
        String n = material.name();
        if (n.contains("CONCRETE") && !n.contains("POWDER")) {
            return 6L;
        }
        if (n.contains("TERRACOTTA")) {
            return 5L;
        }
        if (n.contains("WOOL")) {
            return 4L;
        }
        if (n.contains("GLASS")) {
            return 3L;
        }
        if (n.contains("PLANKS")) {
            return 3L;
        }
        if (n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")) {
            return 4L;
        }
        if (n.equals("SMOOTH_STONE") || n.equals("BRICKS") || n.equals("STONE_BRICKS")) {
            return 3L;
        }
        return 2L;
    }

    private static boolean isBuilding(Material material) {
        String n = material.name();
        if (n.equals("SMOOTH_STONE") || n.equals("COBBLED_DEEPSLATE")) {
            return true;
        }
        if (n.contains("PLANKS") || n.contains("GLASS") || n.contains("TERRACOTTA")
                || n.contains("WOOL") || n.contains("CONCRETE")) {
            return true;
        }
        return n.equals("BRICKS") || n.equals("STONE_BRICKS")
                || n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_HYPHAE");
    }

    private static Long farmingPrice(Material material) {
        String n = material.name();
        if (n.endsWith("_LEAVES") || n.endsWith("_SAPLING")) {
            return 2L;
        }
        if (n.contains("SEEDS") || n.equals("WHEAT") || n.equals("BEETROOT") || n.equals("CARROT")
                || n.equals("POTATO") || n.equals("MELON_SLICE") || n.equals("PUMPKIN")
                || n.equals("SWEET_BERRIES") || n.equals("GLOW_BERRIES") || n.equals("COCOA_BEANS")
                || n.equals("SUGAR_CANE") || n.equals("BAMBOO") || n.equals("KELP") || n.equals("CACTUS")
                || n.equals("NETHER_WART") || n.equals("CHORUS_FRUIT") || n.equals("BONE_MEAL")) {
            return switch (n) {
                case "WHEAT", "CARROT", "POTATO", "BEETROOT" -> 3L;
                case "MELON_SLICE", "SWEET_BERRIES", "GLOW_BERRIES" -> 4L;
                case "PUMPKIN", "CHORUS_FRUIT" -> 6L;
                default -> 2L;
            };
        }
        if (n.equals("BREAD") || n.equals("COOKED_BEEF") || n.equals("COOKED_PORKCHOP")
                || n.equals("COOKED_CHICKEN") || n.equals("COOKED_MUTTON") || n.equals("COOKED_RABBIT")
                || n.equals("COOKED_COD") || n.equals("COOKED_SALMON") || n.equals("BAKED_POTATO")
                || n.equals("MUSHROOM_STEW") || n.equals("RABBIT_STEW") || n.equals("BEETROOT_SOUP")) {
            return 8L;
        }
        if (n.equals("APPLE") || n.equals("GOLDEN_CARROT")) {
            return 6L;
        }
        if (n.equals("GOLDEN_APPLE")) {
            return 12L;
        }
        return null;
    }

    private static Long mobDropPrice(Material material) {
        return switch (material) {
            case BONE, STRING, ROTTEN_FLESH, SPIDER_EYE, GUNPOWDER -> 4L;
            case SLIME_BALL, MAGMA_CREAM, PHANTOM_MEMBRANE -> 8L;
            case BLAZE_ROD, GHAST_TEAR, SHULKER_SHELL -> 20L;
            case ENDER_PEARL -> 40L;
            default -> null;
        };
    }

    private static Long gearPrice(Material material) {
        String n = material.name();
        ToolTier tier = toolTier(n);
        if (tier == null) {
            return null;
        }
        if (n.contains("HELMET") || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS")) {
            return tier.toolPrice * 4L;
        }
        if (n.contains("SWORD") || n.contains("PICKAXE") || n.contains("AXE")
                || n.contains("SHOVEL") || n.contains("HOE")) {
            return tier.toolPrice;
        }
        if (n.contains("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("MACE")) {
            return tier == ToolTier.WOOD ? 8L : tier.toolPrice;
        }
        if (n.equals("SHIELD") || n.equals("ARROW")) {
            return 16L;
        }
        return null;
    }

    private static ToolTier toolTier(String name) {
        if (name.startsWith("WOODEN_") || name.startsWith("WOOD_")) {
            return ToolTier.WOOD;
        }
        if (name.startsWith("STONE_")) {
            return ToolTier.STONE;
        }
        if (name.startsWith("IRON_")) {
            return ToolTier.IRON;
        }
        if (name.startsWith("GOLDEN_") || name.startsWith("GOLD_")) {
            return ToolTier.GOLD;
        }
        if (name.startsWith("DIAMOND_")) {
            return ToolTier.DIAMOND;
        }
        if (name.startsWith("NETHERITE_")) {
            return ToolTier.NETHERITE;
        }
        return null;
    }

    private enum ToolTier {
        WOOD(8L),
        STONE(16L),
        IRON(64L),
        GOLD(80L),
        DIAMOND(500L),
        NETHERITE(2400L);

        final long toolPrice;

        ToolTier(long toolPrice) {
            this.toolPrice = toolPrice;
        }
    }

    private static final Map<String, Long> explicitPrices = buildExplicitPrices();

    private static Map<String, Long> buildExplicitPrices() {
        Map<String, Long> map = new HashMap<>();
        map.put("COAL", 8L);
        map.put("CHARCOAL", 6L);
        map.put("COPPER_INGOT", 8L);
        map.put("RAW_COPPER", 5L);
        map.put("IRON_INGOT", 24L);
        map.put("RAW_IRON", 14L);
        map.put("GOLD_INGOT", 48L);
        map.put("RAW_GOLD", 28L);
        map.put("REDSTONE", 12L);
        map.put("LAPIS_LAZULI", 14L);
        map.put("DIAMOND", 220L);
        map.put("EMERALD", 180L);
        map.put("ANCIENT_DEBRIS", 550L);
        map.put("NETHERITE_INGOT", 2200L);
        map.put("NETHERITE_SCRAP", 500L);
        map.put("ELYTRA", 8000L);
        map.put("DRAGON_EGG", 15000L);
        map.put("BEACON", 4000L);
        map.put("NETHER_STAR", 5000L);
        map.put("ENCHANTED_GOLDEN_APPLE", 1500L);
        map.put("TOTEM_OF_UNDYING", 1200L);

        map.put("COAL_ORE", 8L);
        map.put("DEEPSLATE_COAL_ORE", 10L);
        map.put("COPPER_ORE", 6L);
        map.put("DEEPSLATE_COPPER_ORE", 8L);
        map.put("IRON_ORE", 18L);
        map.put("DEEPSLATE_IRON_ORE", 22L);
        map.put("GOLD_ORE", 36L);
        map.put("DEEPSLATE_GOLD_ORE", 44L);
        map.put("NETHER_GOLD_ORE", 30L);
        map.put("REDSTONE_ORE", 14L);
        map.put("DEEPSLATE_REDSTONE_ORE", 18L);
        map.put("LAPIS_ORE", 16L);
        map.put("DEEPSLATE_LAPIS_ORE", 20L);
        map.put("DIAMOND_ORE", 160L);
        map.put("DEEPSLATE_DIAMOND_ORE", 200L);
        map.put("EMERALD_ORE", 130L);
        map.put("DEEPSLATE_EMERALD_ORE", 160L);
        map.put("NETHER_QUARTZ_ORE", 12L);

        for (Material material : Material.values()) {
            String n = material.name();
            if ((n.endsWith("_ORE") || n.contains("DEEPSLATE") && n.contains("ORE"))
                    && !map.containsKey(n)) {
                map.putIfAbsent(n, orePriceFallback(n));
            }
        }
        return map;
    }

    private static long orePriceFallback(String oreName) {
        String upper = oreName.toUpperCase(Locale.ROOT);
        if (upper.contains("DIAMOND")) {
            return upper.contains("DEEPSLATE") ? 200L : 160L;
        }
        if (upper.contains("EMERALD")) {
            return upper.contains("DEEPSLATE") ? 160L : 130L;
        }
        if (upper.contains("ANCIENT_DEBRIS")) {
            return 550L;
        }
        if (upper.contains("NETHER_GOLD")) {
            return 30L;
        }
        if (upper.contains("GOLD")) {
            return upper.contains("DEEPSLATE") ? 44L : 36L;
        }
        if (upper.contains("IRON")) {
            return upper.contains("DEEPSLATE") ? 22L : 18L;
        }
        if (upper.contains("COPPER")) {
            return upper.contains("DEEPSLATE") ? 8L : 6L;
        }
        if (upper.contains("COAL")) {
            return upper.contains("DEEPSLATE") ? 10L : 8L;
        }
        if (upper.contains("LAPIS")) {
            return upper.contains("DEEPSLATE") ? 20L : 16L;
        }
        if (upper.contains("REDSTONE")) {
            return upper.contains("DEEPSLATE") ? 18L : 14L;
        }
        if (upper.contains("NETHER_QUARTZ") || upper.contains("QUARTZ_ORE")) {
            return 12L;
        }
        return 8L;
    }
}
