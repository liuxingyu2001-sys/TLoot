package com.tloot.listener;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.item.PointerItem;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 寻宝指针（指南针）追踪。
 *
 * <p>指南针的目标是「玩家级」的：服务端通过 {@code ClientboundSetDefaultSpawnPositionPacket}
 * 下发，客户端把该位置当作指南针指向点。而服务端在以下时机都会用世界出生点重新下发这个包：
 * 玩家加入服务器、切换世界、死亡重生、管理员修改世界出生点。只要玩家在这期间手持指针，
 * 客户端的目标就被重置成出生点；如果插件只在「指针指向的宝藏 ID 变化时」才设置一次目标，
 * 重置后就再也不会指回宝藏，表现为「指针失效、乱指 / 指向出生点」。
 *
 * <p>因此这里不再信任一次性缓存：只要玩家手持有效指针，就每秒重新下发一次目标
 * （一个十几字节的小包，开销可忽略），这样无论服务端在什么时机把目标重置成出生点，
 * 最多 1 秒后都会重新指回宝藏。同时：
 * <ul>
 *   <li>主手或副手持指针都生效（原先只认主手，玩家把指针放副手时会被每秒重置成出生点）；</li>
 *   <li>宝藏不在当前世界时清掉旧目标，并在 ActionBar 提示先去哪个世界，而不是指向一个无效坐标。</li>
 * </ul>
 */
public class PointerListener implements Listener {

    /** 描述文本（剩余时间）刷新间隔，毫秒 */
    private static final long LORE_UPDATE_INTERVAL = 10_000L;
    /** 近距离粒子刷新间隔，毫秒 */
    private static final long PARTICLE_INTERVAL = 500L;
    /** 跨世界提示的 ActionBar 刷新间隔，毫秒 */
    private static final long WORLD_HINT_INTERVAL = 2_000L;

    private final TLoot plugin;
    private final Map<UUID, Long> lastParticleTime = new ConcurrentHashMap<>();
    private final Map<UUID, CompassState> compassStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLoreUpdate = new ConcurrentHashMap<>();

    private BukkitTask pointerTask;

    public PointerListener(TLoot plugin) {
        this.plugin = plugin;
        startPointerTask();
    }

    /**
     * 指针当前所在的手与物品。
     */
    private static final class HeldPointer {
        private final ItemStack item;
        private final EquipmentSlot slot;
        private final String treasureId;

        private HeldPointer(ItemStack item, EquipmentSlot slot, String treasureId) {
            this.item = item;
            this.slot = slot;
            this.treasureId = treasureId;
        }
    }

    /**
     * 每个玩家当前的指针目标状态，用于「没拿指针时恢复出生点」和跨世界提示节流。
     */
    private static final class CompassState {
        private long lastHint;
        private boolean targetActive;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    /**
     * 清理玩家状态。
     * 原先仅清理当前在线玩家的 Map 条目，配合每秒写入的任务，
     * 跨服/重载等非正常退出路径会在 Map 中残留 UUID -> Long 条目（内存泄漏）。
     */
    public void clearPlayerState(UUID uuid) {
        if (uuid == null) {
            return;
        }
        lastParticleTime.remove(uuid);
        compassStates.remove(uuid);
        lastLoreUpdate.remove(uuid);
    }

    /**
     * 立即刷新一次指针状态（右键使用指针时调用，避免最多 1 秒的延迟）。
     */
    public void refreshNow(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        tickPlayer(player);
    }

    public void shutdown() {
        if (pointerTask != null) {
            pointerTask.cancel();
            pointerTask = null;
        }
        lastParticleTime.clear();
        compassStates.clear();
        lastLoreUpdate.clear();
    }

    private void startPointerTask() {
        pointerTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    tickPlayer(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tickPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        HeldPointer held = findHeldPointer(player);
        if (held == null) {
            releaseCompass(player);
            return;
        }

        Treasure treasure = plugin.getTreasureManager().getTreasure(held.treasureId);
        if (treasure == null || treasure.isExpired()) {
            releaseCompass(player);
            return;
        }

        Location treasureLoc = treasure.getLocation();
        Location playerLoc = player.getLocation();
        World treasureWorld = treasureLoc.getWorld();

        // 指南针无法跨世界指向：清掉旧目标并提示玩家该去哪个世界，
        // 否则指南针会继续指向上一个目标（通常是出生点），玩家只会觉得"指针坏了"
        if (treasureWorld == null || !playerLoc.getWorld().equals(treasureWorld)) {
            clearCompassTarget(player);
            sendWorldHint(player, treasure, treasureWorld == null);
            updateLoreIfNeeded(player, held, treasure);
            return;
        }

        ensureCompassTarget(player, treasureLoc);

        double distance = playerLoc.distance(treasureLoc);
        if (distance <= plugin.getConfigManager().getClaimDistance()) {
            showNearbyParticles(player, treasureLoc);
        }

        if (plugin.getConfigManager().isPointerActionBarEnabled()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("距离: " + (int) Math.round(distance) + " 格"));
        }

        updateLoreIfNeeded(player, held, treasure);
    }

    /**
     * 主手优先，其次副手。原实现只认主手：玩家把指针放副手时，
     * 每秒都会被当成"没拿指针"而把指南针目标重置成世界出生点。
     */
    private HeldPointer findHeldPointer(Player player) {
        PlayerInventory inventory = player.getInventory();

        ItemStack mainHand = inventory.getItemInMainHand();
        String mainId = PointerItem.getTreasureId(mainHand);
        if (mainId != null) {
            return new HeldPointer(mainHand, EquipmentSlot.HAND, mainId);
        }

        ItemStack offHand = inventory.getItemInOffHand();
        String offId = PointerItem.getTreasureId(offHand);
        if (offId != null) {
            return new HeldPointer(offHand, EquipmentSlot.OFF_HAND, offId);
        }

        return null;
    }

    /**
     * 重新下发指南针目标。
     *
     * <p>直接每秒重发，而不是只在「目标变化时」发一次：服务端在玩家重生、切换世界、
     * 修改世界出生点时会用出生点覆盖客户端目标，一次性缓存在被覆盖后不会再生效。
     * 目标包只有十几字节，每个手持指针的玩家每秒一个，开销远小于一次实体移动广播。
     */
    private void ensureCompassTarget(Player player, Location target) {
        player.setCompassTarget(target);
        compassStates.computeIfAbsent(player.getUniqueId(), key -> new CompassState()).targetActive = true;
    }

    /**
     * 跨世界（或宝藏世界未加载）时在 ActionBar 给出明确提示。
     */
    private void sendWorldHint(Player player, Treasure treasure, boolean worldUnavailable) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        CompassState state = compassStates.computeIfAbsent(uuid, key -> new CompassState());
        if (now - state.lastHint < WORLD_HINT_INTERVAL) {
            return;
        }
        state.lastHint = now;

        String message = worldUnavailable
                ? plugin.getMessageManager().get("pointer.world-unavailable")
                : plugin.getMessageManager().get("pointer.wrong-world",
                        "world", plugin.getConfigManager().getWorldDisplayName(treasure.getWorldName()));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    /**
     * 指针描述中唯一会变化的是剩余时间，10 秒刷新一次即可；
     * 且仅在文本真正变化时才写回物品，避免无意义的元数据更新造成的客户端不同步与性能开销。
     */
    private void updateLoreIfNeeded(Player player, HeldPointer held, Treasure treasure) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUpdate = lastLoreUpdate.get(uuid);
        if (lastUpdate != null && now - lastUpdate < LORE_UPDATE_INTERVAL) {
            return;
        }

        lastLoreUpdate.put(uuid, now);
        if (PointerItem.updatePointerLore(held.item, treasure)) {
            if (held.slot == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(held.item);
            } else {
                player.getInventory().setItemInMainHand(held.item);
            }
        }
    }

    /**
     * 玩家没有持有有效指针：回收指针状态并把指南针恢复成世界出生点。
     */
    private void releaseCompass(Player player) {
        UUID uuid = player.getUniqueId();
        CompassState state = compassStates.remove(uuid);
        lastLoreUpdate.remove(uuid);
        if (state == null) {
            return;
        }

        if (state.targetActive) {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
        }
        clearActionBar(player);
    }

    /**
     * 清掉本插件下发的指南针目标，但保留状态（用于跨世界提示的节流）。
     */
    private void clearCompassTarget(Player player) {
        CompassState state = compassStates.get(player.getUniqueId());
        if (state == null || !state.targetActive) {
            return;
        }

        state.targetActive = false;
        player.setCompassTarget(player.getWorld().getSpawnLocation());
        clearActionBar(player);
    }

    private void clearActionBar(Player player) {
        if (plugin.getConfigManager().isPointerActionBarEnabled()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
    }

    private void showNearbyParticles(Player player, Location treasureLoc) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastParticleTime.get(uuid);

        if (lastTime != null && now - lastTime < PARTICLE_INTERVAL) {
            return;
        }
        lastParticleTime.put(uuid, now);

        Location loc = treasureLoc.clone().add(0, 1, 0);
        player.spawnParticle(Particle.END_ROD, loc, 10, 0.5, 0.5, 0.5, 0.1);
        player.spawnParticle(Particle.FIREWORK, loc, 5, 0.3, 0.3, 0.3, 0.05);
    }
}
