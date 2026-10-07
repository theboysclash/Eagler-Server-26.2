package dev.theboysclash.hubeconomy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public final class PluginConfig {

    private final HubEconomyPlugin plugin;

    public PluginConfig(HubEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureDefaults() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        if (!config.isSet("hub.world")) {
            config.set("hub.world", "hub");
        }
        if (!config.isSet("survival.world")) {
            config.set("survival.world", "survival");
        }
        plugin.saveConfig();
    }

    public String hubWorldName() {
        return plugin.getConfig().getString("hub.world", "hub");
    }

    public String survivalWorldName() {
        return plugin.getConfig().getString("survival.world", "survival");
    }

    public boolean hasConfiguredSpawn(String prefix) {
        return plugin.getConfig().isSet(prefix + ".spawn.x");
    }

    public Location getHubSpawn() {
        return readLocation("hub", hubWorldName());
    }

    public Location getSurvivalSpawn() {
        return readLocation("survival", survivalWorldName());
    }

    public void setHubSpawn(Location location) {
        writeLocation("hub", location);
    }

    public void setSurvivalSpawn(Location location) {
        writeLocation("survival", location);
    }

    public boolean hasNpcLocation(String id) {
        return plugin.getConfig().isSet("npcs." + id + ".x");
    }

    public Location getNpcLocation(String id) {
        if (!hasNpcLocation(id)) {
            return null;
        }
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("npcs." + id + ".world", hubWorldName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                config.getDouble("npcs." + id + ".x"),
                config.getDouble("npcs." + id + ".y"),
                config.getDouble("npcs." + id + ".z"),
                (float) config.getDouble("npcs." + id + ".yaw"),
                (float) config.getDouble("npcs." + id + ".pitch"));
    }

    public void setNpcLocation(String id, Location location) {
        FileConfiguration config = plugin.getConfig();
        config.set("npcs." + id + ".removed", false);
        config.set("npcs." + id + ".world", location.getWorld().getName());
        config.set("npcs." + id + ".x", location.getX());
        config.set("npcs." + id + ".y", location.getY());
        config.set("npcs." + id + ".z", location.getZ());
        config.set("npcs." + id + ".yaw", location.getYaw());
        config.set("npcs." + id + ".pitch", location.getPitch());
        plugin.saveConfig();
    }

    public boolean isNpcRemoved(String id) {
        return plugin.getConfig().getBoolean("npcs." + id + ".removed", false);
    }

    public void removeNpc(String id) {
        FileConfiguration config = plugin.getConfig();
        config.set("npcs." + id + ".removed", true);
        config.set("npcs." + id + ".world", null);
        config.set("npcs." + id + ".x", null);
        config.set("npcs." + id + ".y", null);
        config.set("npcs." + id + ".z", null);
        config.set("npcs." + id + ".yaw", null);
        config.set("npcs." + id + ".pitch", null);
        plugin.saveConfig();
    }

    private Location readLocation(String prefix, String worldName) {
        World world = Bukkit.getWorld(worldName);
        FileConfiguration config = plugin.getConfig();
        double x = config.getDouble(prefix + ".spawn.x", 0.5);
        double y = config.getDouble(prefix + ".spawn.y", 65.0);
        double z = config.getDouble(prefix + ".spawn.z", 0.5);
        float yaw = (float) config.getDouble(prefix + ".spawn.yaw", 0.0);
        float pitch = (float) config.getDouble(prefix + ".spawn.pitch", 0.0);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void writeLocation(String prefix, Location location) {
        FileConfiguration config = plugin.getConfig();
        config.set(prefix + ".world", location.getWorld().getName());
        config.set(prefix + ".spawn.x", location.getX());
        config.set(prefix + ".spawn.y", location.getY());
        config.set(prefix + ".spawn.z", location.getZ());
        config.set(prefix + ".spawn.yaw", location.getYaw());
        config.set(prefix + ".spawn.pitch", location.getPitch());
        plugin.saveConfig();
    }
}
