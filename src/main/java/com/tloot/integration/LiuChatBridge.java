package com.tloot.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import net.md_5.bungee.api.chat.BaseComponent;

import java.lang.reflect.Method;

/** Optional LiuChat integration. TLoot remains usable when LiuChat is absent. */
public final class LiuChatBridge {
    private LiuChatBridge() {
    }

    public static boolean available() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("LiuChat");
        return plugin != null && plugin.isEnabled();
    }

    public static boolean broadcast(Player carrier, BaseComponent... components) {
        if (carrier == null || !carrier.isOnline() || components == null || components.length == 0) {
            return false;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("LiuChat");
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        try {
            Method method = plugin.getClass().getMethod("broadcastAnnouncement", Player.class, BaseComponent[].class);
            method.invoke(plugin, carrier, components);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bukkit.getLogger().warning("[TLoot] LiuChat 公告接口调用失败: " + e.getMessage());
            return false;
        }
    }
}
