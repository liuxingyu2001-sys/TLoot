package com.tloot.item;

import com.liuxingyu.liulib.LiuLib;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

/**
 * LiuLib 物品规格解析桥（{@code plugin.yml} 已 {@code depend: [LiuLib]}）。
 *
 * <p>战利品表的 {@code material} 键交给 LiuLib {@code ItemService#buildOrNull}，从而支持完整物品规格：
 * {@code DIAMOND} / {@code minecraft:diamond} / {@code DIAMOND*3} / {@code ce:ns:id} /
 * {@code craftengine:ns:id} / {@code base64:...} / {@code nbt:...} / {@code bytes:...} /
 * {@code item:base64:...} / {@code lib:<物品库ID>}（{@code lib:} 由 liuitemlib 注册）。
 * 解析失败返回 {@code null}，调用方回退原有的 {@code Material.getMaterial} 逻辑，老配置行为不变。</p>
 */
public final class LiuLibItems {

    private static final Logger LOGGER = Logger.getLogger("TLoot");

    private LiuLibItems() {
    }

    /**
     * 按 LiuLib 物品规格构建物品；规格非法、LiuLib 不可用或构建失败返回 {@code null}。
     *
     * @param raw 配置原文（战利品表的 {@code material} 键）
     */
    public static ItemStack buildOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String spec = raw.trim();
        try {
            // LiuLib 未启用时 items() 会抛 IllegalStateException
            return LiuLib.items().buildOrNull(spec);
        } catch (Throwable t) {
            // 静默回退：老配置（原版材质名）由调用方兜底
            LOGGER.fine("LiuLib 物品规格解析失败，回退原版材质逻辑：" + spec + "（" + t + "）");
            return null;
        }
    }
}
