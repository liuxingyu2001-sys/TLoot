package com.tloot.command;

import com.tloot.TLoot;
import com.tloot.config.MessageManager;
import com.tloot.data.Treasure;
import com.tloot.data.TreasureManager;
import com.tloot.gui.GUIManager;
import com.tloot.util.TreasureLabel;
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
        // 管理员子命令允许控制台执行
        if (args.length > 0 && (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("remove"))) {
            if (args[0].equalsIgnoreCase("reload")) {
                handleReload(sender);
            } else {
                handleRemove(sender, args);
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().get("general.player-only"));
            return true;
        }

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
            case "cancel":
                handleCancel(player, args);
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

        if (args.length < 2) {
            guiManager.openCompassMenu(player, 1);
            return;
        }

        String treasureId = args[1].toUpperCase();
        Treasure treasure = treasureManager.getTreasure(treasureId);
        TreasureManager.JoinResult result = treasureManager.joinTreasure(player, treasure);

        switch (result) {
            case NOT_FOUND -> player.sendMessage(msg.get("join.treasure-not-found"));
            case EXPIRED -> {
                player.sendMessage(ChatColor.RED + "这个宝藏已经过期了！");
                if (treasure != null) {
                    treasureManager.expireTreasure(treasure.getId());
                }
            }
            case OWNER -> player.sendMessage(msg.get("join.cannot-join-own"));
            case NO_SPACE -> player.sendMessage(ChatColor.RED + "背包已满，请先腾出空间再参与寻宝！");
            case NO_MONEY -> player.sendMessage(msg.get("general.no-money"));
            case CHARGE_FAILED -> player.sendMessage(ChatColor.RED + "扣款失败，请稍后重试或联系管理员。");
            case ALREADY_HAS_POINTER -> player.sendMessage(msg.get("join.already-has-pointer"));
            case REJOIN_POINTER -> {
                player.sendMessage(msg.get("join.rejoin"));
                player.sendMessage(ChatColor.GRAY + "寻宝: " + TreasureLabel.colored(treasure));
                sendDoomHint(player, treasure);
            }
            case SUCCESS -> {
                player.sendMessage(msg.get("join.success"));
                player.sendMessage(ChatColor.GRAY + "寻宝: " + TreasureLabel.colored(treasure));
                player.sendMessage(ChatColor.GRAY + "世界: " + ChatColor.AQUA
                        + plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName()));
                player.sendMessage(ChatColor.GRAY + "使用寻宝指针找到宝藏！");
                sendDoomHint(player, treasure);
            }
        }
    }

    private void sendDoomHint(Player player, Treasure treasure) {
        if (treasure == null || !treasure.isDoom() || plugin.getDoomTreasureManager() == null) {
            return;
        }
        int remaining = plugin.getDoomTreasureManager().remainingGuards(treasure.getId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.DARK_RED + "⚠ 这是厄运宝藏：先击败 " + remaining + " 只守卫，宝箱才会出现。");
        }
    }

    /**
     * 取消自己发起的寻宝。已有参与者时不允许取消（避免参与者的门票打水漂），管理员可用 /treasure remove。
     */
    private void handleCancel(Player player, String[] args) {
        MessageManager msg = plugin.getMessageManager();
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "请在「我的寻宝」中 Shift+左键要取消的宝藏，或使用 /treasure cancel <ID>。");
            return;
        }

        String treasureId = args[1].toUpperCase();
        Treasure treasure = plugin.getTreasureManager().getTreasure(treasureId);
        if (treasure == null) {
            player.sendMessage(msg.get("join.treasure-not-found"));
            return;
        }

        boolean admin = player.hasPermission("tloot.admin");
        if (!admin && !treasure.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(msg.get("cancel.not-owner"));
            return;
        }
        if (!admin && treasure.getParticipantCount() > 0) {
            player.sendMessage(msg.get("cancel.has-participants",
                    "count", String.valueOf(treasure.getParticipantCount())));
            return;
        }

        if (!plugin.getTreasureManager().cancelForOwner(treasure, player)) {
            // 理论上前面已拦截；这里兜底，避免绕过校验的调用路径
            player.sendMessage(msg.get("cancel.has-participants",
                    "count", String.valueOf(treasure.getParticipantCount())));
            return;
        }
        player.sendMessage(msg.get("cancel.success"));
    }

    /** 管理员强制移除宝藏（含已有人参与的），控制台也可执行 */
    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tloot.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("general.no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /treasure remove <ID>");
            return;
        }

        String treasureId = args[1].toUpperCase();
        Treasure treasure = plugin.getTreasureManager().getTreasure(treasureId);
        if (treasure == null) {
            sender.sendMessage(plugin.getMessageManager().get("join.treasure-not-found"));
            return;
        }

        plugin.getTreasureManager().removeTreasure(treasureId, true);
        sender.sendMessage(ChatColor.GREEN + "已移除宝藏 #" + treasureId + "（发起者: "
                + treasure.getOwnerName() + "）");
    }

    private void handleList(Player player) {
        GUIManager guiManager = plugin.getGuiManager();
        guiManager.openCompassMenu(player, 1);
    }

    private void handleMy(Player player) {
        GUIManager guiManager = plugin.getGuiManager();
        guiManager.openMyTreasureGUI(player, 1);
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
        player.sendMessage(msg.get("treasure.info-participants", "count", String.valueOf(treasure.getParticipantCount())));
        player.sendMessage(ChatColor.GRAY + "世界: " + ChatColor.AQUA + worldDisplayName);
        if (treasure.isDoom()) {
            int remaining = plugin.getDoomTreasureManager() != null
                    ? plugin.getDoomTreasureManager().remainingGuards(treasure.getId()) : 0;
            player.sendMessage(ChatColor.DARK_RED + "类型: 厄运宝藏 "
                    + (remaining > 0 ? ChatColor.RED + "（守卫剩余 " + remaining + " 只）"
                    : ChatColor.GREEN + "（守卫已清除）"));
        }
        if (treasure.hasParticipant(player.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "状态: " + ChatColor.GREEN + "已参与");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("tloot.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("general.no-permission"));
            return;
        }

        plugin.reloadSettings();
        sender.sendMessage(ChatColor.GREEN + "配置文件已重新加载！");
    }

    private void handleHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "========== 寻宝系统帮助 ==========");
        player.sendMessage(ChatColor.YELLOW + "/treasure " + ChatColor.GRAY + "- 打开寻宝主菜单");
        player.sendMessage(ChatColor.YELLOW + "/treasure create " + ChatColor.GRAY + "- 发起寻宝");
        player.sendMessage(ChatColor.YELLOW + "/treasure join " + ChatColor.GRAY + "- 参与寻宝（点击公告或列表中的宝藏）");
        player.sendMessage(ChatColor.YELLOW + "/treasure list " + ChatColor.GRAY + "- 查看寻宝列表（指南针GUI）");
        player.sendMessage(ChatColor.YELLOW + "/treasure compass " + ChatColor.GRAY + "- 打开指南针领取面板");
        player.sendMessage(ChatColor.YELLOW + "/treasure my " + ChatColor.GRAY + "- 查看我的寻宝（Shift+左键可取消自己发起的）");
        player.sendMessage(ChatColor.YELLOW + "/treasure info " + ChatColor.GRAY + "- 查看我参与的寻宝信息");
        player.sendMessage(ChatColor.YELLOW + "/treasure cancel <ID> " + ChatColor.GRAY + "- 取消自己发起的寻宝");
        if (player.hasPermission("tloot.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/treasure remove <ID> " + ChatColor.GRAY + "- 强制移除宝藏 (管理员)");
            player.sendMessage(ChatColor.YELLOW + "/treasure reload " + ChatColor.GRAY + "- 重载配置 (管理员)");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 0) {
            return completions;
        }

        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "join", "list", "compass", "my", "info", "cancel", "help"));
            if (sender.hasPermission("tloot.admin")) {
                completions.addAll(Arrays.asList("remove", "reload"));
            }
        } else if (args.length == 2
                && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("info")
                || args[0].equalsIgnoreCase("remove"))
                && sender.hasPermission("tloot.admin")) {
            // 内部 ID 不再展示给普通玩家，仅管理员补全，方便 /treasure info <ID> 排查
            for (Treasure treasure : plugin.getTreasureManager().treasuresView()) {
                if (!treasure.isExpired()) {
                    completions.add(treasure.getId());
                }
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        completions.removeIf(entry -> !entry.toLowerCase().startsWith(lastArg));

        return completions;
    }
}
