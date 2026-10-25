package com.tloot.config;

import com.tloot.TLoot;
import com.tloot.item.LiuLibItems;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    /** 金币相关配置的上限，防止 nextInt 溢出与离谱数值 */
    private static final int MAX_COINS = 100_000_000;
    /** 坐标范围上限（略大于原版世界边界 30,000,000），同时保证区间宽度不会让 int 溢出 */
    private static final int MAX_COORDINATE = 30_000_000;

    private final TLoot plugin;
    private FileConfiguration config;
    private List<String> allowedWorlds;
    private Map<String, String> worldNames;
    private List<LootEntry> lootTable;
    private Map<String, int[]> autoTreasureWorldRanges;
    private Map<String, Integer> doomMobs;

    public ConfigManager(TLoot plugin) {
        this.plugin = plugin;
        this.allowedWorlds = new ArrayList<>();
        this.worldNames = new HashMap<>();
        this.lootTable = new ArrayList<>();
        this.autoTreasureWorldRanges = new HashMap<>();
        this.doomMobs = new LinkedHashMap<>();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        loadAllowedWorlds();
        loadWorldNames();
        loadLootTable();
        loadAutoTreasureWorlds();
        loadDoomMobs();
        warnMissingWorldNames();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadAllowedWorlds();
        loadWorldNames();
        loadLootTable();
        loadAutoTreasureWorlds();
        loadDoomMobs();
        warnMissingWorldNames();
    }

    /**
     * world-names 缺少映射时，公告和界面会直接显示英文世界名（如 zysj）。
     * 启动/重载时给出明确警告，避免管理员误以为是插件乱码或英文 ID。
     */
    private void warnMissingWorldNames() {
        List<String> worlds = new ArrayList<>(allowedWorlds);
        for (String world : autoTreasureWorldRanges.keySet()) {
            if (!worlds.contains(world)) {
                worlds.add(world);
            }
        }
        for (String world : worlds) {
            String display = worldNames.get(world);
            if (display == null || display.isBlank()) {
                plugin.getLogger().warning("world-names 缺少世界 \"" + world
                        + "\" 的中文名，公告会显示英文世界名；请在 config.yml 的 world-names 下补充映射。");
            }
        }
    }

    private void loadAllowedWorlds() {
        allowedWorlds = new ArrayList<>(config.getStringList("allowed-worlds"));
    }

    private void loadWorldNames() {
        worldNames.clear();
        ConfigurationSection section = config.getConfigurationSection("world-names");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            worldNames.put(key, config.getString("world-names." + key));
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

    public int getClaimDistance() {
        // 负数会让距离校验出现「贴脸也领不了」；过大则等于取消距离限制
        return Math.max(1, Math.min(32, config.getInt("settings.claim-distance", 5)));
    }

    public long getExpireTime() {
        return expireMillis(config.getLong("settings.expire-time", 360));
    }

    /** 过期时间（分钟）→ 毫秒：至少 1 分钟、最多 1 年，避免 0 或超大数值导致过期判定/溢出异常 */
    private static long expireMillis(long minutes) {
        long safe = Math.max(1L, Math.min(525_600L, minutes));
        return safe * 60_000L;
    }

    public int getMinGuaranteedCoins() {
        return Math.max(0, Math.min(MAX_COINS, config.getInt("settings.min-guaranteed-coins", 100000)));
    }

    public int getMaxGuaranteedCoins() {
        int max = Math.max(0, Math.min(MAX_COINS, config.getInt("settings.max-guaranteed-coins", 1000000)));
        return Math.max(getMinGuaranteedCoins(), max);
    }

    public int getMinTicketPrice() {
        return Math.max(0, Math.min(MAX_COINS, config.getInt("settings.min-ticket-price", 500)));
    }

    /** 发起者是否获得门票收入（关闭后门票作为系统回收） */
    public boolean isOwnerIncomeEnabled() {
        return config.getBoolean("settings.owner-income", true);
    }

    public boolean isPointerActionBarEnabled() {
        return config.getBoolean("pointer.enable-actionbar", true);
    }

    // ==================== 光柱特效 ====================

    public boolean isBeaconEnabled() {
        return config.getBoolean("pointer.beacon.enabled", true);
    }

    public int getBeaconIntervalTicks() {
        // runTaskTimer 的 period 必须 >= 1，配置成 0 会直接抛异常导致重载失败
        return Math.max(5, Math.min(200, config.getInt("pointer.beacon.interval-ticks", 20)));
    }

    /** 光柱相对宝藏的最大高度（格），避免在世界最高处生成大量粒子 */
    public int getBeaconMaxHeight() {
        return Math.max(0, Math.min(320, config.getInt("pointer.beacon.max-height", 80)));
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
            int amountMin = toInt(raw.get("amount-min"));
            int amountMax = toInt(raw.get("amount-max"));
            // LiuLib 完整物品规格优先（base64:/nbt:/lib:/ce:/小写材质名…），解析失败再按原版材质兜底
            ItemStack template = LiuLibItems.buildOrNull(materialName);
            if (template != null) {
                lootTable.add(new LootEntry(template, amountMin, amountMax, weight));
                continue;
            }
            Material material = Material.getMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("战利品表包含无效材料: " + materialName);
                continue;
            }
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
        return Math.max(1, Math.min(1440, config.getInt("auto-treasure.interval", 30)));
    }

    public int getAutoTreasureMaxActive() {
        return Math.max(1, Math.min(50, config.getInt("auto-treasure.max-active", 3)));
    }

    public int getAutoTreasureTicketPrice() {
        return Math.max(0, Math.min(MAX_COINS, config.getInt("auto-treasure.ticket-price", 300)));
    }

    public long getAutoTreasureExpireTime() {
        return expireMillis(config.getLong("auto-treasure.expire-time", 120));
    }

    public int getAutoTreasureMinCoins() {
        return Math.max(0, Math.min(MAX_COINS, config.getInt("auto-treasure.min-guaranteed-coins", 50000)));
    }

    public int getAutoTreasureMaxCoins() {
        int max = Math.max(0, Math.min(MAX_COINS,
                config.getInt("auto-treasure.max-guaranteed-coins", 500000)));
        return Math.max(getAutoTreasureMinCoins(), max);
    }

    private void loadAutoTreasureWorlds() {
        autoTreasureWorldRanges.clear();
        ConfigurationSection section = config.getConfigurationSection("auto-treasure.worlds");
        if (section == null) {
            return;
        }
        for (String worldName : section.getKeys(false)) {
            String base = "auto-treasure.worlds." + worldName;
            int minX = clampCoordinate(config.getInt(base + ".min-x", -5000));
            int maxX = clampCoordinate(config.getInt(base + ".max-x", 5000));
            int minZ = clampCoordinate(config.getInt(base + ".min-z", -5000));
            int maxZ = clampCoordinate(config.getInt(base + ".max-z", 5000));
            int minY = Math.max(-64, Math.min(320, config.getInt(base + ".min-y", 5)));
            int maxY = Math.max(-64, Math.min(320, config.getInt(base + ".max-y", 256)));
            if (minX > maxX || minZ > maxZ || minY > maxY) {
                plugin.getLogger().warning("auto-treasure.worlds." + worldName
                        + " 的坐标区间为反向 (min > max)，已自动纠正；建议修正配置");
                int tmp;
                if (minX > maxX) { tmp = minX; minX = maxX; maxX = tmp; }
                if (minZ > maxZ) { tmp = minZ; minZ = maxZ; maxZ = tmp; }
                if (minY > maxY) { tmp = minY; minY = maxY; maxY = tmp; }
            }
            autoTreasureWorldRanges.put(worldName, new int[]{minX, maxX, minZ, maxZ, minY, maxY});
        }
    }

    private static int clampCoordinate(int value) {
        return Math.max(-MAX_COORDINATE, Math.min(MAX_COORDINATE, value));
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

    // ==================== 厄运宝藏 ====================

    /** 单个怪物条目最多生成数量，避免误配置成上千只把服务器刷崩 */
    private static final int MAX_DOOM_MOBS_PER_TYPE = 20;
    /** 所有守卫怪物总数上限 */
    private static final int MAX_DOOM_MOBS_TOTAL = 50;

    private void loadDoomMobs() {
        doomMobs.clear();
        ConfigurationSection section = config.getConfigurationSection("doom-treasure.mobs");
        if (section == null) {
            return;
        }
        int total = 0;
        for (String mobName : section.getKeys(false)) {
            if (total >= MAX_DOOM_MOBS_TOTAL) {
                plugin.getLogger().warning("doom-treasure.mobs 守卫总数超过 " + MAX_DOOM_MOBS_TOTAL
                        + "，超出部分已忽略；请检查配置是否合理");
                break;
            }
            int raw = config.getInt("doom-treasure.mobs." + mobName, 1);
            if (raw <= 0) {
                continue;
            }
            int count = Math.min(MAX_DOOM_MOBS_PER_TYPE, raw);
            int allowed = Math.min(count, MAX_DOOM_MOBS_TOTAL - total);
            doomMobs.put(mobName, allowed);
            total += allowed;
        }
    }

    public boolean isDoomTreasureEnabled() {
        return config.getBoolean("doom-treasure.enabled", false);
    }

    /** 每次自动寻宝生成时转为厄运宝藏的概率 */
    public double getDoomChance() {
        double chance = config.getDouble("doom-treasure.chance", 0.25);
        if (!Double.isFinite(chance)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, chance));
    }

    /** MythicMobs 内部名 → 生成数量 */
    public Map<String, Integer> getDoomMobs() {
        return new LinkedHashMap<>(doomMobs);
    }

    public int getDoomSpawnRadius() {
        int radius = config.getInt("doom-treasure.spawn-radius", 6);
        return Math.max(2, Math.min(32, radius));
    }

    public double getDoomCoinMultiplier() {
        return doomMultiplier("coin-multiplier", 3.0);
    }

    public double getDoomTicketMultiplier() {
        return doomMultiplier("ticket-multiplier", 2.0);
    }

    /** 倍率必须有限且不超过 100，避免 NaN/Infinity 造成金币溢出或变成 0 */
    private double doomMultiplier(String key, double def) {
        double multiplier = config.getDouble("doom-treasure." + key, def);
        if (!Double.isFinite(multiplier)) {
            return 1.0;
        }
        return Math.max(1.0, Math.min(100.0, multiplier));
    }

    /** 在普通战利品基础上额外抽取的次数 */
    public int getDoomExtraLootRolls() {
        int rolls = config.getInt("doom-treasure.extra-loot-rolls", 3);
        return Math.max(0, Math.min(10, rolls));
    }

    // ==================== 存储设置 ====================

    public String getStorageType() {
        return config.getString("storage.type", "yaml");
    }

    // ==================== 数据库设置 ====================

    public String getDatabaseHost() {
        return config.getString("database.host", "localhost");
    }

    public int getDatabasePort() {
        return config.getInt("database.port", 3306);
    }

    public String getDatabaseName() {
        return config.getString("database.database", "tloot");
    }

    public String getDatabaseUsername() {
        return config.getString("database.username", "root");
    }

    public String getDatabasePassword() {
        return config.getString("database.password", "");
    }

    public String getDatabaseTablePrefix() {
        return config.getString("database.table-prefix", "tloot_");
    }

    public int getDatabasePoolSize() {
        return Math.max(1, Math.min(64, config.getInt("database.pool-size", 10)));
    }

    // ==================== Redis 设置 ====================

    public boolean isRedisEnabled() {
        return config.getBoolean("redis.enabled", false);
    }

    public String getRedisHost() {
        return config.getString("redis.host", "localhost");
    }

    public int getRedisPort() {
        return config.getInt("redis.port", 6379);
    }

    public String getRedisPassword() {
        return config.getString("redis.password", "");
    }

    public String getRedisChannel() {
        return config.getString("redis.channel", "tloot:sync");
    }

    public String getRedisServerId() {
        return config.getString("redis.server-id", "");
    }
}
