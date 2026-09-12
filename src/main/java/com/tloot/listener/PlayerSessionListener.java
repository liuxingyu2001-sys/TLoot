package com.tloot.listener;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.item.PointerItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家会话状态清理。
 *
 * 内存泄漏修复点：GUI 会话（当前界面 / 页码 / 待创建的保底金币与参与费用）、
 * 指针冷却时间等状态原先以 UUID 为键保存在插件级 Map 中，只在特定 GUI 关闭时才清理，
 * 玩家直接退出（或跨服转移）时会永久残留，长期运行导致 Map 无限增长。
 */
public class PlayerSessionListener implements Listener {

    private final TLoot plugin;

    public PlayerSessionListener(TLoot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGuiManager().handlePlayerQuit(event.getPlayer().getUniqueId());
        plugin.getPointerListener().clearPlayerState(event.getPlayer().getUniqueId());
    }

    /**
     * 清理玩家离线期间已经失效的寻宝指针，避免背包里长期堆积分不清用途的指南针。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PointerItem.removeInvalidPointers(player, id -> {
            Treasure treasure = plugin.getTreasureManager().getTreasure(id);
            return treasure != null && !treasure.isExpired();
        });
    }
}
