package com.tloot.beacon;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * 宝藏光柱特效。
 *
 * 修复/优化点：
 * - 原实现把高度上限写死为 80（忽略世界最大高度），且任务实例不可重复取消；
 * - 遍历使用只读视图，避免每秒复制整个宝藏集合；
 * - 光柱参数可配置，并支持关闭以降低粒子开销。
 */
public class BeaconEffectManager {

    private final TLoot plugin;
    private BukkitTask task;

    public BeaconEffectManager(TLoot plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            return;
        }

        int intervalTicks = Math.max(5, plugin.getConfigManager().getBeaconIntervalTicks());
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfigManager().isBeaconEnabled()) {
                    return;
                }

                for (Treasure treasure : plugin.getTreasureManager().treasuresView()) {
                    if (treasure.isExpired()) {
                        continue;
                    }

                    Location loc = treasure.getLocation();
                    if (loc == null || loc.getWorld() == null) {
                        continue;
                    }

                    drawBeaconBeam(loc);
                }
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void drawBeaconBeam(Location baseLoc) {
        World world = baseLoc.getWorld();
        int maxHeight = plugin.getConfigManager().getBeaconMaxHeight();
        int maxY = Math.min(world.getMaxHeight(), baseLoc.getBlockY() + maxHeight);

        Location particleLoc = baseLoc.clone().add(0.5, 1, 0.5);
        int startY = baseLoc.getBlockY() + 1;

        for (int y = startY; y < maxY; y += 4) {
            particleLoc.setY(y);
            world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
        }
    }
}
