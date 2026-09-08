package com.tloot.listener;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.data.TreasureManager;
import com.tloot.item.PointerItem;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class TreasureListener implements Listener {

    private final TLoot plugin;

    public TreasureListener(TLoot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.CHEST && plugin.getTreasureManager().findTreasureAtLocation(block.getLocation()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().get("prefix") + "§c这个宝藏箱子不能被破坏！");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block ->
            block.getType() == Material.CHEST && plugin.getTreasureManager().findTreasureAtLocation(block.getLocation()) != null
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block ->
            block.getType() == Material.CHEST && plugin.getTreasureManager().findTreasureAtLocation(block.getLocation()) != null
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.CHEST && plugin.getTreasureManager().findTreasureAtLocation(block.getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.CHEST && plugin.getTreasureManager().findTreasureAtLocation(block.getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getType() == Material.CHEST) {
                if (handleChestClick(player, clickedBlock, event)) {
                    return;
                }
            }
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (PointerItem.isPointer(item)) {
                handlePointerUse(player, item, event);
            }
        }
    }

    private void handlePointerUse(Player player, ItemStack item, PlayerInteractEvent event) {
        String treasureId = PointerItem.getTreasureId(item);
        if (treasureId == null) {
            return;
        }

        Treasure treasure = plugin.getTreasureManager().getTreasure(treasureId);

        if (treasure == null) {
            player.sendMessage(plugin.getMessageManager().get("expire.pointer-expired"));
            item.setAmount(0);
            return;
        }

        if (treasure.isExpired()) {
            player.sendMessage(plugin.getMessageManager().get("expire.treasure-expired"));
            plugin.getTreasureManager().expireTreasure(treasureId);
            item.setAmount(0);
            return;
        }

        if (!treasure.isWorldLoaded()) {
            player.sendMessage(ChatColor.RED + "该宝藏所在世界当前不可用！");
            return;
        }

        event.setCancelled(true);
        player.setCompassTarget(treasure.getLocation());
    }

    private boolean handleChestClick(Player player, Block chestBlock, PlayerInteractEvent event) {
        Treasure treasure = plugin.getTreasureManager().findTreasureAtLocation(chestBlock.getLocation());

        if (treasure == null) {
            return false;
        }

        if (treasure.isExpired()) {
            player.sendMessage(ChatColor.RED + "这个宝藏已经过期了！");
            plugin.getTreasureManager().expireTreasure(treasure.getId());
            return false;
        }

        if (!treasure.hasParticipant(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你没有参与这个寻宝！");
            event.setCancelled(true);
            return true;
        }

        event.setCancelled(true);
        claimTreasure(player, treasure, chestBlock);
        return true;
    }

    private void claimTreasure(Player player, Treasure treasure, Block chestBlock) {
        Economy economy = plugin.getEconomy();
        TreasureManager treasureManager = plugin.getTreasureManager();

        int coins = treasure.getGuaranteedCoins();
        economy.depositPlayer(player, coins);

        List<ItemStack> items = treasure.getItems();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(new ItemStack[0]));

        treasureManager.claimTreasure(treasure.getId(), player.getName());

        // 先清空箱子内残留的物理物品，再移除箱子，避免物品以掉落物形式洒落（重复发放）
        if (chestBlock.getState() instanceof Chest chest) {
            chest.getInventory().clear();
        }
        chestBlock.setType(Material.AIR);

        // 背包放不下的奖励在领取点附近掉落，确保奖励不丢失
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        for (ItemStack item : player.getInventory().getContents()) {
            String id = PointerItem.getTreasureId(item);
            if (id != null && id.equals(treasure.getId())) {
                item.setAmount(0);
            }
        }

        player.sendMessage(plugin.getMessageManager().get("claim.success"));
        player.sendMessage(ChatColor.GREEN + "你获得了 " + ChatColor.GOLD + coins + ChatColor.GREEN + " 金币！");

        for (String command : treasure.getCommands()) {
            String executed = command.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), executed);
        }

        String ownerName = treasure.getOwnerName();
        plugin.getServer().broadcastMessage(
            plugin.getMessageManager().get("prefix") + 
            "§e" + player.getName() + " §a找到了 §e" + ownerName + " §a发起的宝藏！"
        );
    }
}
