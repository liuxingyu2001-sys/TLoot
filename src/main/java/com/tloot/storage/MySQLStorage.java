package com.tloot.storage;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MySQLStorage implements StorageBackend {

    private final TLoot plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String tablePrefix;
    private final int poolSize;

    private HikariDataSource dataSource;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MySQLStorage(TLoot plugin, String host, int port, String database,
                        String username, String password, String tablePrefix, int poolSize) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix;
        this.poolSize = poolSize;
    }

    @Override
    public void init() {
        createDatabase();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        config.setPoolName("TLoot-HikariPool");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        createTable();
        plugin.getLogger().info("MySQL 存储已连接: " + host + ":" + port + "/" + database);
    }

    private void createDatabase() {
        String url = "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection conn = java.sql.DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException e) {
            plugin.getLogger().severe("无法自动创建数据库: " + e.getMessage());
        }
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "treasures (" +
                "id VARCHAR(8) PRIMARY KEY," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "owner_name VARCHAR(16) NOT NULL," +
                "world VARCHAR(64) NOT NULL," +
                "x DOUBLE NOT NULL," +
                "y DOUBLE NOT NULL," +
                "z DOUBLE NOT NULL," +
                "guaranteed_coins INT NOT NULL," +
                "ticket_price INT NOT NULL," +
                "create_time BIGINT NOT NULL," +
                "expire_time BIGINT NOT NULL," +
                "items LONGTEXT," +
                "commands TEXT," +
                "participants TEXT" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("无法创建数据表: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Treasure> loadAll() {
        Map<String, Treasure> result = new HashMap<>();
        String sql = "SELECT * FROM " + tablePrefix + "treasures";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Treasure treasure = fromResultSet(rs);
                if (treasure != null && !treasure.isExpired()) {
                    result.put(treasure.getId(), treasure);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("无法加载宝藏数据: " + e.getMessage());
        }
        return result;
    }

    @Override
    public void save(Treasure treasure) {
        executor.submit(() -> {
            String sql = "INSERT INTO " + tablePrefix + "treasures " +
                    "(id, owner_uuid, owner_name, world, x, y, z, guaranteed_coins, ticket_price, create_time, expire_time, items, commands, participants) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "owner_uuid=VALUES(owner_uuid), owner_name=VALUES(owner_name), world=VALUES(world), " +
                    "x=VALUES(x), y=VALUES(y), z=VALUES(z), guaranteed_coins=VALUES(guaranteed_coins), " +
                    "ticket_price=VALUES(ticket_price), create_time=VALUES(create_time), expire_time=VALUES(expire_time), " +
                    "items=VALUES(items), commands=VALUES(commands), participants=VALUES(participants)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, treasure.getId());
                ps.setString(2, treasure.getOwnerUuid().toString());
                ps.setString(3, treasure.getOwnerName());
                ps.setString(4, treasure.getWorldName());
                Location loc = treasure.getLocation();
                ps.setDouble(5, loc.getX());
                ps.setDouble(6, loc.getY());
                ps.setDouble(7, loc.getZ());
                ps.setInt(8, treasure.getGuaranteedCoins());
                ps.setInt(9, treasure.getTicketPrice());
                ps.setLong(10, treasure.getCreateTime());
                ps.setLong(11, treasure.getExpireTime());
                ps.setString(12, itemStacksToBase64(treasure.getItems()));
                ps.setString(13, String.join("\n", treasure.getCommands()));
                List<String> participantStrs = new ArrayList<>();
                for (UUID uuid : treasure.getParticipants()) {
                    participantStrs.add(uuid.toString());
                }
                ps.setString(14, String.join(",", participantStrs));
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("无法保存宝藏 " + treasure.getId() + ": " + e.getMessage());
            }
        });
    }

    @Override
    public void delete(String id) {
        executor.submit(() -> {
            String sql = "DELETE FROM " + tablePrefix + "treasures WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("无法删除宝藏 " + id + ": " + e.getMessage());
            }
        });
    }

    @Override
    public void saveAll(Collection<Treasure> treasures) {
        executor.submit(() -> {
            String sql = "INSERT INTO " + tablePrefix + "treasures " +
                    "(id, owner_uuid, owner_name, world, x, y, z, guaranteed_coins, ticket_price, create_time, expire_time, items, commands, participants) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "owner_uuid=VALUES(owner_uuid), owner_name=VALUES(owner_name), world=VALUES(world), " +
                    "x=VALUES(x), y=VALUES(y), z=VALUES(z), guaranteed_coins=VALUES(guaranteed_coins), " +
                    "ticket_price=VALUES(ticket_price), create_time=VALUES(create_time), expire_time=VALUES(expire_time), " +
                    "items=VALUES(items), commands=VALUES(commands), participants=VALUES(participants)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);
                for (Treasure treasure : treasures) {
                    ps.setString(1, treasure.getId());
                    ps.setString(2, treasure.getOwnerUuid().toString());
                    ps.setString(3, treasure.getOwnerName());
                    ps.setString(4, treasure.getWorldName());
                    Location loc = treasure.getLocation();
                    ps.setDouble(5, loc.getX());
                    ps.setDouble(6, loc.getY());
                    ps.setDouble(7, loc.getZ());
                    ps.setInt(8, treasure.getGuaranteedCoins());
                    ps.setInt(9, treasure.getTicketPrice());
                    ps.setLong(10, treasure.getCreateTime());
                    ps.setLong(11, treasure.getExpireTime());
                    ps.setString(12, itemStacksToBase64(treasure.getItems()));
                    ps.setString(13, String.join("\n", treasure.getCommands()));
                    List<String> participantStrs = new ArrayList<>();
                    for (UUID uuid : treasure.getParticipants()) {
                        participantStrs.add(uuid.toString());
                    }
                    ps.setString(14, String.join(",", participantStrs));
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                plugin.getLogger().severe("无法批量保存宝藏数据: " + e.getMessage());
            }
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private Treasure fromResultSet(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
        String ownerName = rs.getString("owner_name");
        String worldName = rs.getString("world");
        double x = rs.getDouble("x");
        double y = rs.getDouble("y");
        double z = rs.getDouble("z");
        int guaranteedCoins = rs.getInt("guaranteed_coins");
        int ticketPrice = rs.getInt("ticket_price");
        long createTime = rs.getLong("create_time");
        long expireTime = rs.getLong("expire_time");
        String itemsBase64 = rs.getString("items");
        String commandsStr = rs.getString("commands");
        String participantsStr = rs.getString("participants");

        World world = Bukkit.getWorld(worldName);
        Location location = new Location(world, x, y, z);

        List<ItemStack> items = itemStacksFromBase64(itemsBase64);

        List<String> commands = new ArrayList<>();
        if (commandsStr != null && !commandsStr.isEmpty()) {
            for (String cmd : commandsStr.split("\n")) {
                if (!cmd.isEmpty()) {
                    commands.add(cmd);
                }
            }
        }

        Treasure treasure = new Treasure(id, ownerUuid, ownerName, worldName, location,
                guaranteedCoins, ticketPrice, items, commands, expireTime, createTime);

        if (participantsStr != null && !participantsStr.isEmpty()) {
            for (String uuidStr : participantsStr.split(",")) {
                if (!uuidStr.isEmpty()) {
                    treasure.addParticipant(UUID.fromString(uuidStr.trim()));
                }
            }
        }

        return treasure;
    }

    public static String itemStacksToBase64(List<ItemStack> items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.size());
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("无法序列化物品", e);
        }
    }

    public static List<ItemStack> itemStacksFromBase64(String data) {
        List<ItemStack> items = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return items;
        }
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();
            for (int i = 0; i < size; i++) {
                ItemStack item = (ItemStack) dataInput.readObject();
                if (item != null) {
                    items.add(item);
                }
            }
            dataInput.close();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("无法反序列化物品", e);
        }
        return items;
    }
}
