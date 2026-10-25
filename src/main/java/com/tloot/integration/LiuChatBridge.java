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

    /** 反射解析结果缓存：避免每次公告都查一遍方法、也避免接口不存在时反复刷警告 */
    private static volatile Method broadcastMethod;
    private static volatile boolean methodResolved;
    private static volatile boolean warnedUnavailable;

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
        Method method = resolveMethod(plugin);
        if (method == null) {
            return false;
        }
        try {
            method.invoke(plugin, carrier, components);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bukkit.getLogger().warning("[TLoot] LiuChat 公告接口调用失败: " + e.getMessage());
            return false;
        }
    }

    private static Method resolveMethod(Plugin plugin) {
        Method cached = broadcastMethod;
        if (cached != null) {
            // LiuChat 被热重载后类加载器会变化，旧 Method 不能再调用
            if (cached.getDeclaringClass().getClassLoader() == plugin.getClass().getClassLoader()) {
                return cached;
            }
            broadcastMethod = null;
            methodResolved = false;
        }
        if (methodResolved) {
            return null;
        }
        methodResolved = true;
        try {
            Method method = plugin.getClass().getMethod(
                    "broadcastAnnouncement", Player.class, BaseComponent[].class);
            broadcastMethod = method;
            return method;
        } catch (NoSuchMethodException e) {
            if (!warnedUnavailable) {
                warnedUnavailable = true;
                Bukkit.getLogger().warning("[TLoot] LiuChat 未提供 broadcastAnnouncement 接口，公告将回退为原版聊天栏广播");
            }
            return null;
        } catch (RuntimeException e) {
            if (!warnedUnavailable) {
                warnedUnavailable = true;
                Bukkit.getLogger().warning("[TLoot] 解析 LiuChat 公告接口失败: " + e.getMessage());
            }
            return null;
        }
    }
}
