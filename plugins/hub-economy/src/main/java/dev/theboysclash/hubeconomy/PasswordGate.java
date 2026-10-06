package dev.theboysclash.hubeconomy;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Name-only joins must register with {@code /login <password> <password>}
 * and later sign in with {@code /login <password>}.
 */
public final class PasswordGate implements CommandExecutor {

    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 32;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;

    private final HubEconomyPlugin plugin;
    private final File file;
    private final Map<String, Account> accounts = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, Integer> failures = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public PasswordGate(HubEconomyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "passwords.yml");
        load();
    }

    public boolean isLoggedIn(Player player) {
        return loggedIn.contains(player.getUniqueId());
    }

    public void logout(Player player) {
        loggedIn.remove(player.getUniqueId());
        failures.remove(player.getUniqueId());
    }

    public Component prompt(Player player) {
        if (isLoggedIn(player)) {
            return Messages.info("Welcome. Right-click Survival to play.");
        }
        if (accounts.containsKey(key(player))) {
            return Messages.error("Log in with /login <password>");
        }
        return Messages.error("Register with /login <password> <password>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        if (isLoggedIn(player)) {
            player.sendMessage(Messages.info("You are already logged in."));
            return true;
        }
        String name = key(player);
        Account existing = accounts.get(name);
        if (existing == null) {
            register(player, name, args);
            return true;
        }
        signIn(player, existing, args);
        return true;
    }

    private void register(Player player, String name, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Messages.error("Register with /login <password> <password>"));
            return;
        }
        if (!args[0].equals(args[1])) {
            player.sendMessage(Messages.error("Those two passwords do not match."));
            return;
        }
        String password = args[0];
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            player.sendMessage(Messages.error("Password must be 4 to 32 characters."));
            return;
        }
        if (password.indexOf(' ') >= 0) {
            player.sendMessage(Messages.error("Password cannot contain spaces."));
            return;
        }
        accounts.put(name, Account.create(password, random));
        save();
        loggedIn.add(player.getUniqueId());
        player.sendMessage(Messages.success("Account created. You are logged in."));
    }

    private void signIn(Player player, Account account, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Messages.error("Log in with /login <password>"));
            return;
        }
        if (!account.matches(args[0])) {
            int count = failures.merge(player.getUniqueId(), 1, Integer::sum);
            if (count >= 5) {
                player.kick(Messages.error("Too many wrong passwords."));
                return;
            }
            player.sendMessage(Messages.error("Wrong password. Try " + (5 - count) + " more time(s)."));
            return;
        }
        failures.remove(player.getUniqueId());
        loggedIn.add(player.getUniqueId());
        player.sendMessage(Messages.success("Logged in."));
    }

    private void load() {
        accounts.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getConfigurationSection("accounts") == null) {
            return;
        }
        for (String name : yaml.getConfigurationSection("accounts").getKeys(false)) {
            String salt = yaml.getString("accounts." + name + ".salt");
            String hash = yaml.getString("accounts." + name + ".hash");
            if (salt == null || hash == null) {
                continue;
            }
            accounts.put(name.toLowerCase(Locale.ROOT), new Account(salt, hash));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Account> entry : accounts.entrySet()) {
            yaml.set("accounts." + entry.getKey() + ".salt", entry.getValue().salt);
            yaml.set("accounts." + entry.getKey() + ".hash", entry.getValue().hash);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save passwords.yml: " + e.getMessage());
        }
    }

    private static String key(Player player) {
        return player.getName().toLowerCase(Locale.ROOT);
    }

    private record Account(String salt, String hash) {
        static Account create(String password, SecureRandom random) {
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            byte[] hash = pbkdf2(password, salt);
            return new Account(Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(hash));
        }

        boolean matches(String password) {
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            byte[] expected = Base64.getDecoder().decode(hash);
            byte[] actual = pbkdf2(password, saltBytes);
            return Arrays.equals(expected, actual);
        }

        private static byte[] pbkdf2(String password, byte[] salt) {
            try {
                PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            } catch (Exception e) {
                throw new IllegalStateException("Could not hash password", e);
            }
        }
    }
}
