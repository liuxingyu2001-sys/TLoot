package com.tloot.config;

import com.tloot.TLoot;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final TLoot plugin;
    private FileConfiguration config;
    private List<String> allowedWorlds;
    private Map<String, String> worldNames;
    private List<LootEntry> lootTable;
    private Map<String, int[]> autoTreasureWorldRanges;

    public ConfigManager(TLoot plugin) {
        this.plugin = plugin;
        this.allowedWorlds = new ArrayList<>();
        this.worldNames = new HashMap<>();
        this.lootTable = new ArrayList<>();
        this.autoTreasureWorldRanges = new HashMap<>();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        loadAllowedWorlds();
        loadWorldNames();
        loadLootTable();
        loadAutoTreasureWorlds();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadAllowedWorlds();
        loadWorldNames();
        loadLootTable();
        loadAutoTreasureWorlds();
    }

    private void loadAllowedWorlds() {
        allowedWorlds = config.getStringList("allowed-worlds");
    }

    private void loadWorldNames() {
        worldNames.clear();
        if (config.contains("world-names")) {
            for (String key : config.getConfigurationSection("world-names").getKeys(false)) {
                worldNames.put(key, config.getString("world-names." + key));
            }
        }
    }

    public boolean isWorldAllowed(String worldName) {
        if (allowedWorlds.isEmpty()) {
            return true;
        }
        return allowedWorlds.contains(worldName);
    }

    public String getWorldDisplayName(String worldName) {
        return worldNames.getOrDefault(worldName, worldName);
    }

    public int getTicketPrice() {
        return config.getInt("settings.ticket-price", 500);
    }

    public int getClaimDistance() {
        return config.getInt("settings.claim-distance", 5);
    }

    public long getExpireTime() {
        return config.getLong("settings.expire-time", 360) * 60 * 1000;
    }

    public int getMinGuaranteedCoins() {
        return config.getInt("settings.min-guaranteed-coins", 100000);
    }

    public int getMaxGuaranteedCoins() {
        return config.getInt("settings.max-guaranteed-coins", 1000000);
    }

    public int getMinTicketPrice() {
        return config.getInt("settings.min-ticket-price", 500);
    }

    public boolean isPointerActionBarEnabled() {
        return config.getBoolean("pointer.enable-actionbar", true);
    }

    public int getGuiSize(String guiName) {
        return config.getInt("gui." + guiName + ".size", 27);
    }

    public String getGuiTitle(String guiName) {
        String title = config.getString("gui." + guiName + ".title", "寻宝系统");
        return ChatColor.translateAlternateColorCodes('&', title);
    }

    public int getGuaranteedCoinsLeftClick() {
        return config.getInt("gui.amount-adjust.guaranteed-coins.left-click", 1000);
    }

    public int getGuaranteedCoinsRightClick() {
        return config.getInt("gui.amount-adjust.guaranteed-coins.right-click", 1000);
    }

    public int getGuaranteedCoinsShiftLeftClick() {
        return config.getInt("gui.amount-adjust.guaranteed-coins.shift-left-click", 10000);
    }

    public int getGuaranteedCoinsShiftRightClick() {
        return config.getInt("gui.amount-adjust.guaranteed-coins.shift-right-click", 100000);
    }

    public int getTicketPriceLeftClick() {
        return config.getInt("gui.amount-adjust.ticket-price.left-click", 100);
    }

    public int getTicketPriceRightClick() {
        return config.getInt("gui.amount-adjust.ticket-price.right-click", 100);
    }

    public int getTicketPriceShiftLeftClick() {
        return config.getInt("gui.amount-adjust.ticket-price.shift-left-click", 10000);
    }

    public int getTicketPriceShiftRightClick() {
        return config.getInt("gui.amount-adjust.ticket-price.shift-right-click", 100000);
    }

    // ==================== 定时自动寻宝 ====================

    private void loadLootTable() {
        lootTable.clear();
        List<Map<?, ?>> rawList = config.getMapList("auto-treasure.loot-table");
        for (Map<?, ?> raw : rawList) {
            int weight = toInt(raw.get("weight"));

            // 指令奖励
            if (raw.containsKey("command")) {
                String command = (String) raw.get("command");
                if (command != null && !command.isEmpty()) {
                    lootTable.add(new LootEntry(command, weight));
                }
                continue;
            }

            // 物品奖励
            String materialName = (String) raw.get("material");
            if (materialName == null) {
                plugin.getLogger().warning("战利品表条目缺少 material 或 command");
                continue;
            }
            Material material = Material.getMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("战利品表包含无效材料: " + materialName);
                continue;
            }
            int amountMin = toInt(raw.get("amount-min"));
            int amountMax = toInt(raw.get("amount-max"));
            lootTable.add(new LootEntry(material, amountMin, amountMax, weight));
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    public List<LootEntry> getLootTable() {
        return new ArrayList<>(lootTable);
    }

    public boolean isAutoTreasureEnabled() {
        return config.getBoolean("auto-treasure.enabled", true);
    }

    public int getAutoTreasureInterval() {
        return config.getInt("auto-treasure.interval", 30);
    }

    public int getAutoTreasureMaxActive() {
        return config.getInt("auto-treasure.max-active", 3);
    }

    public int getAutoTreasureTicketPrice() {
        return config.getInt("auto-treasure.ticket-price", 300);
    }

    public long getAutoTreasureExpireTime() {
        return config.getLong("auto-treasure.expire-time", 120) * 60 * 1000;
    }

    public int getAutoTreasureMinCoins() {
        return config.getInt("auto-treasure.min-guaranteed-coins", 50000);
    }

    public int getAutoTreasureMaxCoins() {
        return config.getInt("auto-treasure.max-guaranteed-coins", 500000);
    }

    private void loadAutoTreasureWorlds() {
        autoTreasureWorldRanges.clear();
        if (!config.contains("auto-treasure.worlds")) {
            return;
        }
        for (String worldName : config.getConfigurationSection("auto-treasure.worlds").getKeys(false)) {
            String base = "auto-treasure.worlds." + worldName;
            int minX = config.getInt(base + ".min-x", -5000);
            int maxX = config.getInt(base + ".max-x", 5000);
            int minZ = config.getInt(base + ".min-z", -5000);
            int maxZ = config.getInt(base + ".max-z", 5000);
            int minY = config.getInt(base + ".min-y", 5);
            int maxY = config.getInt(base + ".max-y", 256);
            autoTreasureWorldRanges.put(worldName, new int[]{minX, maxX, minZ, maxZ, minY, maxY});
        }
    }

    /**
     * @return 配置了自动寻宝范围的世界名列表；为空则回退到 allowed-worlds
     */
    public List<String> getAutoTreasureWorlds() {
        if (!autoTreasureWorldRanges.isEmpty()) {
            return new ArrayList<>(autoTreasureWorldRanges.keySet());
        }
        return new ArrayList<>(allowedWorlds);
    }

    /**
     * @return int[]{minX, maxX, minZ, maxZ, minY, maxY} 或 null（回退到世界边界）
     */
    public int[] getAutoTreasureWorldRange(String worldName) {
        return autoTreasureWorldRanges.get(worldName);
    }
}
