package com.tloot.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional MythicMobs integration for 厄运宝藏 guardians.
 *
 * <p>MythicMobs 5.x 的 API 版本跨度较大，这里用反射调用
 * {@code MythicBukkit.inst().getMobManager().spawnMob(name, location)}，
 * 未安装 MythicMobs 时插件仍可正常运行（厄运宝藏会自动降级为普通宝藏）。</p>
 */
public final class MythicMobsBridge {

    private MythicMobsBridge() {
    }

    private static Object mobManager;
    private static Method spawnMob;
    private static Method getMythicMob;

    public static boolean available() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
        return plugin != null && plugin.isEnabled();
    }

    private static synchronized Object manager() throws ReflectiveOperationException {
        if (mobManager != null) {
            return mobManager;
        }
        Class<?> clazz = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
        Object instance = clazz.getMethod("inst").invoke(null);

        Method getter = null;
        // MythicBukkit#getMobManager 有两个重载（返回 MobManager / MobExecutor），
        // 需要挑出返回具体类 MobExecutor 的那个，spawnMob 才在该类上。
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals("getMobManager")
                    && method.getParameterCount() == 0
                    && method.getReturnType().getName().endsWith("MobExecutor")) {
                getter = method;
                break;
            }
        }
        if (getter == null) {
            throw new NoSuchMethodException("MythicBukkit#getMobManager (MobExecutor)");
        }

        Object manager = getter.invoke(instance);
        spawnMob = manager.getClass().getMethod("spawnMob", String.class, Location.class);
        getMythicMob = manager.getClass().getMethod("getMythicMob", String.class);
        mobManager = manager;
        return manager;
    }

    /** MythicMobs 中是否存在该内部名（未安装 MM 或反射失败返回 false）。 */
    public static boolean isKnownMob(String mobName) {
        if (mobName == null || !available()) {
            return false;
        }
        try {
            // 必须先初始化 manager，否则下面的静态字段还是 null：
            // Java 会先求值 getMythicMob 作为调用目标，再执行 manager() 参数。
            Object manager = manager();
            Object result = getMythicMob.invoke(manager, mobName);
            return result instanceof Optional<?> optional && optional.isPresent();
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bukkit.getLogger().warning("[TLoot] 校验 MythicMobs 怪物 \"" + mobName + "\" 失败: " + e.getMessage());
            return false;
        }
    }

    /** 生成一只 MythicMobs 怪物，返回实体 UUID；失败返回 null。 */
    public static UUID spawn(String mobName, Location location) {
        if (mobName == null || location == null || location.getWorld() == null || !available()) {
            return null;
        }
        try {
            Object manager = manager();
            Object activeMob = spawnMob.invoke(manager, mobName, location);
            if (activeMob == null) {
                return null;
            }
            return (UUID) activeMob.getClass().getMethod("getUniqueId").invoke(activeMob);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bukkit.getLogger().warning("[TLoot] 生成 MythicMobs 怪物 \"" + mobName + "\" 失败: " + e.getMessage());
            return null;
        }
    }
}
