package com.tloot.gui;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.item.TreasureSignItem;
import com.tloot.task.AutoTreasureTask;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GUIManager {

    private final TLoot plugin;
    private final Map<UUID, String> playerOpenGUI;
    private final Map<UUID, Integer> playerCreateCoins;
    private final Map<UUID, Integer> playerTicketPrice;
    private final Map<UUID, Integer> playerCompassPage;
    private final Map<UUID, Integer> playerMyPage;

    public GUIManager(TLoot plugin) {
        this.plugin = plugin;
        this.playerOpenGUI = new HashMap<>();
        this.playerCreateCoins = new HashMap<>();
        this.playerTicketPrice = new HashMap<>();
        this.playerCompassPage = new HashMap<>();
        this.playerMyPage = new HashMap<>();
    }

    // ==================== 通用辅助方法 ====================

    /**
     * 播放 GUI 点击音效
     */
    public static void playClickSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.5f);
    }

    /**
     * 播放成功音效
     */
    public static void playSuccessSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
    }

    /**
     * 播放失败音效
     */
    public static void playFailSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.0f);
    }

    /**
     * 创建带附魔光泽的 GUI 物品（纯装饰用）
     */
    private ItemStack createGlowingGuiItem(Material material, String name, String... lore) {
        ItemStack item = createGuiItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(line);
        }
        meta.setLore(loreList);

        NamespacedKey key = new NamespacedKey(plugin, "gui_item");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "special");

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isGuiItem(TLoot plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        NamespacedKey key = new NamespacedKey(plugin, "gui_item");
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    /**
     * 装饰性玻璃板填充
     */
    private ItemStack glassPane(short color) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        glass.setItemMeta(meta);
        return glass;
    }

    // ==================== 主菜单 ====================

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                plugin.getConfigManager().getGuiTitle("main"));

        // 顶部和底部玻璃板装饰边框
        ItemStack border = glassPane((short) 0);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }
        inv.setItem(17, border);
        inv.setItem(18, border);
        for (int i = 9; i < 27; i += 9) {
            inv.setItem(i, border);
            inv.setItem(i + 8, border);
        }

        // 发起寻宝 - 钻石（附魔光泽）
        ItemStack createItem = createGlowingGuiItem(Material.DIAMOND,
                ChatColor.GREEN + "✦ 发起寻宝 ✦",
                "",
                ChatColor.GRAY + "▸ 设置保底金币和参与费用",
                ChatColor.GRAY + "▸ 获取寻宝告示牌放置到箱子上",
                ChatColor.GRAY + "▸ 寻宝信息将广播全服",
                "",
                ChatColor.YELLOW + "点击打开发起界面");
        inv.setItem(11, createItem);

        // 参与寻宝 - 地图（附魔光泽）
        List<Treasure> available = plugin.getTreasureManager()
                .getAvailableTreasures(player.getUniqueId());
        int availableCount = available.size();

        ItemStack joinItem = createGlowingGuiItem(Material.FILLED_MAP,
                ChatColor.YELLOW + "✦ 参与寻宝 ✦",
                "",
                ChatColor.GRAY + "▸ 浏览所有可参与的寻宝列表",
                ChatColor.GRAY + "▸ 支付参与费用获得寻宝指针",
                ChatColor.GRAY + "▸ 手持指针在世界中寻找宝藏",
                "",
                ChatColor.AQUA + "当前可参与: " + ChatColor.WHITE + availableCount + " 个",
                "",
                ChatColor.YELLOW + "点击浏览寻宝列表");
        inv.setItem(13, joinItem);

        // 我的寻宝 - 箱子
        List<Treasure> owned = plugin.getTreasureManager()
                .getTreasuresByOwner(player.getUniqueId());
        List<Treasure> participating = plugin.getTreasureManager()
                .getTreasuresByParticipant(player.getUniqueId());

        ItemStack myItem = createGuiItem(Material.CHEST,
                ChatColor.AQUA + "✦ 我的寻宝 ✦",
                "",
                ChatColor.GRAY + "▸ 我发起的: " + ChatColor.WHITE + owned.size() + " 个",
                ChatColor.GRAY + "▸ 我参与的: " + ChatColor.WHITE + participating.size() + " 个",
                "",
                ChatColor.GRAY + "查看你的寻宝详情和进度",
                "",
                ChatColor.YELLOW + "点击打开我的寻宝");

        inv.setItem(15, myItem);

        // 底部提示
        inv.setItem(22, createGuiItem(Material.KNOWLEDGE_BOOK,
                ChatColor.DARK_GRAY + "寻宝系统",
                ChatColor.GRAY + "/treasure help 查看帮助"));

        playerOpenGUI.put(player.getUniqueId(), "main");
        player.openInventory(inv);
        playClickSound(player);
    }

    // ==================== 发起寻宝菜单 ====================

    public void openCreateMenu(Player player) {
        UUID uuid = player.getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 27,
                plugin.getConfigManager().getGuiTitle("create"));

        // 装饰边框
        ItemStack border = glassPane((short) 0);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }
        for (int i = 9; i < 27; i += 9) {
            inv.setItem(i, border);
            inv.setItem(i + 8, border);
        }
        inv.setItem(17, border);
        inv.setItem(18, border);

        fillCreateMenuItems(inv, uuid);
        playerOpenGUI.put(uuid, "create");
        player.openInventory(inv);
        playClickSound(player);
    }

    /**
     * 刷新发起寻宝菜单（不重新打开界面，避免闪烁）
     */
    public void refreshCreateMenu(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        fillCreateMenuItems(inv, player.getUniqueId());
        player.updateInventory();
    }

    private void fillCreateMenuItems(Inventory inv, UUID uuid) {
        int coins = getCreateCoins(uuid);
        int ticketPrice = getTicketPrice(uuid);
        int minCoins = plugin.getConfigManager().getMinGuaranteedCoins();
        int maxCoins = plugin.getConfigManager().getMaxGuaranteedCoins();
        int minTicket = plugin.getConfigManager().getMinTicketPrice();

        // 保底金币设置 (slot 11)
        int gcLeft = plugin.getConfigManager().getGuaranteedCoinsLeftClick();
        int gcRight = plugin.getConfigManager().getGuaranteedCoinsRightClick();
        int gcShiftLeft = plugin.getConfigManager().getGuaranteedCoinsShiftLeftClick();
        int gcShiftRight = plugin.getConfigManager().getGuaranteedCoinsShiftRightClick();

        // 计算进度条
        double coinPercent = (double) (coins - minCoins) / (maxCoins - minCoins);
        String coinBar = buildProgressBar(coinPercent);

        ItemStack coinsItem = createGuiItem(Material.GOLD_INGOT,
                ChatColor.GOLD + "设置保底金币",
                "",
                ChatColor.GRAY + "当前: " + ChatColor.GOLD + formatNumber(coins),
                ChatColor.GRAY + "范围: " + ChatColor.GOLD + formatNumber(minCoins) +
                        ChatColor.GRAY + " ~ " + ChatColor.GOLD + formatNumber(maxCoins),
                coinBar,
                "",
                ChatColor.GRAY + "左键 +" + formatNumber(gcLeft) +
                        "  右键 -" + formatNumber(gcRight),
                ChatColor.GRAY + "Shift+左键 +" + formatNumber(gcShiftLeft) +
                        "  Shift+右键 -" + formatNumber(gcShiftRight));
        inv.setItem(11, coinsItem);

        // 确认获取告示牌 (slot 13) - 附魔光泽
        ItemStack confirmItem = createGlowingGuiItem(Material.EMERALD_BLOCK,
                ChatColor.GREEN + "确认获取告示牌",
                "",
                ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + formatNumber(coins),
                ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + formatNumber(ticketPrice),
                ChatColor.GRAY + "发起者支付: " + ChatColor.GOLD + formatNumber(coins),
                "",
                ChatColor.YELLOW + "点击获取寻宝告示牌！",
                ChatColor.GRAY + "左键箱子放置告示牌发起寻宝");
        inv.setItem(13, confirmItem);

        // 参与费用设置 (slot 15)
        int tpLeft = plugin.getConfigManager().getTicketPriceLeftClick();
        int tpRight = plugin.getConfigManager().getTicketPriceRightClick();
        int tpShiftLeft = plugin.getConfigManager().getTicketPriceShiftLeftClick();
        int tpShiftRight = plugin.getConfigManager().getTicketPriceShiftRightClick();

        ItemStack ticketItem = createGuiItem(Material.SUNFLOWER,
                ChatColor.YELLOW + "设置参与费用",
                "",
                ChatColor.GRAY + "当前: " + ChatColor.GOLD + formatNumber(ticketPrice),
                ChatColor.GRAY + "最低: " + ChatColor.GOLD + formatNumber(minTicket),
                "",
                ChatColor.GRAY + "左键 +" + formatNumber(tpLeft) +
                        "  右键 -" + formatNumber(tpRight),
                ChatColor.GRAY + "Shift+左键 +" + formatNumber(tpShiftLeft) +
                        "  Shift+右键 -" + formatNumber(tpShiftRight));
        inv.setItem(15, ticketItem);

        // 返回 (slot 22)
        ItemStack backItem = createGuiItem(Material.BARRIER,
                ChatColor.RED + "返回主菜单",
                ChatColor.GRAY + "点击返回");
        inv.setItem(22, backItem);
    }

    // ==================== 聊天列表（保留兼容） ====================

    public void openListMenu(Player player) {
        List<Treasure> treasures = plugin.getTreasureManager()
                .getAvailableTreasures(player.getUniqueId());

        if (treasures.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "当前没有可参与的寻宝活动。");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "========== 可参与的寻宝 ==========");

        for (Treasure treasure : treasures) {
            String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());
            String systemTag = treasure.getOwnerUuid().equals(AutoTreasureTask.SYSTEM_OWNER_UUID)
                    ? ChatColor.GOLD + "【系统】" : "";

            TextComponent message = new TextComponent(
                    ChatColor.GOLD + "宝藏 #" + treasure.getId() + " " +
                    systemTag +
                    ChatColor.GRAY + "发起者: " + ChatColor.YELLOW + treasure.getOwnerName() + " " +
                    ChatColor.GRAY + "保底: " + ChatColor.GOLD + formatNumber(treasure.getGuaranteedCoins()) + " " +
                    ChatColor.GRAY + "费用: " + ChatColor.GOLD + formatNumber(treasure.getTicketPrice()) + " " +
                    ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName
            );
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/treasure join " + treasure.getId()));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.GREEN + "点击参与此寻宝").create()));

            player.spigot().sendMessage(message);
        }

        player.sendMessage(ChatColor.GREEN + "=================================");
    }

    // ==================== 指南针面板 ====================

    public void openCompassMenu(Player player, int page) {
        List<Treasure> treasures = plugin.getTreasureManager()
                .getAvailableTreasures(player.getUniqueId());

        int guiSize = plugin.getConfigManager().getGuiSize("compass");
        String guiTitle = plugin.getConfigManager().getGuiTitle("compass");
        Inventory inv = Bukkit.createInventory(null, guiSize, guiTitle);

        int itemsPerPage = guiSize - 9;
        int totalPages = (int) Math.ceil((double) treasures.size() / itemsPerPage);
        if (totalPages < 1) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, treasures.size());

        for (int i = startIndex; i < endIndex; i++) {
            Treasure treasure = treasures.get(i);
            String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());

            boolean isSystem = treasure.getOwnerUuid().equals(AutoTreasureTask.SYSTEM_OWNER_UUID);
            Material icon = isSystem ? Material.ENDER_EYE : Material.COMPASS;
            String ownerLabel = isSystem
                    ? ChatColor.GOLD + "【系统宝藏】"
                    : ChatColor.YELLOW + treasure.getOwnerName();

            String typeTag = isSystem
                    ? ChatColor.LIGHT_PURPLE + "类型: " + ChatColor.GOLD + "系统宝藏"
                    : ChatColor.LIGHT_PURPLE + "类型: " + ChatColor.AQUA + "玩家宝藏";

            int participantCount = treasure.getParticipants().size();
            String participantColor;
            if (participantCount >= 5) {
                participantColor = ChatColor.GREEN.toString();
            } else if (participantCount >= 2) {
                participantColor = ChatColor.YELLOW.toString();
            } else {
                participantColor = ChatColor.GRAY.toString();
            }

            ItemStack treasureItem = createGuiItem(icon,
                    ChatColor.GREEN + "宝藏 #" + treasure.getId(),
                    "",
                    ChatColor.GRAY + "发起者: " + ownerLabel,
                    typeTag,
                    ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + formatNumber(treasure.getGuaranteedCoins()),
                    ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + formatNumber(treasure.getTicketPrice()),
                    ChatColor.GRAY + "参与人数: " + participantColor + participantCount + " 人",
                    ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName,
                    ChatColor.GRAY + "剩余时间: " + ChatColor.RED + treasure.getRemainingTimeFormatted(),
                    "",
                    ChatColor.YELLOW + "点击领取寻宝指针！");

            inv.setItem(i - startIndex, treasureItem);
        }

        // 底部导航栏
        // 上一页
        if (page > 1) {
            ItemStack prev = createGuiItem(Material.ARROW,
                    ChatColor.GREEN + "◀ 上一页",
                    ChatColor.GRAY + "第 " + (page - 1) + " 页 / 共 " + totalPages + " 页");
            inv.setItem(guiSize - 9, prev);
        } else {
            ItemStack noPrev = createGuiItem(Material.GRAY_DYE,
                    ChatColor.DARK_GRAY + "◀ 已是第一页",
                    ChatColor.GRAY + "第 1 页 / 共 " + totalPages + " 页");
            inv.setItem(guiSize - 9, noPrev);
        }

        // 页码信息
        ItemStack info = createGuiItem(Material.PAPER,
                ChatColor.YELLOW + "第 " + page + " 页 / 共 " + totalPages + " 页",
                ChatColor.GRAY + "共 " + treasures.size() + " 个可参与的寻宝");
        inv.setItem(guiSize - 5, info);

        // 下一页
        if (page < totalPages) {
            ItemStack next = createGuiItem(Material.ARROW,
                    ChatColor.GREEN + "下一页 ▶",
                    ChatColor.GRAY + "第 " + (page + 1) + " 页 / 共 " + totalPages + " 页");
            inv.setItem(guiSize - 1, next);
        } else {
            ItemStack noNext = createGuiItem(Material.GRAY_DYE,
                    ChatColor.DARK_GRAY + "▶ 已是最后一页",
                    ChatColor.GRAY + "第 " + totalPages + " 页 / 共 " + totalPages + " 页");
            inv.setItem(guiSize - 1, noNext);
        }

        // 返回按钮
        ItemStack back = createGuiItem(Material.BARRIER,
                ChatColor.RED + "返回主菜单",
                ChatColor.GRAY + "点击返回");
        inv.setItem(guiSize - 8, back);

        setCompassPage(player.getUniqueId(), page);
        playerOpenGUI.put(player.getUniqueId(), "compass");
        player.openInventory(inv);
        playClickSound(player);
    }

    // ==================== 我的寻宝 GUI（NEW!） ====================

    public void openMyTreasureGUI(Player player, int page) {
        List<Treasure> ownedTreasures = plugin.getTreasureManager()
                .getTreasuresByOwner(player.getUniqueId());
        List<Treasure> participatingTreasures = plugin.getTreasureManager()
                .getTreasuresByParticipant(player.getUniqueId());

        // 合并列表：先我发起的，再我参与的
        List<Treasure> allMyTreasures = new ArrayList<>();
        allMyTreasures.addAll(ownedTreasures);
        allMyTreasures.addAll(participatingTreasures);

        int guiSize = 54;
        String guiTitle = ChatColor.DARK_GRAY + "我的寻宝";

        Inventory inv = Bukkit.createInventory(null, guiSize, guiTitle);

        int itemsPerPage = guiSize - 9; // 45
        int totalPages = (int) Math.ceil((double) allMyTreasures.size() / itemsPerPage);
        if (totalPages < 1) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allMyTreasures.size());

        int slotOffset = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Treasure treasure = allMyTreasures.get(i);
            boolean isOwner = treasure.getOwnerUuid().equals(player.getUniqueId());
            String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());

            ItemStack treasureItem;
            if (isOwner) {
                int pCount = treasure.getParticipants().size();
                String countColor = pCount > 0 ? ChatColor.GREEN.toString() : ChatColor.GRAY.toString();

                treasureItem = createGuiItem(Material.CHEST,
                        ChatColor.GOLD + "✦ 宝藏 #" + treasure.getId(),
                        ChatColor.GRAY + "状态: " + ChatColor.GREEN + "发起中",
                        "",
                        ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName,
                        ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + formatNumber(treasure.getGuaranteedCoins()),
                        ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + formatNumber(treasure.getTicketPrice()),
                        ChatColor.GRAY + "参与人数: " + countColor + pCount + " 人",
                        ChatColor.GRAY + "剩余时间: " + ChatColor.RED + treasure.getRemainingTimeFormatted(),
                        ChatColor.GRAY + "坐标: " + ChatColor.DARK_GREEN +
                                (int)treasure.getLocation().getX() + ", " +
                                (int)treasure.getLocation().getY() + ", " +
                                (int)treasure.getLocation().getZ());
            } else {
                treasureItem = createGlowingGuiItem(Material.COMPASS,
                        ChatColor.AQUA + "✦ 宝藏 #" + treasure.getId(),
                        ChatColor.GRAY + "状态: " + ChatColor.YELLOW + "已参与",
                        "",
                        ChatColor.GRAY + "发起者: " + ChatColor.YELLOW + treasure.getOwnerName(),
                        ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName,
                        ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + formatNumber(treasure.getGuaranteedCoins()),
                        ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + formatNumber(treasure.getTicketPrice()),
                        ChatColor.GRAY + "剩余时间: " + ChatColor.RED + treasure.getRemainingTimeFormatted(),
                        ChatColor.GRAY + "坐标: " + ChatColor.DARK_GREEN +
                                (int)treasure.getLocation().getX() + ", " +
                                (int)treasure.getLocation().getY() + ", " +
                                (int)treasure.getLocation().getZ(),
                        "",
                        ChatColor.YELLOW + "点击重新获取指针！");
            }

            inv.setItem(slotOffset, treasureItem);
            slotOffset++;
        }

        // 底部导航栏
        // 上一页
        if (page > 1) {
            ItemStack prev = createGuiItem(Material.ARROW,
                    ChatColor.GREEN + "◀ 上一页",
                    ChatColor.GRAY + "第 " + (page - 1) + " 页 / 共 " + totalPages + " 页");
            inv.setItem(guiSize - 9, prev);
        }

        // 页码信息
        int totalCount = allMyTreasures.size();
        ItemStack infoItem = createGuiItem(Material.PAPER,
                ChatColor.YELLOW + "第 " + page + " 页 / 共 " + totalPages + " 页",
                ChatColor.GRAY + "我发起的: " + ownedTreasures.size() + " 个  我参与的: " + participatingTreasures.size() + " 个");
        inv.setItem(guiSize - 5, infoItem);

        // 下一页
        if (page < totalPages) {
            ItemStack next = createGuiItem(Material.ARROW,
                    ChatColor.GREEN + "下一页 ▶",
                    ChatColor.GRAY + "第 " + (page + 1) + " 页 / 共 " + totalPages + " 页");
            inv.setItem(guiSize - 1, next);
        }

        // 返回按钮
        ItemStack backItem = createGuiItem(Material.BARRIER,
                ChatColor.RED + "返回主菜单",
                ChatColor.GRAY + "点击返回");
        inv.setItem(guiSize - 8, backItem);

        // 空状态
        if (allMyTreasures.isEmpty()) {
            ItemStack emptyItem = createGuiItem(Material.PAPER,
                    ChatColor.YELLOW + "暂无寻宝记录",
                    ChatColor.GRAY + "你还没有发起或参与任何寻宝",
                    "",
                    ChatColor.GRAY + "在主菜单选择「参与寻宝」浏览可用的寻宝活动");
            inv.setItem(22, emptyItem);
        }

        setMyPage(player.getUniqueId(), page);
        playerOpenGUI.put(player.getUniqueId(), "mytreasure");
        player.openInventory(inv);
        playClickSound(player);
    }

    /**
     * 刷新我的寻宝 GUI（不重新打开）
     */
    public void refreshMyTreasureGUI(Player player) {
        int page = getMyPage(player.getUniqueId());
        openMyTreasureGUI(player, page);
    }

    // ==================== 聊天「我的寻宝」（保留兼容） ====================

    public void openMyTreasureMenu(Player player) {
        List<Treasure> ownedTreasures = plugin.getTreasureManager()
                .getTreasuresByOwner(player.getUniqueId());
        List<Treasure> participatingTreasures = plugin.getTreasureManager()
                .getTreasuresByParticipant(player.getUniqueId());

        player.sendMessage(ChatColor.GREEN + "========== 我的寻宝 ==========");

        if (!ownedTreasures.isEmpty()) {
            player.sendMessage(ChatColor.AQUA + "--- 我发起的寻宝 ---");
            for (Treasure treasure : ownedTreasures) {
                String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());
                player.sendMessage(ChatColor.GOLD + "宝藏 #" + treasure.getId() + " " +
                        ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName + " " +
                        ChatColor.GRAY + "参与人数: " + ChatColor.WHITE + treasure.getParticipants().size() + " " +
                        ChatColor.GRAY + "剩余时间: " + ChatColor.RED + treasure.getRemainingTimeFormatted());
            }
        }

        if (!participatingTreasures.isEmpty()) {
            player.sendMessage(ChatColor.AQUA + "--- 我参与的寻宝 ---");
            for (Treasure treasure : participatingTreasures) {
                String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());
                player.sendMessage(ChatColor.GOLD + "宝藏 #" + treasure.getId() + " " +
                        ChatColor.GRAY + "发起者: " + ChatColor.YELLOW + treasure.getOwnerName() + " " +
                        ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName + " " +
                        ChatColor.GRAY + "剩余时间: " + ChatColor.RED + treasure.getRemainingTimeFormatted());
            }
        }

        if (ownedTreasures.isEmpty() && participatingTreasures.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "你还没有发起或参与任何寻宝。");
        }

        player.sendMessage(ChatColor.GREEN + "=================================");
    }

    // ==================== 进度条工具 ====================

    private String buildProgressBar(double percent) {
        int barLength = 20;
        int filled = (int) (barLength * Math.max(0, Math.min(1, percent)));
        StringBuilder bar = new StringBuilder(ChatColor.GRAY.toString());
        for (int i = 0; i < barLength; i++) {
            if (i == filled) bar.append(ChatColor.DARK_GRAY);
            bar.append(i < filled ? "█" : "░");
        }
        return bar.toString();
    }

    private String formatNumber(int number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 10_000) {
            return String.format("%.1f万", number / 10_000.0);
        }
        return String.format("%,d", number);
    }

    // ==================== Getter/Setter ====================

    public String getOpenGUI(UUID uuid) {
        return playerOpenGUI.get(uuid);
    }

    public int getCreateCoins(UUID uuid) {
        return playerCreateCoins.getOrDefault(uuid, plugin.getConfigManager().getMinGuaranteedCoins());
    }

    public void setCreateCoins(UUID uuid, int coins) {
        playerCreateCoins.put(uuid, coins);
    }

    public void addCreateCoins(UUID uuid, int amount) {
        int current = getCreateCoins(uuid);
        int max = plugin.getConfigManager().getMaxGuaranteedCoins();
        setCreateCoins(uuid, Math.min(current + amount, max));
    }

    public void removeCreateCoins(UUID uuid, int amount) {
        int current = getCreateCoins(uuid);
        int min = plugin.getConfigManager().getMinGuaranteedCoins();
        setCreateCoins(uuid, Math.max(current - amount, min));
    }

    public int getTicketPrice(UUID uuid) {
        int minTicketPrice = plugin.getConfigManager().getMinTicketPrice();
        return playerTicketPrice.getOrDefault(uuid, minTicketPrice);
    }

    public void setTicketPrice(UUID uuid, int price) {
        int minTicketPrice = plugin.getConfigManager().getMinTicketPrice();
        playerTicketPrice.put(uuid, Math.max(price, minTicketPrice));
    }

    public void addTicketPrice(UUID uuid, int amount) {
        int current = getTicketPrice(uuid);
        setTicketPrice(uuid, current + amount);
    }

    public void removeTicketPrice(UUID uuid, int amount) {
        int current = getTicketPrice(uuid);
        int minTicketPrice = plugin.getConfigManager().getMinTicketPrice();
        setTicketPrice(uuid, Math.max(current - amount, minTicketPrice));
    }

    public void clearCreateData(UUID uuid) {
        playerCreateCoins.remove(uuid);
        playerTicketPrice.remove(uuid);
    }

    public void removePlayer(UUID uuid) {
        playerOpenGUI.remove(uuid);
        playerCompassPage.remove(uuid);
        playerMyPage.remove(uuid);
    }

    public int getCompassPage(UUID uuid) {
        return playerCompassPage.getOrDefault(uuid, 1);
    }

    public void setCompassPage(UUID uuid, int page) {
        playerCompassPage.put(uuid, page);
    }

    public int getMyPage(UUID uuid) {
        return playerMyPage.getOrDefault(uuid, 1);
    }

    public void setMyPage(UUID uuid, int page) {
        playerMyPage.put(uuid, page);
    }

    // ==================== 工具方法 ====================

    public void giveTreasureSign(Player player) {
        int guaranteedCoins = getCreateCoins(player.getUniqueId());
        int ticketPrice = getTicketPrice(player.getUniqueId());
        int minTicketPrice = plugin.getConfigManager().getMinTicketPrice();

        if (ticketPrice < minTicketPrice) {
            ticketPrice = minTicketPrice;
            setTicketPrice(player.getUniqueId(), ticketPrice);  // 修复：同步更新存储的值
        }

        ItemStack sign = TreasureSignItem.createSign(guaranteedCoins, ticketPrice);
        player.getInventory().addItem(sign);

        player.sendMessage(ChatColor.GREEN + "你获得了一个寻宝告示牌！");
        player.sendMessage(ChatColor.GRAY + "左键点击箱子放置告示牌，箱子将成为宝藏。");

        clearCreateData(player.getUniqueId());
    }
}
