package com.tloot.item;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class PointerItem {

    private static final String POINTER_NAME = "&b寻宝指针";
    private static final Material POINTER_MATERIAL = Material.COMPASS;
    private static final String TREASURE_ID_KEY = "treasure_id";

    /** NamespacedKey 缓存：原先每次调用都 new 一个，指针任务每秒会对每个在线玩家调用多次 */
    private static NamespacedKey treasureIdKey;

    private PointerItem() {
    }

    private static NamespacedKey key(TLoot plugin) {
        NamespacedKey cached = treasureIdKey;
        if (cached == null) {
            cached = new NamespacedKey(plugin, TREASURE_ID_KEY);
            treasureIdKey = cached;
        }
        return cached;
    }

    public static ItemStack createPointer(Treasure treasure) {
        ItemStack pointer = new ItemStack(POINTER_MATERIAL);
        ItemMeta meta = pointer.getItemMeta();
        if (meta == null) {
            return pointer;
        }

        String displayName = ChatColor.translateAlternateColorCodes('&', POINTER_NAME);
        meta.setDisplayName(displayName);
        meta.setLore(buildLore(treasure));

        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        NamespacedKey key = key(TLoot.getInstance());
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(key, PersistentDataType.STRING, treasure.getId());

        pointer.setItemMeta(meta);
        return pointer;
    }

    public static String getTreasureId(ItemStack item) {
        if (item == null || item.getType() != POINTER_MATERIAL) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        TLoot plugin = TLoot.getInstance();
        if (plugin == null) {
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = key(plugin);
        if (container.has(key, PersistentDataType.STRING)) {
            return container.get(key, PersistentDataType.STRING);
        }

        return null;
    }

    public static boolean isPointer(ItemStack item) {
        return getTreasureId(item) != null;
    }

    /**
     * 刷新指针的剩余时间等描述。
     * 仅在内容真正变化时才写回物品，避免每秒无意义的 setItemMeta 造成客户端不同步与服务端开销。
     */
    public static boolean updatePointerLore(ItemStack item, Treasure treasure) {
        if (item == null || item.getType() != POINTER_MATERIAL) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        List<String> lore = buildLore(treasure);
        if (lore.equals(meta.getLore())) {
            return false;
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return true;
    }

    private static List<String> buildLore(Treasure treasure) {
        TLoot plugin = TLoot.getInstance();
        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());

        List<String> lore = new ArrayList<>(8);
        lore.add(ChatColor.GRAY + "发起者: " + ChatColor.YELLOW + treasure.getOwnerName());
        lore.add(ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins());
        lore.add(ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice());
        lore.add(ChatColor.GRAY + "剩余时间: " + ChatColor.RED + treasure.getRemainingTimeFormatted());
        lore.add(ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName);
        lore.add("");
        lore.add(ChatColor.GREEN + "手持时指南针自动指向宝藏");
        lore.add(ChatColor.GREEN + "右键查看距离和方向");
        return lore;
    }

    /**
     * 移除玩家背包中所有指向指定宝藏的指针。
     *
     * @return 被移除的指针数量
     */
    public static int removePointers(Player player, String treasureId) {
        if (player == null || treasureId == null) {
            return 0;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        int removed = 0;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            String id = getTreasureId(item);
            if (id != null && id.equals(treasureId)) {
                // 必须清空槽位而不是 setAmount(0)，否则客户端会残留一个“幽灵物品”
                inventory.setItem(slot, null);
                removed++;
            }
        }

        return removed;
    }

    /**
     * 移除玩家背包中所有已失效（对应宝藏不存在或已过期）的指针。
     *
     * @return 被移除的指针数量
     */
    public static int removeInvalidPointers(Player player, java.util.function.Predicate<String> isValid) {
        if (player == null) {
            return 0;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        int removed = 0;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            String id = getTreasureId(item);
            if (id != null && !isValid.test(id)) {
                inventory.setItem(slot, null);
                removed++;
            }
        }

        return removed;
    }
}
