package nl.tinyaii.hardcore.listener;

import nl.tinyaii.hardcore.TinyHardcorePlugin;
import nl.tinyaii.hardcore.data.HardcoreManager;
import nl.tinyaii.hardcore.util.Messages;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极限生存核心逻辑：
 *  - 进服：登记新玩家 + 死玩家拦截 + 首次进服提示
 *  - 死亡：标记永久死亡 + 全服公告 + 踢出/观战（spectator-lock 持续锁定）
 *  - 重生：阻止正常重生（死玩家只能观战）
 *  - 持续锁定：spectator-lock 模式下，观战期间每 0.25 秒重新锁定最近存活玩家第一视角；
 *    无存活玩家则强制拉回死亡点（禁止自由飞）；复活/退出/切模式自动停止。
 */
public class HardcoreListener implements Listener {

    private final TinyHardcorePlugin plugin;
    private final HardcoreManager manager;
    /** 锁定任务：玩家 UUID → 持续锁定任务（spectator-lock 用） */
    private final Map<UUID, BukkitTask> lockTasks = new ConcurrentHashMap<>();

    public HardcoreListener(TinyHardcorePlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getManager();
    }

    /** 是否"观战（锁定视角）"模式 */
    private boolean isLockMode() {
        return "spectator-lock".equalsIgnoreCase(plugin.getConfig().getString("hardcore.death-handling", "kick"));
    }

    /** 是否观战模式（自由或锁定） */
    private boolean isSpectatorMode(String mode) {
        return "spectator".equalsIgnoreCase(mode) || "spectator-lock".equalsIgnoreCase(mode);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        // 豁免名单：进服直接放行
        if (manager.isExempt(u) || p.hasPermission("hardcore.exempt")) return;
        // 已死玩家：拦截（踢出 或 观战）
        if (manager.isDead(u)) {
            String mode = plugin.getConfig().getString("hardcore.death-handling", "kick");
            if (isSpectatorMode(mode)) {
                p.setGameMode(GameMode.SPECTATOR);
                p.sendMessage(Messages.color(plugin.getConfig().getString(
                        "hardcore.death-message", "&c你已永久死亡！{mode}").replace("{mode}", "观战中")));
                e.setJoinMessage(null);
                if (isLockMode()) startLockTask(p);   // 锁定视角：启动持续锁定
            } else {
                p.kickPlayer(Messages.color(plugin.getConfig().getString(
                        "hardcore.death-message", "&c你已永久死亡！{mode}").replace("{mode}", "无法进入")));
                e.setJoinMessage(null);
            }
            return;
        }
        // 新玩家登记
        boolean isNew = manager.get(u) == null;
        manager.register(u, p.getName());
        if (isNew && plugin.getConfig().getBoolean("hardcore.first-join-message", true)) {
            p.sendMessage(Messages.color(plugin.getConfig().getString(
                    "hardcore.first-join-message", "&c【极限生存】你只有一条命，死了就没了！")));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        UUID u = p.getUniqueId();
        // 豁免：不标记
        if (manager.isExempt(u) || p.hasPermission("hardcore.exempt")) return;
        if (!plugin.getConfig().getBoolean("hardcore.enabled", true)) return;

        // 标记永久死亡
        manager.markDead(u, deathCause(e));
        // 全服公告
        if (plugin.getConfig().getBoolean("hardcore.announce-death", true)) {
            String time = HardcoreManager.fmtTime(manager.get(u).aliveMillis());
            String fmt = plugin.getConfig().getString("hardcore.announce-format",
                    "&c{player} &4永久死亡了！存活 {time}")
                    .replace("{player}", p.getName())
                    .replace("{time}", time);
            plugin.getServer().broadcastMessage(Messages.color(fmt));
        }
        // 观战模式：立刻强制切观战（不等重生流程），避免"不是强制观战"
        String mode = plugin.getConfig().getString("hardcore.death-handling", "kick");
        if (isSpectatorMode(mode)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && manager.isDead(p.getUniqueId())) {
                    p.setGameMode(GameMode.SPECTATOR);
                    if (isLockMode()) startLockTask(p);   // 锁定视角：启动持续锁定
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        if (manager.isDead(u)) {
            String mode = plugin.getConfig().getString("hardcore.death-handling", "kick");
            // 死玩家重生：观战模式滞留原地（不真正回重生点）
            if (isSpectatorMode(mode)) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) {
                        p.setGameMode(GameMode.SPECTATOR);
                        if (isLockMode()) startLockTask(p);   // 锁定视角：启动持续锁定
                    }
                }, 1L);
            } else {
                // 踢出模式：死后重生立刻踢出（防卡重生循环）
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) {
                        p.kickPlayer(Messages.color(plugin.getConfig().getString(
                                "hardcore.death-message", "&c你已永久死亡！").replace("{mode}", "")));
                    }
                }, 2L);
            }
        }
    }

    /** 退出：停止锁定任务 */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopLockTask(e.getPlayer().getUniqueId());
    }

    /** 切模式：离开观战（如被复活切回生存）→ 停止锁定任务 */
    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        if (e.getNewGameMode() != GameMode.SPECTATOR) {
            stopLockTask(e.getPlayer().getUniqueId());
        }
    }

    /**
     * 持续锁定任务：每 0.25 秒重新锁定最近存活玩家第一视角。
     * 无存活玩家 → 强制拉回死亡点（禁止自由飞）。
     * 玩家复活/退出/切回非观战 → 停止。
     */
    private void startLockTask(Player p) {
        UUID u = p.getUniqueId();
        stopLockTask(u);
        final Location deathLoc = p.getLocation().clone();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline() || !manager.isDead(u)) { stopLockTask(u); return; }
            if (p.getGameMode() != GameMode.SPECTATOR) { stopLockTask(u); return; }
            // 找最近存活玩家
            Player nearest = null;
            double best = Double.MAX_VALUE;
            boolean lockAdmin = plugin.getConfig().getBoolean("hardcore.spectator-lock-admin", false);
            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (other.getUniqueId().equals(u)) continue;
                if (other.getGameMode() == GameMode.SPECTATOR) continue;
                if (manager.isDead(other.getUniqueId())) continue;
                // 不允许锁定到管理员视角时，跳过管理员（OP 或 hardcore.admin 权限）
                if (!lockAdmin && (other.isOp() || other.hasPermission("hardcore.admin"))) continue;
                double d = p.getLocation().distanceSquared(other.getLocation());
                if (d < best) { best = d; nearest = other; }
            }
            if (nearest != null) {
                p.setSpectatorTarget(nearest);   // 锁定第一视角（持续）
            } else {
                // 无存活玩家：强制固定在死亡点（拉回，禁止自由飞）
                p.teleport(deathLoc);
            }
        }, 0L, 5L);   // 每 5 tick（0.25 秒）
        lockTasks.put(u, task);
    }

    private void stopLockTask(UUID u) {
        BukkitTask t = lockTasks.remove(u);
        if (t != null) t.cancel();
    }

    private String deathCause(PlayerDeathEvent e) {
        try {
            return e.getDeathMessage() != null ? e.getDeathMessage() : "未知原因";
        } catch (Throwable t) {
            return "未知原因";
        }
    }
}