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
        loc.add(0.5, 1, 0.5);

        int maxY = loc.getWorld().getMaxHeight();
        for (int y = 0; y < maxY; y += 3) {
            Location particleLoc = loc.clone().add(0, y, 0);

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
