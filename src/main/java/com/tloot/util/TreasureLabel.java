package com.tloot.util;

import com.tloot.data.Treasure;
import com.tloot.task.AutoTreasureTask;
import org.bukkit.ChatColor;

/**
 * 玩家可见的宝藏称呼。
 *
 * <p>内部随机 ID（形如 {@code A1B2C3D4}）只用于存储、命令参数与点击事件，
 * 不再直接展示给玩家；玩家看到的是「系统寻宝」或「{玩家} 的寻宝」。
 * 管理员仍可通过 Tab 补全与后台日志取得 ID。</p>
 */
public final class TreasureLabel {

    private TreasureLabel() {
    }

    public static boolean isSystem(Treasure treasure) {
        return treasure != null
                && AutoTreasureTask.SYSTEM_OWNER_UUID.equals(treasure.getOwnerUuid());
    }

    /** 纯文本称呼，例如「系统寻宝」「Steve 的寻宝」「厄运宝藏」。 */
    public static String plain(Treasure treasure) {
        if (treasure == null) {
            return "寻宝";
        }
        if (treasure.isDoom()) {
            return "厄运宝藏";
        }
        if (isSystem(treasure)) {
            return "系统寻宝";
        }
        String owner = treasure.getOwnerName();
        if (owner == null || owner.isBlank()) {
            return "玩家寻宝";
        }
        return owner + " 的寻宝";
    }

    /** 带颜色的称呼：系统宝藏金色，玩家宝藏黄色，厄运宝藏暗红色。 */
    public static String colored(Treasure treasure) {
        if (treasure != null && treasure.isDoom()) {
            return ChatColor.DARK_RED + plain(treasure);
        }
        return (isSystem(treasure) ? ChatColor.GOLD : ChatColor.YELLOW) + plain(treasure);
    }
}
