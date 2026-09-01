package nl.tinyaii.hardcore.data;

import nl.tinyaii.hardcore.TinyHardcorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 极限生存数据管理：每条命（存活/死亡）+ 加入时间 + 死亡时间/原因 + 豁免名单。
 * 持久化 data.yml。死亡状态按 UUID 记录（防换名绕过）。
 */
public class HardcoreManager {

    public enum Status { ALIVE, DEAD }

    /** 玩家记录 */
    public static class Entry {
        public final UUID uuid;
        public String name;
        public Status status = Status.ALIVE;
        public long joinTime = System.currentTimeMillis();
        public long deathTime = 0;
        public String deathCause = "";

        public Entry(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public long aliveMillis() {
            if (status == Status.DEAD) return deathTime - joinTime;
            return System.currentTimeMillis() - joinTime;
        }
    }

    private final TinyHardcorePlugin plugin;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, Boolean> exempt = new HashMap<>();
    private File file;

    public HardcoreManager(TinyHardcorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        entries.clear();
        exempt.clear();
        file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("entries");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    Entry e = new Entry(uuid, sec.getString(key + ".name", "?"));
                    e.status = "dead".equalsIgnoreCase(sec.getString(key + ".status", "alive")) ? Status.DEAD : Status.ALIVE;
                    e.joinTime = sec.getLong(key + ".join", System.currentTimeMillis());
                    e.deathTime = sec.getLong(key + ".death", 0);
                    e.deathCause = sec.getString(key + ".death-cause", "");
                    entries.put(uuid, e);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        ConfigurationSection ex = yml.getConfigurationSection("exempt");
        if (ex != null) {
            for (String key : ex.getKeys(false)) {
                try { exempt.put(UUID.fromString(key), true); } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Entry e : entries.values()) {
            String path = "entries." + e.uuid;
            yml.set(path + ".name", e.name);
            yml.set(path + ".status", e.status == Status.DEAD ? "dead" : "alive");
            yml.set(path + ".join", e.joinTime);
            yml.set(path + ".death", e.deathTime);
            yml.set(path + ".death-cause", e.deathCause);
        }
        for (UUID u : exempt.keySet()) {
            yml.set("exempt." + u.toString(), true);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("保存 data.yml 失败: " + ex.getMessage());
        }
    }

    // ===== 查询 =====
    public Entry get(UUID uuid) {
        return entries.get(uuid);
    }

    /** 玩家是否已死（查内存记录；无记录=存活，但新玩家进服会自动登记） */
    public boolean isDead(UUID uuid) {
        Entry e = entries.get(uuid);
        return e != null && e.status == Status.DEAD;
    }

    public boolean isExempt(UUID uuid) { return exempt.getOrDefault(uuid, false); }

    // ===== 登记 =====
    public Entry register(UUID uuid, String name) {
        Entry e = entries.get(uuid);
        if (e == null) {
            e = new Entry(uuid, name);
            entries.put(uuid, e);
            save();
        } else {
            e.name = name;
        }
        return e;
    }

    // ===== 死亡 =====
    public void markDead(UUID uuid, String cause) {
        Entry e = register(uuid, Bukkit.getOfflinePlayer(uuid).getName());
        e.status = Status.DEAD;
        e.deathTime = System.currentTimeMillis();
        e.deathCause = cause;
        save();
    }

    // ===== 复活 =====
    public boolean revive(UUID uuid) {
        Entry e = entries.get(uuid);
        if (e == null) return false;
        e.status = Status.ALIVE;
        e.deathTime = 0;
        e.deathCause = "";
        e.joinTime = System.currentTimeMillis();   // 复活血条重新计时
        save();
        return true;
    }

    // ===== 删档重来（restart-mode: reset）=====
    public void resetPlayer(UUID uuid) {
        entries.remove(uuid);
        save();
    }

    // ===== 神权操作（管理员）=====

    /** 处决：直接标记永久死亡（管理员可"处决"违规玩家） */
    public void kill(UUID uuid, String cause) {
        Entry e = register(uuid, Bukkit.getOfflinePlayer(uuid).getName());
        e.status = Status.DEAD;
        e.deathTime = System.currentTimeMillis();
        e.deathCause = cause;
        save();
    }

    /** 全服复活：所有死亡玩家恢复一条命，返回复活人数 */
    public int reviveAll() {
        int count = 0;
        for (Entry e : entries.values()) {
            if (e.status == Status.DEAD) {
                e.status = Status.ALIVE;
                e.deathTime = 0;
                e.deathCause = "";
                e.joinTime = System.currentTimeMillis();
                count++;
            }
        }
        if (count > 0) save();
        return count;
    }

    /** 全服重置（新赛季）：清空全部记录，返回清理条数 */
    public int resetAll() {
        int count = entries.size();
        entries.clear();
        save();
        return count;
    }

    // ===== 豁免名单 =====
    public void addExempt(UUID uuid) { exempt.put(uuid, true); save(); }
    public void removeExempt(UUID uuid) { exempt.remove(uuid); save(); }
    public List<UUID> getExempts() { return new ArrayList<>(exempt.keySet()); }

    // ===== 排行 =====
    /** 存活玩家按存活时长降序 */
    public List<Entry> topAlive(int limit) {
        return entries.values().stream()
                .filter(e -> e.status == Status.ALIVE)
                .sorted((a, b) -> Long.compare(b.aliveMillis(), a.aliveMillis()))
                .limit(limit)
                .collect(java.util.ArrayList::new, java.util.List::add, java.util.List::addAll);
    }

    /** 死亡玩家按死亡时间降序（最新死在前） */
    public List<Entry> topDeaths(int limit) {
        return entries.values().stream()
                .filter(e -> e.status == Status.DEAD)
                .sorted((a, b) -> Long.compare(b.deathTime, a.deathTime))
                .limit(limit)
                .collect(java.util.ArrayList::new, java.util.List::add, java.util.List::addAll);
    }

    /** 格式化存活时长 */
    public static String fmtTime(long millis) {
        long sec = millis / 1000;
        long d = sec / 86400, h = (sec % 86400) / 3600, m = (sec % 3600) / 60;
        if (d > 0) return d + " 天 " + h + " 小时";
        if (h > 0) return h + " 小时 " + m + " 分";
        return m + " 分";
    }
}