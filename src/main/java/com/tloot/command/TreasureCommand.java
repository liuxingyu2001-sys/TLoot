package com.tloot.command;

import com.tloot.TLoot;
import com.tloot.config.MessageManager;
import com.tloot.data.Treasure;
import com.tloot.data.TreasureManager;
import com.tloot.gui.GUIManager;
import com.tloot.item.PointerItem;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TreasureCommand implements CommandExecutor, TabCompleter {

    private final TLoot plugin;

    public TreasureCommand(TLoot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().get("general.player-only"));
            return true;
        }

        Player player = (Player) sender;
        GUIManager guiManager = plugin.getGuiManager();

        if (args.length == 0) {
            guiManager.openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                handleCreate(player);
                break;
            case "join":
                handleJoin(player, args);
                break;
            case "list":
            case "compass":
                handleList(player);
                break;
            case "my":
                handleMy(player);
                break;
            case "info":
                handleInfo(player, args);
                break;
            case "reload":
                handleReload(player);
                break;
            case "help":
                handleHelp(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "未知命令！使用 /treasure help 查看帮助");
        }

        return true;
    }

    private void handleCreate(Player player) {
        GUIManager guiManager = plugin.getGuiManager();
        guiManager.openCreateMenu(player);
    }

    private void handleJoin(Player player, String[] args) {
        GUIManager guiManager = plugin.getGuiManager();
        MessageManager msg = plugin.getMessageManager();
        TreasureManager treasureManager = plugin.getTreasureManager();
        Economy economy = plugin.getEconomy();
        
        if (args.length < 2) {
            guiManager.openCompassMenu(player, 1);
            return;
        }

        String treasureId = args[1].toUpperCase();
        Treasure treasure = treasureManager.getTreasure(treasureId);

        if (treasure == null) {
            player.sendMessage(msg.get("join.treasure-not-found"));
            return;
        }

        if (treasure.isExpired()) {
            player.sendMessage(ChatColor.RED + "这个宝藏已经过期了！");
            return;
        }

        if (treasure.hasParticipant(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "你已经参与了此寻宝！");
            return;
        }

        if (treasure.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你不能参与自己发起的寻宝！");
            return;
        }

        int ticketPrice = treasure.getTicketPrice();
        if (economy.getBalance(player) < ticketPrice) {
            player.sendMessage(msg.get("general.no-money"));
            return;
        }

        economy.withdrawPlayer(player, ticketPrice);
        treasure.addParticipant(player.getUniqueId());
        treasureManager.saveTreasures();

        player.getInventory().addItem(PointerItem.createPointer(treasure));
        
        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());
        player.sendMessage(ChatColor.GREEN + "你成功参与了寻宝！");
        player.sendMessage(ChatColor.GRAY + "寻宝ID: " + ChatColor.AQUA + treasure.getId());
        player.sendMessage(ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName);
        player.sendMessage(ChatColor.GRAY + "使用寻宝指针找到宝藏！");
    }

    private void handleList(Player player) {
        GUIManager guiManager = plugin.getGuiManager();
        guiManager.openCompassMenu(player, 1);
    }

    private void handleMy(Player player) {
        GUIManager guiManager = plugin.getGuiManager();
        guiManager.openMyTreasureMenu(player);
    }

    private void handleInfo(Player player, String[] args) {
        MessageManager msg = plugin.getMessageManager();
        TreasureManager treasureManager = plugin.getTreasureManager();

        if (args.length < 2) {
            List<Treasure> participating = treasureManager.getTreasuresByParticipant(player.getUniqueId());
            if (participating.isEmpty()) {
                player.sendMessage(msg.get("treasure.list-empty"));
                return;
            }

            for (Treasure treasure : participating) {
                sendTreasureInfo(player, treasure);
            }
            return;
        }

        String treasureId = args[1].toUpperCase();
        Treasure treasure = treasureManager.getTreasure(treasureId);

        if (treasure == null) {
            player.sendMessage(msg.get("join.treasure-not-found"));
            return;
        }

        sendTreasureInfo(player, treasure);
    }

    private void sendTreasureInfo(Player player, Treasure treasure) {
        MessageManager msg = plugin.getMessageManager();
        String worldDisplayName = plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName());
        player.sendMessage(msg.get("treasure.info-header"));
        player.sendMessage(msg.get("treasure.info-owner", "owner", treasure.getOwnerName()));
        player.sendMessage(msg.get("treasure.info-coins", "coins", String.valueOf(treasure.getGuaranteedCoins())));
        player.sendMessage(msg.get("treasure.info-time", "time", treasure.getRemainingTimeFormatted()));
        player.sendMessage(msg.get("treasure.info-participants", "count", String.valueOf(treasure.getParticipants().size())));
        player.sendMessage(ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName);
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("tloot.admin")) {
            player.sendMessage(plugin.getMessageManager().get("general.no-permission"));
            return;
        }

        plugin.getConfigManager().reloadConfig();
        plugin.getMessageManager().loadMessages();
        player.sendMessage(ChatColor.GREEN + "配置文件已重新加载！");
    }

    private void handleHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "========== 寻宝系统帮助 ==========");
        player.sendMessage(ChatColor.YELLOW + "/treasure " + ChatColor.GRAY + "- 打开寻宝主菜单");
        player.sendMessage(ChatColor.YELLOW + "/treasure create " + ChatColor.GRAY + "- 发起寻宝");
        player.sendMessage(ChatColor.YELLOW + "/treasure join <ID> " + ChatColor.GRAY + "- 参与寻宝");
        player.sendMessage(ChatColor.YELLOW + "/treasure list " + ChatColor.GRAY + "- 查看寻宝列表（指南针GUI）");
        player.sendMessage(ChatColor.YELLOW + "/treasure compass " + ChatColor.GRAY + "- 打开指南针领取面板");
        player.sendMessage(ChatColor.YELLOW + "/treasure my " + ChatColor.GRAY + "- 查看我的寻宝");
        player.sendMessage(ChatColor.YELLOW + "/treasure info [ID] " + ChatColor.GRAY + "- 查看寻宝信息");
        player.sendMessage(ChatColor.YELLOW + "/treasure reload " + ChatColor.GRAY + "- 重载配置 (管理员)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "join", "list", "compass", "my", "info", "reload", "help"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            for (Treasure treasure : plugin.getTreasureManager().getAllTreasures()) {
                completions.add(treasure.getId());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            for (Treasure treasure : plugin.getTreasureManager().getAllTreasures()) {
                completions.add(treasure.getId());
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(lastArg));

        return completions;
    }
}
