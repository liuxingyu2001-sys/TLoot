package com.tloot.doom;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.integration.LiuChatBridge;
import com.tloot.integration.MythicMobsBridge;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 厄运宝藏：用 MythicMobs 刷出守卫，全部击杀后宝箱才会出现。
 *
 * <p>宝箱的「是否已解锁」不额外落库，而是用「宝藏坐标上是否已有箱子」来判断：
 * 服务端重启后，有箱子的厄运宝藏视为已解锁，没有箱子的会重新刷出守卫。</p>
 */
public class DoomTreasureManager implements Listener {

    private final TLoot plugin;
    private final Map<String, Guarded> guarded = new ConcurrentHashMap<>();
    private final java.util.Set<String> warnedMobs = ConcurrentHashMap.newKeySet();
    /** 刷怪失败（MM 未安装/怪物名错误）的宝藏本会话不再重试，避免每秒刷怪 */
    private final java.util.Set<String> spawnFailed = ConcurrentHashMap.newKeySet();
    private BukkitTask task;
    private boolean warnedNoMythic;
    private boolean warnedNoMobs;

    public DoomTreasureManager(TLoot plugin) {
        this.plugin = plugin;
    }

    public void init() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getConfigManager().isDoomTreasureEnabled()) {
            if (!MythicMobsBridge.available()) {
                warnNoMythic();
            } else if (plugin.getConfigManager().getDoomMobs().isEmpty()) {
                warnNoMobs();
            }
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    // ==================== 创建 / 解锁 ====================

    /**
     * 为刚创建的厄运宝藏刷出守卫。
     *
     * @return true 表示守卫已刷出；false 表示未刷出（调用方应把宝箱直接放出来，按普通宝藏处理）
     */
    public boolean start(Treasure treasure) {
        if (treasure == null || !treasure.isDoom()) {
            return false;
        }
        Location location = treasure.getLocation();
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Guarded guard = new Guarded(treasure.getId(), location.clone());
        if (!spawnGuards(guard)) {
            return false;
        }
        guarded.put(treasure.getId(), guard);
        plugin.getLogger().info("[厄运宝藏] 已为宝藏 #" + treasure.getId() + " 刷出 "
                + guard.total + " 只守卫");
        return true;
    }

    private boolean spawnGuards(Guarded guard) {
        if (!MythicMobsBridge.available()) {
            warnNoMythic();
            return false;
        }
        Map<String, Integer> mobs = plugin.getConfigManager().getDoomMobs();
        if (mobs.isEmpty()) {
            warnNoMobs();
            return false;
        }
        int spawned = 0;
        int radius = plugin.getConfigManager().getDoomSpawnRadius();
        for (Map.Entry<String, Integer> entry : mobs.entrySet()) {
            String mobName = entry.getKey();
            if (!MythicMobsBridge.isKnownMob(mobName)) {
                if (warnedMobs.add(mobName)) {
                    plugin.getLogger().warning("[厄运宝藏] MythicMobs 中找不到怪物 \"" + mobName
                            + "\"，请检查 doom-treasure.mobs 配置");
                }
                continue;
            }
            for (int i = 0; i < entry.getValue(); i++) {
                Location spawn = randomSpawn(guard.location, radius);
                UUID uuid = MythicMobsBridge.spawn(mobName, spawn);
                if (uuid != null) {
                    guard.alive.put(uuid, new SpawnSlot(mobName, spawn));
                    spawned++;
                }
            }
        }
        guard.total = spawned;
        guard.remaining = spawned;
        return spawned > 0;
    }

    private Location randomSpawn(Location center, int radius) {
        World world = center.getWorld();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = Math.random() * Math.PI * 2;
            double distance = 1.5 + Math.random() * (radius - 1.5);
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location location = new Location(world, x + 0.5, y, z + 0.5);
            Block block = location.getBlock();
            if (block.isEmpty() || block.getType().isAir()) {
                return location;
            }
        }
        return center.clone().add(0, 1, 0);
    }

    private void unlock(Guarded guard) {
        if (!guarded.containsKey(guard.treasureId)) {
            return;
        }
        guard.alive.clear();
        // 宝藏可能已被管理员移除/领取，避免留下一个没有宝藏记录的“幽灵箱子”
        if (plugin.getTreasureManager().getTreasure(guard.treasureId) == null) {
            guarded.remove(guard.treasureId);
            return;
        }
        if (placeChest(guard.location)) {
            guarded.remove(guard.treasureId);
            broadcastCleared(guard.location);
        } else {
            // 宝箱位置被占用：保留记录，下一轮 tick 继续尝试（只提示一次，避免刷屏）
            if (!guard.warningLogged) {
                guard.warningLogged = true;
                plugin.getLogger().warning("[厄运宝藏] 宝藏 #" + guard.treasureId
                        + " 的宝箱位置被占用，稍后重试: " + describe(guard.location));
            }
        }
    }

    private boolean placeChest(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        Block block = location.getBlock();
        if (block.getType() == Material.CHEST) {
            return true;
        }
        if (!block.isEmpty() && !block.getType().isAir()) {
            return false;
        }
        block.setType(Material.CHEST, false);
        return block.getType() == Material.CHEST;
    }

    private void broadcastCleared(Location location) {
        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(location.getWorld().getName());
        TextComponent message = new TextComponent(
                plugin.getMessageManager().get("prefix")
                        + plugin.getMessageManager().get("broadcast.doom-cleared", "world", worldDisplayName));
        if (!LiuChatBridge.broadcast(Bukkit.getOnlinePlayers().stream().findFirst().orElse(null), message)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.spigot().sendMessage(message);
            }
        }
    }

    // ==================== 周期维护 ====================

    private void tick() {
        // 1) 重启/跨服同步后，为「没有箱子」的厄运宝藏重新刷守卫
        syncLoadedTreasures();

        // 2) 校正守卫存活状态：被击杀的计数由死亡事件处理，
        //    这里只处理「实体消失」（MM 自然消失/区块卸载）并补刷，避免守卫白掉。
        for (Guarded guard : new ArrayList<>(guarded.values())) {
            Treasure treasure = plugin.getTreasureManager().getTreasure(guard.treasureId);
            if (treasure == null || treasure.isExpired()) {
                cancel(guard.treasureId);
                continue;
            }

            if (guard.remaining <= 0) {
                unlock(guard);
                continue;
            }

            List<SpawnSlot> missing = new ArrayList<>();
            for (Map.Entry<UUID, SpawnSlot> entry : guard.alive.entrySet()) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity == null) {
                    Location slot = entry.getValue().location();
                    World world = slot.getWorld();
                    if (world != null && world.isChunkLoaded(slot.getBlockX() >> 4, slot.getBlockZ() >> 4)) {
                        guard.alive.remove(entry.getKey());
                        missing.add(entry.getValue());
                    }
                } else if (entity.isDead()) {
                    if (guard.alive.remove(entry.getKey()) != null) {
                        guard.remaining--;
                    }
                }
            }

            // 只补刷，不改变 remaining（守卫仍然算没死）
            for (SpawnSlot slot : missing) {
                UUID uuid = MythicMobsBridge.spawn(slot.mobName(), slot.location());
                if (uuid != null) {
                    guard.alive.put(uuid, slot);
                }
            }
        }
    }

    private void syncLoadedTreasures() {
        for (Treasure treasure : plugin.getTreasureManager().treasuresView()) {
            if (!treasure.isDoom() || guarded.containsKey(treasure.getId())
                    || spawnFailed.contains(treasure.getId())) {
                continue;
            }
            Location location = treasure.getLocation();
            if (location == null || location.getWorld() == null) {
                continue;
            }
            if (location.getBlock().getType() == Material.CHEST) {
                continue; // 已解锁
            }
            Guarded guard = new Guarded(treasure.getId(), location.clone());
            if (plugin.getConfigManager().isDoomTreasureEnabled() && spawnGuards(guard)) {
                guarded.put(treasure.getId(), guard);
                plugin.getLogger().info("[厄运宝藏] 已恢复宝藏 #" + treasure.getId() + " 的 "
                        + guard.total + " 只守卫");
            } else {
                // 没有可用的守卫（功能已关闭/MM 未安装/怪物名配置错误）：直接解锁，避免宝藏永远无法领取
                spawnFailed.add(treasure.getId());
                guarded.put(treasure.getId(), guard);
                unlock(guard);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (guarded.isEmpty()) {
            return;
        }
        UUID uuid = event.getEntity().getUniqueId();
        for (Guarded guard : guarded.values()) {
            if (guard.alive.remove(uuid) != null) {
                guard.remaining--;
                if (guard.remaining <= 0) {
                    unlock(guard);
                }
                return;
            }
        }
    }

    // ==================== 查询 / 清理 ====================

    public boolean isGuarded(String treasureId) {
        Guarded guard = guarded.get(treasureId);
        return guard != null && guard.remaining > 0;
    }

    public int remainingGuards(String treasureId) {
        Guarded guard = guarded.get(treasureId);
        return guard != null ? Math.max(0, guard.remaining) : 0;
    }

    /** 宝藏被领取/过期/移除时调用，清掉守卫，避免野外残留怪物 */
    public void cancel(String treasureId) {
        Guarded guard = guarded.remove(treasureId);
        if (guard == null) {
            return;
        }
        for (UUID uuid : guard.alive.keySet()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        guard.alive.clear();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Guarded guard : new ArrayList<>(guarded.values())) {
            cancel(guard.treasureId);
        }
        guarded.clear();
    }

    /** 配置重载后允许之前刷怪失败的厄运宝藏重新尝试 */
    public void reload() {
        spawnFailed.clear();
        warnedMobs.clear();
        warnedNoMythic = false;
        warnedNoMobs = false;
    }

    private void warnNoMythic() {
        if (warnedNoMythic) {
            return;
        }
        warnedNoMythic = true;
        plugin.getLogger().warning("[厄运宝藏] 未检测到 MythicMobs，doom-treasure 将自动降级为普通宝藏");
    }

    private void warnNoMobs() {
        if (warnedNoMobs) {
            return;
        }
        warnedNoMobs = true;
        plugin.getLogger().warning("[厄运宝藏] 已启用但 doom-treasure.mobs 为空，无法刷出守卫");
    }

    private static String describe(Location location) {
        if (location == null) {
            return "未知位置";
        }
        World world = location.getWorld();
        return (world != null ? world.getName() : "?") + " " + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }

    private static final class Guarded {
        private final String treasureId;
        private final Location location;
        /** 存活守卫：实体 UUID → 出生点与怪物名，用于自然消失后补刷 */
        private final Map<UUID, SpawnSlot> alive = new ConcurrentHashMap<>();
        private int total;
        private int remaining;
        private volatile boolean warningLogged;

        private Guarded(String treasureId, Location location) {
            this.treasureId = treasureId;
            this.location = location;
        }
    }

    private record SpawnSlot(String mobName, Location location) {
    }
}
