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
import net.milkbowl.vault.economy.EconomyResponse;
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
        if (event.isCancelled()
                || event.getAction() != Action.LEFT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) {
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

        // 告示牌发起寻宝必须完全由本插件接管，先取消事件避免原版左键破坏/其它保护插件插手
        event.setCancelled(true);

        String worldName = clickedBlock.getWorld().getName();
        if (!plugin.getConfigManager().isWorldAllowed(worldName)) {
            player.sendMessage(ChatColor.RED + "这个世界不允许创建宝藏！");
            return;
        }

        TreasureManager treasureManager = plugin.getTreasureManager();
        if (treasureManager.findTreasureAtLocation(clickedBlock.getLocation()) != null) {
            player.sendMessage(ChatColor.RED + "这个箱子已经是宝藏了！");
            return;
        }

        int guaranteedCoins = TreasureSignItem.getGuaranteedCoins(itemInHand);
        int ticketPrice = TreasureSignItem.getTicketPrice(itemInHand);

        Economy economy = plugin.getEconomy();
        if (economy.getBalance(player) < guaranteedCoins) {
            player.sendMessage(plugin.getMessageManager().get("general.no-money"));
            return;
        }

        if (!(clickedBlock.getState() instanceof Chest chest)) {
            player.sendMessage(ChatColor.RED + "这个方块不是箱子！");
            return;
        }

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : chest.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }

        if (items.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().get("create.no-items"));
            return;
        }

        // 真正扣款成功后才创建宝藏；扣款失败立即中止，避免"没扣钱却生成了宝藏"
        EconomyResponse withdraw = economy.withdrawPlayer(player, guaranteedCoins);
        if (!withdraw.transactionSuccess()) {
            player.sendMessage(ChatColor.RED + "扣款失败: " + withdraw.errorMessage);
            plugin.getLogger().warning("为玩家 " + player.getName() + " 扣款失败: " + withdraw.errorMessage);
            return;
        }

        Treasure treasure;
        try {
            treasure = treasureManager.createTreasure(
                    player.getUniqueId(),
                    player.getName(),
                    clickedBlock.getLocation(),
                    guaranteedCoins,
                    ticketPrice,
                    items
            );
        } catch (Exception e) {
            // 创建失败必须退款，否则玩家金币凭空消失
            economy.depositPlayer(player, guaranteedCoins);
            player.sendMessage(ChatColor.RED + "创建宝藏失败，已退还金币。");
            plugin.getLogger().severe("创建宝藏失败: " + e.getMessage());
            return;
        }

        chest.getInventory().clear();
        consumeSign(player, itemInHand);

        player.sendMessage(plugin.getMessageManager().get("create.success"));
        player.sendMessage(ChatColor.GREEN + "宝藏ID: " + ChatColor.GOLD + treasure.getId());
        player.sendMessage(ChatColor.GREEN + "保底金币: " + ChatColor.GOLD + guaranteedCoins);
        player.sendMessage(ChatColor.GREEN + "参与费用: " + ChatColor.GOLD + ticketPrice);

        broadcastTreasureCreated(treasure);
    }

    private void consumeSign(Player player, ItemStack itemInHand) {
        int remaining = itemInHand.getAmount() - 1;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            itemInHand.setAmount(remaining);
        }
    }

    private void broadcastTreasureCreated(Treasure treasure) {
        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getUniqueId().equals(treasure.getOwnerUuid())) {
                continue;
            }

            TextComponent message = new TextComponent(
                    plugin.getMessageManager().get("prefix")
                            + ChatColor.GREEN + " " + treasure.getOwnerName() + " 发起了一个寻宝！ "
                            + ChatColor.GRAY + "保底金币: " + ChatColor.GOLD + treasure.getGuaranteedCoins() + " "
                            + ChatColor.GRAY + "参与费用: " + ChatColor.GOLD + treasure.getTicketPrice() + " "
                            + ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName
            );
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/treasure join " + treasure.getId()));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.GREEN + "点击参与此寻宝").create()));

            onlinePlayer.spigot().sendMessage(message);
        }
    }
}
