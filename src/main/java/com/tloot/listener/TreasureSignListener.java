package com.tloot.listener;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.data.TreasureManager;
import com.tloot.item.TreasureSignItem;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TreasureSignListener implements Listener {

    private final TLoot plugin;

    public TreasureSignListener(TLoot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.CHEST) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (!TreasureSignItem.isTreasureSign(itemInHand)) {
            return;
        }

        event.setCancelled(true);

        String worldName = clickedBlock.getWorld().getName();
        if (!plugin.getConfigManager().isWorldAllowed(worldName)) {
            player.sendMessage(ChatColor.RED + "这个世界不允许创建宝藏！");
            return;
        }

        int guaranteedCoins = TreasureSignItem.getGuaranteedCoins(itemInHand);
        int ticketPrice = TreasureSignItem.getTicketPrice(itemInHand);

        Economy economy = plugin.getEconomy();
        if (economy.getBalance(player) < guaranteedCoins) {
            player.sendMessage(plugin.getMessageManager().get("general.no-money"));
            return;
        }

        if (!(clickedBlock.getState() instanceof Chest)) {
            player.sendMessage(ChatColor.RED + "这个方块不是箱子！");
            return;
        }

        Chest chest = (Chest) clickedBlock.getState();
        List<ItemStack> items = new ArrayList<>();
        
        for (ItemStack item : chest.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }

        if (items.isEmpty()) {
            player.sendMessage(ChatColor.RED + "箱子里没有物品！请先放入奖励物品。");
            return;
        }

        economy.withdrawPlayer(player, guaranteedCoins);

        chest.getInventory().clear();

        TreasureManager treasureManager = plugin.getTreasureManager();
        Treasure treasure = treasureManager.createTreasure(
                player.getUniqueId(),
                player.getName(),
                clickedBlock.getLocation(),
                guaranteedCoins,
                ticketPrice,
                items
        );

        itemInHand.setAmount(itemInHand.getAmount() - 1);

        player.sendMessage(plugin.getMessageManager().get("create.success"));
        player.sendMessage(ChatColor.GREEN + "宝藏ID: " + ChatColor.GOLD + treasure.getId());
        player.sendMessage(ChatColor.GREEN + "保底金币: " + ChatColor.GOLD + guaranteedCoins);
        player.sendMessage(ChatColor.GREEN + "参与费用: " + ChatColor.GOLD + ticketPrice);

        broadcastTreasureCreated(treasure);
    }

    private void broadcastTreasureCreated(Treasure treasure) {
        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getUniqueId().equals(treasure.getOwnerUuid())) {
                continue;
            }

            TextComponent message = new TextComponent(
                    plugin.getMessageManager().get("prefix") +
                    ChatColor.GREEN + " " + treasure.getOwnerName() + " 发起了一个寻宝！ " +
                    ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins() + " " +
                    ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice() + " " +
                    ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName
            );
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/treasure join " + treasure.getId()));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    new ComponentBuilder(ChatColor.GREEN + "点击参与此寻宝").create()));
            
            onlinePlayer.spigot().sendMessage(message);
            onlinePlayer.sendMessage(plugin.getMessageManager().get("prefix") + ChatColor.YELLOW + "点击聊天信息参与寻宝");
        }
    }
}
