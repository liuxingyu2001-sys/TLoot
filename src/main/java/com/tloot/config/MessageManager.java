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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageManager {

    private final TLoot plugin;
    private final File messagesFile;
    private FileConfiguration messagesConfig;
    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, List<String>> messageLists = new HashMap<>();

    public MessageManager(TLoot plugin) {
        this.plugin = plugin;
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
    }

    public void loadMessages() {
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        YamlConfiguration defaultConfig = null;
        try (Reader defaultReader = new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            defaultConfig = YamlConfiguration.loadConfiguration(defaultReader);
            messagesConfig.setDefaults(defaultConfig);
        } catch (IOException e) {
            plugin.getLogger().warning("无法加载默认消息文件: " + e.getMessage());
        }

        messages.clear();
        messageLists.clear();
        for (String key : messagesConfig.getKeys(true)) {
            if (messagesConfig.isString(key)) {
                String raw = messagesConfig.getString(key);
                if (raw == null) {
                    continue;
                }
                // 支持在配置里用 \n 断行：先翻译颜色码，再把字面量 \n 转成真正的换行符
                messages.put(key, ChatColor.translateAlternateColorCodes('&', raw).replace("\\n", "\n"));
            } else if (messagesConfig.isList(key)) {
                messageLists.put(key, translateList(messagesConfig.getStringList(key)));
            }
        }

        // 老服务器的 messages.yml 可能缺少新版本才加入的键（如 broadcast.* / doom-*）。
        // setDefaults 在部分 Bukkit 版本下不会把默认键并入 getKeys()，这里显式兜底，
        // 避免玩家看到 “消息未找到: xxx”。
        if (defaultConfig != null) {
            for (String key : defaultConfig.getKeys(true)) {
                if (messages.containsKey(key) || !defaultConfig.isString(key)) {
                    continue;
                }
                String raw = defaultConfig.getString(key);
                if (raw != null) {
                    messages.put(key, ChatColor.translateAlternateColorCodes('&', raw).replace("\\n", "\n"));
                }
            }
            for (String key : defaultConfig.getKeys(true)) {
                if (messageLists.containsKey(key) || !defaultConfig.isList(key)) {
                    continue;
                }
                messageLists.put(key, translateList(defaultConfig.getStringList(key)));
            }
        }
    }

    private static List<String> translateList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> translated = new ArrayList<>(raw.size());
        for (String line : raw) {
            translated.add(ChatColor.translateAlternateColorCodes('&', line == null ? "" : line));
        }
        return Collections.unmodifiableList(translated);
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

    /** 读取列表型消息（如指针 Lore）；缺少时返回空列表，由调用方兜底 */
    public List<String> getList(String key) {
        return messageLists.getOrDefault(key, Collections.emptyList());
    }

    public void saveMessages() {
        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存消息文件: " + e.getMessage());
        }
    }
}
