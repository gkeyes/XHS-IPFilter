package com.xposed.ipfilter;

import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 白名单管理器
 * 
 * 加载优先级：文件 > SharedPreferences > 内置默认值
 * 
 * 配置文件路径：{filesDir}/ipfilter_whitelist.txt
 * 格式：一行一个城市名，或以逗号分隔
 * 
 * 示例：
 *   上海
 *   北京
 *   广州
 *   深圳
 * 
 * 或：
 *   上海,北京,广州,深圳
 * 
 * 修改文件后，重启小红书即可生效。
 */
public class PrefManager {
    // 内置默认值（仅当SP和文件都为空时使用）
    private static final String DEFAULT_WHITELIST = "上海,北京";
    private static final String CONFIG_FILENAME = "ipfilter_whitelist.txt";
    private static final String KEY_WHITELIST = "ip_whitelist";

    private final SharedPreferences prefs;
    private final File configFile;
    private Set<String> whitelist;
    private String source = "default"; // "file" | "sp" | "default"
    private long lastLoadedTimestamp = 0;

    public PrefManager(SharedPreferences prefs, File filesDir) {
        this.prefs = prefs;
        this.configFile = new File(filesDir, CONFIG_FILENAME);
        LogWriter.i("PrefManager: config file path → " + configFile.getAbsolutePath());
        reload();
    }

    /** 重新加载白名单 — 文件 > SP > 默认值 */
    public void reload() {
        // 1. 优先读文件
        Set<String> loaded = loadFromFile();
        if (loaded != null && !loaded.isEmpty()) {
            whitelist = Collections.synchronizedSet(loaded);
            source = "file";
            LogWriter.i("PrefManager: loaded from file, " + loaded.size() + " items");
            return;
        }

        // 2. 文件不存在或空，回退到 SP
        loaded = loadFromPreferences();
        if (loaded != null && !loaded.isEmpty()) {
            whitelist = Collections.synchronizedSet(loaded);
            source = "sp";
            saveToFile(loaded);
            LogWriter.i("PrefManager: loaded from SP, " + loaded.size() + " items, synced to file");
            return;
        }

        // 3. 两者都没有，用内置默认值，并写入文件和 SP
        loaded = parseItems(DEFAULT_WHITELIST);
        whitelist = Collections.synchronizedSet(loaded);
        source = "default";
        saveToPreferences(loaded);
        saveToFile(loaded);
        LogWriter.i("PrefManager: using defaults, " + loaded.size() + " items, written to file & SP");
    }

    /** 检查IP是否在白名单 */
    public boolean isInWhitelist(String ipLocation) {
        if (ipLocation == null || ipLocation.isEmpty()) return false;
        synchronized (whitelist) {
            for (String w : whitelist) {
                if (ipLocation.contains(w)) return true;
            }
        }
        return false;
    }

    /** 获取白名单集合（副本） */
    public Set<String> getWhitelist() {
        synchronized (whitelist) {
            return new HashSet<>(whitelist);
        }
    }

    /** 添加城市 */
    public void add(String city) {
        whitelist.add(city.trim());
        save();
    }

    /** 移除城市 */
    public void remove(String city) {
        whitelist.remove(city.trim());
        save();
    }

    /** 获取配置文件路径 */
    public String getConfigFilePath() {
        return configFile.getAbsolutePath();
    }

    /** 获取当前白名单来源 */
    public String getSource() {
        return source;
    }

    // ========== 内部实现 ==========

    private void save() {
        synchronized (whitelist) {
            saveToPreferences(whitelist);
            saveToFile(whitelist);
        }
    }

    private Set<String> tryLoadFromFileIfChanged() {
        if (!configFile.exists()) return null;
        long lastModified = configFile.lastModified();
        if (lastModified == lastLoadedTimestamp) {
            return null;
        }
        Set<String> loaded = loadFromFile();
        if (loaded != null && !loaded.isEmpty()) {
            lastLoadedTimestamp = lastModified;
        }
        return loaded;
    }

    private Set<String> loadFromFile() {
        try {
            if (!configFile.exists()) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
                String line;
                boolean firstLine = true;
                while ((line = reader.readLine()) != null) {
                    if (firstLine) {
                        line = stripBOM(line);
                        firstLine = false;
                    }
                    if (sb.length() > 0) sb.append(",");
                    sb.append(line.trim());
                }
            }
            return parseItems(sb.toString());
        } catch (Throwable t) {
            LogWriter.d("PrefManager: failed to read config file: " + t.getMessage());
            return null;
        }
    }

    private static String stripBOM(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private Set<String> loadFromPreferences() {
        String saved = prefs.getString(KEY_WHITELIST, null);
        if (saved == null || saved.isEmpty()) return null;
        return parseItems(saved);
    }

    private void saveToFile(Set<String> items) {
        try {
            File dir = configFile.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
                for (String item : items) {
                    writer.write(item);
                    writer.write("\n");
                }
                writer.flush();
            }
        } catch (Throwable t) {
            LogWriter.d("PrefManager: failed to write config file: " + t.getMessage());
        }
    }

    private void saveToPreferences(Set<String> items) {
        prefs.edit().putString(KEY_WHITELIST, String.join(",", items)).apply();
    }

    private static Set<String> parseItems(String raw) {
        Set<String> result = new HashSet<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String item : raw.split("[,\
]+")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}