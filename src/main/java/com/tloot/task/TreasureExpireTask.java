package com.tloot.task;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.data.TreasureManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class TreasureExpireTask extends BukkitRunnable {

    private final TLoot plugin;

    public TreasureExpireTask(TLoot plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        TreasureManager treasureManager = plugin.getTreasureManager();
        
        for (Treasure treasure : treasureManager.treasuresView()) {
            if (treasure.isExpired()) {
                handleExpiredTreasure(treasure);
            }
        }

        // 统一在管理器内完成注销 / 落库 / 跨服广播 / 指针回收
        treasureManager.cleanupExpiredTreasures();
    }

    private void handleExpiredTreasure(Treasure treasure) {
        Player owner = Bukkit.getPlayer(treasure.getOwnerUuid());
        if (owner != null) {
            owner.sendMessage(ChatColor.RED + "你的宝藏 #" + treasure.getId() + " 已过期！");
        }

        for (UUID participantUuid : treasure.participantsView()) {
            Player participant = Bukkit.getPlayer(participantUuid);
            if (participant == null) {
                continue;
            }
            participant.sendMessage(ChatColor.RED + "宝藏 #" + treasure.getId() + " 已过期！");
        }
    }
}
