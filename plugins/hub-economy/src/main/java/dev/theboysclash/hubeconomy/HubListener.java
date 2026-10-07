package dev.theboysclash.hubeconomy;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class HubListener implements Listener {

    private final HubEconomyPlugin plugin;
    private final PluginConfig config;
    private final WorldService worlds;
    private final HubNpc hubNpc;
    private final CoinSidebar coinSidebar;
    private final PasswordGate passwordGate;

    public HubListener(
            HubEconomyPlugin plugin,
            PluginConfig config,
            WorldService worlds,
            HubNpc hubNpc,
            CoinSidebar coinSidebar,
            PasswordGate passwordGate) {
        this.plugin = plugin;
        this.config = config;
        this.worlds = worlds;
        this.hubNpc = hubNpc;
        this.coinSidebar = coinSidebar;
        this.passwordGate = passwordGate;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            worlds.sendToHub(event.getPlayer());
            coinSidebar.attach(event.getPlayer());
            event.getPlayer().sendMessage(passwordGate.prompt(event.getPlayer()));
        });
    }

    @EventHandler
    public void onHubPlace(BlockPlaceEvent event) {
        worlds.markDirty();
    }

    @EventHandler
    public void onHubBreak(BlockBreakEvent event) {
        worlds.markDirty();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        coinSidebar.detach(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (player.getLastDeathLocation() != null
                && player.getLastDeathLocation().getWorld() != null
                && player.getLastDeathLocation().getWorld().getName().equals(config.survivalWorldName())) {
            event.setRespawnLocation(config.getSurvivalSpawn());
            plugin.getServer().getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SURVIVAL));
        } else {
            event.setRespawnLocation(config.getHubSpawn());
            plugin.getServer().getScheduler().runTask(plugin, () -> worlds.sendToHub(player));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (hubNpc.isSurvivalNpc(event.getRightClicked())) {
            event.setCancelled(true);
            hubNpc.handleSurvivalWarp(event.getPlayer());
        }
    }

    @EventHandler
    public void onNpcDamage(EntityDamageEvent event) {
        if (hubNpc.isSurvivalNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcHit(EntityDamageByEntityEvent event) {
        if (hubNpc.isSurvivalNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHubFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (player.getWorld().getName().equals(config.hubWorldName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHubTeleportFall(PlayerTeleportEvent event) {
        if (event.getTo() != null && event.getTo().getWorld() != null
                && event.getTo().getWorld().getName().equals(config.hubWorldName())) {
            event.getPlayer().setFallDistance(0f);
        }
    }

    @EventHandler
    public void onVillagerTrade(PlayerInteractEntityEvent event) {
        if (hubNpc.isSurvivalNpc(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void cancelOffhandNpc(PlayerSwapHandItemsEvent event) {
        if (hubNpc.isSurvivalNpc(event.getPlayer().getTargetEntity(5))) {
            event.setCancelled(true);
        }
    }
}
