package com.tloot;

import com.tloot.beacon.BeaconEffectManager;
import com.tloot.command.TreasureCommand;
import com.tloot.config.ConfigManager;
import com.tloot.config.MessageManager;
import com.tloot.data.TreasureManager;
import com.tloot.gui.GUIManager;
import com.tloot.listener.PlayerSessionListener;
import com.tloot.listener.PointerListener;
import com.tloot.listener.TreasureListener;
import com.tloot.listener.TreasureSignListener;
import com.tloot.listener.gui.CompassGUIListener;
import com.tloot.listener.gui.CreateGUIListener;
import com.tloot.listener.gui.MainGUIListener;
import com.tloot.listener.gui.MyTreasureGUIListener;
import com.tloot.storage.MySQLStorage;
import com.tloot.storage.StorageBackend;
import com.tloot.storage.YamlStorage;
import com.tloot.sync.RedisSyncManager;
import com.tloot.task.AutoTreasureTask;
import com.tloot.task.TreasureExpireTask;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class TLoot extends JavaPlugin {

    private static TLoot instance;

    private Economy economy;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private TreasureManager treasureManager;
    private GUIManager guiManager;
    private BeaconEffectManager beaconEffectManager;
    private PointerListener pointerListener;
    private TreasureExpireTask expireTask;
    private AutoTreasureTask autoTreasureTask;
    private StorageBackend storageBackend;
    private RedisSyncManager redisSyncManager;

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe("未找到Vault经济插件，插件已禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        configManager = new ConfigManager(this);
        configManager.loadConfig();

        messageManager = new MessageManager(this);
        messageManager.loadMessages();

        treasureManager = new TreasureManager(this);

        // 存储后端初始化失败不应让整个插件处于半可用状态
        try {
            storageBackend = createStorageBackend();
            storageBackend.init();
        } catch (Exception e) {
            getLogger().severe("存储后端初始化失败，插件已禁用: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        treasureManager.setStorage(storageBackend);

        if (storageBackend instanceof YamlStorage yamlStorage) {
            yamlStorage.setTreasuresSupplier(treasureManager::getAllTreasures);
            yamlStorage.init();
        }

        if (configManager.isRedisEnabled()) {
            redisSyncManager = new RedisSyncManager(this,
                    configManager.getRedisHost(),
                    configManager.getRedisPort(),
                    configManager.getRedisPassword(),
                    configManager.getRedisChannel(),
                    configManager.getRedisServerId());
            treasureManager.setSyncManager(redisSyncManager);
            redisSyncManager.init(treasureManager);
        }

        treasureManager.loadTreasures();

        guiManager = new GUIManager(this);

        beaconEffectManager = new BeaconEffectManager(this);
        beaconEffectManager.start();

        // 监听器必须在依赖的管理器就绪之后再注册，避免事件先于初始化到达
        registerListeners();

        TreasureCommand treasureCommand = new TreasureCommand(this);
        if (getCommand("treasure") != null) {
            getCommand("treasure").setExecutor(treasureCommand);
            getCommand("treasure").setTabCompleter(treasureCommand);
        } else {
            getLogger().warning("未在 plugin.yml 中找到 treasure 命令，命令注册被跳过");
        }

        expireTask = new TreasureExpireTask(this);
        expireTask.runTaskTimer(this, 20L * 60, 20L * 60);

        startAutoTreasureTask();

        getLogger().info("TLoot 寻宝插件已启用！存储: " + configManager.getStorageType()
                + (configManager.isRedisEnabled() ? ", Redis跨服同步: 已启用" : ""));
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();

        // PointerListener 持有定时任务，必须只实例化一次
        pointerListener = new PointerListener(this);

        pluginManager.registerEvents(new MainGUIListener(this), this);
        pluginManager.registerEvents(new CreateGUIListener(this), this);
        pluginManager.registerEvents(new CompassGUIListener(this), this);
        pluginManager.registerEvents(new MyTreasureGUIListener(this), this);
        pluginManager.registerEvents(pointerListener, this);
        pluginManager.registerEvents(new PlayerSessionListener(this), this);
        pluginManager.registerEvents(new TreasureListener(this), this);
        pluginManager.registerEvents(new TreasureSignListener(this), this);
    }

    private void startAutoTreasureTask() {
        if (!configManager.isAutoTreasureEnabled()) {
            return;
        }

        int intervalMinutes = Math.max(1, configManager.getAutoTreasureInterval());
        long intervalTicks = intervalMinutes * 60L * 20L;
        long initialDelay = 20L * 20L;

        // 任务实例在重载时会被重新创建，先取消旧实例避免重复生成宝藏
        if (autoTreasureTask != null) {
            autoTreasureTask.cancel();
        }
        autoTreasureTask = new AutoTreasureTask(this);
        autoTreasureTask.runTaskTimer(this, initialDelay, intervalTicks);
        getLogger().info("定时自动寻宝已启用，间隔: " + intervalMinutes + " 分钟（首次 " + (initialDelay / 20) + " 秒后生成）");
    }

    @Override
    public void onDisable() {
        if (expireTask != null) {
            expireTask.cancel();
            expireTask = null;
        }
        if (autoTreasureTask != null) {
            autoTreasureTask.cancel();
            autoTreasureTask = null;
        }
        if (beaconEffectManager != null) {
            beaconEffectManager.stop();
        }
        if (pointerListener != null) {
            pointerListener.shutdown();
            pointerListener = null;
        }
        if (guiManager != null) {
            guiManager.clearAll();
        }

        // 先落盘再关闭存储后端，避免异步写入被中断导致数据丢失
        if (treasureManager != null) {
            treasureManager.saveTreasures();
        }
        if (redisSyncManager != null) {
            redisSyncManager.shutdown();
            redisSyncManager = null;
        }
        if (storageBackend != null) {
            storageBackend.close();
            storageBackend = null;
        }

        instance = null;
        getLogger().info("TLoot 寻宝插件已禁用！");
    }

    private StorageBackend createStorageBackend() {
        if ("mysql".equalsIgnoreCase(configManager.getStorageType())) {
            return new MySQLStorage(this,
                    configManager.getDatabaseHost(),
                    configManager.getDatabasePort(),
                    configManager.getDatabaseName(),
                    configManager.getDatabaseUsername(),
                    configManager.getDatabasePassword(),
                    configManager.getDatabaseTablePrefix(),
                    configManager.getDatabasePoolSize());
        }
        return new YamlStorage(this);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public static TLoot getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public TreasureManager getTreasureManager() {
        return treasureManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public PointerListener getPointerListener() {
        return pointerListener;
    }

    public RedisSyncManager getRedisSyncManager() {
        return redisSyncManager;
    }
}
