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
            if (clickType == ClickType.LEFT) {
                guiManager.addCreateCoins(uuid, configManager.getGuaranteedCoinsLeftClick());
                guiManager.openCreateMenu(player);
            } else if (clickType == ClickType.RIGHT) {
                guiManager.removeCreateCoins(uuid, configManager.getGuaranteedCoinsRightClick());
                guiManager.openCreateMenu(player);
            } else if (clickType == ClickType.SHIFT_LEFT) {
                guiManager.addCreateCoins(uuid, configManager.getGuaranteedCoinsShiftLeftClick());
                guiManager.openCreateMenu(player);
            } else if (clickType == ClickType.SHIFT_RIGHT) {
                guiManager.removeCreateCoins(uuid, configManager.getGuaranteedCoinsShiftRightClick());
                guiManager.openCreateMenu(player);
            }
        } else if (slot == 13) {
            if (clickType == ClickType.LEFT || clickType == ClickType.RIGHT) {
                guiManager.giveTreasureSign(player);
                player.closeInventory();
            }
        } else if (slot == 15) {
            if (clickType == ClickType.LEFT) {
                guiManager.addTicketPrice(uuid, configManager.getTicketPriceLeftClick());
                guiManager.openCreateMenu(player);
            } else if (clickType == ClickType.RIGHT) {
                guiManager.removeTicketPrice(uuid, configManager.getTicketPriceRightClick());
                guiManager.openCreateMenu(player);
            } else if (clickType == ClickType.SHIFT_LEFT) {
                guiManager.addTicketPrice(uuid, configManager.getTicketPriceShiftLeftClick());
                guiManager.openCreateMenu(player);
            } else if (clickType == ClickType.SHIFT_RIGHT) {
                guiManager.removeTicketPrice(uuid, configManager.getTicketPriceShiftRightClick());
                guiManager.openCreateMenu(player);
            }
        } else if (slot == 22) {
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
