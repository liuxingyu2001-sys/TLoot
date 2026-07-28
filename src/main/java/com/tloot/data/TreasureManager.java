package com.tloot.data;

import com.tloot.TLoot;
import com.tloot.storage.StorageBackend;
import com.tloot.sync.RedisSyncManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TreasureManager implements RedisSyncManager.SyncCallback {

    private final TLoot plugin;
    private final Map<String, Treasure> treasures;
    private final Map<UUID, String> playerCreatingTreasure;
    private final Map<String, String> locationIndex;

    private StorageBackend storage;
    private RedisSyncManager syncManager;

    public TreasureManager(TLoot plugin) {
        this.plugin = plugin;
        this.treasures = new ConcurrentHashMap<>();
        this.playerCreatingTreasure = new ConcurrentHashMap<>();
        this.locationIndex = new ConcurrentHashMap<>();
    }

    public void setStorage(StorageBackend storage) {
        this.storage = storage;
    }

    public void setSyncManager(RedisSyncManager syncManager) {
        this.syncManager = syncManager;
    }

    public void loadTreasures() {
        if (storage == null) {
            return;
        }
        Map<String, Treasure> loaded = storage.loadAll();
        treasures.clear();
        locationIndex.clear();
        treasures.putAll(loaded);
        for (Map.Entry<String, Treasure> entry : loaded.entrySet()) {
            locationIndex.put(entry.getValue().getLocationKey(), entry.getKey());
        }
        plugin.getLogger().info("已加载 " + treasures.size() + " 个宝藏");
    }

    public void saveTreasures() {
        if (storage != null) {
            storage.saveAll(treasures.values());
        }
    }

    public Treasure createTreasure(UUID ownerUuid, String ownerName,
                                    org.bukkit.Location location,
                                    int guaranteedCoins,
                                    int ticketPrice,
                                    List<org.bukkit.inventory.ItemStack> items) {
        return createTreasure(ownerUuid, ownerName, location, guaranteedCoins, ticketPrice, items, null);
    }

    public Treasure createTreasure(UUID ownerUuid, String ownerName,
                                    org.bukkit.Location location,
                                    int guaranteedCoins,
                                    int ticketPrice,
                                    List<org.bukkit.inventory.ItemStack> items,
                                    List<String> commands) {
        return createTreasure(ownerUuid, ownerName, location, guaranteedCoins, ticketPrice, items, commands, plugin.getConfigManager().getExpireTime());
    }

    public Treasure createTreasure(UUID ownerUuid, String ownerName,
                                    org.bukkit.Location location,
                                    int guaranteedCoins,
                                    int ticketPrice,
                                    List<org.bukkit.inventory.ItemStack> items,
                                    List<String> commands,
                                    long expireTimeMillis) {
        String id = generateId();

        Treasure treasure = new Treasure(id, ownerUuid, ownerName, location,
                                         guaranteedCoins, ticketPrice, items, commands, expireTimeMillis, System.currentTimeMillis());
        treasures.put(id, treasure);
        locationIndex.put(treasure.getLocationKey(), id);

        if (storage != null) {
            storage.save(treasure);
        }
        if (syncManager != null) {
            syncManager.publishCreate(treasure);
        }

        return treasure;
    }

    public void removeTreasure(String id) {
        Treasure treasure = treasures.remove(id);
        if (treasure != null) {
            locationIndex.remove(treasure.getLocationKey());
        }
        if (storage != null) {
            storage.delete(id);
        }
        if (syncManager != null) {
            syncManager.publishRemove(id);
        }
    }

    public void claimTreasure(String id, String claimerName) {
        Treasure treasure = treasures.remove(id);
        if (treasure != null) {
            locationIndex.remove(treasure.getLocationKey());
        }
        if (storage != null) {
            storage.delete(id);
        }
        if (syncManager != null) {
            String ownerName = treasure != null ? treasure.getOwnerName() : "未知";
            syncManager.publishClaim(id, claimerName, ownerName);
        }
    }

    public void expireTreasure(String id) {
        Treasure treasure = treasures.remove(id);
        if (treasure != null) {
            locationIndex.remove(treasure.getLocationKey());
        }
        if (storage != null) {
            storage.delete(id);
        }
        if (syncManager != null) {
            syncManager.publishExpire(id);
        }
    }

    public void addParticipant(String treasureId, UUID participantUuid) {
        Treasure treasure = treasures.get(treasureId);
        if (treasure != null) {
            treasure.addParticipant(participantUuid);
            if (storage != null) {
                storage.save(treasure);
            }
            if (syncManager != null) {
                syncManager.publishJoin(treasureId, participantUuid);
            }
        }
    }

    // ==================== Redis 同步回调（来自其他服务器） ====================

    @Override
    public void onTreasureCreated(Treasure treasure) {
        treasures.put(treasure.getId(), treasure);
        locationIndex.put(treasure.getLocationKey(), treasure.getId());

        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());
        boolean isSystem = treasure.getOwnerUuid().equals(com.tloot.task.AutoTreasureTask.SYSTEM_OWNER_UUID);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!isSystem && onlinePlayer.getUniqueId().equals(treasure.getOwnerUuid())) {
                continue;
            }

            TextComponent message;
            if (isSystem) {
                message = new TextComponent(
                        plugin.getMessageManager().get("prefix") +
                        ChatColor.GOLD + "【系统寻宝】" +
                        ChatColor.GREEN + "一个新的宝藏出现了！ " +
                        ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins() + " " +
                        ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice() + " " +
                        ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName
                );
            } else {
                message = new TextComponent(
                        plugin.getMessageManager().get("prefix") +
                        ChatColor.GREEN + " " + treasure.getOwnerName() + " 发起了一个寻宝！ " +
                        ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins() + " " +
                        ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice() + " " +
                        ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName
                );
            }
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/treasure join " + treasure.getId()));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.GREEN + "点击参与此寻宝").create()));

            onlinePlayer.spigot().sendMessage(message);
        }
    }

    @Override
    public void onTreasureRemoved(String treasureId) {
        Treasure treasure = treasures.remove(treasureId);
        if (treasure != null) {
            locationIndex.remove(treasure.getLocationKey());
        }
    }

    @Override
    public void onTreasureClaimed(String treasureId, String claimerName, String ownerName) {
        Treasure treasure = treasures.remove(treasureId);
        if (treasure != null) {
            locationIndex.remove(treasure.getLocationKey());
        }
        plugin.getServer().broadcastMessage(
                plugin.getMessageManager().get("prefix") +
                "§e" + claimerName + " §a找到了 §e" + ownerName + " §a发起的宝藏！"
        );
    }

    @Override
    public void onTreasureExpired(String treasureId) {
        Treasure treasure = treasures.remove(treasureId);
        if (treasure != null) {
            locationIndex.remove(treasure.getLocationKey());

            Player owner = Bukkit.getPlayer(treasure.getOwnerUuid());
            if (owner != null) {
                owner.sendMessage(ChatColor.RED + "你的宝藏 #" + treasureId + " 已过期！");
            }
            for (UUID participantUuid : treasure.getParticipants()) {
                Player participant = Bukkit.getPlayer(participantUuid);
                if (participant != null) {
                    participant.sendMessage(ChatColor.RED + "宝藏 #" + treasureId + " 已过期！");
                }
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

    public Treasure findTreasureAtLocation(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String key = location.getWorld().getName() + "," +
                     location.getBlockX() + "," +
                     location.getBlockY() + "," +
                     location.getBlockZ();
        String id = locationIndex.get(key);
        return id != null ? treasures.get(id) : null;
    }

    public Treasure getTreasure(String id) {
        return treasures.get(id);
    }

    public List<Treasure> getAllTreasures() {
        return new ArrayList<>(treasures.values());
    }

    public List<Treasure> getAvailableTreasures(UUID playerUuid) {
        List<Treasure> available = new ArrayList<>();
        for (Treasure treasure : treasures.values()) {
            if (!treasure.getOwnerUuid().equals(playerUuid) && !treasure.isExpired()) {
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

    // ==================== 创建流程状态 ====================

    public void setPlayerCreatingTreasure(UUID playerUuid, String treasureId) {
        playerCreatingTreasure.put(playerUuid, treasureId);
    }

    public String getPlayerCreatingTreasure(UUID playerUuid) {
        return playerCreatingTreasure.get(playerUuid);
    }

    public void removePlayerCreatingTreasure(UUID playerUuid) {
        playerCreatingTreasure.remove(playerUuid);
    }

    public boolean isPlayerCreatingTreasure(UUID playerUuid) {
        return playerCreatingTreasure.containsKey(playerUuid);
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public void cleanupExpiredTreasures() {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Treasure> entry : treasures.entrySet()) {
            Treasure treasure = entry.getValue();
            if (treasure.isExpired()) {
                toRemove.add(entry.getKey());
            }
        }

        for (String id : toRemove) {
            Treasure treasure = treasures.remove(id);
            if (treasure != null) {
                locationIndex.remove(treasure.getLocationKey());
            }
            if (storage != null) {
                storage.delete(id);
            }
            if (syncManager != null) {
                syncManager.publishExpire(id);
            }
        }
    }
}
