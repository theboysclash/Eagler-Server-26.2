package dev.theboysclash.hubeconomy;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Random;

public final class WorldService {

    private final HubEconomyPlugin plugin;
    private final PluginConfig config;
    private BukkitTask pendingSave;
    private BukkitTask repeatingSave;

    public WorldService(HubEconomyPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setupWorlds() {
        ensureHubWorld();
        ensureSurvivalWorld();
        ensureDefaultSpawns();
        saveWorlds();
        repeatingSave = plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveWorlds, 600L, 600L);
    }

    public void markDirty() {
        if (pendingSave != null) {
            return;
        }
        pendingSave = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingSave = null;
            saveWorlds();
        }, 40L);
    }

    public void saveWorlds() {
        for (World world : plugin.getServer().getWorlds()) {
            world.setAutoSave(true);
            world.save();
        }
    }

    public void shutdown() {
        if (pendingSave != null) {
            pendingSave.cancel();
            pendingSave = null;
        }
        if (repeatingSave != null) {
            repeatingSave.cancel();
            repeatingSave = null;
        }
        saveWorlds();
    }

    private void ensureHubWorld() {
        String name = config.hubWorldName();
        World world = plugin.getServer().getWorld(name);
        if (world == null) {
            File hubDir = new File(plugin.getServer().getWorldContainer(), name);
            boolean imported = new File(hubDir, "level.dat").isFile();
            WorldCreator creator = new WorldCreator(name);
            creator.environment(World.Environment.NORMAL);
            if (!imported) {
                creator.type(WorldType.FLAT);
                creator.generator(new HubPlatformGenerator());
            } else {
                plugin.getLogger().info("Loading saved hub world at " + hubDir.getAbsolutePath());
            }
            world = creator.createWorld();
        }
        if (world != null) {
            world.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
            world.setAutoSave(true);
            world.save();
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        }
    }

    private void ensureSurvivalWorld() {
        String name = config.survivalWorldName();
        if (plugin.getServer().getWorld(name) == null) {
            WorldCreator creator = new WorldCreator(name);
            creator.environment(World.Environment.NORMAL);
            creator.createWorld();
        }
        ensureDimension(name + "_nether", World.Environment.NETHER);
        ensureDimension(name + "_the_end", World.Environment.THE_END);
    }

    private void ensureDimension(String name, World.Environment environment) {
        if (plugin.getServer().getWorld(name) != null) {
            return;
        }
        WorldCreator creator = new WorldCreator(name);
        creator.environment(environment);
        World world = creator.createWorld();
        if (world != null) {
            world.setAutoSave(true);
        }
    }

    public World rtpWorld(World.Environment environment) {
        String base = config.survivalWorldName();
        String name = switch (environment) {
            case NETHER -> base + "_nether";
            case THE_END -> base + "_the_end";
            default -> base;
        };
        World world = plugin.getServer().getWorld(name);
        if (world == null) {
            ensureDimension(name, environment == World.Environment.NORMAL ? World.Environment.NORMAL : environment);
            world = plugin.getServer().getWorld(name);
        }
        return world;
    }

    /**
     * Picks a solid floor with two air blocks above it. Returns null if none is found.
     */
    public Location findRandomSafe(World world, int radius) {
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        boolean nether = world.getEnvironment() == World.Environment.NETHER;
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = random.nextInt(radius * 2 + 1) - radius;
            int z = random.nextInt(radius * 2 + 1) - radius;
            world.getChunkAt(x >> 4, z >> 4).load(true);
            int start = nether ? 110 : Math.min(world.getHighestBlockYAt(x, z), world.getMaxHeight() - 3);
            int stop = nether ? 8 : world.getMinHeight() + 1;
            for (int y = start; y > stop; y--) {
                org.bukkit.block.Block floor = world.getBlockAt(x, y, z);
                org.bukkit.block.Block feet = world.getBlockAt(x, y + 1, z);
                org.bukkit.block.Block head = world.getBlockAt(x, y + 2, z);
                if (!isSafeFloor(floor.getType()) || !feet.getType().isAir() || !head.getType().isAir()) {
                    continue;
                }
                return new Location(world, x + 0.5, y + 1.0, z + 0.5);
            }
        }
        return null;
    }

    private static boolean isSafeFloor(org.bukkit.Material type) {
        if (!type.isSolid() || type.isAir()) {
            return false;
        }
        String name = type.name();
        return !name.contains("LAVA")
                && !name.contains("FIRE")
                && !name.contains("CACTUS")
                && !name.contains("MAGMA")
                && !name.contains("CAMPFIRE")
                && !name.contains("BEDROCK");
    }

    private void ensureDefaultSpawns() {
        World hub = plugin.getServer().getWorld(config.hubWorldName());
        if (hub != null && !config.hasConfiguredSpawn("hub")) {
            File hubDir = new File(plugin.getServer().getWorldContainer(), config.hubWorldName());
            Location spawn;
            if (new File(hubDir, "level.dat").isFile()) {
                spawn = hub.getSpawnLocation().clone();
                spawn.setY(Math.max(spawn.getY(), hub.getHighestBlockYAt(spawn) + 1));
            } else {
                spawn = new Location(hub, 0.5, 65.0, 0.5, 0f, 0f);
            }
            config.setHubSpawn(spawn);
        }
        World survival = plugin.getServer().getWorld(config.survivalWorldName());
        if (survival != null && !config.hasConfiguredSpawn("survival")) {
            Location spawn = survival.getSpawnLocation().clone();
            spawn.setY(Math.max(spawn.getBlockY(), survival.getHighestBlockYAt(spawn) + 1));
            config.setSurvivalSpawn(spawn);
        }
    }

    public World hubWorld() {
        return plugin.getServer().getWorld(config.hubWorldName());
    }

    public World survivalWorld() {
        return plugin.getServer().getWorld(config.survivalWorldName());
    }

    public void sendToHub(Player player) {
        Location spawn = config.getHubSpawn();
        if (spawn == null || spawn.getWorld() == null) {
            player.sendMessage(Messages.error("Hub spawn is not set."));
            return;
        }
        player.teleport(spawn);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0f);
    }

    public void sendToSurvival(Player player) {
        Location spawn = config.getSurvivalSpawn();
        if (spawn == null || spawn.getWorld() == null) {
            player.sendMessage(Messages.error("Survival spawn is not set."));
            return;
        }
        player.teleport(spawn);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0f);
    }

    public static final class HubPlatformGenerator extends ChunkGenerator {

        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }

        @Override
        public ChunkGenerator.ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ,
                                                          ChunkGenerator.BiomeGrid biome) {
            ChunkGenerator.ChunkData chunk = createChunkData(world);
            int originX = chunkX * 16;
            int originZ = chunkZ * 16;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int blockX = originX + x;
                    int blockZ = originZ + z;
                    if (blockX >= -16 && blockX < 16 && blockZ >= -16 && blockZ < 16) {
                        chunk.setBlock(x, 64, z, org.bukkit.Material.STONE);
                    }
                }
            }
            return chunk;
        }
    }
}
