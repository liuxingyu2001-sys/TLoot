package com.tloot.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.storage.MySQLStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RedisSyncManager {

    private final TLoot plugin;
    private final Gson gson = new Gson();
    private final String serverId;
    private final String channel;
    private final String host;
    private final int port;
    private final String password;

    private JedisPool jedisPool;
    private Thread subscriberThread;
    private volatile boolean running = false;
    private volatile JedisPubSub pubSub;
    private SyncCallback callback;
    private final ExecutorService publishExecutor = Executors.newSingleThreadExecutor();

    public interface SyncCallback {
        void onTreasureCreated(Treasure treasure);
        void onTreasureRemoved(String treasureId);
        void onTreasureClaimed(String treasureId, String claimerName, String ownerName);
        void onTreasureExpired(String treasureId);
        void onPlayerJoined(String treasureId, UUID participantUuid);
    }

    public RedisSyncManager(TLoot plugin, String host, int port, String password,
                            String channel, String serverId) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.password = password;
        this.channel = channel;
        this.serverId = (serverId == null || serverId.isEmpty())
                ? UUID.randomUUID().toString().substring(0, 8)
                : serverId;
    }

    public void init(SyncCallback callback) {
        this.callback = callback;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(4);
        poolConfig.setTestOnBorrow(true);

        if (password != null && !password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, 2000);
        }

        running = true;
        subscriberThread = new Thread(this::subscribeLoop, "TLoot-RedisSync");
        subscriberThread.setDaemon(true);
        subscriberThread.start();

        plugin.getLogger().info("Redis 跨服同步已启用，服务器ID: " + serverId);
    }

    private void subscribeLoop() {
        while (running) {
            try (Jedis jedis = jedisPool.getResource()) {
                pubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        handleMessage(message);
                    }
                };
                jedis.subscribe(pubSub, channel);
            } catch (Exception e) {
                if (running) {
                    plugin.getLogger().warning("Redis 连接断开，5秒后重连: " + e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void handleMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String fromServer = json.get("serverId").getAsString();

            if (fromServer.equals(serverId)) {
                return;
            }

            String type = json.get("type").getAsString();
            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : null;

            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (type) {
                    case "CREATE" -> handleCreate(data);
                    case "REMOVE" -> handleRemove(json);
                    case "CLAIM" -> handleClaim(json, data);
                    case "EXPIRE" -> handleExpire(json);
                    case "JOIN" -> handleJoin(json, data);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("处理Redis同步消息失败: " + e.getMessage());
        }
    }

    private void handleCreate(JsonObject data) {
        if (data == null || callback == null) return;

        String id = data.get("id").getAsString();
        UUID ownerUuid = UUID.fromString(data.get("ownerUuid").getAsString());
        String ownerName = data.get("ownerName").getAsString();
        String worldName = data.get("world").getAsString();
        double x = data.get("x").getAsDouble();
        double y = data.get("y").getAsDouble();
        double z = data.get("z").getAsDouble();
        int guaranteedCoins = data.get("guaranteedCoins").getAsInt();
        int ticketPrice = data.get("ticketPrice").getAsInt();
        long createTime = data.get("createTime").getAsLong();
        long expireTime = data.get("expireTime").getAsLong();

        World world = Bukkit.getWorld(worldName);
        Location location = new Location(world, x, y, z);

        List<ItemStack> items = new ArrayList<>();
        if (data.has("items")) {
            items = MySQLStorage.itemStacksFromBase64(data.get("items").getAsString());
        }

        List<String> commands = new ArrayList<>();
        if (data.has("commands")) {
            for (var cmd : data.getAsJsonArray("commands")) {
                commands.add(cmd.getAsString());
            }
        }

        Treasure treasure = new Treasure(id, ownerUuid, ownerName, worldName, location,
                guaranteedCoins, ticketPrice, items, commands, expireTime, createTime);

        if (data.has("participants")) {
            for (var p : data.getAsJsonArray("participants")) {
                treasure.addParticipant(UUID.fromString(p.getAsString()));
            }
        }

        callback.onTreasureCreated(treasure);
    }

    private void handleRemove(JsonObject json) {
        if (callback == null) return;
        String treasureId = json.get("treasureId").getAsString();
        callback.onTreasureRemoved(treasureId);
    }

    private void handleClaim(JsonObject json, JsonObject data) {
        if (callback == null) return;
        String treasureId = json.get("treasureId").getAsString();
        String claimerName = data != null && data.has("claimerName") ? data.get("claimerName").getAsString() : "未知";
        String ownerName = data != null && data.has("ownerName") ? data.get("ownerName").getAsString() : "未知";
        callback.onTreasureClaimed(treasureId, claimerName, ownerName);
    }

    private void handleExpire(JsonObject json) {
        if (callback == null) return;
        String treasureId = json.get("treasureId").getAsString();
        callback.onTreasureExpired(treasureId);
    }

    private void handleJoin(JsonObject json, JsonObject data) {
        if (callback == null) return;
        String treasureId = json.get("treasureId").getAsString();
        UUID participantUuid = UUID.fromString(data.get("participantUuid").getAsString());
        callback.onPlayerJoined(treasureId, participantUuid);
    }

    // ==================== 发布方法（异步） ====================

    public void publishCreate(Treasure treasure) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "CREATE");
        json.addProperty("serverId", serverId);

        JsonObject data = new JsonObject();
        data.addProperty("id", treasure.getId());
        data.addProperty("ownerUuid", treasure.getOwnerUuid().toString());
        data.addProperty("ownerName", treasure.getOwnerName());
        data.addProperty("world", treasure.getWorldName());
        data.addProperty("x", treasure.getLocation().getX());
        data.addProperty("y", treasure.getLocation().getY());
        data.addProperty("z", treasure.getLocation().getZ());
        data.addProperty("guaranteedCoins", treasure.getGuaranteedCoins());
        data.addProperty("ticketPrice", treasure.getTicketPrice());
        data.addProperty("createTime", treasure.getCreateTime());
        data.addProperty("expireTime", treasure.getExpireTime());
        data.addProperty("items", MySQLStorage.itemStacksToBase64(treasure.getItems()));

        JsonArray commandsArray = new JsonArray();
        for (String cmd : treasure.getCommands()) {
            commandsArray.add(cmd);
        }
        data.add("commands", commandsArray);

        JsonArray participantsArray = new JsonArray();
        for (UUID uuid : treasure.participantsView()) {
            participantsArray.add(uuid.toString());
        }
        data.add("participants", participantsArray);

        json.add("data", data);
        publishAsync(gson.toJson(json));
    }

    public void publishRemove(String treasureId) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "REMOVE");
        json.addProperty("serverId", serverId);
        json.addProperty("treasureId", treasureId);
        publishAsync(gson.toJson(json));
    }

    public void publishClaim(String treasureId, String claimerName, String ownerName) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "CLAIM");
        json.addProperty("serverId", serverId);
        json.addProperty("treasureId", treasureId);

        JsonObject data = new JsonObject();
        data.addProperty("claimerName", claimerName);
        data.addProperty("ownerName", ownerName);
        json.add("data", data);

        publishAsync(gson.toJson(json));
    }

    public void publishExpire(String treasureId) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "EXPIRE");
        json.addProperty("serverId", serverId);
        json.addProperty("treasureId", treasureId);
        publishAsync(gson.toJson(json));
    }

    public void publishJoin(String treasureId, UUID participantUuid) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "JOIN");
        json.addProperty("serverId", serverId);
        json.addProperty("treasureId", treasureId);

        JsonObject data = new JsonObject();
        data.addProperty("participantUuid", participantUuid.toString());
        json.add("data", data);

        publishAsync(gson.toJson(json));
    }

    private void publishAsync(String message) {
        publishExecutor.submit(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish(channel, message);
            } catch (Exception e) {
                plugin.getLogger().warning("发布Redis消息失败: " + e.getMessage());
            }
        });
    }

    public String getServerId() {
        return serverId;
    }

    public void shutdown() {
        running = false;

        if (pubSub != null) {
            try {
                pubSub.unsubscribe();
            } catch (Exception ignored) {
            }
        }

        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }

        publishExecutor.shutdown();
        try {
            if (!publishExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                publishExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            publishExecutor.shutdownNow();
        }

        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}
