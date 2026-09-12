package com.tloot.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;

/**
 * 宝藏方块的公共工具。
 *
 * 集中处理「清空箱内物品并移除箱子」这一动作，避免各调用点重复实现：
 * - 领取宝藏（本服）
 * - 宝藏过期（本服）
 * - 通过 Redis 收到其他子服的领取/过期/移除事件（跨服）
 */
public final class TreasureBlocks {

    private TreasureBlocks() {
    }

    /**
     * 移除宝藏箱子。
     * 仅在区块已加载时操作，不强制加载区块：跨服同步与过期清理都属于兜底逻辑，
     * 为移除一个箱子强制生成/加载区块得不偿失。未加载区块中的残留箱子会在
     * 玩家下次靠近、区块自然加载后由同一逻辑回收。
     *
     * @return 是否实际移除了箱子
     */
    public static boolean removeChest(Location location) {
        if (location == null) {
            return false;
        }

        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return false;
        }

        Block block = location.getBlock();
        if (block.getType() != Material.CHEST) {
            return false;
        }

        // 先清空箱内物理物品再移除，避免物品洒落在地上造成重复发放
        if (block.getState() instanceof Chest chest) {
            chest.getInventory().clear();
        }
        block.setType(Material.AIR, false);
        return true;
    }
}
