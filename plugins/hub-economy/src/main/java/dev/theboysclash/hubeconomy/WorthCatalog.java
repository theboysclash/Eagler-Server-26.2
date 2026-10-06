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

    private final HubEconomyPlugin plugin;
    private final File file;
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    public WorthCatalog(HubEconomyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "values.yml");
    }

    public void loadOrCreate() {
        plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            writeDefaults();
        }
        reloadFromDisk();
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
                prices.put(material, config.getDouble("prices." + key));
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

    public double unitPrice(Material material) {
        if (material == null || !material.isItem() || material == Material.AIR) {
            return 0.0;
        }
        return prices.getOrDefault(material, 1.0);
    }

    public double stackUnitWorthWithEnchants(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0.0;
        }
        double unit = unitPrice(stack.getType());
        return unit * enchantMultiplier(stack);
    }

    public double stackTotalWorth(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0.0;
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

    static double defaultPriceFor(Material material) {
        String name = material.name();
        Double explicit = explicitPrices.get(name);
        if (explicit != null) {
            return explicit;
        }
        if (isBulk(material)) {
            return 0.25;
        }
        if (isBuilding(material)) {
            return 1.0;
        }
        Double farming = farmingPrice(material);
        if (farming != null) {
            return farming;
        }
        Double mob = mobDropPrice(material);
        if (mob != null) {
            return mob;
        }
        Double gear = gearPrice(material);
        if (gear != null) {
            return gear;
        }
        return 1.0;
    }

    private static boolean isBulk(Material material) {
        String n = material.name();
        if (n.equals("DIRT") || n.equals("COBBLESTONE") || n.equals("NETHERRACK")
                || n.equals("SAND") || n.equals("RED_SAND") || n.equals("GRAVEL")) {
            return true;
        }
        return n.contains("DEEPSLATE") && !n.contains("ORE") && !n.contains("INGOT");
    }

    private static boolean isBuilding(Material material) {
        String n = material.name();
        if (n.equals("STONE") || n.equals("SMOOTH_STONE") || n.equals("COBBLED_DEEPSLATE")) {
            return true;
        }
        if (n.contains("PLANKS") || n.contains("GLASS") || n.contains("TERRACOTTA")
                || n.contains("WOOL") || n.contains("CONCRETE")) {
            return true;
        }
        return n.equals("BRICKS") || n.equals("STONE_BRICKS");
    }

    private static Double farmingPrice(Material material) {
        String n = material.name();
        if (n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")) {
            return 4.0;
        }
        if (n.endsWith("_LEAVES") || n.endsWith("_SAPLING")) {
            return 2.0;
        }
        if (n.contains("SEEDS") || n.equals("WHEAT") || n.equals("BEETROOT") || n.equals("CARROT")
                || n.equals("POTATO") || n.equals("MELON_SLICE") || n.equals("PUMPKIN")
                || n.equals("SWEET_BERRIES") || n.equals("GLOW_BERRIES") || n.equals("COCOA_BEANS")
                || n.equals("SUGAR_CANE") || n.equals("BAMBOO") || n.equals("KELP") || n.equals("CACTUS")
                || n.equals("NETHER_WART") || n.equals("CHORUS_FRUIT")) {
            return switch (n) {
                case "WHEAT", "CARROT", "POTATO", "BEETROOT" -> 3.0;
                case "MELON_SLICE", "SWEET_BERRIES", "GLOW_BERRIES" -> 4.0;
                case "PUMPKIN", "CHORUS_FRUIT" -> 6.0;
                default -> 2.0;
            };
        }
        if (n.equals("BREAD") || n.equals("COOKED_BEEF") || n.equals("COOKED_PORKCHOP")
                || n.equals("COOKED_CHICKEN") || n.equals("COOKED_MUTTON") || n.equals("COOKED_RABBIT")
                || n.equals("COOKED_COD") || n.equals("COOKED_SALMON") || n.equals("BAKED_POTATO")
                || n.equals("MUSHROOM_STEW") || n.equals("RABBIT_STEW") || n.equals("BEETROOT_SOUP")) {
            return 8.0;
        }
        if (n.equals("APPLE") || n.equals("GOLDEN_CARROT")) {
            return 6.0;
        }
        return null;
    }

    private static Double mobDropPrice(Material material) {
        return switch (material) {
            case BONE, STRING, ROTTEN_FLESH, SPIDER_EYE, GUNPOWDER -> 4.0;
            case SLIME_BALL, MAGMA_CREAM, PHANTOM_MEMBRANE -> 8.0;
            case BLAZE_ROD, GHAST_TEAR, SHULKER_SHELL -> 20.0;
            case ENDER_PEARL -> 40.0;
            default -> null;
        };
    }

    private static Double gearPrice(Material material) {
        String n = material.name();
        ToolTier tier = toolTier(n);
        if (tier == null) {
            return null;
        }
        if (n.contains("HELMET") || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS")) {
            return tier.toolPrice * 4.0;
        }
        if (n.contains("SWORD") || n.contains("PICKAXE") || n.contains("AXE")
                || n.contains("SHOVEL") || n.contains("HOE")) {
            return tier.toolPrice;
        }
        if (n.contains("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("MACE")) {
            return tier == ToolTier.WOOD ? 8.0 : tier.toolPrice;
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
        WOOD(8.0),
        STONE(16.0),
        IRON(48.0),
        GOLD(64.0),
        DIAMOND(400.0),
        NETHERITE(2000.0);

        final double toolPrice;

        ToolTier(double toolPrice) {
            this.toolPrice = toolPrice;
        }
    }

    private static final Map<String, Double> explicitPrices = buildExplicitPrices();

    private static Map<String, Double> buildExplicitPrices() {
        Map<String, Double> map = new HashMap<>();
        map.put("COAL", 6.0);
        map.put("CHARCOAL", 5.0);
        map.put("COPPER_INGOT", 4.0);
        map.put("RAW_COPPER", 3.0);
        map.put("IRON_INGOT", 12.0);
        map.put("RAW_IRON", 8.0);
        map.put("GOLD_INGOT", 24.0);
        map.put("RAW_GOLD", 16.0);
        map.put("REDSTONE", 8.0);
        map.put("LAPIS_LAZULI", 10.0);
        map.put("DIAMOND", 120.0);
        map.put("EMERALD", 90.0);
        map.put("ANCIENT_DEBRIS", 400.0);
        map.put("NETHERITE_INGOT", 1500.0);
        map.put("NETHERITE_SCRAP", 350.0);
        map.put("ELYTRA", 5000.0);
        map.put("DRAGON_EGG", 10000.0);
        map.put("BEACON", 2500.0);
        map.put("NETHER_STAR", 3000.0);
        map.put("ENCHANTED_GOLDEN_APPLE", 1000.0);
        map.put("TOTEM_OF_UNDYING", 750.0);
        for (Material material : Material.values()) {
            String n = material.name();
            if (n.endsWith("_ORE") || n.endsWith("_DEEPSLATE_ORE")) {
                map.putIfAbsent(n, orePrice(n));
            }
        }
        return map;
    }

    private static double orePrice(String oreName) {
        String upper = oreName.toUpperCase(Locale.ROOT);
        if (upper.contains("DIAMOND")) {
            return 80.0;
        }
        if (upper.contains("EMERALD")) {
            return 60.0;
        }
        if (upper.contains("ANCIENT_DEBRIS")) {
            return 350.0;
        }
        if (upper.contains("GOLD")) {
            return 18.0;
        }
        if (upper.contains("IRON")) {
            return 10.0;
        }
        if (upper.contains("COPPER")) {
            return 3.0;
        }
        if (upper.contains("COAL")) {
            return 4.0;
        }
        if (upper.contains("LAPIS")) {
            return 8.0;
        }
        if (upper.contains("REDSTONE")) {
            return 6.0;
        }
        if (upper.contains("NETHER_QUARTZ") || upper.contains("QUARTZ")) {
            return 6.0;
        }
        return 5.0;
    }
}
