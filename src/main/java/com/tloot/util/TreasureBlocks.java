package com.tloot.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 宝藏方块的公共工具。
 *
 * 集中处理「清空箱内物品并移除箱子」这一动作，避免各调用点重复实现：
 * - 领取宝藏（本服）
 * - 宝藏过期（本服）
 * - 通过 Redis 收到其他子服的领取/过期/移除事件（跨服）
 */
public final class TreasureBlocks {

    /**
     * 待清理的残留宝箱：区块未加载时无法安全移除，记录 chunkKey -> 坐标，
     * 由 {@link com.tloot.listener.TreasureChunkCleanupListener} 在区块加载时回收。
     */
    private static final Map<Long, List<Location>> PENDING = new ConcurrentHashMap<>();
    /** 单个区块最多记录的待清理箱子数（正常情况为个位数） */
    private static final int MAX_PENDING_PER_CHUNK = 64;

    private TreasureBlocks() {
    }

    /**
     * 记录一个待清理的宝箱位置（区块当前未加载）。
     * 单个区块的记录数有上限，避免异常情况下无界增长。
     */
    public static void scheduleCleanup(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        long key = chunkKey(location);
        List<Location> locations = PENDING.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        if (locations.size() >= MAX_PENDING_PER_CHUNK) {
            return;
        }
        for (Location existing : locations) {
            if (existing.equals(location)) {
                return;
            }
        }
        locations.add(location.clone());
    }

    /**
     * 处理某个区块加载：回收记录在案的残留宝箱。
     *
     * @return 实际移除的箱子数量
     */
    public static int cleanupOnChunkLoad(org.bukkit.Chunk chunk) {
        if (chunk == null) {
            return 0;
        }

        long key = chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        List<Location> locations = PENDING.remove(key);
        if (locations == null || locations.isEmpty()) {
            return 0;
        }

        int removed = 0;
        for (Location location : locations) {
            if (removeChest(location)) {
                removed++;
            }
        }
        return removed;
    }

    /** 世界名 + 区块坐标的打包键 */
    public static long chunkKey(Location location) {
        return chunkKey(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private static long chunkKey(String worldName, int chunkX, int chunkZ) {
        long hash = worldName.hashCode();
        hash = hash * 31L + chunkX;
        hash = hash * 31L + chunkZ;
        return hash;
    }

    public static void clearPending() {
        PENDING.clear();
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
            // 区块未加载：登记，等区块加载（玩家靠近）后再清理
            scheduleCleanup(location);
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
