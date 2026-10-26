package com.tloot.util;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 本服公告：只发给本服在线玩家与控制台，<b>绝不跨服</b>。
 *
 * <p>为什么不用 LiuChat 的 broadcastAnnouncement：它在把公告发给本服的同时，还会经
 * LiuChat 的跨服链路（Redis/BungeeCord）把同一条公告转发到其它子服。而 TLoot 自己
 * 已经有 Redis 同步（{@code publishXxx} → 其它子服 {@code onXxx} 回调再公告一次），
 * 两条链路叠加后 N 台子服会各自看到 N 条重复公告（3 台子服时每条公告重复 3 次）。</p>
 *
 * <p>因此约定：<b>本服公告由这里发，跨服公告由 Redis 同步发，且各发一次。</b></p>
 */
public final class Announcer {

    private Announcer() {
    }

    public static void announce(BaseComponent... components) {
        announce(null, components);
    }

    /**
     * @param excludeUuid 不接收本次公告的玩家（例如自己发起寻宝的玩家），可为 null
     */
    public static void announce(UUID excludeUuid, BaseComponent... components) {
        if (components == null || components.length == 0) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (excludeUuid != null && player.getUniqueId().equals(excludeUuid)) {
                continue;
            }
            player.spigot().sendMessage(components);
        }
        // 控制台也留一份，便于管理员在后台看到公告内容（与 LiuChat 的行为一致）
        Bukkit.getConsoleSender().sendMessage(BaseComponent.toLegacyText(components));
    }

    public static void announceText(String legacyText) {
        announce(new net.md_5.bungee.api.chat.TextComponent(legacyText));
    }
}
