package com.tloot.data;

import com.tloot.TLoot;
import com.tloot.util.Announcer;
import com.tloot.item.PointerItem;
import com.tloot.storage.StorageBackend;
import com.tloot.sync.RedisSyncManager;
import com.tloot.util.TreasureBlocks;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
            throw new IllegalStateException("加载宝藏数据失败", e);
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
        return createTreasure(ownerUuid, ownerName, location, guaranteedCoins, ticketPrice, items, commands,
                expireTimeMillis, false);
    }

    /**
     * @param doom 是否创建「厄运宝藏」：守卫全部被击杀后才会出现宝箱
     */
    public Treasure createTreasure(UUID ownerUuid, String ownerName,
                                    Location location,
                                    int guaranteedCoins,
                                    int ticketPrice,
                                    List<ItemStack> items,
                                    List<String> commands,
                                    long expireTimeMillis,
                                    boolean doom) {
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
                System.currentTimeMillis(), doom);

        index(treasure);

        if (storage != null) {
            try {
                storage.save(treasure);
            } catch (RuntimeException e) {
                unindex(id);
                throw e;
            }
        }
        if (syncManager != null) {
            try {
                syncManager.publishCreate(treasure);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("宝藏 #" + id + " 已创建，但 Redis 广播失败: " + e.getMessage());
            }
        }

        return treasure;
    }

    public void removeTreasure(String id) {
        removeTreasure(id, false);
    }

    /**
     * 移除宝藏（管理员取消、创建回滚等）。
     *
     * @param announce 是否全服公告「寻宝已被取消」；创建失败回滚等内部路径不应公告
     */
    public void removeTreasure(String id, boolean announce) {
        Treasure treasure = unindex(id);
        if (storage != null) {
            try {
                storage.delete(id);
            } catch (RuntimeException e) {
                // 数据已从内存移除，落库失败只影响重启后的残留，记录后继续清理世界中的箱子
                plugin.getLogger().warning("删除宝藏 #" + id + " 的存储记录失败: " + e.getMessage());
            }
        }
        if (syncManager != null) {
            try {
                syncManager.publishRemove(id);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("宝藏 #" + id + " 已移除，但 Redis 广播失败: " + e.getMessage());
            }
        }
        if (treasure != null) {
            TreasureBlocks.removeChest(treasure.getLocation());
            cancelGuards(id);
            removePointers(treasure);
            if (announce) {
                broadcastCancelled(treasure);
            }
        }
    }

    public void claimTreasure(String id, String claimerName) {
        Treasure treasure = treasures.get(id);
        if (treasure == null) return;
        if (storage != null) {
            try {
                storage.delete(id);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("删除已领取宝藏 #" + id + " 的存储记录失败: " + e.getMessage());
            }
        }
        unindex(id);
        cancelGuards(id);
        // 所有参与者的指针都要回收；只回收领取者的话，其他参与者背包里会留下永久失效的指南针
        removePointers(treasure);
        if (syncManager != null) {
            try {
                syncManager.publishClaim(id, claimerName, treasure.getOwnerName());
            } catch (RuntimeException e) {
                plugin.getLogger().warning("宝藏领取已完成，但 Redis 广播失败: " + e.getMessage());
            }
        }
    }

    public void expireTreasure(String id) {
        Treasure treasure = treasures.get(id);
        if (treasure == null) return;
        if (storage != null) {
            try {
                storage.delete(id);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("删除过期宝藏 #" + id + " 的存储记录失败: " + e.getMessage());
            }
        }
        unindex(id);
        TreasureBlocks.removeChest(treasure.getLocation());
        cancelGuards(id);
        if (syncManager != null) {
            try {
                syncManager.publishExpire(id);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("宝藏 #" + id + " 已过期，但 Redis 广播失败: " + e.getMessage());
            }
        }
        removePointers(treasure);
    }

    /**
     * 发起者主动取消（无人参与时）：移除宝藏并把保底金币与箱内物品原样退还。
     *
     * <p>只有「没有参与者」时才允许走到这里，所以退还不会产生刷钱空间；
     * 管理员强制移除请用 {@link #removeTreasure(String, boolean)}，不退押金。</p>
     */
    public boolean cancelForOwner(Treasure treasure, Player owner) {
        if (treasure == null || owner == null) {
            return false;
        }
        if (!treasure.getOwnerUuid().equals(owner.getUniqueId())) {
            return false;
        }
        if (treasure.getParticipantCount() > 0) {
            return false;
        }

        removeTreasure(treasure.getId(), true);

        Economy economy = plugin.getEconomy();
        int refund = Math.max(0, treasure.getGuaranteedCoins());
        if (refund > 0 && economy != null) {
            EconomyResponse response = economy.depositPlayer(owner, refund);
            if (!response.transactionSuccess()) {
                plugin.getLogger().warning("取消宝藏 #" + treasure.getId() + " 时退还 "
                        + refund + " 金币失败: " + response.errorMessage);
                owner.sendMessage(plugin.getMessageManager().get("cancel.refund-failed"));
            }
        }

        List<ItemStack> items = treasure.getItems();
        if (!items.isEmpty()) {
            for (ItemStack leftover : owner.getInventory()
                    .addItem(items.toArray(new ItemStack[0])).values()) {
                owner.getWorld().dropItemNaturally(owner.getLocation(), leftover);
            }
        }
        return true;
    }

    /** 宝藏消失后清掉守卫，避免野外残留 MythicMobs 怪物 */
    private void cancelGuards(String treasureId) {
        if (plugin.getDoomTreasureManager() != null) {
            plugin.getDoomTreasureManager().cancel(treasureId);
        }
    }

    private void broadcastCancelled(Treasure treasure) {
        String announcement = plugin.getMessageManager().get("prefix")
                + plugin.getMessageManager().get("broadcast.cancelled",
                "owner", treasure.getOwnerName() == null ? "未知" : treasure.getOwnerName(),
                "world", plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName()));
        Announcer.announceText(announcement);
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
            try {
                storage.save(treasure);
            } catch (RuntimeException e) {
                // 玩家已经扣费，不能因为一次落库失败就让参与流程报错；
                // 内存中已记录参与资格，关闭服务器时的全量保存会补写
                plugin.getLogger().warning("保存参与者数据失败（宝藏 #" + treasureId + "）: " + e.getMessage());
            }
        }
        if (syncManager != null) {
            try {
                syncManager.publishJoin(treasureId, participantUuid);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("同步参与信息失败（宝藏 #" + treasureId + "）: " + e.getMessage());
            }
        }
    }

    // ==================== 参与寻宝（命令与 GUI 共用同一套校验/扣费逻辑） ====================

    public enum JoinResult {
        /** 首次参与成功，已扣费并发放指针 */
        SUCCESS,
        /** 已经参与过，补发了一个新指针（不重复扣费） */
        REJOIN_POINTER,
        /** 已经参与过，且背包里已有该宝藏的指针 */
        ALREADY_HAS_POINTER,
        NOT_FOUND,
        EXPIRED,
        OWNER,
        NO_SPACE,
        NO_MONEY,
        CHARGE_FAILED
    }

    /**
     * 参与寻宝：校验世界/费用/背包，扣费、记录参与者、发放指针，并把门票收入结算给发起者。
     *
     * <p>命令 {@code /treasure join} 与指南针 GUI 都走这里，避免两套实现行为不一致
     * （例如一边先扣费再检查背包、另一边先检查背包）。</p>
     */
    public JoinResult joinTreasure(Player player, Treasure treasure) {
        if (treasure == null) {
            return JoinResult.NOT_FOUND;
        }
        if (treasure.isExpired()) {
            return JoinResult.EXPIRED;
        }

        UUID uuid = player.getUniqueId();
        if (treasure.getOwnerUuid().equals(uuid)) {
            return JoinResult.OWNER;
        }

        boolean alreadyJoined = treasure.hasParticipant(uuid);
        if (!alreadyJoined) {
            if (player.getInventory().firstEmpty() == -1) {
                return JoinResult.NO_SPACE;
            }

            Economy economy = plugin.getEconomy();
            if (economy == null) {
                return JoinResult.CHARGE_FAILED;
            }
            int price = Math.max(0, treasure.getTicketPrice());
            if (economy.getBalance(player) < price) {
                return JoinResult.NO_MONEY;
            }

            EconomyResponse withdraw = economy.withdrawPlayer(player, price);
            if (!withdraw.transactionSuccess()) {
                plugin.getLogger().warning("玩家 " + player.getName() + " 参与宝藏 #" + treasure.getId()
                        + " 扣费失败: " + withdraw.errorMessage);
                return JoinResult.CHARGE_FAILED;
            }

            addParticipant(treasure.getId(), uuid);
            payOwnerIncome(treasure, player.getName(), price);
        }

        boolean hasPointer = hasPointer(player, treasure.getId());
        if (hasPointer) {
            return alreadyJoined ? JoinResult.ALREADY_HAS_POINTER : JoinResult.SUCCESS;
        }

        if (player.getInventory().firstEmpty() == -1) {
            // 首次参与已在扣费前检查过背包，这里只可能是老参与者背包已满
            return JoinResult.NO_SPACE;
        }
        for (ItemStack leftover : player.getInventory()
                .addItem(PointerItem.createPointer(treasure)).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        return alreadyJoined ? JoinResult.REJOIN_POINTER : JoinResult.SUCCESS;
    }

    private boolean hasPointer(Player player, String treasureId) {
        for (ItemStack item : player.getInventory().getContents()) {
            String id = PointerItem.getTreasureId(item);
            if (id != null && id.equals(treasureId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 门票收入结算给发起者：发起者已预先支付保底金币作为奖励，
     * 门票是发起者的收益，否则「发起寻宝」只会亏钱，没人愿意发起。
     * 系统宝藏没有发起者，门票作为系统回收（防止无限刷金币）。
     */
    private void payOwnerIncome(Treasure treasure, String payerName, int price) {
        if (price <= 0 || SYSTEM_OWNER_UUID.equals(treasure.getOwnerUuid())) {
            return;
        }
        if (!plugin.getConfigManager().isOwnerIncomeEnabled()) {
            return;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            return;
        }
        try {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(treasure.getOwnerUuid());
            EconomyResponse deposit = economy.depositPlayer(owner, price);
            if (!deposit.transactionSuccess()) {
                plugin.getLogger().warning("无法结算宝藏 #" + treasure.getId() + " 的门票收入给 "
                        + treasure.getOwnerName() + ": " + deposit.errorMessage);
                return;
            }
            Player online = owner.getPlayer();
            if (online != null) {
                online.sendMessage(plugin.getMessageManager().get("join.owner-income",
                        "player", payerName, "amount", String.valueOf(price)));
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("结算门票收入时出错（宝藏 #" + treasure.getId() + "）: " + e.getMessage());
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
        // 厄运宝藏要用专属文案，否则其它子服会收到一条「普通系统寻宝」的公告
        boolean isDoom = treasure.isDoom() && plugin.getConfigManager().isDoomTreasureEnabled();
        String messageKey = isDoom
                ? "broadcast.doom-created"
                : (isSystem ? "broadcast.system-created" : "broadcast.player-created");

        String body = plugin.getMessageManager().get(messageKey,
                "owner", treasure.getOwnerName() == null ? "" : treasure.getOwnerName(),
                "coins", String.valueOf(treasure.getGuaranteedCoins()),
                "price", String.valueOf(treasure.getTicketPrice()),
                "world", worldDisplayName);

        TextComponent message = new TextComponent(plugin.getMessageManager().get("prefix") + body);
        message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/treasure join " + treasure.getId()));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(plugin.getMessageManager().get(
                        isDoom ? "broadcast.hover-join-doom"
                                : (isSystem ? "broadcast.hover-join-system" : "broadcast.hover-join"))).create()));

        // 只发本服；跨服由 Redis 同步的 onTreasureCreated 回调在各子服各公告一次
        Announcer.announce(isSystem ? null : treasure.getOwnerUuid(), message);
    }

    @Override
    public void onTreasureRemoved(String treasureId) {
        Treasure treasure = unindex(treasureId);
        if (treasure != null) {
            TreasureBlocks.removeChest(treasure.getLocation());
            cancelGuards(treasureId);
            removePointers(treasure);
            broadcastCancelled(treasure);
        }
    }

    @Override
    public void onTreasureClaimed(String treasureId, String claimerName, String ownerName) {
        Treasure treasure = unindex(treasureId);
        if (treasure != null) {
            // 其他子服完成领取后，本服可能还留着同一个箱子，必须一并移除，否则会残留可被拾取的宝箱
            TreasureBlocks.removeChest(treasure.getLocation());
            cancelGuards(treasureId);
            removePointers(treasure);
        }

        String announcement = plugin.getMessageManager().get("prefix")
                + plugin.getMessageManager().get("broadcast.claimed", "claimer", claimerName, "owner", ownerName);
        Announcer.announceText(announcement);
    }

    @Override
    public void onTreasureExpired(String treasureId) {
        Treasure treasure = unindex(treasureId);
        if (treasure == null) {
            return;
        }

        TreasureBlocks.removeChest(treasure.getLocation());
        cancelGuards(treasureId);
        removePointers(treasure);

        Player owner = Bukkit.getPlayer(treasure.getOwnerUuid());
        if (owner != null) {
            owner.sendMessage(ChatColor.RED + "你的宝藏已过期！");
        }
        for (UUID participantUuid : treasure.participantsView()) {
            Player participant = Bukkit.getPlayer(participantUuid);
            if (participant != null) {
                participant.sendMessage(ChatColor.RED + "你参与的宝藏已过期！");
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
        // 新发起的排在前面（列表按创建时间倒序），方便玩家优先看到最新活动
        available.sort(Comparator.comparingLong(Treasure::getCreateTime).reversed());
        return available;
    }

    public List<Treasure> getTreasuresByOwner(UUID ownerUuid) {
        List<Treasure> owned = new ArrayList<>();
        for (Treasure treasure : treasures.values()) {
            if (treasure.getOwnerUuid().equals(ownerUuid) && !treasure.isExpired()) {
                owned.add(treasure);
            }
        }
        owned.sort(Comparator.comparingLong(Treasure::getCreateTime).reversed());
        return owned;
    }

    public List<Treasure> getTreasuresByParticipant(UUID participantUuid) {
        List<Treasure> participating = new ArrayList<>();
        for (Treasure treasure : treasures.values()) {
            if (treasure.hasParticipant(participantUuid) && !treasure.isExpired()) {
                participating.add(treasure);
            }
        }
        participating.sort(Comparator.comparingLong(Treasure::getCreateTime).reversed());
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
            try {
                expireTreasure(treasure.getId());
            } catch (RuntimeException e) {
                plugin.getLogger().severe("清理过期宝藏 #" + treasure.getId() + " 失败: " + e.getMessage());
            }
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
