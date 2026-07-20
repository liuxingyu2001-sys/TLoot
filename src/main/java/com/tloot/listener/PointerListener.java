package com.tloot.listener;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.item.PointerItem;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PointerListener implements Listener {

    private static final long LORE_UPDATE_INTERVAL = 5000L;

    private final TLoot plugin;
    private final Map<UUID, Long> lastParticleTime;
    private final Map<UUID, String> activeCompassTarget;
    private final Map<UUID, Long> lastLoreUpdate;

    public PointerListener(TLoot plugin) {
        this.plugin = plugin;
        this.lastParticleTime = new HashMap<>();
        this.activeCompassTarget = new HashMap<>();
        this.lastLoreUpdate = new HashMap<>();
        startPointerTask();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastParticleTime.remove(uuid);
        activeCompassTarget.remove(uuid);
        lastLoreUpdate.remove(uuid);
    }

    private void startPointerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    String treasureId = PointerItem.getTreasureId(item);
                    
                    if (treasureId != null) {
                        Treasure treasure = plugin.getTreasureManager().getTreasure(treasureId);
                        if (treasure != null && !treasure.isExpired()) {
                            updatePointer(player, treasure);
                        }
                    } else {
                        clearCompassTarget(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void updatePointer(Player player, Treasure treasure) {
        Location playerLoc = player.getLocation();
        Location treasureLoc = treasure.getLocation();

        if (treasureLoc.getWorld() != null && playerLoc.getWorld().equals(treasureLoc.getWorld())) {
            String currentTarget = activeCompassTarget.get(player.getUniqueId());
            if (currentTarget == null || !currentTarget.equals(treasure.getId())) {
                player.setCompassTarget(treasureLoc);
                activeCompassTarget.put(player.getUniqueId(), treasure.getId());
            }

            double distance = playerLoc.distance(treasureLoc);
            int claimDistance = plugin.getConfigManager().getClaimDistance();

            if (distance <= claimDistance) {
                showNearbyParticles(player, treasureLoc);
            }

            // ActionBar 实时距离
            if (plugin.getConfigManager().isPointerActionBarEnabled()) {
                int intDist = (int) Math.round(distance);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent("距离: " + intDist + " 格"));
            }
        }

        ItemStack pointer = player.getInventory().getItemInMainHand();
        if (PointerItem.isPointer(pointer)) {  // 添加检查
            long now = System.currentTimeMillis();
            Long lastUpdate = lastLoreUpdate.get(player.getUniqueId());
            if (lastUpdate == null || now - lastUpdate >= LORE_UPDATE_INTERVAL) {
                PointerItem.updatePointerLore(pointer, treasure);
                player.getInventory().setItemInMainHand(pointer);  // 同步回背包
                lastLoreUpdate.put(player.getUniqueId(), now);
            }
        }
    }

    private void clearCompassTarget(Player player) {
        String currentTarget = activeCompassTarget.get(player.getUniqueId());
        if (currentTarget != null) {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
            activeCompassTarget.remove(player.getUniqueId());
            // 清除 ActionBar
            if (plugin.getConfigManager().isPointerActionBarEnabled()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            }
        }
    }

    private void showNearbyParticles(Player player, Location treasureLoc) {
        long now = System.currentTimeMillis();
        Long lastTime = lastParticleTime.get(player.getUniqueId());
        
        if (lastTime != null && now - lastTime < 500) {
            return;
        }
        lastParticleTime.put(player.getUniqueId(), now);

        Location loc = treasureLoc.clone().add(0, 1, 0);
        player.spawnParticle(Particle.END_ROD, loc, 10, 0.5, 0.5, 0.5, 0.1);
        player.spawnParticle(Particle.FIREWORK, loc, 5, 0.3, 0.3, 0.3, 0.05);
    }
}
