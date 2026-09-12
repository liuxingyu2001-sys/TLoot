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
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PointerListener implements Listener {

    /** 描述文本（剩余时间）刷新间隔，秒 */
    private static final long LORE_UPDATE_INTERVAL = 10_000L;
    /** 近距离粒子刷新间隔 */
    private static final long PARTICLE_INTERVAL = 500L;

    private final TLoot plugin;
    private final Map<UUID, Long> lastParticleTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeCompassTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLoreUpdate = new ConcurrentHashMap<>();

    private BukkitTask pointerTask;

    public PointerListener(TLoot plugin) {
        this.plugin = plugin;
        startPointerTask();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    /**
     * 清理玩家状态。
     * 原先仅清理当前在线玩家的 Map 条目，配合每秒写入的任务，
     * 跨服/重载等非正常退出路径会在 Map 中残留 UUID -> Long 条目（内存泄漏）。
     */
    public void clearPlayerState(UUID uuid) {
        if (uuid == null) {
            return;
        }
        lastParticleTime.remove(uuid);
        activeCompassTarget.remove(uuid);
        lastLoreUpdate.remove(uuid);
    }

    public void shutdown() {
        if (pointerTask != null) {
            pointerTask.cancel();
            pointerTask = null;
        }
        lastParticleTime.clear();
        activeCompassTarget.clear();
        lastLoreUpdate.clear();
    }

    private void startPointerTask() {
        pointerTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    tickPlayer(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tickPlayer(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        String treasureId = PointerItem.getTreasureId(item);

        if (treasureId == null) {
            clearCompassTarget(player);
            return;
        }

        Treasure treasure = plugin.getTreasureManager().getTreasure(treasureId);
        if (treasure == null || treasure.isExpired()) {
            clearCompassTarget(player);
            return;
        }

        Location treasureLoc = treasure.getLocation();
        Location playerLoc = player.getLocation();

        if (treasureLoc.getWorld() != null && playerLoc.getWorld().equals(treasureLoc.getWorld())) {
            if (!treasureId.equals(activeCompassTarget.get(player.getUniqueId()))) {
                player.setCompassTarget(treasureLoc);
                activeCompassTarget.put(player.getUniqueId(), treasureId);
            }

            double distance = playerLoc.distance(treasureLoc);

            if (distance <= plugin.getConfigManager().getClaimDistance()) {
                showNearbyParticles(player, treasureLoc);
            }

            if (plugin.getConfigManager().isPointerActionBarEnabled()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent("距离: " + (int) Math.round(distance) + " 格"));
            }
        }

        updateLoreIfNeeded(player, item, treasure);
    }

    /**
     * 指针描述中唯一会变化的是剩余时间，10 秒刷新一次即可；
     * 且仅在文本真正变化时才写回物品，避免无意义的元数据更新造成的客户端不同步与性能开销。
     */
    private void updateLoreIfNeeded(Player player, ItemStack item, Treasure treasure) {
        long now = System.currentTimeMillis();
        Long lastUpdate = lastLoreUpdate.get(player.getUniqueId());
        if (lastUpdate != null && now - lastUpdate < LORE_UPDATE_INTERVAL) {
            return;
        }

        lastLoreUpdate.put(player.getUniqueId(), now);
        if (PointerItem.updatePointerLore(item, treasure)) {
            player.getInventory().setItemInMainHand(item);
        }
    }

    private void clearCompassTarget(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeCompassTarget.remove(uuid) == null) {
            return;
        }

        player.setCompassTarget(player.getWorld().getSpawnLocation());
        lastLoreUpdate.remove(uuid);

        if (plugin.getConfigManager().isPointerActionBarEnabled()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
    }

    private void showNearbyParticles(Player player, Location treasureLoc) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastParticleTime.get(uuid);

        if (lastTime != null && now - lastTime < PARTICLE_INTERVAL) {
            return;
        }
        lastParticleTime.put(uuid, now);

        Location loc = treasureLoc.clone().add(0, 1, 0);
        player.spawnParticle(Particle.END_ROD, loc, 10, 0.5, 0.5, 0.5, 0.1);
        player.spawnParticle(Particle.FIREWORK, loc, 5, 0.3, 0.3, 0.3, 0.05);
    }
}
