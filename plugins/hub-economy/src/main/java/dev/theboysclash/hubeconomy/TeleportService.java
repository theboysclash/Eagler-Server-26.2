package dev.theboysclash.hubeconomy;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TeleportService implements CommandExecutor, TabCompleter, Listener {

    private static final long COOLDOWN_MS = 15_000L;
    private static final long REQUEST_MS = 60_000L;

    private final Map<UUID, Map<UUID, Long>> incoming = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    @EventHandler
    public void onVanillaTeleport(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (!message.regionMatches(true, 0, "/tp ", 0, 4)) {
            return;
        }
        String[] args = message.substring(4).trim().split(" ");
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
            return;
        }
        event.setCancelled(true);
        request(event.getPlayer(), args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        if (!player.hasPermission("hubeconomy.use")) {
            player.sendMessage(Messages.error("No permission."));
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("tp")) {
            return request(player, args);
        }
        if (name.equals("tpa")) {
            return accept(player, args);
        }
        return false;
    }

    private boolean request(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Messages.error("Usage: /tp <player>"));
            return true;
        }
        long now = System.currentTimeMillis();
        long readyAt = cooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < readyAt) {
            long seconds = Math.max(1L, (readyAt - now + 999L) / 1000L);
            player.sendMessage(Messages.error("Wait " + seconds + " seconds before another teleport request."));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Messages.error("That player is not online."));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Messages.error("You cannot teleport to yourself."));
            return true;
        }
        incoming.computeIfAbsent(target.getUniqueId(), ignored -> new HashMap<>())
                .put(player.getUniqueId(), now + REQUEST_MS);
        cooldownUntil.put(player.getUniqueId(), now + COOLDOWN_MS);
        player.sendMessage(Messages.success("Teleport request sent to " + target.getName() + "."));
        Component notice = Messages.info(player.getName() + " wants to teleport to you. Type /tpa " + player.getName() + " to accept.");
        target.sendMessage(notice);
        target.sendActionBar(notice);
        return true;
    }

    private boolean accept(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Messages.error("Usage: /tpa <player>"));
            return true;
        }
        Player requester = Bukkit.getPlayerExact(args[0]);
        if (requester == null) {
            player.sendMessage(Messages.error("That player is not online."));
            return true;
        }
        Map<UUID, Long> requests = incoming.get(player.getUniqueId());
        Long expires = requests == null ? null : requests.get(requester.getUniqueId());
        if (expires == null || expires < System.currentTimeMillis()) {
            player.sendMessage(Messages.error("You do not have a teleport request from " + requester.getName() + "."));
            return true;
        }
        requests.remove(requester.getUniqueId());
        requester.teleport(player.getLocation());
        requester.setFallDistance(0f);
        requester.sendMessage(Messages.success("Teleported to " + player.getName() + "."));
        player.sendMessage(Messages.success("Accepted " + requester.getName() + "'s teleport request."));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
