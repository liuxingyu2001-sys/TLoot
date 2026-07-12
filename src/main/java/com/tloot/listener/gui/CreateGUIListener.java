package com.tloot.listener.gui;

import com.tloot.TLoot;
import com.tloot.config.ConfigManager;
import com.tloot.gui.GUIManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

public class CreateGUIListener implements Listener {

    private final TLoot plugin;

    public CreateGUIListener(TLoot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = player.getOpenInventory().getTitle();
        String createTitle = plugin.getConfigManager().getGuiTitle("create");

        if (!title.equals(createTitle)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null) {
            return;
        }

        if (!event.getClickedInventory().equals(player.getOpenInventory().getTopInventory())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        GUIManager guiManager = plugin.getGuiManager();
        ConfigManager configManager = plugin.getConfigManager();

        int slot = event.getSlot();
        ClickType clickType = event.getClick();

        if (slot == 11) {
            // 调整保底金币 — 原地刷新，不重开 GUI
            if (clickType == ClickType.LEFT) {
                guiManager.addCreateCoins(uuid, configManager.getGuaranteedCoinsLeftClick());
            } else if (clickType == ClickType.RIGHT) {
                guiManager.removeCreateCoins(uuid, configManager.getGuaranteedCoinsRightClick());
            } else if (clickType == ClickType.SHIFT_LEFT) {
                guiManager.addCreateCoins(uuid, configManager.getGuaranteedCoinsShiftLeftClick());
            } else if (clickType == ClickType.SHIFT_RIGHT) {
                guiManager.removeCreateCoins(uuid, configManager.getGuaranteedCoinsShiftRightClick());
            } else {
                return;
            }
            // 原地刷新，不闪烁
            guiManager.refreshCreateMenu(player);
            GUIManager.playClickSound(player);

        } else if (slot == 13) {
            // 确认获取告示牌
            if (clickType == ClickType.LEFT || clickType == ClickType.RIGHT) {
                guiManager.giveTreasureSign(player);
                GUIManager.playSuccessSound(player);
                player.closeInventory();
            }

        } else if (slot == 15) {
            // 调整参与费用 — 原地刷新
            if (clickType == ClickType.LEFT) {
                guiManager.addTicketPrice(uuid, configManager.getTicketPriceLeftClick());
            } else if (clickType == ClickType.RIGHT) {
                guiManager.removeTicketPrice(uuid, configManager.getTicketPriceRightClick());
            } else if (clickType == ClickType.SHIFT_LEFT) {
                guiManager.addTicketPrice(uuid, configManager.getTicketPriceShiftLeftClick());
            } else if (clickType == ClickType.SHIFT_RIGHT) {
                guiManager.removeTicketPrice(uuid, configManager.getTicketPriceShiftRightClick());
            } else {
                return;
            }
            guiManager.refreshCreateMenu(player);
            GUIManager.playClickSound(player);

        } else if (slot == 22) {
            // 返回主菜单
            GUIManager.playClickSound(player);
            guiManager.openMainMenu(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = player.getOpenInventory().getTitle();
        String createTitle = plugin.getConfigManager().getGuiTitle("create");

        if (!title.equals(createTitle)) {
            return;
        }

        int guiSize = player.getOpenInventory().getTopInventory().getSize();
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

        if (openGUI != null && openGUI.equals("create")) {
            guiManager.removePlayer(player.getUniqueId());
        }
    }
}
