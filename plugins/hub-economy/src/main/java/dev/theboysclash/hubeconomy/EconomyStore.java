package dev.theboysclash.hubeconomy;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class EconomyStore {

    private final HubEconomyPlugin plugin;
    private final File file;
    private final Map<UUID, Long> balances = new HashMap<>();
    private Consumer<UUID> balanceChangeListener;

    public EconomyStore(HubEconomyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
    }

    public void setBalanceChangeListener(Consumer<UUID> balanceChangeListener) {
        this.balanceChangeListener = balanceChangeListener;
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
                double raw = config.getDouble("balances." + key);
                long coins = Math.round(raw);
                balances.put(id, coins);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in balances.yml: " + key);
            }
        }
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            config.set("balances." + entry.getKey(), entry.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save balances.yml: " + e.getMessage());
        }
    }

    public long getBalance(UUID playerId) {
        return balances.getOrDefault(playerId, 0L);
    }

    public void setBalance(UUID playerId, long amount) {
        balances.put(playerId, amount);
        save();
        notifyChange(playerId);
    }

    public void addBalance(UUID playerId, long amount) {
        if (amount == 0) {
            return;
        }
        setBalance(playerId, getBalance(playerId) + amount);
    }

    /**
     * Removes coins if the player has enough. Returns true when the balance was reduced.
     */
    public boolean tryWithdraw(UUID playerId, long amount) {
        if (amount <= 0) {
            return true;
        }
        long current = getBalance(playerId);
        if (current < amount) {
            return false;
        }
        balances.put(playerId, current - amount);
        save();
        notifyChange(playerId);
        return true;
    }

    public void deposit(UUID playerId, long amount) {
        addBalance(playerId, amount);
    }

    private void notifyChange(UUID playerId) {
        if (balanceChangeListener == null) {
            return;
        }
        Consumer<UUID> listener = balanceChangeListener;
        Bukkit.getScheduler().runTask(plugin, () -> listener.accept(playerId));
    }
}
