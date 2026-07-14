package com.tloot.data;

import com.tloot.TLoot;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TreasureManager {

    private final TLoot plugin;
    private final File dataFile;
    private final Map<String, Treasure> treasures;
    private final Map<UUID, String> playerCreatingTreasure;
    private final Map<String, String> locationIndex;  // 位置到宝藏ID的索引

    public TreasureManager(TLoot plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.treasures = new ConcurrentHashMap<>();
        this.playerCreatingTreasure = new ConcurrentHashMap<>();
        this.locationIndex = new ConcurrentHashMap<>();
    }

    public void loadTreasures() {
        if (!dataFile.exists()) {
            return;
        }

        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection treasuresSection = dataConfig.getConfigurationSection("treasures");

        if (treasuresSection == null) {
            return;
        }

        locationIndex.clear();

        for (String id : treasuresSection.getKeys(false)) {
            ConfigurationSection treasureSection = treasuresSection.getConfigurationSection(id);
            if (treasureSection != null) {
                Map<String, Object> data = new HashMap<>();
                for (String key : treasureSection.getKeys(false)) {
                    data.put(key, treasureSection.get(key));
                }
                Treasure treasure = Treasure.deserialize(data);
                if (treasure != null && !treasure.isExpired()) {
                    treasures.put(id, treasure);
                    // 添加位置索引
                    locationIndex.put(locationToKey(treasure.getLocation()), id);
                }
            }
        }
    }

    public void saveTreasures() {
        FileConfiguration dataConfig = new YamlConfiguration();
        
        for (Map.Entry<String, Treasure> entry : treasures.entrySet()) {
            String path = "treasures." + entry.getKey();
            Map<String, Object> data = entry.getValue().serialize();
            for (Map.Entry<String, Object> dataEntry : data.entrySet()) {
                dataConfig.set(path + "." + dataEntry.getKey(), dataEntry.getValue());
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存宝藏数据: " + e.getMessage());
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

    /**
     * 创建宝藏，使用自定义过期时间（供系统自动寻宝使用）
     */
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
        locationIndex.put(locationToKey(location), id);
        saveTreasures();

        return treasure;
    }

    public void removeTreasure(String id) {
        Treasure treasure = treasures.remove(id);
        if (treasure != null) {
            locationIndex.remove(locationToKey(treasure.getLocation()));
        }
        saveTreasures();
    }

    private String locationToKey(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getName() + "," +
               location.getBlockX() + "," +
               location.getBlockY() + "," +
               location.getBlockZ();
    }

    public Treasure findTreasureAtLocation(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String key = locationToKey(location);
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
            treasures.remove(id);
        }
        
        if (!toRemove.isEmpty()) {
            saveTreasures();
        }
    }
}
