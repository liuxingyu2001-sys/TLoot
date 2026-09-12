package com.tloot.storage;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.util.TreasureBlocks;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
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

    private static final String UPSERT_SQL =
            "INSERT INTO %s (id, owner_uuid, owner_name, world, x, y, z, guaranteed_coins, ticket_price,"
                    + " create_time, expire_time, items, commands, participants) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "owner_uuid=VALUES(owner_uuid), owner_name=VALUES(owner_name), world=VALUES(world), "
                    + "x=VALUES(x), y=VALUES(y), z=VALUES(z), guaranteed_coins=VALUES(guaranteed_coins), "
                    + "ticket_price=VALUES(ticket_price), create_time=VALUES(create_time), "
                    + "expire_time=VALUES(expire_time), items=VALUES(items), commands=VALUES(commands), "
                    + "participants=VALUES(participants)";

    private HikariDataSource dataSource;
    /**
     * 单线程异步写库，有界队列。
     *
     * 旧实现使用无界队列（Executors.newSingleThreadExecutor）：数据库卡顿时任务会无限堆积，
     * 每个任务还持有 Treasure 强引用，长时间阻塞会持续占用内存。
     * 队列满说明数据库已严重滞后，此时丢弃最旧的待写任务（都是整条记录的全量 upsert，
     * 丢弃旧任务不会丢数据）比让服务器主线程执行 JDBC 写入更安全，同时记录日志便于排查。
     */
    private ExecutorService executor;
    /** 不可被队列溢出丢弃的任务（删除操作） */
    private final Set<Runnable> criticalTasks = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

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
        executor = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2048),
                new ThreadPoolExecutor.DiscardOldestPolicy() {
                    @Override
                    public void rejectedExecution(Runnable task, ThreadPoolExecutor pool) {
                        // 丢弃最旧的任务以释放队列空间；但删除类任务必须保留：
                        // 丢弃一条 delete 会让已被领取/过期的宝藏残留在数据库中，
                        // 重启后会被重新加载，玩家甚至可能为它再付一次参与费用。
                        if (dropOldestNonCritical(pool.getQueue())) {
                            plugin.getLogger().warning("数据库写入队列已满，已丢弃一条较早的宝藏变更（请检查数据库性能）");
                        } else if (!pool.getQueue().offer(task)) {
                            plugin.getLogger().severe("数据库写入队列已满且无法丢弃任务，已放弃一条待写入的宝藏变更");
                        }
                    }
                });

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
        if (dataSource == null) {
            plugin.getLogger().severe("数据库尚未初始化，无法加载宝藏数据");
            return result;
        }
        String sql = "SELECT * FROM " + tablePrefix + "treasures";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int skipped = 0;
            int expired = 0;
            while (rs.next()) {
                try {
                    Treasure treasure = fromResultSet(rs);
                    if (treasure == null) {
                        continue;
                    }
                    if (treasure.isExpired()) {
                        // 记录已过期但箱子可能仍残留在世界里：登记后在区块加载时回收
                        TreasureBlocks.scheduleCleanup(treasure.getLocation());
                        expired++;
                        continue;
                    }
                    result.put(treasure.getId(), treasure);
                } catch (Exception e) {
                    // 单行数据损坏（例如 uuid 字段被手工改坏）不应让整份宝藏数据加载失败
                    skipped++;
                    plugin.getLogger().warning("跳过一条无法解析的宝藏记录: " + e.getMessage());
                }
            }
            if (skipped > 0) {
                plugin.getLogger().warning("共跳过 " + skipped + " 条损坏的宝藏记录");
            }
            if (expired > 0) {
                plugin.getLogger().info("已清理 " + expired + " 条过期宝藏记录（残留宝箱将在区块加载时清理）");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("无法加载宝藏数据: " + e.getMessage());
        }
        return result;
    }

    @Override
    public void save(Treasure treasure) {
        if (dataSource == null || executor == null || treasure == null) {
            return;
        }

        // 提前在调用线程（主线程）完成数据快照，避免异步线程读取可变对象
        final String itemsBase64;
        try {
            itemsBase64 = itemStacksToBase64(treasure.getItems());
        } catch (RuntimeException e) {
            // 不能写入 NULL：那会把数据库里原有的奖励物品覆盖掉
            plugin.getLogger().severe("宝藏 " + treasure.getId() + " 的物品无法序列化，已跳过本次保存: "
                    + e.getMessage());
            return;
        }
        String commands = String.join("\n", treasure.getCommands());
        String participants = joinParticipants(treasure.getParticipants());

        executor.submit(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(upsertSql())) {
                bind(ps, treasure, itemsBase64, commands, participants);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("无法保存宝藏 " + treasure.getId() + ": " + e.getMessage());
            }
        });
    }

    @Override
    public void delete(String id) {
        if (dataSource == null || executor == null || id == null) {
            return;
        }

        executor.submit(critical(() -> {
            String sql = "DELETE FROM " + tablePrefix + "treasures WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("无法删除宝藏 " + id + ": " + e.getMessage());
            }
        }));
    }

    /**
     * 标记为不可丢弃的任务。队列满时 {@link #dropOldestNonCritical} 会跳过这些任务，
     * 保证删除操作最终一定落库（否则重启后已领取的宝藏会"复活"）。
     * 任务开始执行后即从集合中移除，集合大小始终与队列积压量同阶。
     */
    private Runnable critical(Runnable task) {
        return () -> {
            criticalTasks.remove(task);
            task.run();
        };
    }

    /** 从队列头部丢弃第一个非关键任务；返回是否成功丢弃 */
    private boolean dropOldestNonCritical(BlockingQueue<Runnable> queue) {
        for (Runnable queued : queue) {
            if (!criticalTasks.contains(queued) && queue.remove(queued)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void saveAll(Collection<Treasure> treasures) {
        if (dataSource == null || executor == null || treasures == null || treasures.isEmpty()) {
            return;
        }

        List<Treasure> snapshot = new ArrayList<>(treasures);
        List<String> itemsData = new ArrayList<>(snapshot.size());
        List<String> commandsData = new ArrayList<>(snapshot.size());
        List<String> participantsData = new ArrayList<>(snapshot.size());
        for (Treasure treasure : snapshot) {
            try {
                itemsData.add(itemStacksToBase64(treasure.getItems()));
            } catch (RuntimeException e) {
                // 写入 NULL 会覆盖数据库中已有的奖励物品，因此整条记录跳过（原因见上）
                plugin.getLogger().severe("宝藏 " + treasure.getId() + " 的物品无法序列化，已跳过该记录: "
                        + e.getMessage());
                continue;
            }
            commandsData.add(String.join("\n", treasure.getCommands()));
            participantsData.add(joinParticipants(treasure.getParticipants()));
        }

        if (snapshot.isEmpty()) {
            return;
        }

        executor.submit(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(upsertSql())) {
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    for (int i = 0; i < snapshot.size(); i++) {
                        bind(ps, snapshot.get(i), itemsData.get(i), commandsData.get(i), participantsData.get(i));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("无法批量保存宝藏数据: " + e.getMessage());
            }
        });
    }

    private String upsertSql() {
        return UPSERT_SQL.formatted(tablePrefix + "treasures");
    }

    private static String joinParticipants(List<UUID> participants) {
        List<String> participantStrs = new ArrayList<>(participants.size());
        for (UUID uuid : participants) {
            participantStrs.add(uuid.toString());
        }
        return String.join(",", participantStrs);
    }

    private static void bind(PreparedStatement ps, Treasure treasure,
                             String itemsBase64, String commands, String participants) throws SQLException {
        ps.setString(1, treasure.getId());
        ps.setString(2, treasure.getOwnerUuid().toString());
        ps.setString(3, treasure.getOwnerName());
        ps.setString(4, treasure.getWorldName());

        Location loc = treasure.getLocation();
        ps.setDouble(5, loc != null ? loc.getX() : 0);
        ps.setDouble(6, loc != null ? loc.getY() : 0);
        ps.setDouble(7, loc != null ? loc.getZ() : 0);

        ps.setInt(8, treasure.getGuaranteedCoins());
        ps.setInt(9, treasure.getTicketPrice());
        ps.setLong(10, treasure.getCreateTime());
        ps.setLong(11, treasure.getExpireTime());
        ps.setString(12, itemsBase64);
        ps.setString(13, commands);
        ps.setString(14, participants);
    }

    @Override
    public void close() {
        if (executor == null) {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            return;
        }

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
