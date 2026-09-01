package nl.tinyaii.hardcore;

import nl.tinyaii.hardcore.data.HardcoreManager;
import org.bukkit.plugin.java.JavaPlugin;

public class TinyHardcorePlugin extends JavaPlugin {

    private HardcoreManager manager;
    private nl.tinyaii.hardcore.data.AdminLog adminLog;

    @Override
    public void onEnable() {
        // TinyAII 品牌横幅 —— 必须在所有初始化逻辑之前输出（与 AutoBackup 完全一致）
        getLogger().info(" _____ _                _    ___ ___");
        getLogger().info("|_   _(_)_ __  _   _   / \\  |_ _|_ _|");
        getLogger().info("  | | | | '_ \\| | | | / _ \\  | | | |");
        getLogger().info("  | | | | | | | |_| |/ ___ \\ | | | |");
        getLogger().info("  |_| |_|_| |_|\\__, /_/   \\_\\___|___|");
        getLogger().info("               |___/");
        getLogger().info("TinyHardcore 极限生存 v" + getDescription().getVersion() + " - TinyAII 出品");

        saveDefaultConfig();
        manager = new HardcoreManager(this);
        manager.load();
        adminLog = new nl.tinyaii.hardcore.data.AdminLog(this);

        getServer().getPluginManager().registerEvents(new nl.tinyaii.hardcore.listener.HardcoreListener(this), this);
        getCommand("极限").setExecutor(new nl.tinyaii.hardcore.command.HardcoreCommand(this));

        getLogger().info("极限生存已启用。死亡处理=" + getConfig().getString("hardcore.death-handling", "kick")
                + " 重开模式=" + getConfig().getString("hardcore.restart-mode", "new-player"));
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
    }

    public HardcoreManager getManager() { return manager; }
    public nl.tinyaii.hardcore.data.AdminLog getAdminLog() { return adminLog; }
}