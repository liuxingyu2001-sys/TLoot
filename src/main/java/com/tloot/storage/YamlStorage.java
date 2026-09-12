package com.tloot.storage;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import com.tloot.util.TreasureBlocks;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * YAML 文件存储。
 *
 * 优化点：原实现对每次 {@link #save} / {@link #delete} 都同步重写整份文件，
 * 玩家参与寻宝时会触发一次全量序列化 + 磁盘写入（O(n²)，主线程阻塞）。
 * 现在改为「标记脏数据 + 定时批量落盘」，并在异步线程写文件。
 */
public class YamlStorage implements StorageBackend {

    /** 脏数据落盘间隔（tick），5 秒 */
    private static final long FLUSH_INTERVAL_TICKS = 100L;

    private final TLoot plugin;
    private final File dataFile;
    private Supplier<Collection<Treasure>> treasuresSupplier;

    private final ReentrantLock writeLock = new ReentrantLock();
    private volatile boolean dirty = false;
    private volatile boolean flushing = false;
    /** 写入序号：异步写入完成时若已有更新的快照在写，则不再清除 flushing 标记 */
    private final AtomicLong writeSequence = new AtomicLong();
    private BukkitTask flushTask;

    public YamlStorage(TLoot plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public void setTreasuresSupplier(Supplier<Collection<Treasure>> supplier) {
        this.treasuresSupplier = supplier;
    }

    @Override
    public void init() {
        if (flushTask != null) {
            return;
        }
        flushTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                flush();
            }
        }, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    @Override
    public Map<String, Treasure> loadAll() {
        Map<String, Treasure> result = new LinkedHashMap<>();
        if (!dataFile.exists()) {
            return result;
        }

        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection treasuresSection = dataConfig.getConfigurationSection("treasures");
        if (treasuresSection == null) {
            return result;
        }

        int skipped = 0;
        for (String id : treasuresSection.getKeys(false)) {
            ConfigurationSection treasureSection = treasuresSection.getConfigurationSection(id);
            if (treasureSection == null) {
                continue;
            }

            Map<String, Object> data = new HashMap<>();
            for (String key : treasureSection.getKeys(false)) {
                data.put(key, treasureSection.get(key));
            }

            // deserialize 内部已兜底异常并返回 null；过期数据由调用方过滤
            Treasure treasure = Treasure.deserialize(data);
            if (treasure == null) {
                skipped++;
                continue;
            }
            if (treasure.isExpired()) {
                // 记录已过期但箱子可能still残留在世界里：登记后在区块加载时回收
                TreasureBlocks.scheduleCleanup(treasure.getLocation());
                skipped++;
                continue;
            }
            result.put(id, treasure);
        }

        if (skipped > 0) {
            plugin.getLogger().info("已跳过 " + skipped + " 条过期或损坏的宝藏记录（残留宝箱将在区块加载时清理）");
        }
        return result;
    }

    @Override
    public void save(Treasure treasure) {
        dirty = true;
    }

    @Override
    public void delete(String id) {
        dirty = true;
    }

    @Override
    public void saveAll(Collection<Treasure> treasures) {
        writeToDisk(new ArrayList<>(treasures));
    }

    private void flush() {
        if (flushing) {
            return;
        }
        Supplier<Collection<Treasure>> supplier = treasuresSupplier;
        if (supplier == null) {
            return;
        }

        Collection<Treasure> snapshot = supplier.get();
        dirty = false;
        writeToDisk(snapshot instanceof ArrayList<Treasure> list ? list : new ArrayList<>(snapshot));
    }

    private FileConfiguration buildConfig(Collection<Treasure> treasures) {
        FileConfiguration dataConfig = new YamlConfiguration();
        for (Treasure treasure : treasures) {
            String path = "treasures." + treasure.getId();
            try {
                for (Map.Entry<String, Object> entry : treasure.serialize().entrySet()) {
                    dataConfig.set(path + "." + entry.getKey(), entry.getValue());
                }
            } catch (RuntimeException e) {
                // 单条数据损坏时跳过该条，避免整份存档写入失败甚至中断定时落盘任务
                plugin.getLogger().warning("宝藏 " + treasure.getId() + " 序列化失败，已跳过: " + e.getMessage());
            }
        }
        return dataConfig;
    }

    private void writeToDisk(Collection<Treasure> treasures) {
        FileConfiguration dataConfig = buildConfig(treasures);

        if (dataFile.getParentFile() != null && !dataFile.getParentFile().exists()
                && !dataFile.getParentFile().mkdirs()) {
            plugin.getLogger().severe("无法创建数据目录: " + dataFile.getParentFile());
            return;
        }

        flushing = true;
        long sequence = writeSequence.incrementAndGet();

        // 文件 IO 放到异步线程，避免大存档阻塞服务器主线程；
        // 插件正在关闭时调度器已不可用，直接同步写入
        if (!plugin.isEnabled()) {
            writeSync(dataConfig);
            flushing = false;
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                writeSync(dataConfig);
            } finally {
                // 期间若有更新的写入启动，则由那次写入负责复位标记
                if (writeSequence.get() == sequence) {
                    flushing = false;
                }
            }
        });
    }

    /**
     * 实际写文件。用锁串行化：定时任务的异步写入与关闭时的同步写入可能并发，
     * 若旧快照后写入完成，会用过期数据覆盖新数据。
     */
    private void writeSync(FileConfiguration dataConfig) {
        writeLock.lock();
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存宝藏数据: " + e.getMessage());
            dirty = true; // 保存失败，下一轮重试
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        // 关闭时必须同步落盘，确保数据不丢失
        if (treasuresSupplier != null) {
            writeToDiskSync(new ArrayList<>(treasuresSupplier.get()));
        }
    }

    private void writeToDiskSync(Collection<Treasure> treasures) {
        writeSync(buildConfig(treasures));
    }
}
