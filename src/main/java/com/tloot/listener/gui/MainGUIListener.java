package com.tloot.listener.gui;

import com.tloot.TLoot;
import com.tloot.gui.GUIManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class MainGUIListener extends AbstractGUIListener {

    public MainGUIListener(TLoot plugin) {
        super(plugin);
    }

    @Override
    protected String guiType() {
        return GUIManager.GUI_MAIN;
    }

    @Override
    protected void handleClick(Player player, Inventory top, int slot, InventoryClickEvent event) {
        GUIManager guiManager = plugin.getGuiManager();

        switch (slot) {
            case 11 -> guiManager.openCreateMenu(player);
            case 13 -> {
                GUIManager.playClickSound(player);
                guiManager.openCompassMenu(player, 1);
            }
            case 15 -> guiManager.openMyTreasureGUI(player, 1);
            case 22 -> {
                player.closeInventory();
                player.performCommand("treasure help");
            }
            default -> {
                // 其它槽位为装饰物品，忽略点击
            }
        }
    }
}
