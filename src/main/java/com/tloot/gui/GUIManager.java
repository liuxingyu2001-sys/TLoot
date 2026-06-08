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
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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

    public GUIManager(TLoot plugin) {
        this.plugin = plugin;
        this.playerOpenGUI = new HashMap<>();
        this.playerCreateCoins = new HashMap<>();
        this.playerTicketPrice = new HashMap<>();
        this.playerCompassPage = new HashMap<>();
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, 
                plugin.getConfigManager().getGuiTitle("main"));

        ItemStack createItem = createGuiItem(Material.DIAMOND, 
                ChatColor.GREEN + "发起寻宝", 
                ChatColor.GRAY + "点击发起一个新的寻宝");
        inv.setItem(11, createItem);

        ItemStack joinItem = createGuiItem(Material.MAP, 
                ChatColor.YELLOW + "参与寻宝", 
                ChatColor.GRAY + "查看可参与的寻宝列表");
        inv.setItem(13, joinItem);

        ItemStack myItem = createGuiItem(Material.CHEST, 
                ChatColor.AQUA + "我的寻宝", 
                ChatColor.GRAY + "查看你发起和参与的寻宝");
        inv.setItem(15, myItem);

        playerOpenGUI.put(player.getUniqueId(), "main");
        player.openInventory(inv);
    }

    public void openCreateMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, 
                plugin.getConfigManager().getGuiTitle("create"));

        ItemStack confirmItem = createGuiItem(Material.EMERALD_BLOCK, 
                ChatColor.GREEN + "确认获取告示牌", 
                ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + getCreateCoins(player.getUniqueId()),
                ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + getTicketPrice(player.getUniqueId()));
        inv.setItem(13, confirmItem);

        int gcLeft = plugin.getConfigManager().getGuaranteedCoinsLeftClick();
        int gcRight = plugin.getConfigManager().getGuaranteedCoinsRightClick();
        int gcShiftLeft = plugin.getConfigManager().getGuaranteedCoinsShiftLeftClick();
        int gcShiftRight = plugin.getConfigManager().getGuaranteedCoinsShiftRightClick();

        ItemStack coinsItem = createGuiItem(Material.GOLD_INGOT, 
                ChatColor.GOLD + "设置保底金币", 
                ChatColor.GRAY + "当前: " + ChatColor.GOLD + getCreateCoins(player.getUniqueId()),
                ChatColor.GRAY + "左键 +" + gcLeft + ", 右键 -" + gcRight,
                ChatColor.GRAY + "Shift+左键 +" + gcShiftLeft + ", Shift+右键 -" + gcShiftRight);
        inv.setItem(11, coinsItem);

        int tpLeft = plugin.getConfigManager().getTicketPriceLeftClick();
        int tpRight = plugin.getConfigManager().getTicketPriceRightClick();
        int tpShiftLeft = plugin.getConfigManager().getTicketPriceShiftLeftClick();
        int tpShiftRight = plugin.getConfigManager().getTicketPriceShiftRightClick();

        ItemStack ticketItem = createGuiItem(Material.SUNFLOWER, 
                ChatColor.YELLOW + "设置参与费用", 
                ChatColor.GRAY + "当前: " + ChatColor.GOLD + getTicketPrice(player.getUniqueId()),
                ChatColor.GRAY + "左键 +" + tpLeft + ", 右键 -" + tpRight,
                ChatColor.GRAY + "Shift+左键 +" + tpShiftLeft + ", Shift+右键 -" + tpShiftRight);
        inv.setItem(15, ticketItem);

        ItemStack backItem = createGuiItem(Material.BARRIER, 
                ChatColor.RED + "返回", 
                ChatColor.GRAY + "返回主菜单");
        inv.setItem(22, backItem);

        playerOpenGUI.put(player.getUniqueId(), "create");
        player.openInventory(inv);
    }

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
            TextComponent message = new TextComponent(
                    ChatColor.GOLD + "宝藏 #" + treasure.getId() + " " +
                    ChatColor.GRAY + "发起者: " + ChatColor.YELLOW + treasure.getOwnerName() + " " +
                    ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins() + " " +
                    ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice() + " " +
                    ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName
            );
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/treasure join " + treasure.getId()));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    new ComponentBuilder(ChatColor.GREEN + "点击参与此寻宝").create()));
            
            player.spigot().sendMessage(message);
        }
        
        player.sendMessage(ChatColor.GREEN + "=================================");
    }

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

            Material icon = treasure.getOwnerUuid().equals(AutoTreasureTask.SYSTEM_OWNER_UUID)
                    ? Material.ENDER_EYE : Material.COMPASS;

            String ownerLabel = treasure.getOwnerUuid().equals(AutoTreasureTask.SYSTEM_OWNER_UUID)
                    ? ChatColor.GOLD + "【系统】" : ChatColor.YELLOW + treasure.getOwnerName();

            ItemStack treasureItem = createGuiItem(icon,
                    ChatColor.GREEN + "宝藏 #" + treasure.getId(),
                    ChatColor.GRAY + "发起者: " + ownerLabel,
                    ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins(),
                    ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice(),
                    ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName,
                    ChatColor.GRAY + "剩余时间: " + ChatColor.RED + treasure.getRemainingTimeFormatted(),
                    "",
                    ChatColor.YELLOW + "点击领取寻宝指针！");

            inv.setItem(i - startIndex, treasureItem);
        }

        if (page > 1) {
            ItemStack prev = createGuiItem(Material.ARROW,
                    ChatColor.GREEN + "上一页",
                    ChatColor.GRAY + "第 " + (page - 1) + " 页 / 共 " + totalPages + " 页");
            inv.setItem(guiSize - 9, prev);
        }

        ItemStack info = createGuiItem(Material.PAPER,
                ChatColor.YELLOW + "第 " + page + " 页 / 共 " + totalPages + " 页",
                ChatColor.GRAY + "共 " + treasures.size() + " 个可参与的寻宝");
        inv.setItem(guiSize - 5, info);

        if (page < totalPages) {
            ItemStack next = createGuiItem(Material.ARROW,
                    ChatColor.GREEN + "下一页",
                    ChatColor.GRAY + "第 " + (page + 1) + " 页 / 共 " + totalPages + " 页");
            inv.setItem(guiSize - 1, next);
        }

        ItemStack back = createGuiItem(Material.BARRIER,
                ChatColor.RED + "返回主菜单",
                ChatColor.GRAY + "点击返回");
        inv.setItem(guiSize - 8, back);

        setCompassPage(player.getUniqueId(), page);
        playerOpenGUI.put(player.getUniqueId(), "compass");
        player.openInventory(inv);
    }

    public void openMyTreasureMenu(Player player) {
        List<Treasure> ownedTreasures = plugin.getTreasureManager()
                .getTreasuresByOwner(player.getUniqueId());
        List<Treasure> participatingTreasures = plugin.getTreasureManager()
                .getTreasuresByParticipant(player.getUniqueId());

        player.sendMessage(ChatColor.GREEN + "========== 我的寻宝 ==========");
        
        if (!ownedTreasures.isEmpty()) {
            player.sendMessage(ChatColor.AQUA + "--- 我发起的寻宝 ---");
            for (Treasure treasure : ownedTreasures) {
                player.sendMessage(ChatColor.GOLD + "宝藏 #" + treasure.getId() + " " +
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
    }

    public int getCompassPage(UUID uuid) {
        return playerCompassPage.getOrDefault(uuid, 1);
    }

    public void setCompassPage(UUID uuid, int page) {
        playerCompassPage.put(uuid, page);
    }

    public void giveTreasureSign(Player player) {
        int guaranteedCoins = getCreateCoins(player.getUniqueId());
        int ticketPrice = getTicketPrice(player.getUniqueId());
        int minTicketPrice = plugin.getConfigManager().getMinTicketPrice();
        
        if (ticketPrice < minTicketPrice) {
            ticketPrice = minTicketPrice;
        }
        
        ItemStack sign = TreasureSignItem.createSign(guaranteedCoins, ticketPrice);
        player.getInventory().addItem(sign);
        
        player.sendMessage(ChatColor.GREEN + "你获得了一个寻宝告示牌！");
        player.sendMessage(ChatColor.GRAY + "左键点击箱子放置告示牌，箱子将成为宝藏。");
        
        clearCreateData(player.getUniqueId());
    }
}
