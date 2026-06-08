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

        // Back button
        if (slot == guiSize - 8) {
            guiManager.openMainMenu(player);
            return;
        }

        // Previous page
        if (slot == guiSize - 9 && displayName.contains("上一页")) {
            guiManager.openCompassMenu(player, page - 1);
            return;
        }

        // Next page
        if (slot == guiSize - 1 && displayName.contains("下一页")) {
            guiManager.openCompassMenu(player, page + 1);
            return;
        }

        // Info item - ignore
        if (slot == guiSize - 5) {
            return;
        }

        // Treasure item - find treasure by display name
        if (displayName.startsWith(ChatColor.GREEN + "宝藏 #")) {
            String treasureId = displayName.substring((ChatColor.GREEN + "宝藏 #").length()).trim();
            handleClaimCompass(player, treasureId);
        }
    }

    private void handleClaimCompass(Player player, String treasureId) {
        Treasure treasure = plugin.getTreasureManager().getTreasure(treasureId);

        if (treasure == null) {
            player.sendMessage(ChatColor.RED + "该宝藏已不存在！");
            plugin.getGuiManager().openCompassMenu(player,
                    plugin.getGuiManager().getCompassPage(player.getUniqueId()));
            return;
        }

        if (treasure.isExpired()) {
            player.sendMessage(ChatColor.RED + "该宝藏已过期！");
            plugin.getGuiManager().openCompassMenu(player,
                    plugin.getGuiManager().getCompassPage(player.getUniqueId()));
            return;
        }

        UUID uuid = player.getUniqueId();

        if (treasure.getOwnerUuid().equals(uuid)) {
            player.sendMessage(ChatColor.RED + "你不能参与自己发起的寻宝！");
            return;
        }

        if (treasure.hasParticipant(uuid)) {
            player.sendMessage(ChatColor.YELLOW + "你已经参与了此寻宝！");
            return;
        }

        Economy economy = plugin.getEconomy();
        int ticketPrice = treasure.getTicketPrice();

        if (economy.getBalance(player) < ticketPrice) {
            player.sendMessage(plugin.getMessageManager().get("general.no-money"));
            return;
        }

        economy.withdrawPlayer(player, ticketPrice);
        treasure.addParticipant(uuid);
        plugin.getTreasureManager().saveTreasures();

        player.getInventory().addItem(PointerItem.createPointer(treasure));
        player.closeInventory();

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
