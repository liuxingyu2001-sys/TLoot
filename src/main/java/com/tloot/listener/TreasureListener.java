package com.tloot.listener;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.data.TreasureManager;
import com.tloot.item.PointerItem;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class TreasureListener implements Listener {

    private final TLoot plugin;

    public TreasureListener(TLoot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getType() == Material.CHEST) {
                if (handleChestClick(player, clickedBlock, event)) {
                    return;
                }
            }
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (PointerItem.isPointer(item)) {
                handlePointerUse(player, item, event);
            }
        }
    }

    private void handlePointerUse(Player player, ItemStack item, PlayerInteractEvent event) {
        String treasureId = PointerItem.getTreasureId(item);
        if (treasureId == null) {
            return;
        }

        Treasure treasure = plugin.getTreasureManager().getTreasure(treasureId);

        if (treasure == null) {
            player.sendMessage(plugin.getMessageManager().get("expire.pointer-expired"));
            item.setAmount(0);
            return;
        }

        if (treasure.isExpired()) {
            player.sendMessage(plugin.getMessageManager().get("expire.treasure-expired"));
            plugin.getTreasureManager().removeTreasure(treasureId);
            item.setAmount(0);
            return;
        }

        event.setCancelled(true);
        player.setCompassTarget(treasure.getLocation());
    }

    private boolean handleChestClick(Player player, Block chestBlock, PlayerInteractEvent event) {
        Treasure treasure = findTreasureAtLocation(chestBlock.getLocation());
        
        if (treasure == null) {
            return false;
        }

        if (treasure.isExpired()) {
            player.sendMessage(ChatColor.RED + "这个宝藏已经过期了！");
            plugin.getTreasureManager().removeTreasure(treasure.getId());
            return false;
        }

        if (!treasure.hasParticipant(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你没有参与这个寻宝！");
            event.setCancelled(true);
            return true;
        }

        event.setCancelled(true);
        claimTreasure(player, treasure, chestBlock);
        return true;
    }

    private Treasure findTreasureAtLocation(Location location) {
        TreasureManager treasureManager = plugin.getTreasureManager();
        
        for (Treasure treasure : treasureManager.getAllTreasures()) {
            Location treasureLoc = treasure.getLocation();
            if (treasureLoc != null && treasureLoc.getWorld() != null && 
                treasureLoc.getWorld().equals(location.getWorld()) &&
                treasureLoc.getBlockX() == location.getBlockX() &&
                treasureLoc.getBlockY() == location.getBlockY() &&
                treasureLoc.getBlockZ() == location.getBlockZ()) {
                return treasure;
            }
        }
        
        return null;
    }

    private void claimTreasure(Player player, Treasure treasure, Block chestBlock) {
        Economy economy = plugin.getEconomy();
        TreasureManager treasureManager = plugin.getTreasureManager();

        int coins = treasure.getGuaranteedCoins();
        economy.depositPlayer(player, coins);

        List<ItemStack> items = treasure.getItems();
        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }

        treasureManager.removeTreasure(treasure.getId());

        chestBlock.setType(Material.AIR);

        for (ItemStack item : player.getInventory().getContents()) {
            String id = PointerItem.getTreasureId(item);
            if (id != null && id.equals(treasure.getId())) {
                item.setAmount(0);
            }
        }

        player.sendMessage(plugin.getMessageManager().get("claim.success"));
        player.sendMessage(ChatColor.GREEN + "你获得了 " + ChatColor.GOLD + coins + ChatColor.GREEN + " 金币！");

        for (String command : treasure.getCommands()) {
            String executed = command.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), executed);
        }

        String ownerName = treasure.getOwnerName();
        plugin.getServer().broadcastMessage(
            plugin.getMessageManager().get("prefix") + 
            "§e" + player.getName() + " §a找到了 §e" + ownerName + " §a发起的宝藏！"
        );
    }
}
