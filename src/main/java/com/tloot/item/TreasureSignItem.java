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

    /** NamespacedKey 缓存：避免高频点击事件反复创建对象 */
    private static NamespacedKey signKey;
    private static NamespacedKey coinsKey;
    private static NamespacedKey ticketKey;

    private TreasureSignItem() {
    }

    private static NamespacedKey signKey(TLoot plugin) {
        NamespacedKey cached = signKey;
        if (cached == null) {
            cached = new NamespacedKey(plugin, "treasure_sign");
            signKey = cached;
        }
        return cached;
    }

    private static NamespacedKey coinsKey(TLoot plugin) {
        NamespacedKey cached = coinsKey;
        if (cached == null) {
            cached = new NamespacedKey(plugin, "guaranteed_coins");
            coinsKey = cached;
        }
        return cached;
    }

    private static NamespacedKey ticketKey(TLoot plugin) {
        NamespacedKey cached = ticketKey;
        if (cached == null) {
            cached = new NamespacedKey(plugin, "ticket_price");
            ticketKey = cached;
        }
        return cached;
    }

    public static ItemStack createSign(int guaranteedCoins, int ticketPrice) {
        ItemStack sign = new ItemStack(SIGN_MATERIAL);
        ItemMeta meta = sign.getItemMeta();
        if (meta == null) {
            return sign;
        }

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', SIGN_NAME));

        List<String> lore = new ArrayList<>(5);
        lore.add(ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + guaranteedCoins);
        lore.add(ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + ticketPrice);
        lore.add("");
        lore.add(ChatColor.YELLOW + "左键点击箱子放置");
        lore.add(ChatColor.YELLOW + "箱子将成为宝藏");
        meta.setLore(lore);

        TLoot plugin = TLoot.getInstance();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(signKey(plugin), PersistentDataType.STRING, "treasure_sign");
        container.set(coinsKey(plugin), PersistentDataType.INTEGER, guaranteedCoins);
        container.set(ticketKey(plugin), PersistentDataType.INTEGER, ticketPrice);

        sign.setItemMeta(meta);
        return sign;
    }

    private static PersistentDataContainer containerOf(TLoot plugin, ItemStack item) {
        if (plugin == null || item == null || item.getType() != SIGN_MATERIAL) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        return meta.getPersistentDataContainer();
    }

    public static boolean isTreasureSign(ItemStack item) {
        PersistentDataContainer container = containerOf(TLoot.getInstance(), item);
        return container != null
                && container.has(signKey(TLoot.getInstance()), PersistentDataType.STRING);
    }

    public static int getGuaranteedCoins(ItemStack item) {
        TLoot plugin = TLoot.getInstance();
        PersistentDataContainer container = containerOf(plugin, item);
        if (container == null) {
            return 0;
        }

        Integer value = container.get(coinsKey(plugin), PersistentDataType.INTEGER);
        return value != null ? value : 0;
    }

    public static int getTicketPrice(ItemStack item) {
        TLoot plugin = TLoot.getInstance();
        PersistentDataContainer container = containerOf(plugin, item);
        if (container == null) {
            return 0;
        }

        Integer value = container.get(ticketKey(plugin), PersistentDataType.INTEGER);
        return value != null ? value : 0;
    }
}
