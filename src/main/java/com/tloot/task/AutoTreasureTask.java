package com.tloot.task;

import com.tloot.TLoot;
import com.tloot.config.LootEntry;
import com.tloot.data.Treasure;
import com.tloot.data.TreasureManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class AutoTreasureTask extends BukkitRunnable {

    public static final UUID SYSTEM_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    public static final String SYSTEM_OWNER_NAME = "服务器";

    private final TLoot plugin;
    private final Random random = new Random();

    public AutoTreasureTask(TLoot plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfigManager().isAutoTreasureEnabled()) {
            return;
        }

        int maxActive = Math.max(1, plugin.getConfigManager().getAutoTreasureMaxActive());
        int systemCount = countSystemTreasures();

        if (systemCount >= maxActive) {
            plugin.getLogger().info("[自动寻宝] 当前系统宝藏 " + systemCount + "/" + maxActive + "，已达上限，跳过本次生成");
            return;
        }

        generateTreasure();
    }

    private int countSystemTreasures() {
        return plugin.getTreasureManager().countSystemTreasures();
    }

    private void generateTreasure() {
        List<String> worlds = plugin.getConfigManager().getAutoTreasureWorlds();

        if (worlds.isEmpty()) {
            plugin.getLogger().warning("[自动寻宝] 没有配置可用的世界！请在 config.yml 的 auto-treasure.worlds 中配置");
            return;
        }

        // 随机选世界，优先选已加载的
        List<String> loadedWorlds = new ArrayList<>();
        for (String wn : worlds) {
            if (Bukkit.getWorld(wn) != null) {
                loadedWorlds.add(wn);
            }
        }

        if (loadedWorlds.isEmpty()) {
            plugin.getLogger().warning("[自动寻宝] 所有配置的世界均未加载！worlds=" + String.join(", ", worlds));
            return;
        }

        String worldName = loadedWorlds.get(random.nextInt(loadedWorlds.size()));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return; // 不应到达
        }

        int[] range = plugin.getConfigManager().getAutoTreasureWorldRange(worldName);
        Location chestLoc = findSafeLocation(world, range);
        if (chestLoc == null) {
            plugin.getLogger().warning("[自动寻宝] 在 " + worldName + " 中尝试 100 次未找到安全位置（可能区块未生成或地形不合适）");
            return;
        }

        List<ItemStack> loot = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        generateRewards(loot, commands);

        if (loot.isEmpty() && commands.isEmpty()) {
            plugin.getLogger().warning("[自动寻宝] 战利品表为空，无法生成系统宝藏");
            return;
        }

        int minCoins = plugin.getConfigManager().getAutoTreasureMinCoins();
        int maxCoins = plugin.getConfigManager().getAutoTreasureMaxCoins();
        if (maxCoins < minCoins) {
            plugin.getLogger().warning("[自动寻宝] 保底金币区间配置非法 (min=" + minCoins
                    + ", max=" + maxCoins + ")，已交换处理");
            int swapped = minCoins;
            minCoins = maxCoins;
            maxCoins = swapped;
        }
        int guaranteedCoins = minCoins + random.nextInt(maxCoins - minCoins + 1);
        int ticketPrice = plugin.getConfigManager().getAutoTreasureTicketPrice();
        long expireTime = plugin.getConfigManager().getAutoTreasureExpireTime();

        // 强制加载区块，确保方块操作安全
        world.getChunkAt(chestLoc).load(true);

        TreasureManager treasureManager = plugin.getTreasureManager();
        if (treasureManager.findTreasureAtLocation(chestLoc) != null) {
            plugin.getLogger().warning("[自动寻宝] 位置已被其它宝藏占用，跳过本次生成");
            return;
        }

        // 先建立宝藏记录，成功后再放置箱子；否则生成失败会留下一个无法领取的"幽灵箱子"
        Treasure treasure;
        try {
            // 使用系统宝藏专属的过期时间
            treasure = treasureManager.createTreasure(
                    SYSTEM_OWNER_UUID,
                    SYSTEM_OWNER_NAME,
                    chestLoc,
                    guaranteedCoins,
                    ticketPrice,
                    loot,
                    commands,
                    expireTime
            );
        } catch (Exception e) {
            plugin.getLogger().severe("[自动寻宝] 创建系统宝藏失败: " + e.getMessage());
            return;
        }

        // 奖励物品只保存在 Treasure 记录中，不物理放入箱子：
        // 否则领取/过期移除箱子时物品会洒落在地上造成重复发放，且可被漏斗抽走
        Block block = chestLoc.getBlock();
        block.setType(Material.CHEST, false);

        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(worldName);
        plugin.getLogger().info("[自动寻宝] 已生成宝藏 #" + treasure.getId()
                + " 世界=" + worldDisplayName
                + " 保底=" + guaranteedCoins
                + " 费用=" + ticketPrice
                + " 过期=" + (expireTime / 60000) + "分钟");

        broadcastTreasure(treasure);
    }

    private Location findSafeLocation(World world, int[] range) {
        int maxAttempts = 100;

        int minX, maxX, minZ, maxZ, minY, maxY;
        boolean hasYRange = false;
        if (range != null) {
            minX = range[0];
            maxX = range[1];
            minZ = range[2];
            maxZ = range[3];
            if (range.length >= 6) {
                minY = range[4];
                maxY = range[5];
                hasYRange = true;
            } else {
                minY = 0;
                maxY = world.getMaxHeight();
            }
        } else {
            WorldBorder border = world.getWorldBorder();
            double halfSize = border.getSize() / 2.0;
            double centerX = border.getCenter().getX();
            double centerZ = border.getCenter().getZ();
            minX = (int) (centerX - halfSize * 0.8);
            maxX = (int) (centerX + halfSize * 0.8);
            minZ = (int) (centerZ - halfSize * 0.8);
            maxZ = (int) (centerZ + halfSize * 0.8);
            minY = 0;
            maxY = world.getMaxHeight();
        }

        // 归一化配置区间：min > max 时 random.nextInt 会抛 IllegalArgumentException，
        // 进而取消整个重复任务（自动寻宝会永久停止）
        if (minX > maxX) {
            int tmp = minX;
            minX = maxX;
            maxX = tmp;
        }
        if (minZ > maxZ) {
            int tmp = minZ;
            minZ = maxZ;
            maxZ = tmp;
        }
        if (hasYRange && minY > maxY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }
        if (minX > maxX || minZ > maxZ || (hasYRange && minY > maxY)) {
            plugin.getLogger().warning("[自动寻宝] 世界 " + world.getName()
                    + " 的坐标范围配置为反向区间 (min > max)，已自动纠正，建议修正配置");
        }

        int skippedUngenerated = 0;
        int skippedYRange = 0;
        int skippedBadGround = 0;
        int skippedBlocked = 0;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);

            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            // 跳过未生成的区块，避免触发区块生成导致服务器卡顿或崩溃
            if (!world.isChunkGenerated(chunkX, chunkZ)) {
                skippedUngenerated++;
                continue;
            }

            // 加载区块确保方块数据在内存中
            world.getChunkAt(chunkX, chunkZ).load(true);

            int highestY = world.getHighestBlockYAt(x, z);

            // 跳过超出 Y 范围的表面高度（如地狱基岩顶层）
            if (hasYRange && (highestY < minY || highestY > maxY)) {
                skippedYRange++;
                continue;
            }

            Location chestLoc = new Location(world, x + 0.5, highestY + 1, z + 0.5);

            Block ground = chestLoc.clone().subtract(0, 1, 0).getBlock();
            if (ground.isLiquid() || ground.isEmpty()) {
                skippedBadGround++;
                continue;
            }

            Block chestBlock = chestLoc.getBlock();
            if (!chestBlock.isEmpty() && !chestBlock.getType().isAir()) {
                skippedBlocked++;
                continue;
            }

            return chestLoc;
        }

        // 详细失败原因
        plugin.getLogger().warning("[自动寻宝] 位置搜索失败统计 (世界=" + world.getName()
                + "): 未生成区块=" + skippedUngenerated
                + ", Y范围不符=" + skippedYRange
                + ", 地面不合适=" + skippedBadGround
                + ", 位置被占=" + skippedBlocked);

        return null;
    }

    private void generateRewards(List<ItemStack> loot, List<String> commands) {
        // 过滤权重 <= 0 的条目，避免 totalWeight 为 0 时 random.nextInt(0) 抛异常导致定时任务被取消
        List<LootEntry> lootTable = new ArrayList<>();
        for (LootEntry entry : plugin.getConfigManager().getLootTable()) {
            if (entry.getWeight() > 0) {
                lootTable.add(entry);
            }
        }
        if (lootTable.isEmpty()) {
            return;
        }

        int totalWeight = 0;
        for (LootEntry entry : lootTable) {
            totalWeight += entry.getWeight();
        }

        int itemCount = 3 + random.nextInt(4);

        for (int i = 0; i < itemCount; i++) {
            int roll = random.nextInt(totalWeight);
            int cumulative = 0;

            for (LootEntry entry : lootTable) {
                cumulative += entry.getWeight();
                if (roll < cumulative) {
                    if (entry.isCommand()) {
                        commands.add(entry.getCommand());
                    } else {
                        int amountMin = Math.max(1, entry.getAmountMin());
                        int amountMax = Math.max(amountMin, entry.getAmountMax());
                        int amount = amountMin + random.nextInt(amountMax - amountMin + 1);
                        loot.add(new ItemStack(entry.getMaterial(), amount));
                    }
                    break;
                }
            }
        }
    }

    private void broadcastTreasure(Treasure treasure) {
        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            TextComponent message = new TextComponent(
                    plugin.getMessageManager().get("prefix") +
                    ChatColor.GOLD + "【系统寻宝】" +
                    ChatColor.GREEN + "一个新的宝藏出现了！ " +
                    ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins() + " " +
                    ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice() + " " +
                    ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName
            );
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/treasure join " + treasure.getId()));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.GREEN + "点击参与此系统寻宝").create()));

            onlinePlayer.spigot().sendMessage(message);
        }
    }
}
