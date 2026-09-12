package com.tloot.listener.gui;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.gui.GUIManager;
import com.tloot.item.PointerItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class MyTreasureGUIListener extends AbstractGUIListener {

    public MyTreasureGUIListener(TLoot plugin) {
        super(plugin);
    }

    @Override
    protected String guiType() {
        return GUIManager.GUI_MY_TREASURE;
    }

    @Override
    protected void handleClick(Player player, Inventory top, int slot, InventoryClickEvent event) {
        GUIManager guiManager = plugin.getGuiManager();
        int guiSize = top.getSize();
        int page = guiManager.getMyPage(player.getUniqueId());

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        // 返回按钮
        if (slot == guiSize - 8) {
            GUIManager.playClickSound(player);
            guiManager.openMainMenu(player);
            return;
        }

        // 上一页 / 下一页
        if (slot == guiSize - 9) {
            if (isNavigationArrow(clicked, "上一页")) {
                GUIManager.playClickSound(player);
                guiManager.openMyTreasureGUI(player, page - 1);
            }
            return;
        }

        if (slot == guiSize - 1) {
            if (isNavigationArrow(clicked, "下一页")) {
                GUIManager.playClickSound(player);
                guiManager.openMyTreasureGUI(player, page + 1);
            }
            return;
        }

        // 页码信息
        if (slot == guiSize - 5) {
            return;
        }

        String treasureId = guiManager.getTreasureIdFromItem(clicked);
        if (treasureId == null) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String displayName = meta.getDisplayName();
                if (displayName.contains("暂无寻宝记录")) {
                    return;
                }
                treasureId = extractTreasureId(displayName);
            }
        }

        if (treasureId != null && !treasureId.isEmpty()) {
            handleMyTreasureClick(player, treasureId);
        }
    }

    private boolean isNavigationArrow(ItemStack item, String label) {
        if (item == null || item.getType() != Material.ARROW) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().contains(label);
    }

    /**
     * 旧格式兜底：从 "✦ 宝藏 #XXXXXXXX" 中解析ID。
     */
    private String extractTreasureId(String displayName) {
        int hashIndex = displayName.indexOf('#');
        if (hashIndex == -1) {
            return null;
        }

        String afterHash = displayName.substring(hashIndex + 1);
        StringBuilder id = new StringBuilder(8);
        for (int i = 0; i < afterHash.length() && id.length() < 8; i++) {
            char c = afterHash.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                id.append(c);
            } else if (id.length() > 0) {
                break;
            }
        }

        return id.length() == 8 ? id.toString().toUpperCase() : null;
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
            sendOwnerDetails(player, treasure);
            return;
        }

        if (!treasure.hasParticipant(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你未参与此寻宝！");
            GUIManager.playFailSound(player);
            return;
        }

        // 只检查“指向同一个宝藏”的指针；原实现只要背包里有任意指针就拒绝，
        // 导致参与多个寻宝的玩家无法重新获取其它宝藏的指针。
        for (ItemStack item : player.getInventory().getContents()) {
            String id = PointerItem.getTreasureId(item);
            if (id != null && id.equals(treasure.getId())) {
                player.sendMessage(ChatColor.YELLOW + "你背包里已经有这个宝藏的指针了！手持它去寻找宝藏吧。");
                GUIManager.playFailSound(player);
                return;
            }
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "背包已满，请先腾出空间！");
            GUIManager.playFailSound(player);
            return;
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(PointerItem.createPointer(treasure));
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }

        GUIManager.playSuccessSound(player);
        player.sendMessage(ChatColor.GREEN + "已重新获取寻宝指针！");
        player.sendMessage(ChatColor.GRAY + "寻宝ID: " + ChatColor.AQUA + treasure.getId());
    }

    private void sendOwnerDetails(Player player, Treasure treasure) {
        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());
        GUIManager.playClickSound(player);
        player.sendMessage(ChatColor.GREEN + "========== 你的寻宝详情 ==========");
        player.sendMessage(ChatColor.GOLD + "宝藏 #" + treasure.getId());
        player.sendMessage(ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName);
        player.sendMessage(ChatColor.GRAY + "坐标: " + ChatColor.DARK_GREEN
                + treasure.getLocation().getBlockX() + ", "
                + treasure.getLocation().getBlockY() + ", "
                + treasure.getLocation().getBlockZ());
        player.sendMessage(ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins());
        player.sendMessage(ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice());
        player.sendMessage(ChatColor.GRAY + "参与人数: " + ChatColor.WHITE + treasure.getParticipantCount());
        player.sendMessage(ChatColor.GRAY + "剩余时间: " + ChatColor.RED + treasure.getRemainingTimeFormatted());
        player.sendMessage(ChatColor.GREEN + "====================================");
    }
}
