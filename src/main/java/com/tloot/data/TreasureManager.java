package com.tloot.data;

import com.tloot.TLoot;
import com.tloot.item.PointerItem;
import com.tloot.storage.StorageBackend;
import com.tloot.sync.RedisSyncManager;
import com.tloot.util.TreasureBlocks;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TreasureManager implements RedisSyncManager.SyncCallback {

    private static final UUID SYSTEM_OWNER_UUID = com.tloot.task.AutoTreasureTask.SYSTEM_OWNER_UUID;


    private final TLoot plugin;
    private final Map<String, Treasure> treasures;
    private final Map<String, String> locationIndex;

    private StorageBackend storage;
    private RedisSyncManager syncManager;

    public TreasureManager(TLoot plugin) {
        this.plugin = plugin;
        this.treasures = new ConcurrentHashMap<>();
        this.locationIndex = new ConcurrentHashMap<>();
    }

    public void setStorage(StorageBackend storage) {
        this.storage = storage;
    }

    public void setSyncManager(RedisSyncManager syncManager) {
        this.syncManager = syncManager;
    }

    // ==================== 加载与保存 ====================

    public void loadTreasures() {
        if (storage == null) {
            return;
        }

        Map<String, Treasure> loaded;
        try {
            loaded = storage.loadAll();
        } catch (Exception e) {
            plugin.getLogger().severe("加载宝藏数据失败: " + e.getMessage());
            return;
        }

        treasures.clear();
        locationIndex.clear();
        for (Map.Entry<String, Treasure> entry : loaded.entrySet()) {
            index(entry.getValue());
        }
        plugin.getLogger().info("已加载 " + treasures.size() + " 个宝藏");
    }

    public void saveTreasures() {
        if (storage == null) {
            return;
        }
        try {
            storage.saveAll(new ArrayList<>(treasures.values()));
        } catch (Exception e) {
            plugin.getLogger().severe("保存宝藏数据失败: " + e.getMessage());
        }
    }

    /** 写入映射并返回是否成功（ID 或位置冲突时拒绝） */
    private boolean index(Treasure treasure) {
        if (treasure == null || treasure.getId() == null) {
            return false;
        }
        treasures.put(treasure.getId(), treasure);
        locationIndex.put(treasure.getLocationKey(), treasure.getId());
        return true;
    }

    /** 从内存中移除并同步清理位置索引，返回被移除的宝藏 */
    private Treasure unindex(String id) {
        Treasure treasure = treasures.remove(id);
        if (treasure != null) {
            // 仅当索引仍指向自己时才移除，避免误删同位置的新宝藏
            locationIndex.remove(treasure.getLocationKey(), treasure.getId());
        }
        return treasure;
    }

    // ==================== 创建与移除 ====================

    public Treasure createTreasure(UUID ownerUuid, String ownerName,
                                    Location location,
                                    int guaranteedCoins,
                                    int ticketPrice,
                                    List<ItemStack> items) {
        return createTreasure(ownerUuid, ownerName, location, guaranteedCoins, ticketPrice, items, null);
    }

    public Treasure createTreasure(UUID ownerUuid, String ownerName,
                                    Location location,
                                    int guaranteedCoins,
                                    int ticketPrice,
                                    List<ItemStack> items,
                                    List<String> commands) {
        return createTreasure(ownerUuid, ownerName, location, guaranteedCoins, ticketPrice, items, commands,
                plugin.getConfigManager().getExpireTime());
    }

    public Treasure createTreasure(UUID ownerUuid, String ownerName,
                                    Location location,
                                    int guaranteedCoins,
                                    int ticketPrice,
                                    List<ItemStack> items,
                                    List<String> commands,
                                    long expireTimeMillis) {
        // 同一坐标只允许存在一个宝藏，避免旧宝藏变成无法领取的“幽灵数据”
        Treasure occupant = findTreasureAtLocation(location);
        if (occupant != null) {
            throw new IllegalStateException("该位置已存在宝藏 #" + occupant.getId());
        }

        String id = generateId();
        while (treasures.containsKey(id)) {
            id = generateId();
        }

        Treasure treasure = new Treasure(id, ownerUuid, ownerName, location,
                guaranteedCoins, ticketPrice, items, commands, expireTimeMillis,
                System.currentTimeMillis());

        index(treasure);

        if (storage != null) {
            storage.save(treasure);
        }
        if (syncManager != null) {
            syncManager.publishCreate(treasure);
        }

        return treasure;
    }

    public void removeTreasure(String id) {
        Treasure treasure = unindex(id);
        if (storage != null) {
            storage.delete(id);
        }
        if (syncManager != null) {
            syncManager.publishRemove(id);
        }
        if (treasure != null) {
            removePointers(treasure);
        }
    }

    public void claimTreasure(String id, String claimerName) {
        Treasure treasure = unindex(id);
        if (storage != null) {
            storage.delete(id);
        }
        if (syncManager != null) {
            String ownerName = treasure != null ? treasure.getOwnerName() : "未知";
            syncManager.publishClaim(id, claimerName, ownerName);
        }
    }

    public void expireTreasure(String id) {
        Treasure treasure = unindex(id);
        if (storage != null) {
            storage.delete(id);
        }
        if (syncManager != null) {
            syncManager.publishExpire(id);
        }
        if (treasure != null) {
            removePointers(treasure);
        }
    }

    /**
     * 回收该宝藏对应的所有指针，防止宝藏消失后玩家背包里留下永久失效的指针。
     */
    private void removePointers(Treasure treasure) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PointerItem.removePointers(player, treasure.getId());
        }
    }

    public void addParticipant(String treasureId, UUID participantUuid) {
        Treasure treasure = treasures.get(treasureId);
        if (treasure == null) {
            return;
        }
        if (!treasure.addParticipant(participantUuid)) {
            return; // 已是参与者，无需重复落库
        }

        if (storage != null) {
            storage.save(treasure);
        }
        if (syncManager != null) {
            syncManager.publishJoin(treasureId, participantUuid);
        }
    }

    // ==================== Redis 同步回调（来自其他服务器） ====================

    @Override
    public void onTreasureCreated(Treasure treasure) {
        if (treasure == null) {
            return;
        }

        Treasure occupant = findTreasureAtLocation(treasure.getLocation());
        if (occupant != null && !occupant.getId().equals(treasure.getId())) {
            plugin.getLogger().warning("忽略来自其他服务器的宝藏 #" + treasure.getId()
                    + "：位置已被 #" + occupant.getId() + " 占用");
            return;
        }

        index(treasure);

        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());
        boolean isSystem = SYSTEM_OWNER_UUID.equals(treasure.getOwnerUuid());

        TextComponent message = new TextComponent(
                plugin.getMessageManager().get("prefix")
                        + (isSystem
                        ? ChatColor.GOLD + "【系统寻宝】"
                        + ChatColor.GREEN + "一个新的宝藏出现了！ "
                        : ChatColor.GREEN + " " + treasure.getOwnerName() + " 发起了一个寻宝！ ")
                        + ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins() + " "
                        + ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice() + " "
                        + ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName
        );
        message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/treasure join " + treasure.getId()));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GREEN + "点击参与此寻宝").create()));

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!isSystem && onlinePlayer.getUniqueId().equals(treasure.getOwnerUuid())) {
                continue;
            }
            onlinePlayer.spigot().sendMessage(message);
        }
    }

    @Override
    public void onTreasureRemoved(String treasureId) {
        Treasure treasure = unindex(treasureId);
        if (treasure != null) {
            TreasureBlocks.removeChest(treasure.getLocation());
            removePointers(treasure);
        }
    }

    @Override
    public void onTreasureClaimed(String treasureId, String claimerName, String ownerName) {
        Treasure treasure = unindex(treasureId);
        if (treasure != null) {
            // 其他子服完成领取后，本服可能还留着同一个箱子，必须一并移除，否则会残留可被拾取的宝箱
            TreasureBlocks.removeChest(treasure.getLocation());
            removePointers(treasure);
        }

        plugin.getServer().broadcastMessage(
                plugin.getMessageManager().get("prefix")
                        + "§e" + claimerName + " §a找到了 §e" + ownerName + " §a发起的宝藏！"
        );
    }

    @Override
    public void onTreasureExpired(String treasureId) {
        Treasure treasure = unindex(treasureId);
        if (treasure == null) {
            return;
        }

        TreasureBlocks.removeChest(treasure.getLocation());
        removePointers(treasure);

        Player owner = Bukkit.getPlayer(treasure.getOwnerUuid());
        if (owner != null) {
            owner.sendMessage(ChatColor.RED + "你的宝藏 #" + treasureId + " 已过期！");
        }
        for (UUID participantUuid : treasure.participantsView()) {
            Player participant = Bukkit.getPlayer(participantUuid);
            if (participant != null) {
                participant.sendMessage(ChatColor.RED + "宝藏 #" + treasureId + " 已过期！");
            }
        }
    }

    @Override
    public void onPlayerJoined(String treasureId, UUID participantUuid) {
        Treasure treasure = treasures.get(treasureId);
        if (treasure != null) {
            treasure.addParticipant(participantUuid);
        }
    }

    // ==================== 查询方法 ====================

    public Treasure findTreasureAtLocation(Location location) {
        String key = Treasure.locationKeyOf(location);
        if (key == null) {
            return null;
        }
        String id = locationIndex.get(key);
        return id != null ? treasures.get(id) : null;
    }

    public Treasure getTreasure(String id) {
        return id == null ? null : treasures.get(id);
    }

    public List<Treasure> getAllTreasures() {
        return new ArrayList<>(treasures.values());
    }

    /** 只读视图，供高频遍历（粒子任务、统计）使用，避免复制集合 */
    public Collection<Treasure> treasuresView() {
        return java.util.Collections.unmodifiableCollection(treasures.values());
    }

    public List<Treasure> getAvailableTreasures(UUID playerUuid) {
        List<Treasure> available = new ArrayList<>();
        for (Treasure treasure : treasures.values()) {
            if (!treasure.isExpired() && !treasure.getOwnerUuid().equals(playerUuid)) {
                available.add(treasure);
            }
        }
        return available;
    }

    public List<Treasure> getTreasuresByOwner(UUID ownerUuid) {
        List<Treasure> owned = new ArrayList<>();
        for (Treasure treasure : treasures.values()) {
            if (treasure.getOwnerUuid().equals(ownerUuid)) {
                owned.add(treasure);
            }
        }
        return owned;
    }

    public List<Treasure> getTreasuresByParticipant(UUID participantUuid) {
        List<Treasure> participating = new ArrayList<>();
        for (Treasure treasure : treasures.values()) {
            if (treasure.hasParticipant(participantUuid)) {
                participating.add(treasure);
            }
        }
        return participating;
    }

    public int countSystemTreasures() {
        int count = 0;
        for (Treasure treasure : treasures.values()) {
            if (SYSTEM_OWNER_UUID.equals(treasure.getOwnerUuid()) && !treasure.isExpired()) {
                count++;
            }
        }
        return count;
    }

    // ==================== 过期清理 ====================

    public void cleanupExpiredTreasures() {
        List<Treasure> expired = new ArrayList<>();
        for (Treasure treasure : treasures.values()) {
            if (treasure.isExpired()) {
                expired.add(treasure);
            }
        }

        for (Treasure treasure : expired) {
            String id = treasure.getId();
            unindex(id);
            TreasureBlocks.removeChest(treasure.getLocation());
            if (storage != null) {
                storage.delete(id);
            }
            if (syncManager != null) {
                syncManager.publishExpire(id);
            }
            removePointers(treasure);
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
