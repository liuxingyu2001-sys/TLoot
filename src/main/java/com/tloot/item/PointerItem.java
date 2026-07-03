package com.tloot.item;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class PointerItem {

    private static final String POINTER_NAME = "&b寻宝指针";
    private static final Material POINTER_MATERIAL = Material.COMPASS;

    public static ItemStack createPointer(Treasure treasure) {
        ItemStack pointer = new ItemStack(POINTER_MATERIAL);
        ItemMeta meta = pointer.getItemMeta();

        String displayName = ChatColor.translateAlternateColorCodes('&', POINTER_NAME);
        meta.setDisplayName(displayName);

        String worldDisplayName = TLoot.getInstance().getConfigManager().getWorldDisplayName(treasure.getWorldName());
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "发起者: " + ChatColor.YELLOW + treasure.getOwnerName());
        lore.add(ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins());
        lore.add(ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice());
        lore.add(ChatColor.GRAY + "剩余时间: " + ChatColor.RED + treasure.getRemainingTimeFormatted());
        lore.add(ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName);
        lore.add("");
        lore.add(ChatColor.GREEN + "手持时指南针自动指向宝藏");
        lore.add(ChatColor.GREEN + "右键查看距离和方向");
        meta.setLore(lore);

        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        NamespacedKey key = new NamespacedKey(TLoot.getInstance(), "treasure_id");
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

        NamespacedKey key = new NamespacedKey(TLoot.getInstance(), "treasure_id");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        if (container.has(key, PersistentDataType.STRING)) {
            return container.get(key, PersistentDataType.STRING);
        }

        return null;
    }

    public static boolean isPointer(ItemStack item) {
        return getTreasureId(item) != null;
    }

    public static ItemStack updatePointerLore(ItemStack item, Treasure treasure) {
        if (item == null || item.getType() != POINTER_MATERIAL) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String worldDisplayName = TLoot.getInstance().getConfigManager().getWorldDisplayName(treasure.getWorldName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "发起者: " + ChatColor.YELLOW + treasure.getOwnerName());
        lore.add(ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins());
        lore.add(ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice());
        lore.add(ChatColor.GRAY + "剩余时间: " + ChatColor.RED + treasure.getRemainingTimeFormatted());
        lore.add(ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName);
        lore.add("");
        lore.add(ChatColor.GREEN + "手持时指南针自动指向宝藏");
        lore.add(ChatColor.GREEN + "右键查看距离和方向");
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }
}
