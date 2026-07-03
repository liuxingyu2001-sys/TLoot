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

public class Treasure {

    private final String id;
    private final UUID ownerUuid;
    private final String ownerName;
    private final Location location;
    private final int guaranteedCoins;
    private final int ticketPrice;
    private final List<ItemStack> items;
    private final List<String> commands;
    private final long createTime;
    private final long expireTime;
    private final List<UUID> participants;

    public Treasure(String id, UUID ownerUuid, String ownerName, Location location,
                    int guaranteedCoins, int ticketPrice, List<ItemStack> items, long expireTime) {
        this(id, ownerUuid, ownerName, location, guaranteedCoins, ticketPrice, items, null, expireTime, System.currentTimeMillis());
    }

    public Treasure(String id, UUID ownerUuid, String ownerName, Location location,
                    int guaranteedCoins, int ticketPrice, List<ItemStack> items, List<String> commands,
                    long expireTime, long createTime) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.location = location;
        this.guaranteedCoins = guaranteedCoins;
        this.ticketPrice = ticketPrice;
        this.items = new ArrayList<>(items);
        this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
        this.createTime = createTime;
        this.expireTime = expireTime;
        this.participants = new ArrayList<>();
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
        return location;
    }
    
    public String getWorldName() {
        return location.getWorld() != null ? location.getWorld().getName() : "未知世界";
    }

    public int getGuaranteedCoins() {
        return guaranteedCoins;
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
        return System.currentTimeMillis() > createTime + expireTime;
    }

    public long getRemainingTime() {
        long remaining = (createTime + expireTime) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public String getRemainingTimeFormatted() {
        long remaining = getRemainingTime();
        long hours = remaining / (1000 * 60 * 60);
        long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);
        return hours + "小时" + minutes + "分钟";
    }

    public List<UUID> getParticipants() {
        return new ArrayList<>(participants);
    }

    public void addParticipant(UUID uuid) {
        if (!participants.contains(uuid)) {
            participants.add(uuid);
        }
    }

    public boolean hasParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("ownerUuid", ownerUuid.toString());
        data.put("ownerName", ownerName);
        data.put("world", location.getWorld().getName());
        data.put("x", location.getX());
        data.put("y", location.getY());
        data.put("z", location.getZ());
        data.put("guaranteedCoins", guaranteedCoins);
        data.put("ticketPrice", ticketPrice);
        data.put("createTime", createTime);
        data.put("expireTime", expireTime);
        
        List<Map<String, Object>> itemsData = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("index", i);
            itemData.put("item", items.get(i).serialize());
            itemsData.add(itemData);
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

    @SuppressWarnings("unchecked")
    public static Treasure deserialize(Map<String, Object> data) {
        String id = (String) data.get("id");
        UUID ownerUuid = UUID.fromString((String) data.get("ownerUuid"));
        String ownerName = (String) data.get("ownerName");
        World world = Bukkit.getWorld((String) data.get("world"));
        if (world == null) {
            return null;
        }
        double x = toDouble(data.get("x"));
        double y = toDouble(data.get("y"));
        double z = toDouble(data.get("z"));
        Location location = new Location(world, x, y, z);
        int guaranteedCoins = toInt(data.get("guaranteedCoins"));
        int ticketPrice = data.containsKey("ticketPrice") ? toInt(data.get("ticketPrice")) : 0;
        long createTime = toLong(data.get("createTime"));
        long expireTime = toLong(data.get("expireTime"));

        List<ItemStack> items = new ArrayList<>();
        List<Map<String, Object>> itemsData = (List<Map<String, Object>>) data.get("items");
        if (itemsData != null) {
            for (Map<String, Object> itemData : itemsData) {
                Map<String, Object> itemMap = (Map<String, Object>) itemData.get("item");
                ItemStack item = ItemStack.deserialize(itemMap);
                items.add(item);
            }
        }

        List<String> commands = (List<String>) data.get("commands");
        if (commands == null) {
            commands = new ArrayList<>();
        }

        Treasure treasure = new Treasure(id, ownerUuid, ownerName, location, guaranteedCoins, ticketPrice, items, commands, expireTime, createTime);

        List<String> participantsData = (List<String>) data.get("participants");
        if (participantsData != null) {
            for (String uuidStr : participantsData) {
                treasure.participants.add(UUID.fromString(uuidStr));
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
        return System.currentTimeMillis();
    }
}
