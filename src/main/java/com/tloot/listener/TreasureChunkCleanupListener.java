package com.tloot.listener;

import com.tloot.TLoot;
import com.tloot.util.TreasureBlocks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * 残留宝箱兜底回收。
 *
 * 宝藏被领取/过期/跨服同步移除时，若目标区块尚未加载，箱子无法立即移除，
 * 会被登记为待清理；区块加载（玩家靠近）时在这里补一次清理，
 * 避免世界里长期残留一个已失效但仍可打开的空箱子。
 */
public class TreasureChunkCleanupListener implements Listener {

    private final TLoot plugin;

    public TreasureChunkCleanupListener(TLoot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        int removed = TreasureBlocks.cleanupOnChunkLoad(event.getChunk());
        if (removed > 0) {
            plugin.getLogger().info("已清理 " + removed + " 个残留的宝藏箱子（区块 "
                    + event.getWorld().getName() + " " + event.getChunk().getX() + ","
                    + event.getChunk().getZ() + "）");
        }
    }
}
