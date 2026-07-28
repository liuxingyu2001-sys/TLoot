package com.tloot.storage;

import com.tloot.TLoot;
import com.tloot.data.Treasure;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class YamlStorage implements StorageBackend {

    private final TLoot plugin;
    private final File dataFile;
    private Supplier<Collection<Treasure>> treasuresSupplier;

    public YamlStorage(TLoot plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public void setTreasuresSupplier(Supplier<Collection<Treasure>> supplier) {
        this.treasuresSupplier = supplier;
    }

    @Override
    public void init() {
    }

    @Override
    public Map<String, Treasure> loadAll() {
        Map<String, Treasure> result = new HashMap<>();
        if (!dataFile.exists()) {
            return result;
        }

        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection treasuresSection = dataConfig.getConfigurationSection("treasures");
        if (treasuresSection == null) {
            return result;
        }

        for (String id : treasuresSection.getKeys(false)) {
            ConfigurationSection treasureSection = treasuresSection.getConfigurationSection(id);
            if (treasureSection != null) {
                Map<String, Object> data = new HashMap<>();
                for (String key : treasureSection.getKeys(false)) {
                    data.put(key, treasureSection.get(key));
                }
                Treasure treasure = Treasure.deserialize(data);
                if (treasure != null && !treasure.isExpired()) {
                    result.put(id, treasure);
                }
            }
        }
        return result;
    }

    @Override
    public void save(Treasure treasure) {
        saveAll();
    }

    @Override
    public void delete(String id) {
        saveAll();
    }

    @Override
    public void saveAll(Collection<Treasure> treasures) {
        FileConfiguration dataConfig = new YamlConfiguration();

        for (Treasure treasure : treasures) {
            String path = "treasures." + treasure.getId();
            Map<String, Object> data = treasure.serialize();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                dataConfig.set(path + "." + entry.getKey(), entry.getValue());
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存宝藏数据: " + e.getMessage());
        }
    }

    private void saveAll() {
        if (treasuresSupplier != null) {
            saveAll(treasuresSupplier.get());
        }
    }

    @Override
    public void close() {
    }
}
