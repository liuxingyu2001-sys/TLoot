package com.tloot.listener.gui;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.data.TreasureManager;
import com.tloot.gui.GUIManager;
import com.tloot.util.TreasureLabel;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;


public class CompassGUIListener extends AbstractGUIListener {

    public CompassGUIListener(TLoot plugin) {
        super(plugin);
    }

    @Override
    protected String guiType() {
        return GUIManager.GUI_COMPASS;
    }

    @Override
    protected void handleClick(Player player, Inventory top, int slot, InventoryClickEvent event) {
        GUIManager guiManager = plugin.getGuiManager();
        int guiSize = top.getSize();
        int page = guiManager.getCompassPage(player.getUniqueId());

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
                guiManager.openCompassMenu(player, page - 1);
            }
            return;
        }

        if (slot == guiSize - 1) {
            if (isNavigationArrow(clicked, "下一页")) {
                GUIManager.playClickSound(player);
                guiManager.openCompassMenu(player, page + 1);
            }
            return;
        }

        // 页码信息
        if (slot == guiSize - 5) {
            return;
        }

        // 宝藏物品 —— 优先读取物品上绑定的宝藏ID（PDC），仅旧物品回退到解析显示名称
        String treasureId = guiManager.getTreasureIdFromItem(clicked);
        if (treasureId == null) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                treasureId = extractTreasureId(meta.getDisplayName());
            }
        }

        if (treasureId != null && !treasureId.isEmpty()) {
            handleClaimCompass(player, treasureId);
        }
    }

    /**
     * 仅当槽位放着可用的箭头按钮时才翻页；
     * 禁用状态显示为灰色染料「已是第一页 / 已是最后一页」，不会响应点击。
     */
    private boolean isNavigationArrow(ItemStack item, String label) {
        if (item == null || item.getType() != Material.ARROW) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().contains(label);
    }

    /**
     * 旧格式兜底：从显示名称 "§a宝藏 #XXXXXXXX" 中解析ID。
     * 新物品通过 PersistentDataContainer 直接携带ID，不再依赖此方法。
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
            if (c == '§') {
                break;
            }
            if (Character.isLetterOrDigit(c)) {
                id.append(c);
            } else if (id.length() > 0) {
                break;
            }
        }

        return id.length() == 8 ? id.toString().toUpperCase() : null;
    }

    private void handleClaimCompass(Player player, String treasureId) {
        Treasure treasure = plugin.getTreasureManager().getTreasure(treasureId);
        GUIManager guiManager = plugin.getGuiManager();

        if (treasure == null) {
            player.sendMessage(ChatColor.RED + "该宝藏已不存在！");
            GUIManager.playFailSound(player);
            guiManager.openCompassMenu(player, guiManager.getCompassPage(player.getUniqueId()));
            return;
        }

        // 命令与 GUI 共用同一套校验/扣费/发指针逻辑
        TreasureManager.JoinResult result = plugin.getTreasureManager().joinTreasure(player, treasure);
        switch (result) {
            case NOT_FOUND -> {
                player.sendMessage(ChatColor.RED + "该宝藏已不存在！");
                GUIManager.playFailSound(player);
                guiManager.openCompassMenu(player, guiManager.getCompassPage(player.getUniqueId()));
            }
            case EXPIRED -> {
                player.sendMessage(ChatColor.RED + "该宝藏已过期！");
                GUIManager.playFailSound(player);
                guiManager.openCompassMenu(player, guiManager.getCompassPage(player.getUniqueId()));
            }
            case OWNER -> {
                player.sendMessage(ChatColor.RED + "你不能参与自己发起的寻宝！");
                GUIManager.playFailSound(player);
            }
            case NO_SPACE -> {
                player.sendMessage(ChatColor.RED + "背包已满，请先腾出空间再领取寻宝指针！");
                GUIManager.playFailSound(player);
            }
            case NO_MONEY -> {
                player.sendMessage(plugin.getMessageManager().get("general.no-money"));
                GUIManager.playFailSound(player);
            }
            case CHARGE_FAILED -> {
                player.sendMessage(ChatColor.RED + "扣款失败，请稍后重试或联系管理员。");
                GUIManager.playFailSound(player);
            }
            case ALREADY_HAS_POINTER -> {
                player.sendMessage(plugin.getMessageManager().get("join.already-has-pointer"));
                GUIManager.playFailSound(player);
            }
            case REJOIN_POINTER -> {
                player.closeInventory();
                GUIManager.playSuccessSound(player);
                player.sendMessage(plugin.getMessageManager().get("join.rejoin"));
                player.sendMessage(ChatColor.GRAY + "寻宝: " + TreasureLabel.colored(treasure));
            }
            case SUCCESS -> {
                player.closeInventory();
                GUIManager.playSuccessSound(player);
                player.sendMessage(ChatColor.GREEN + "你成功领取了寻宝指针！");
                player.sendMessage(ChatColor.GRAY + "寻宝: " + TreasureLabel.colored(treasure));
                player.sendMessage(ChatColor.GRAY + "世界: " + ChatColor.AQUA
                        + plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName()));
                player.sendMessage(ChatColor.GRAY + "手持指南针找到宝藏吧！");
            }
        }
    }
}
