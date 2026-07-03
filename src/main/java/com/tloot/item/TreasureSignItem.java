package com.tloot.item;

import com.tloot.TLoot;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class TreasureSignItem {

    private static final String SIGN_NAME = "&6寻宝告示牌";
    private static final Material SIGN_MATERIAL = Material.OAK_SIGN;

    public static ItemStack createSign(int guaranteedCoins, int ticketPrice) {
        ItemStack sign = new ItemStack(SIGN_MATERIAL);
        ItemMeta meta = sign.getItemMeta();

        String displayName = ChatColor.translateAlternateColorCodes('&', SIGN_NAME);
        meta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + guaranteedCoins);
        lore.add(ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + ticketPrice);
        lore.add("");
        lore.add(ChatColor.YELLOW + "左键点击箱子放置");
        lore.add(ChatColor.YELLOW + "箱子将成为宝藏");
        meta.setLore(lore);

        NamespacedKey key = new NamespacedKey(TLoot.getInstance(), "treasure_sign");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(key, PersistentDataType.STRING, "treasure_sign");
        
        NamespacedKey coinsKey = new NamespacedKey(TLoot.getInstance(), "guaranteed_coins");
        container.set(coinsKey, PersistentDataType.INTEGER, guaranteedCoins);
        
        NamespacedKey ticketKey = new NamespacedKey(TLoot.getInstance(), "ticket_price");
        container.set(ticketKey, PersistentDataType.INTEGER, ticketPrice);

        sign.setItemMeta(meta);
        return sign;
    }

    public static boolean isTreasureSign(ItemStack item) {
        if (item == null || item.getType() != SIGN_MATERIAL) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        NamespacedKey key = new NamespacedKey(TLoot.getInstance(), "treasure_sign");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        return container.has(key, PersistentDataType.STRING);
    }

    public static int getGuaranteedCoins(ItemStack item) {
        if (item == null || item.getType() != SIGN_MATERIAL) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }

        NamespacedKey key = new NamespacedKey(TLoot.getInstance(), "guaranteed_coins");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        if (container.has(key, PersistentDataType.INTEGER)) {
            return container.get(key, PersistentDataType.INTEGER);
        }

        return 0;
    }

    public static int getTicketPrice(ItemStack item) {
        if (item == null || item.getType() != SIGN_MATERIAL) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }

        NamespacedKey key = new NamespacedKey(TLoot.getInstance(), "ticket_price");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        if (container.has(key, PersistentDataType.INTEGER)) {
            return container.get(key, PersistentDataType.INTEGER);
        }

        return 0;
    }
}
