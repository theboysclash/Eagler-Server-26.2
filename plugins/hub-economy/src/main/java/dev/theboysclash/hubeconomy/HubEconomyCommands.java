package dev.theboysclash.hubeconomy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class HubEconomyCommands implements CommandExecutor, TabCompleter {

    private final HubEconomyPlugin plugin;
    private final PluginConfig config;
    private final WorldService worlds;
    private final HubNpc hubNpc;
    private final EconomyStore economy;
    private final WorthCatalog worth;
    private final SellMenuService sellMenu;
    private final ShopMenu shopMenu;
    private final AuctionHouse auctionHouse;

    public HubEconomyCommands(
            HubEconomyPlugin plugin,
            PluginConfig config,
            WorldService worlds,
            HubNpc hubNpc,
            EconomyStore economy,
            WorthCatalog worth,
            SellMenuService sellMenu,
            ShopMenu shopMenu,
            AuctionHouse auctionHouse) {
        this.plugin = plugin;
        this.config = config;
        this.worlds = worlds;
        this.hubNpc = hubNpc;
        this.economy = economy;
        this.worth = worth;
        this.sellMenu = sellMenu;
        this.shopMenu = shopMenu;
        this.auctionHouse = auctionHouse;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "sell" -> handleSell(sender);
            case "bal", "balance" -> handleBalance(sender, args);
            case "worth" -> handleWorth(sender);
            case "hub" -> handleHub(sender);
            case "survival" -> handleSurvival(sender);
            case "sethub" -> handleSetHub(sender);
            case "setsurvival" -> handleSetSurvival(sender);
            case "hubeconomy" -> handleAdmin(sender, args);
            case "shop" -> handleShop(sender);
            case "ah" -> handleAuction(sender, args);
            default -> false;
        };
    }

    private boolean handleSell(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        if (!player.hasPermission("hubeconomy.use")) {
            player.sendMessage(Messages.error("No permission."));
            return true;
        }
        sellMenu.open(player);
        return true;
    }

    private boolean handleBalance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hubeconomy.use")) {
            sender.sendMessage(Messages.error("No permission."));
            return true;
        }
        if (args.length >= 1) {
            if (!sender.hasPermission("hubeconomy.admin")) {
                sender.sendMessage(Messages.error("No permission."));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Messages.error("Player not found."));
                return true;
            }
            sender.sendMessage(Messages.info(
                    target.getName() + ": " + MoneyFormat.format(economy.getBalance(target.getUniqueId()))));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        sender.sendMessage(Messages.info(
                "Balance: " + MoneyFormat.format(economy.getBalance(player.getUniqueId()))));
        return true;
    }

    private boolean handleWorth(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        if (!player.hasPermission("hubeconomy.use")) {
            player.sendMessage(Messages.error("No permission."));
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            player.sendMessage(Messages.error("Hold an item to check its worth."));
            return true;
        }
        long unit = worth.stackUnitWorthWithEnchants(hand);
        player.sendMessage(Messages.info(
                hand.getType().name() + ": " + MoneyFormat.formatCoins(unit) + " each"));
        return true;
    }

    private boolean handleShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        if (!player.hasPermission("hubeconomy.use")) {
            player.sendMessage(Messages.error("No permission."));
            return true;
        }
        shopMenu.open(player);
        return true;
    }

    private boolean handleAuction(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        if (!player.hasPermission("hubeconomy.use")) {
            player.sendMessage(Messages.error("No permission."));
            return true;
        }
        if (args.length == 0) {
            auctionHouse.openBrowser(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("sell")) {
            if (args.length < 2) {
                player.sendMessage(Messages.error("Usage: /ah sell <coins>"));
                return true;
            }
            try {
                long price = Long.parseLong(args[1]);
                auctionHouse.sellFromHand(player, price);
            } catch (NumberFormatException e) {
                player.sendMessage(Messages.error("Invalid price."));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("collect")) {
            auctionHouse.collectExpired(player);
            return true;
        }
        player.sendMessage(Messages.info("Usage: /ah | /ah sell <coins> | /ah collect"));
        return true;
    }

    private boolean handleHub(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        if (!player.hasPermission("hubeconomy.use")) {
            player.sendMessage(Messages.error("No permission."));
            return true;
        }
        worlds.sendToHub(player);
        player.sendMessage(Messages.success("Teleported to hub."));
        return true;
    }

    private boolean handleSurvival(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        if (!player.hasPermission("hubeconomy.use")) {
            player.sendMessage(Messages.error("No permission."));
            return true;
        }
        hubNpc.handleSurvivalWarp(player);
        return true;
    }

    private boolean handleSetHub(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        if (!player.hasPermission("hubeconomy.admin")) {
            player.sendMessage(Messages.error("No permission."));
            return true;
        }
        config.setHubSpawn(player.getLocation());
        player.sendMessage(Messages.success("Hub spawn saved."));
        return true;
    }

    private boolean handleSetSurvival(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Players only."));
            return true;
        }
        if (!player.hasPermission("hubeconomy.admin")) {
            player.sendMessage(Messages.error("No permission."));
            return true;
        }
        config.setSurvivalSpawn(player.getLocation());
        player.sendMessage(Messages.success("Survival spawn saved."));
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hubeconomy.admin")) {
            sender.sendMessage(Messages.error("No permission."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPluginConfig();
            worth.reloadFromDisk();
            sender.sendMessage(Messages.success("Reloaded config and values.yml."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("tool")) {
            if (args.length < 2) {
                sender.sendMessage(Messages.error("Usage: /hubeconomy tool <player>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Messages.error("Player not found."));
                return true;
            }
            plugin.hubTool().give(target);
            sender.sendMessage(Messages.success("Gave the hub tool to " + target.getName() + "."));
            target.sendMessage(Messages.info("Hub tool: sneak + right-click to change mode, right-click to use it."));
            return true;
        }
        sender.sendMessage(Messages.info("Usage: /hubeconomy reload | /hubeconomy tool <player>"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("hubeconomy")) {
            return List.of();
        }
        if (args.length == 1) {
            return java.util.stream.Stream.of("reload", "tool")
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tool")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }

    private static List<String> filterPrefix(String option, String prefix) {
        if (option.startsWith(prefix.toLowerCase(Locale.ROOT))) {
            return List.of(option);
        }
        return List.of();
    }
}
