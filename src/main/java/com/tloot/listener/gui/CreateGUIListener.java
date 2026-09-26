package com.tloot.listener.gui;

import com.tloot.TLoot;
import com.tloot.config.ConfigManager;
import com.tloot.gui.GUIManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class CreateGUIListener extends AbstractGUIListener {

    public CreateGUIListener(TLoot plugin) {
        super(plugin);
    }

    @Override
    protected String guiType() {
        return GUIManager.GUI_CREATE;
    }

    @Override
    protected void handleClick(Player player, Inventory top, int slot, InventoryClickEvent event) {
        GUIManager guiManager = plugin.getGuiManager();
        ConfigManager configManager = plugin.getConfigManager();
        ClickType clickType = event.getClick();

        switch (slot) {
            case 11 -> adjustCoins(player, guiManager, configManager, clickType);
            case 15 -> adjustTicketPrice(player, guiManager, configManager, clickType);
            case 13 -> {
                if (clickType == ClickType.LEFT || clickType == ClickType.RIGHT) {
                    if (guiManager.giveTreasureSign(player)) {
                        GUIManager.playSuccessSound(player);
                        player.closeInventory();
                    }
                }
            }
            case 22 -> {
                GUIManager.playClickSound(player);
                guiManager.openMainMenu(player);
            }
            default -> {
                // 装饰槽位
            }
        }
    }

    private void adjustCoins(Player player, GUIManager guiManager, ConfigManager configManager, ClickType clickType) {
        switch (clickType) {
            case LEFT -> guiManager.addCreateCoins(player.getUniqueId(), configManager.getGuaranteedCoinsLeftClick());
            case RIGHT -> guiManager.removeCreateCoins(player.getUniqueId(), configManager.getGuaranteedCoinsRightClick());
            case SHIFT_LEFT -> guiManager.addCreateCoins(player.getUniqueId(), configManager.getGuaranteedCoinsShiftLeftClick());
            case SHIFT_RIGHT -> guiManager.removeCreateCoins(player.getUniqueId(), configManager.getGuaranteedCoinsShiftRightClick());
            default -> {
                return;
            }
        }

        guiManager.refreshCreateMenu(player);
        GUIManager.playClickSound(player);
    }

    private void adjustTicketPrice(Player player, GUIManager guiManager, ConfigManager configManager, ClickType clickType) {
        switch (clickType) {
            case LEFT -> guiManager.addTicketPrice(player.getUniqueId(), configManager.getTicketPriceLeftClick());
            case RIGHT -> guiManager.removeTicketPrice(player.getUniqueId(), configManager.getTicketPriceRightClick());
            case SHIFT_LEFT -> guiManager.addTicketPrice(player.getUniqueId(), configManager.getTicketPriceShiftLeftClick());
            case SHIFT_RIGHT -> guiManager.removeTicketPrice(player.getUniqueId(), configManager.getTicketPriceShiftRightClick());
            default -> {
                return;
            }
        }

        guiManager.refreshCreateMenu(player);
        GUIManager.playClickSound(player);
    }
}
