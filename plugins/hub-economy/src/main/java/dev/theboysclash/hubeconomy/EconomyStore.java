package dev.theboysclash.hubeconomy;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EconomyStore {

    private final HubEconomyPlugin plugin;
    private final File file;
    private final Map<UUID, Double> balances = new HashMap<>();

    public EconomyStore(HubEconomyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
    }

    public void load() {
        balances.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.getConfigurationSection("balances") == null) {
            return;
        }
        for (String key : config.getConfigurationSection("balances").getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                double value = config.getDouble("balances." + key);
                balances.put(id, value);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in balances.yml: " + key);
            }
        }
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            config.set("balances." + entry.getKey(), entry.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save balances.yml: " + e.getMessage());
        }
    }

    public double getBalance(UUID playerId) {
        return balances.getOrDefault(playerId, 0.0);
    }

    public void setBalance(UUID playerId, double amount) {
        balances.put(playerId, amount);
        save();
    }

    public void addBalance(UUID playerId, double amount) {
        setBalance(playerId, getBalance(playerId) + amount);
    }
}
