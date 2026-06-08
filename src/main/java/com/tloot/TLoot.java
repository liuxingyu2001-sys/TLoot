package com.tloot;

import com.tloot.beacon.BeaconEffectManager;
import com.tloot.command.TreasureCommand;
import com.tloot.config.ConfigManager;
import com.tloot.config.MessageManager;
import com.tloot.data.TreasureManager;
import com.tloot.gui.GUIManager;
import com.tloot.listener.gui.CompassGUIListener;
import com.tloot.listener.gui.CreateGUIListener;
import com.tloot.listener.gui.MainGUIListener;
import com.tloot.listener.PointerListener;
import com.tloot.listener.TreasureListener;
import com.tloot.listener.TreasureSignListener;
import com.tloot.task.AutoTreasureTask;
import com.tloot.task.TreasureExpireTask;
import net.milkbowl.vault.economy.Economy;
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
    private TreasureExpireTask expireTask;
    private AutoTreasureTask autoTreasureTask;

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
        treasureManager.loadTreasures();

        guiManager = new GUIManager(this);

        beaconEffectManager = new BeaconEffectManager(this);
        beaconEffectManager.start();

        TreasureCommand treasureCommand = new TreasureCommand(this);
        if (getCommand("treasure") != null) {
            getCommand("treasure").setExecutor(treasureCommand);
            getCommand("treasure").setTabCompleter(treasureCommand);
        }

        getServer().getPluginManager().registerEvents(new MainGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new CreateGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new CompassGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new PointerListener(this), this);
        getServer().getPluginManager().registerEvents(new TreasureListener(this), this);
        getServer().getPluginManager().registerEvents(new TreasureSignListener(this), this);

        expireTask = new TreasureExpireTask(this);
        expireTask.runTaskTimer(this, 20L * 60, 20L * 60);

        if (configManager.isAutoTreasureEnabled()) {
            autoTreasureTask = new AutoTreasureTask(this);
            long intervalTicks = configManager.getAutoTreasureInterval() * 60L * 20L;
            autoTreasureTask.runTaskTimer(this, intervalTicks, intervalTicks);
            getLogger().info("定时自动寻宝已启用，间隔: " + configManager.getAutoTreasureInterval() + "秒");
        }

        getLogger().info("TLoot 寻宝插件已启用！");
    }

    @Override
    public void onDisable() {
        if (expireTask != null) {
            expireTask.cancel();
        }
        if (autoTreasureTask != null) {
            autoTreasureTask.cancel();
        }
        if (beaconEffectManager != null) {
            beaconEffectManager.stop();
        }
        if (treasureManager != null) {
            treasureManager.saveTreasures();
        }
        getLogger().info("TLoot 寻宝插件已禁用！");
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
}
