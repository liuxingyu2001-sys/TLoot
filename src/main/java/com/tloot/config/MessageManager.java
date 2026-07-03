package com.tloot.config;

import com.tloot.TLoot;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final TLoot plugin;
    private final File messagesFile;
    private FileConfiguration messagesConfig;
    private final Map<String, String> messages = new HashMap<>();

    public MessageManager(TLoot plugin) {
        this.plugin = plugin;
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
    }

    public void loadMessages() {
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        try (Reader defaultReader = new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultReader);
            messagesConfig.setDefaults(defaultConfig);
        } catch (IOException e) {
            plugin.getLogger().warning("无法加载默认消息文件: " + e.getMessage());
        }

        messages.clear();
        for (String key : messagesConfig.getKeys(true)) {
            if (messagesConfig.isString(key)) {
                messages.put(key, ChatColor.translateAlternateColorCodes('&', messagesConfig.getString(key)));
            }
        }
    }

    public String get(String key) {
        return messages.getOrDefault(key, "&c消息未找到: " + key);
    }

    public String get(String key, String... args) {
        String message = get(key);
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 < args.length) {
                message = message.replace("{" + args[i] + "}", args[i + 1]);
            }
        }
        return message;
    }

    public void saveMessages() {
        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存消息文件: " + e.getMessage());
        }
    }
}
