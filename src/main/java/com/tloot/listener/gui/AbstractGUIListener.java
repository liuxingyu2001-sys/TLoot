package com.tloot.listener.gui;

import com.tloot.TLoot;
import com.tloot.gui.GUIManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/**
 * GUI 事件处理基类。
 *
 * 统一了原先在 4 个监听器中重复出现的判断逻辑：
 * - 事件是否发生在「本监听器负责的」GUI 内（按背包实例 + 界面类型判定，替代标题字符串比较）；
 * - 点击/拖拽一律取消，保护 GUI 内容不被拿走。
 *
 * 注意：Bukkit 的事件分发中，同一优先级下只要有一个监听器取消了可取消事件，
 * 后续声明 {@code ignoreCancelled = true} 的监听器就会被跳过（见 RegisteredListener#callEvent）。
 * 因此每个子类必须在取消事件之前先确认 GUI 类型匹配，否则会互相"吃掉"事件。
 */
public abstract class AbstractGUIListener implements Listener {

    protected final TLoot plugin;

    protected AbstractGUIListener(TLoot plugin) {
        this.plugin = plugin;
    }

    /** 本监听器负责的 GUI 类型标识（见 GUIManager 的 GUI_* 常量） */
    protected abstract String guiType();

    private boolean notPlayer(org.bukkit.entity.HumanEntity who) {
        return !(who instanceof Player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (notPlayer(event.getWhoClicked())) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        GUIManager guiManager = plugin.getGuiManager();
        InventoryView view = event.getView();

        if (guiManager == null || !guiManager.isGUIOpen(player, guiType(), view)) {
            return;
        }

        // 只取消属于本 GUI 的点击；其它 GUI 的监听器仍能收到事件
        event.setCancelled(true);

        Inventory top = view.getTopInventory();
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        handleClick(player, top, event.getSlot(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (notPlayer(event.getWhoClicked())) {
            return;
        }

        GUIManager guiManager = plugin.getGuiManager();
        if (guiManager == null
                || !guiManager.isGUIOpen((Player) event.getWhoClicked(), guiType(), event.getView())) {
            return;
        }

        event.setCancelled(true);
    }

    /**
     * 处理 GUI 内的点击。
     *
     * @param player 点击的玩家
     * @param top    插件创建的顶部背包
     * @param slot   被点击的槽位
     * @param event  原始点击事件
     */
    protected abstract void handleClick(Player player, Inventory top, int slot, InventoryClickEvent event);
}
