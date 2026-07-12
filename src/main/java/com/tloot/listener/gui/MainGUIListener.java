package com.tloot.listener.gui;

import com.tloot.TLoot;
import com.tloot.gui.GUIManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

public class MainGUIListener implements Listener {

    private final TLoot plugin;

    public MainGUIListener(TLoot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = player.getOpenInventory().getTitle();
        String mainTitle = plugin.getConfigManager().getGuiTitle("main");

        if (!title.equals(mainTitle)) {
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

        if (slot == 11) {
            // 发起寻宝
            guiManager.openCreateMenu(player);
        } else if (slot == 13) {
            // 参与寻宝
            GUIManager.playClickSound(player);
            guiManager.openCompassMenu(player, 1);
        } else if (slot == 15) {
            // 我的寻宝 → GUI!
            guiManager.openMyTreasureGUI(player, 1);
        } else if (slot == 22) {
            // 帮助提示
            player.closeInventory();
            player.performCommand("treasure help");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = player.getOpenInventory().getTitle();
        String mainTitle = plugin.getConfigManager().getGuiTitle("main");

        if (!title.equals(mainTitle)) {
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

        if (openGUI != null && openGUI.equals("main")) {
            guiManager.removePlayer(player.getUniqueId());
        }
    }
}
