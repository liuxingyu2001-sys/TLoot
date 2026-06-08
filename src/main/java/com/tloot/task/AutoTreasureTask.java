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
import org.bukkit.block.Chest;
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

        int systemCount = countSystemTreasures();
        int maxActive = plugin.getConfigManager().getAutoTreasureMaxActive();

        if (systemCount >= maxActive) {
            return;
        }

        generateTreasure();
    }

    private int countSystemTreasures() {
        int count = 0;
        for (Treasure t : plugin.getTreasureManager().getAllTreasures()) {
            if (t.getOwnerUuid().equals(SYSTEM_OWNER_UUID) && !t.isExpired()) {
                count++;
            }
        }
        return count;
    }

    private void generateTreasure() {
        List<String> worlds = plugin.getConfigManager().getAutoTreasureWorlds();

        if (worlds.isEmpty()) {
            return;
        }

        String worldName = worlds.get(random.nextInt(worlds.size()));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }

        int[] range = plugin.getConfigManager().getAutoTreasureWorldRange(worldName);
        Location chestLoc = findSafeLocation(world, range);
        if (chestLoc == null) {
            plugin.getLogger().warning("无法在 " + worldName + " 找到安全位置（已尝试100次），请检查 Y 范围或世界地形");
            return;
        }

        List<ItemStack> loot = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        generateRewards(loot, commands);

        if (loot.isEmpty() && commands.isEmpty()) {
            plugin.getLogger().warning("战利品表为空，无法生成系统宝藏");
            return;
        }

        int minCoins = plugin.getConfigManager().getAutoTreasureMinCoins();
        int maxCoins = plugin.getConfigManager().getAutoTreasureMaxCoins();
        int guaranteedCoins = minCoins + random.nextInt(maxCoins - minCoins + 1);
        int ticketPrice = plugin.getConfigManager().getAutoTreasureTicketPrice();
        long expireTime = plugin.getConfigManager().getAutoTreasureExpireTime();

        Block block = chestLoc.getBlock();
        block.setType(Material.CHEST);

        Chest chest = (Chest) block.getState();
        for (ItemStack item : loot) {
            chest.getInventory().addItem(item);
        }

        TreasureManager treasureManager = plugin.getTreasureManager();
        Treasure treasure = treasureManager.createTreasure(
                SYSTEM_OWNER_UUID,
                SYSTEM_OWNER_NAME,
                chestLoc,
                guaranteedCoins,
                ticketPrice,
                loot,
                commands
        );

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

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);

            int highestY = world.getHighestBlockYAt(x, z);

            // 跳过超出 Y 范围的表面高度（如地狱基岩顶层）
            if (hasYRange && (highestY < minY || highestY > maxY)) {
                continue;
            }

            Location chestLoc = new Location(world, x + 0.5, highestY + 1, z + 0.5);

            Block ground = chestLoc.clone().subtract(0, 1, 0).getBlock();
            if (ground.isLiquid() || ground.isEmpty()) {
                continue;
            }

            Block chestBlock = chestLoc.getBlock();
            if (!chestBlock.isEmpty() && !chestBlock.getType().isAir()) {
                continue;
            }

            return chestLoc;
        }

        return null;
    }

    private void generateRewards(List<ItemStack> loot, List<String> commands) {
        List<LootEntry> lootTable = plugin.getConfigManager().getLootTable();
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
                        int amount = entry.getAmountMin()
                                + random.nextInt(entry.getAmountMax() - entry.getAmountMin() + 1);
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
