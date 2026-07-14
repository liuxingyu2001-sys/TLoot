package com.tloot.beacon;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;

public class BeaconEffectManager {

    private final TLoot plugin;
    private BukkitRunnable task;

    public BeaconEffectManager(TLoot plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                Collection<Treasure> treasures = plugin.getTreasureManager().getAllTreasures();
                for (Treasure treasure : treasures) {
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
        };
        task.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void drawBeaconBeam(Location baseLoc) {
        Location loc = baseLoc.clone();
        loc.add(0.5, 1, 0);

        int maxY = Math.min(loc.getWorld().getMaxHeight(), 80);  // 限制最大高度，避免过高产生大量粒子
        int startY = loc.getBlockY() + 1;

        for (int y = startY; y < startY + maxY && y < loc.getWorld().getMaxHeight(); y += 4) {  // 增加间隔，减少粒子数量
            Location particleLoc = loc.clone().add(0, y - startY, 0);

            loc.getWorld().spawnParticle(
                Particle.END_ROD,
                particleLoc,
                1,
                0, 0, 0,
                0
            );
        }
    }
}
