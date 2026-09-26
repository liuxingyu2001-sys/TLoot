package com.tloot.listener;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.data.TreasureManager;
import com.tloot.item.PointerItem;
import com.tloot.integration.LiuChatBridge;
import net.md_5.bungee.api.chat.TextComponent;
import com.tloot.util.TreasureBlocks;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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

    private TreasureManager treasureManager() {
        return plugin.getTreasureManager();
    }

    private boolean isTreasureChest(Block block) {
        return block != null
                && block.getType() == Material.CHEST
                && treasureManager().findTreasureAtLocation(block.getLocation()) != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isTreasureChest(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().get("prefix") + "§c这个宝藏箱子不能被破坏！");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isTreasureChest);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isTreasureChest);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isTreasureChest(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (isTreasureChest(event.getBlock())) {
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
            if (clickedBlock != null && clickedBlock.getType() == Material.CHEST
                    && handleChestClick(player, clickedBlock, event)) {
                return;
            }
        }

        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && PointerItem.isPointer(item)) {
            handlePointerUse(player, item, event);
        }
    }

    private void handlePointerUse(Player player, ItemStack item, PlayerInteractEvent event) {
        String treasureId = PointerItem.getTreasureId(item);
        if (treasureId == null) {
            return;
        }

        Treasure treasure = treasureManager().getTreasure(treasureId);

        if (treasure == null) {
            player.sendMessage(plugin.getMessageManager().get("expire.pointer-expired"));
            player.getInventory().setItemInMainHand(null);
            return;
        }

        if (treasure.isExpired()) {
            player.sendMessage(plugin.getMessageManager().get("expire.treasure-expired"));
            player.getInventory().setItemInMainHand(null);
            try {
                treasureManager().expireTreasure(treasureId);
            } catch (RuntimeException e) {
                plugin.getLogger().severe("清理过期宝藏失败: " + e.getMessage());
            }
            return;
        }

        if (!treasure.isWorldLoaded()) {
            player.sendMessage(ChatColor.RED + "该宝藏所在世界当前不可用！");
            return;
        }

        event.setCancelled(true);
        Location treasureLoc = treasure.getLocation();
        player.setCompassTarget(treasureLoc);

        // 跨世界时 Location#distance 会抛 IllegalArgumentException，必须判断世界是否一致
        if (player.getWorld().equals(treasureLoc.getWorld())) {
            player.sendMessage(ChatColor.GREEN + "指针已指向宝藏 #" + treasureId + "，距离 "
                    + (int) Math.round(player.getLocation().distance(treasureLoc)) + " 格");
        } else {
            player.sendMessage(ChatColor.GREEN + "指针已指向宝藏 #" + treasureId);
            player.sendMessage(ChatColor.GRAY + "该宝藏位于其它世界: " + ChatColor.AQUA
                    + plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName()));
        }
    }

    /**
     * @return true 表示事件已被本插件处理（调用方不应再继续处理）
     */
    private boolean handleChestClick(Player player, Block chestBlock, PlayerInteractEvent event) {
        Treasure treasure = treasureManager().findTreasureAtLocation(chestBlock.getLocation());
        if (treasure == null) {
            return false;
        }

        event.setCancelled(true);
        if (treasure.isExpired()) {
            player.sendMessage(ChatColor.RED + "这个宝藏已经过期了！");
            try {
                treasureManager().expireTreasure(treasure.getId());
            } catch (RuntimeException e) {
                plugin.getLogger().severe("清理过期宝藏失败: " + e.getMessage());
            }
            return true;
        }

        if (!treasure.hasParticipant(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().get("claim.not-your-treasure"));
            event.setCancelled(true);
            return true;
        }

        // 防止客户端作弊/跨世界交互远程领取：校验实际距离
        Location treasureLoc = treasure.getLocation();
        if (!player.getWorld().equals(treasureLoc.getWorld())
                || player.getLocation().distanceSquared(treasureLoc)
                > Math.pow(plugin.getConfigManager().getClaimDistance() + 1.0, 2)) {
            player.sendMessage(plugin.getMessageManager().get("claim.too-far"));
            event.setCancelled(true);
            return true;
        }

        event.setCancelled(true);
        claimTreasure(player, treasure, chestBlock);
        return true;
    }

    private void claimTreasure(Player player, Treasure treasure, Block chestBlock) {
        Economy economy = plugin.getEconomy();
        TreasureManager treasureManager = treasureManager();
        String treasureId = treasure.getId();

        // 注销失败时保留箱子和奖励供重试。
        try {
            treasureManager.claimTreasure(treasureId, player.getName());
        } catch (RuntimeException e) {
            plugin.getLogger().severe("领取宝藏 #" + treasureId + " 失败: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "领取失败，请稍后重试。");
            return;
        }
        TreasureBlocks.removeChest(chestBlock.getLocation());

        // 3) 先回收指针，再发放奖励：否则奖励占满背包后，多余的指针会被当成"放不下的奖励"掉在地上
        PointerItem.removePointers(player, treasureId);

        List<ItemStack> items = treasure.getItems();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        int coins = treasure.getGuaranteedCoins();
        if (coins > 0 && !economy.depositPlayer(player, coins).transactionSuccess()) {
            plugin.getLogger().warning("无法向玩家 " + player.getName() + " 发放保底金币 " + coins
                    + "（宝藏 #" + treasureId + "），请检查经济插件");
        }

        player.sendMessage(plugin.getMessageManager().get("claim.success"));
        player.sendMessage(ChatColor.GREEN + "你获得了 " + ChatColor.GOLD + coins + ChatColor.GREEN + " 金币！");

        for (String command : treasure.getCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()));
        }

        // 本服公告在此发出；其它子服的公告由 Redis 的 onTreasureClaimed 回调发出
        // （不能只依赖回调：Redis 默认关闭时回调根本不会触发，公告会完全消失）
        String announcement = plugin.getMessageManager().get("prefix")
                + "§e" + player.getName() + " §a找到了 §e" + treasure.getOwnerName() + " §a发起的宝藏！";
        if (!LiuChatBridge.broadcast(player, new TextComponent(announcement))) {
            plugin.getServer().broadcastMessage(announcement);
        }
    }
}
