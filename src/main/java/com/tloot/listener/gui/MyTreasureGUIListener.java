package com.tloot.listener.gui;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.gui.GUIManager;
import com.tloot.item.PointerItem;
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

public class MyTreasureGUIListener implements Listener {

    private final TLoot plugin;

    public MyTreasureGUIListener(TLoot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = player.getOpenInventory().getTitle();
        String myTitle = ChatColor.DARK_GRAY + "我的寻宝";

        if (!title.equals(myTitle)) {
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
        int guiSize = 54;
        int page = guiManager.getMyPage(player.getUniqueId());

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
            if (displayName.contains("上一页")) {
                GUIManager.playClickSound(player);
                guiManager.openMyTreasureGUI(player, page - 1);
            }
            return;
        }

        // 下一页
        if (slot == guiSize - 1) {
            if (displayName.contains("下一页")) {
                GUIManager.playClickSound(player);
                guiManager.openMyTreasureGUI(player, page + 1);
            }
            return;
        }

        // 信息页
        if (slot == guiSize - 5) {
            return;
        }

        // 空状态
        if (displayName.contains("暂无寻宝记录")) {
            return;
        }

        // 宝藏物品 - 通过 display name 解析
        if (displayName.contains("✦ 宝藏 #")) {
            // 提取 ID: "✦ 宝藏 #XXXXXXXX"
            String afterHash = displayName.substring(displayName.indexOf("#") + 1).trim();
            // afterHash could be like "ABC12345" or "ABC12345 (我发起的)"
            int spaceIdx = afterHash.indexOf(" ");
            String treasureId;
            if (spaceIdx > 0) {
                treasureId = afterHash.substring(0, spaceIdx);
            } else {
                treasureId = afterHash;
            }

            handleMyTreasureClick(player, treasureId);
        }
    }

    private void handleMyTreasureClick(Player player, String treasureId) {
        Treasure treasure = plugin.getTreasureManager().getTreasure(treasureId);
        GUIManager guiManager = plugin.getGuiManager();

        if (treasure == null) {
            player.sendMessage(ChatColor.RED + "该宝藏已不存在！");
            GUIManager.playFailSound(player);
            guiManager.refreshMyTreasureGUI(player);
            return;
        }

        if (treasure.isExpired()) {
            player.sendMessage(ChatColor.RED + "该宝藏已过期！");
            GUIManager.playFailSound(player);
            guiManager.refreshMyTreasureGUI(player);
            return;
        }

        if (treasure.getOwnerUuid().equals(player.getUniqueId())) {
            // 玩家是发起者 — 显示信息
            String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());
            GUIManager.playClickSound(player);
            player.sendMessage(ChatColor.GREEN + "========== 你的寻宝详情 ==========");
            player.sendMessage(ChatColor.GOLD + "宝藏 #" + treasure.getId());
            player.sendMessage(ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName);
            player.sendMessage(ChatColor.GRAY + "坐标: " + ChatColor.DARK_GREEN +
                    (int) treasure.getLocation().getX() + ", " +
                    (int) treasure.getLocation().getY() + ", " +
                    (int) treasure.getLocation().getZ());
            player.sendMessage(ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins());
            player.sendMessage(ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice());
            player.sendMessage(ChatColor.GRAY + "参与人数: " + ChatColor.WHITE + treasure.getParticipants().size());
            player.sendMessage(ChatColor.GRAY + "剩余时间: " + ChatColor.RED + treasure.getRemainingTimeFormatted());
            player.sendMessage(ChatColor.GREEN + "====================================");
            return;
        }

        // 玩家是参与者 — 重新获取指针
        if (!treasure.hasParticipant(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你未参与此寻宝！");
            GUIManager.playFailSound(player);
            return;
        }

        // 检查背包是否已有指针
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && PointerItem.isPointer(item)) {
                player.sendMessage(ChatColor.YELLOW + "你背包里已经有寻宝指针了！手持它去寻找宝藏吧。");
                GUIManager.playFailSound(player);
                return;
            }
        }

        player.getInventory().addItem(PointerItem.createPointer(treasure));
        GUIManager.playSuccessSound(player);
        player.sendMessage(ChatColor.GREEN + "已重新获取寻宝指针！");
        player.sendMessage(ChatColor.GRAY + "寻宝ID: " + ChatColor.AQUA + treasure.getId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = player.getOpenInventory().getTitle();
        String myTitle = ChatColor.DARK_GRAY + "我的寻宝";

        if (!title.equals(myTitle)) {
            return;
        }

        int guiSize = 54;
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

        if (openGUI != null && openGUI.equals("mytreasure")) {
            guiManager.removePlayer(player.getUniqueId());
        }
    }
}
