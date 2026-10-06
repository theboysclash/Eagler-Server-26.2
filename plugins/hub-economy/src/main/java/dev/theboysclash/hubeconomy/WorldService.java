package dev.theboysclash.hubeconomy;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

public final class WorldService {

    private final HubEconomyPlugin plugin;
    private final PluginConfig config;

    public WorldService(HubEconomyPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setupWorlds() {
        ensureHubWorld();
        ensureSurvivalWorld();
        ensureDefaultSpawns();
    }

    private void ensureHubWorld() {
        String name = config.hubWorldName();
        World world = plugin.getServer().getWorld(name);
        if (world == null) {
            WorldCreator creator = new WorldCreator(name);
            creator.type(WorldType.FLAT);
            creator.generator(new HubPlatformGenerator());
            creator.environment(World.Environment.NORMAL);
            world = creator.createWorld();
        }
        if (world != null) {
            world.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
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
    }

    private void ensureDefaultSpawns() {
        World hub = plugin.getServer().getWorld(config.hubWorldName());
        if (hub != null && !config.hasConfiguredSpawn("hub")) {
            Location spawn = new Location(hub, 0.5, 65.0, 0.5, 0f, 0f);
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
        player.setGameMode(GameMode.ADVENTURE);
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
