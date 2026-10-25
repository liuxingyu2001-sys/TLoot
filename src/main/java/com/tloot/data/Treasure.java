package com.tloot.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class Treasure {

    private final String id;
    private final UUID ownerUuid;
    private final String ownerName;
    private final String worldName;
    private final Location location;
    private final int guaranteedCoins;
    private final int ticketPrice;
    private final List<ItemStack> items;
    private final List<String> commands;
    private final long createTime;
    private final long expireTime;
    private final List<UUID> participants;
    /** 厄运宝藏：宝箱在守卫被全部击杀后才出现 */
    private final boolean doom;
    /** 位置索引键，构造后不变，预先算好避免每次查询都拼接字符串 */
    private final String locationKey;

    public Treasure(String id, UUID ownerUuid, String ownerName, Location location,
                    int guaranteedCoins, int ticketPrice, List<ItemStack> items, long expireTime) {
        this(id, ownerUuid, ownerName, location, guaranteedCoins, ticketPrice, items, null, expireTime, System.currentTimeMillis());
    }

    public Treasure(String id, UUID ownerUuid, String ownerName, Location location,
                    int guaranteedCoins, int ticketPrice, List<ItemStack> items, List<String> commands,
                    long expireTime, long createTime) {
        this(id, ownerUuid, ownerName,
                location.getWorld() != null ? location.getWorld().getName() : "unknown",
                location, guaranteedCoins, ticketPrice, items, commands, expireTime, createTime);
    }

    public Treasure(String id, UUID ownerUuid, String ownerName, Location location,
                    int guaranteedCoins, int ticketPrice, List<ItemStack> items, List<String> commands,
                    long expireTime, long createTime, boolean doom) {
        this(id, ownerUuid, ownerName,
                location.getWorld() != null ? location.getWorld().getName() : "unknown",
                location, guaranteedCoins, ticketPrice, items, commands, expireTime, createTime, doom);
    }

    public Treasure(String id, UUID ownerUuid, String ownerName, String worldName, Location location,
                    int guaranteedCoins, int ticketPrice, List<ItemStack> items, List<String> commands,
                    long expireTime, long createTime) {
        this(id, ownerUuid, ownerName, worldName, location, guaranteedCoins, ticketPrice, items, commands,
                expireTime, createTime, false);
    }

    public Treasure(String id, UUID ownerUuid, String ownerName, String worldName, Location location,
                    int guaranteedCoins, int ticketPrice, List<ItemStack> items, List<String> commands,
                    long expireTime, long createTime, boolean doom) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName == null || ownerName.isBlank() ? "未知" : ownerName;
        this.worldName = worldName;
        this.location = location;
        this.guaranteedCoins = guaranteedCoins;
        this.ticketPrice = ticketPrice;
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
        this.createTime = createTime;
        this.expireTime = expireTime;
        this.doom = doom;
        this.participants = new CopyOnWriteArrayList<>();
        this.locationKey = computeLocationKey();
    }

    public String getId() {
        return id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Location getLocation() {
        if (location == null) {
            return null;
        }
        // 多世界插件可能在 TLoot 加载存档之后才加载世界，此时反序列化得到的世界是 null。
        // 这里按世界名惰性补全，否则宝藏所在世界加载完成后，指针仍会一直提示"世界不可用"。
        if (location.getWorld() == null && worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                location.setWorld(world);
            }
        }
        return location;
    }

    public String getWorldName() {
        return worldName;
    }

    public boolean isWorldLoaded() {
        return getLocation().getWorld() != null;
    }

    public int getGuaranteedCoins() {
        return guaranteedCoins;
    }

    /** 厄运宝藏：守卫未清空时宝箱不会出现，奖励也更丰厚 */
    public boolean isDoom() {
        return doom;
    }

    public int getTicketPrice() {
        return ticketPrice;
    }

    public List<ItemStack> getItems() {
        return new ArrayList<>(items);
    }

    public List<String> getCommands() {
        return new ArrayList<>(commands);
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public boolean isExpired() {
        if (expireTime <= 0 || createTime <= 0) {
            // 数据损坏或旧格式（缺少过期时间）时按已过期处理，避免永不过期的宝藏堆积
            return true;
        }
        // 用「已存在时长」比较而不是 createTime + expireTime：
        // 后者在 createTime 异常（手工改库）时可能发生 long 溢出，导致宝藏被误判为已过期
        return System.currentTimeMillis() - createTime > expireTime;
    }

    public long getRemainingTime() {
        if (expireTime <= 0 || createTime <= 0) {
            return 0L;
        }
        return Math.max(0L, expireTime - (System.currentTimeMillis() - createTime));
    }

    public String getRemainingTimeFormatted() {
        long remaining = getRemainingTime();
        if (remaining <= 0) {
            return "即将过期";
        }
        if (remaining < 60_000L) {
            return "不足1分钟";
        }
        long hours = remaining / (1000 * 60 * 60);
        long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);
        if (hours <= 0) {
            return minutes + "分钟";
        }
        return hours + "小时" + minutes + "分钟";
    }

    public List<UUID> getParticipants() {
        return new ArrayList<>(participants);
    }

    /**
     * 只读遍历用视图，避免热路径（每 tick 任务、GUI 构建）反复复制列表。
     */
    public List<UUID> participantsView() {
        return java.util.Collections.unmodifiableList(participants);
    }

    public int getParticipantCount() {
        return participants.size();
    }

    /**
     * @return 是否新增成功（重复参与返回 false，调用方可据此跳过无意义的落库）
     */
    public boolean addParticipant(UUID uuid) {
        if (uuid == null || participants.contains(uuid)) {
            return false;
        }
        participants.add(uuid);
        return true;
    }

    public boolean hasParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public String getLocationKey() {
        return locationKey;
    }

    private String computeLocationKey() {
        if (location == null) {
            return worldName + ",0,0,0";
        }
        return worldName + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    /**
     * 位置键的静态形式，便于在创建前做占用检查（无需构造 Treasure 对象）。
     */
    public static String locationKeyOf(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getName() + "," + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }

    public Map<String, Object> serialize() {
        if (location == null) {
            // 仅数据损坏时可能出现；直接抛出让存储层记录并跳过该条，避免整份存档写入失败
            throw new IllegalStateException("宝藏 #" + id + " 缺少坐标数据，无法序列化");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("ownerUuid", ownerUuid.toString());
        data.put("ownerName", ownerName);
        data.put("world", worldName);
        data.put("x", location.getX());
        data.put("y", location.getY());
        data.put("z", location.getZ());
        data.put("guaranteedCoins", guaranteedCoins);
        data.put("ticketPrice", ticketPrice);
        data.put("createTime", createTime);
        data.put("expireTime", expireTime);
        data.put("doom", doom);

        List<Map<String, Object>> itemsData = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }
            // 顺序即索引，无需再存一份冗余的 index 字段
            itemsData.add(Map.of("item", item.serialize()));
        }
        data.put("items", itemsData);

        data.put("commands", new ArrayList<>(commands));

        List<String> participantsData = new ArrayList<>();
        for (UUID uuid : participants) {
            participantsData.add(uuid.toString());
        }
        data.put("participants", participantsData);

        return data;
    }

    /**
     * 兼容旧数据：反序列化失败时返回 null，由调用方跳过该条记录，避免一条坏数据导致整份存档加载失败。
     */
    public static Treasure deserialize(Map<String, Object> data) {
        try {
            return deserializeInternal(data);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[TLoot] 跳过无法解析的宝藏数据: " + e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Treasure deserializeInternal(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        String id = (String) data.get("id");
        Object ownerUuidRaw = data.get("ownerUuid");
        if (id == null || !(ownerUuidRaw instanceof String ownerUuidStr)) {
            return null;
        }

        UUID ownerUuid = UUID.fromString(ownerUuidStr);
        String ownerName = (String) data.get("ownerName");
        String worldName = (String) data.get("world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        double x = toDouble(data.get("x"));
        double y = toDouble(data.get("y"));
        double z = toDouble(data.get("z"));
        Location location = new Location(world, x, y, z);
        int guaranteedCoins = toInt(data.get("guaranteedCoins"));
        int ticketPrice = data.containsKey("ticketPrice") ? toInt(data.get("ticketPrice")) : 0;
        long createTime = toLong(data.get("createTime"));
        long expireTime = toLong(data.get("expireTime"));

        List<ItemStack> items = new ArrayList<>();
        Object itemsRaw = data.get("items");
        if (itemsRaw instanceof List<?> itemsData) {
            for (Object itemEntry : itemsData) {
                if (!(itemEntry instanceof Map<?, ?> itemData)) {
                    continue;
                }
                Object itemMap = itemData.get("item");
                if (itemMap == null) {
                    // 兼容直接存放 ItemStack 序列化 Map 的旧格式
                    itemMap = itemData;
                }
                if (itemMap instanceof Map<?, ?>) {
                    ItemStack item = ItemStack.deserialize((Map<String, Object>) itemMap);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        }

        List<String> commands = new ArrayList<>();
        if (data.get("commands") instanceof List<?> commandsRaw) {
            for (Object cmd : commandsRaw) {
                if (cmd != null) {
                    commands.add(String.valueOf(cmd));
                }
            }
        }

        Treasure treasure = new Treasure(id, ownerUuid, ownerName, worldName, location,
                guaranteedCoins, ticketPrice, items, commands, expireTime, createTime,
                Boolean.TRUE.equals(data.get("doom")));

        if (data.get("participants") instanceof List<?> participantsData) {
            for (Object uuidObj : participantsData) {
                if (uuidObj == null) {
                    continue;
                }
                try {
                    UUID participant = UUID.fromString(String.valueOf(uuidObj).trim());
                    if (!treasure.participants.contains(participant)) {
                        treasure.participants.add(participant);
                    }
                } catch (IllegalArgumentException ignored) {
                    // 忽略损坏的参与者记录
                }
            }
        }

        return treasure;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private static long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}
