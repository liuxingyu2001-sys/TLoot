package com.tloot.listener.gui;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.gui.GUIManager;
import com.tloot.item.PointerItem;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class CompassGUIListener implements Listener {

    private final TLoot plugin;

    public CompassGUIListener(TLoot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = player.getOpenInventory().getTitle();
        String compassTitle = plugin.getConfigManager().getGuiTitle("compass");

        if (!title.equals(compassTitle)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null) {
            return;
        }

        if (!event.getClickedInventory().equals(player.getOpenInventory().getTopInventory())) {
            return;
        }

        GUIManager guiManager = plugin.getGuiManager();
        int slot = event.getSlot();
        int guiSize = plugin.getConfigManager().getGuiSize("compass");
        int page = guiManager.getCompassPage(player.getUniqueId());

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        String displayName = meta.getDisplayName();

        // 返回按钮
        if (slot == guiSize - 8) {
            GUIManager.playClickSound(player);
            guiManager.openMainMenu(player);
            return;
        }

        // 上一页
        if (slot == guiSize - 9) {
            if (displayName.contains("上一页") && !displayName.contains("已是")) {
                GUIManager.playClickSound(player);
                guiManager.openCompassMenu(player, page - 1);
            }
            return;
        }

        // 下一页
        if (slot == guiSize - 1) {
            if (displayName.contains("下一页") && !displayName.contains("已是")) {
                GUIManager.playClickSound(player);
                guiManager.openCompassMenu(player, page + 1);
            }
            return;
        }

        // 信息页 - 忽略点击
        if (slot == guiSize - 5) {
            return;
        }

        // 宝藏物品 - 通过 display name 解析 ID
        if (displayName.startsWith(ChatColor.GREEN + "宝藏 #")) {
            String treasureId = extractTreasureId(displayName);
            if (treasureId != null && !treasureId.isEmpty()) {
                handleClaimCompass(player, treasureId);
            }
        }
    }

    private String extractTreasureId(String displayName) {
        // 从显示名称中提取宝藏ID，格式: "§a宝藏 #XXXXXXXX"
        try {
            int hashIndex = displayName.indexOf("#");
            if (hashIndex == -1) {
                return null;
            }
            // 提取从 # 后面到颜色代码或结尾的字符串
            String afterHash = displayName.substring(hashIndex + 1).trim();
            // 查找下一个颜色代码（如果有）
            int colorIndex = -1;
            for (int i = 0; i < afterHash.length(); i++) {
                if (afterHash.charAt(i) == '§' && i + 1 < afterHash.length()) {
                    colorIndex = i;
                    break;
                }
            }
            if (colorIndex > 0) {
                afterHash = afterHash.substring(0, colorIndex).trim();
            }
            // 宝藏ID是8位大写字母/数字
            if (afterHash.length() >= 8) {
                return afterHash.substring(0, 8).toUpperCase();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("解析宝藏ID失败: " + displayName);
        }
        return null;
    }

    private void handleClaimCompass(Player player, String treasureId) {
        Treasure treasure = plugin.getTreasureManager().getTreasure(treasureId);
        GUIManager guiManager = plugin.getGuiManager();

        if (treasure == null) {
            player.sendMessage(ChatColor.RED + "该宝藏已不存在！");
            GUIManager.playFailSound(player);
            guiManager.openCompassMenu(player,
                    guiManager.getCompassPage(player.getUniqueId()));
            return;
        }

        if (treasure.isExpired()) {
            player.sendMessage(ChatColor.RED + "该宝藏已过期！");
            GUIManager.playFailSound(player);
            guiManager.openCompassMenu(player,
                    guiManager.getCompassPage(player.getUniqueId()));
            return;
        }

        UUID uuid = player.getUniqueId();

        if (treasure.getOwnerUuid().equals(uuid)) {
            player.sendMessage(ChatColor.RED + "你不能参与自己发起的寻宝！");
            GUIManager.playFailSound(player);
            return;
        }

        if (treasure.hasParticipant(uuid)) {
            player.sendMessage(ChatColor.YELLOW + "你已经参与了此寻宝！");
            GUIManager.playFailSound(player);
            return;
        }

        Economy economy = plugin.getEconomy();
        int ticketPrice = treasure.getTicketPrice();

        if (economy.getBalance(player) < ticketPrice) {
            player.sendMessage(plugin.getMessageManager().get("general.no-money"));
            GUIManager.playFailSound(player);
            return;
        }

        economy.withdrawPlayer(player, ticketPrice);
        treasure.addParticipant(uuid);
        plugin.getTreasureManager().saveTreasures();

        player.getInventory().addItem(PointerItem.createPointer(treasure));
        player.closeInventory();

        GUIManager.playSuccessSound(player);

        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());
        player.sendMessage(ChatColor.GREEN + "你成功领取了寻宝指针！");
        player.sendMessage(ChatColor.GRAY + "寻宝ID: " + ChatColor.AQUA + treasure.getId());
        player.sendMessage(ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName);
        player.sendMessage(ChatColor.GRAY + "手持指南针找到宝藏吧！");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = player.getOpenInventory().getTitle();
        String compassTitle = plugin.getConfigManager().getGuiTitle("compass");

        if (!title.equals(compassTitle)) {
            return;
        }

        int guiSize = plugin.getConfigManager().getGuiSize("compass");
        for (int slot : event.getRawSlots()) {
            if (slot < guiSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        GUIManager guiManager = plugin.getGuiManager();
        String openGUI = guiManager.getOpenGUI(player.getUniqueId());

        if (openGUI != null && openGUI.equals("compass")) {
            guiManager.removePlayer(player.getUniqueId());
        }
    }
}
