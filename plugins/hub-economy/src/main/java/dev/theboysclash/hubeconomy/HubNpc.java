package dev.theboysclash.hubeconomy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

public final class HubNpc {

    public static final String NPC_VALUE_SURVIVAL = "survival";

    private final HubEconomyPlugin plugin;
    private final PluginConfig config;
    private final WorldService worlds;
    private NamespacedKey npcKey;

    public HubNpc(HubEconomyPlugin plugin, PluginConfig config, WorldService worlds) {
        this.plugin = plugin;
        this.config = config;
        this.worlds = worlds;
    }

    public void init() {
        npcKey = new NamespacedKey(plugin, "npc");
        removeDuplicates();
        spawnNpc();
    }

    public void respawn() {
        removeDuplicates();
        spawnNpc();
    }

    public boolean isHubNpc(Entity entity) {
        if (!(entity instanceof Villager villager) || npcKey == null) {
            return false;
        }
        String id = villager.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        return id != null && !id.isBlank();
    }

    public void deleteNpc(Entity entity) {
        if (!(entity instanceof Villager villager) || npcKey == null) {
            return;
        }
        String id = villager.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        entity.remove();
        if (id != null && !id.isBlank()) {
            config.removeNpc(id);
        }
    }

    public boolean isSurvivalNpc(Entity entity) {
        if (!(entity instanceof Villager villager)) {
            return false;
        }
        return NPC_VALUE_SURVIVAL.equals(
                villager.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING));
    }

    private void removeDuplicates() {
        for (var world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager
                        && NPC_VALUE_SURVIVAL.equals(
                                villager.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING))) {
                    entity.remove();
                }
            }
        }
    }

    public void moveSurvivalNpc(Location location) {
        config.setNpcLocation(NPC_VALUE_SURVIVAL, location);
        respawn();
    }

    private void spawnNpc() {
        if (config.isNpcRemoved(NPC_VALUE_SURVIVAL)) {
            return;
        }
        Location npcLoc = config.getNpcLocation(NPC_VALUE_SURVIVAL);
        if (npcLoc == null || npcLoc.getWorld() == null) {
            Location hubSpawn = config.getHubSpawn();
            if (hubSpawn == null || hubSpawn.getWorld() == null) {
                plugin.getLogger().warning("Cannot spawn hub NPC: hub spawn missing");
                return;
            }
            npcLoc = hubSpawn.clone();
            double yawRad = Math.toRadians(hubSpawn.getYaw());
            npcLoc.add(-Math.sin(yawRad) * 2.0, 0, Math.cos(yawRad) * 2.0);
            npcLoc.setYaw(hubSpawn.getYaw() + 180f);
            npcLoc.setPitch(0f);
        }

        Villager villager = (Villager) npcLoc.getWorld().spawnEntity(npcLoc, EntityType.VILLAGER);
        villager.customName(Component.text("Survival", NamedTextColor.GREEN));
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setSilent(true);
        villager.setInvulnerable(true);
        villager.setGravity(false);
        villager.setCollidable(true);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setProfession(Villager.Profession.NITWIT);
        villager.setRecipes(java.util.List.of());
        villager.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, NPC_VALUE_SURVIVAL);
    }

    public void handleSurvivalWarp(org.bukkit.entity.Player player) {
        worlds.sendToSurvival(player);
        player.sendMessage(Messages.success("Welcome to survival."));
    }
}
