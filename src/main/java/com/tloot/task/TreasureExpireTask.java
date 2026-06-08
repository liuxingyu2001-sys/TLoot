package com.tloot.task;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.data.TreasureManager;
import com.tloot.item.PointerItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;

public class TreasureExpireTask extends BukkitRunnable {

    private final TLoot plugin;

    public TreasureExpireTask(TLoot plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        TreasureManager treasureManager = plugin.getTreasureManager();
        
        for (Treasure treasure : treasureManager.getAllTreasures()) {
            if (treasure.isExpired()) {
                handleExpiredTreasure(treasure);
            }
        }
        
        treasureManager.cleanupExpiredTreasures();
        
        treasureManager.saveTreasures();
    }

    private void handleExpiredTreasure(Treasure treasure) {
        Location loc = treasure.getLocation();
        if (loc.getWorld() != null) {
            Block block = loc.getBlock();
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
            }
        }

        Player owner = Bukkit.getPlayer(treasure.getOwnerUuid());
        if (owner != null) {
            owner.sendMessage(ChatColor.RED + "你的宝藏 #" + treasure.getId() + " 已过期！");
        }

        for (UUID participantUuid : treasure.getParticipants()) {
            Player participant = Bukkit.getPlayer(participantUuid);
            if (participant != null) {
                participant.sendMessage(ChatColor.RED + "宝藏 #" + treasure.getId() + " 已过期！");
                invalidatePointer(participant, treasure.getId());
            }
        }
    }

    private void invalidatePointer(Player player, String treasureId) {
        for (ItemStack item : player.getInventory().getContents()) {
            String id = PointerItem.getTreasureId(item);
            if (id != null && id.equals(treasureId)) {
                item.setAmount(0);
            }
        }
    }
}
