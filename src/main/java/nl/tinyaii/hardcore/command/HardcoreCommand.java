package nl.tinyaii.hardcore.command;

import nl.tinyaii.hardcore.TinyHardcorePlugin;
import nl.tinyaii.hardcore.data.HardcoreManager;
import nl.tinyaii.hardcore.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * 极限生存命令（双语）：
 *  /极限                     → 查看自己状态
 *  /极限 排行                → 存活排行榜 TOP10
 *  /极限 死亡榜              → 死亡榜 TOP10
 *  /极限 复活 <玩家>          → (管理) 复活玩家
 *  /极限 豁免 添加|移除 <玩家>  → (管理) 豁免名单
 *  /极限 豁免 列表            → (管理) 查看豁免名单
 *  /极限 重载                → (管理) 重载配置
 */
public class HardcoreCommand implements CommandExecutor {

    private final TinyHardcorePlugin plugin;

    public HardcoreCommand(TinyHardcorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isAdmin(CommandSender s) {
        if (s.hasPermission("hardcore.admin") || s.isOp()) return true;
        s.sendMessage(Messages.color(plugin.getConfig().getString("messages.no-permission", "&c你没有权限这么做。")));
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        HardcoreManager mgr = plugin.getManager();
        if (args.length == 0) {
            if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
            return status((Player) sender);
        }
        String sub = args[0];
        switch (sub) {
            case "管理": case "admin":
                return admin(sender, args);
            case "排行": case "top":
                return top(sender);
            case "死亡榜": case "deaths":
                return deaths(sender);
            case "复活": case "revive":
                if (!isAdmin(sender)) return true;
                if (args.length < 2) { sender.sendMessage(Messages.color("&c用法: /极限 复活 <玩家>")); return true; }
                return revive(sender, args[1]);
            case "豁免": case "exempt":
                if (!isAdmin(sender)) return true;
                return exempt(sender, args);
            case "重载": case "reload":
                if (!isAdmin(sender)) return true;
                plugin.reloadConfig();
                plugin.getManager().load();
                sender.sendMessage(Messages.color(plugin.getConfig().getString("messages.reloaded", "&a配置已重载。")));
                return true;
            default:
                sender.sendMessage(Messages.color("&c未知子命令。可用: 管理 / 排行 / 死亡榜 / 复活 / 豁免 / 重载"));
                return true;
        }
    }

    // ===== 神权管理（仅 hardcore.admin，硬校验）=====
    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hardcore.admin") && !sender.isOp()) {
            sender.sendMessage(Messages.color(plugin.getConfig().getString("messages.no-permission", "&c你没有权限这么做。")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.color("&e用法: /极限 管理 查看|复活|击杀|重置|豁免|全服复活|重置全服|排行|重载"));
            return true;
        }
        String op = args[1];
        HardcoreManager mgr = plugin.getManager();
        switch (op) {
            case "查看": case "info":
                if (args.length < 3) { sender.sendMessage(Messages.color("&c用法: /极限 管理 查看 <玩家>")); return true; }
                return adminInfo(sender, args[2]);
            case "复活": case "revive":
                if (args.length < 3) { sender.sendMessage(Messages.color("&c用法: /极限 管理 复活 <玩家>")); return true; }
                return revive(sender, args[2]);
            case "击杀": case "kill":
                if (args.length < 3) { sender.sendMessage(Messages.color("&c用法: /极限 管理 击杀 <玩家>")); return true; }
                return adminKill(sender, args[2]);
            case "重置": case "reset":
                if (args.length < 3) { sender.sendMessage(Messages.color("&c用法: /极限 管理 重置 <玩家>")); return true; }
                return adminReset(sender, args[2]);
            case "豁免": case "exempt":
                return exempt(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "全服复活": case "reviveall":
                return adminReviveAll(sender);
            case "重置全服": case "resetall":
                return adminResetAll(sender, args.length > 2 && args[2].equals("确认"));
            case "排行": case "top":
                return top(sender);
            case "重载": case "reload":
                plugin.reloadConfig();
                plugin.getManager().load();
                sender.sendMessage(Messages.color(plugin.getConfig().getString("messages.reloaded", "&a配置已重载。")));
                return true;
            default:
                sender.sendMessage(Messages.color("&c用法: /极限 管理 查看|复活|击杀|重置|豁免|全服复活|重置全服|排行|重载"));
                return true;
        }
    }

    private boolean adminInfo(CommandSender sender, String targetName) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
        HardcoreManager.Entry e = plugin.getManager().get(op.getUniqueId());
        if (e == null) {
            sender.sendMessage(Messages.color("&c该玩家没有极限生存记录（可能从未进服）。"));
            return true;
        }
        sender.sendMessage(Messages.color("&e===== " + targetName + " 极限状态 ====="));
        sender.sendMessage(Messages.color("&7状态: " + (e.status == HardcoreManager.Status.ALIVE ? "&a存活" : "&c已死亡")));
        sender.sendMessage(Messages.color("&7存活时长: &e" + HardcoreManager.fmtTime(e.aliveMillis())));
        if (e.status == HardcoreManager.Status.DEAD) {
            sender.sendMessage(Messages.color("&7死亡时间: &e" + java.time.Instant.ofEpochMilli(e.deathTime)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().substring(0, 16)));
            sender.sendMessage(Messages.color("&7死亡原因: &e" + e.deathCause));
        }
        return true;
    }

    private boolean adminKill(CommandSender sender, String targetName) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
        plugin.getManager().kill(op.getUniqueId(), "管理员处决");
        sender.sendMessage(Messages.color("&c已处决 &e" + targetName + "&c（永久死亡）。"));
        plugin.getAdminLog().log(sender.getName(), targetName, "处决", "永久死亡");
        Player online = Bukkit.getPlayer(op.getUniqueId());
        if (online != null) {
            online.kickPlayer(Messages.color(plugin.getConfig().getString(
                    "hardcore.death-message", "&c你已永久死亡！{mode}").replace("{mode}", "管理员处决")));
        }
        return true;
    }

    private boolean adminReset(CommandSender sender, String targetName) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
        plugin.getManager().resetPlayer(op.getUniqueId());
        sender.sendMessage(Messages.color("&a已重置 &e" + targetName + "&a（下次进服当新号）。"));
        plugin.getAdminLog().log(sender.getName(), targetName, "重置", "删档重来");
        return true;
    }

    private boolean adminReviveAll(CommandSender sender) {
        int count = plugin.getManager().reviveAll();
        sender.sendMessage(Messages.color("&a已全服复活 &e" + count + " &a名死亡玩家。"));
        plugin.getAdminLog().log(sender.getName(), "ALL", "全服复活", count + " 人");
        for (HardcoreManager.Entry e : plugin.getManager().topAlive(100)) {
            Player online = Bukkit.getPlayer(e.uuid);
            if (online != null) {
                reviveOnline(online);
            }
        }
        return true;
    }

    private boolean adminResetAll(CommandSender sender, boolean confirmed) {
        if (!confirmed) {
            sender.sendMessage(Messages.color("&c危险操作！再输入 &e/极限 管理 重置全服 确认 &c执行（清空全部极限记录 = 新赛季）"));
            return true;
        }
        int count = plugin.getManager().resetAll();
        sender.sendMessage(Messages.color("&a已重置全服（新赛季），清理 &e" + count + " &a条记录。"));
        plugin.getAdminLog().log(sender.getName(), "ALL", "重置全服", count + " 条");
        return true;
    }

    private boolean status(Player p) {
        HardcoreManager mgr = plugin.getManager();
        HardcoreManager.Entry e = mgr.get(p.getUniqueId());
        if (e == null || e.status == HardcoreManager.Status.ALIVE) {
            p.sendMessage(Messages.color(plugin.getConfig().getString(
                    "messages.status-alive", "&a你当前存活。加入时间: &e{join}")
                    .replace("{join}", java.time.Instant.ofEpochMilli(
                            e == null ? System.currentTimeMillis() : e.joinTime)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().substring(0, 16))));
        } else {
            p.sendMessage(Messages.color(plugin.getConfig().getString(
                    "messages.status-dead", "&c你已永久死亡。死亡时间: &e{death}&c，原因: &e{cause}")
                    .replace("{death}", java.time.Instant.ofEpochMilli(e.deathTime)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().substring(0, 16))
                    .replace("{cause}", e.deathCause)));
        }
        return true;
    }

    private boolean top(CommandSender sender) {
        HardcoreManager mgr = plugin.getManager();
        List<HardcoreManager.Entry> list = mgr.topAlive(10);
        sender.sendMessage(Messages.color(plugin.getConfig().getString(
                "messages.top-title", "&e===== 极限生存存活排行榜 TOP{limit} =====")
                .replace("{limit}", String.valueOf(10))));
        if (list.isEmpty()) { sender.sendMessage(Messages.color("&7暂无存活玩家。")); return true; }
        int rank = 1;
        for (HardcoreManager.Entry e : list) {
            sender.sendMessage(Messages.color(plugin.getConfig().getString(
                    "messages.top-entry", "&e{rank}. &f{player} &7- 存活 &f{time}")
                    .replace("{rank}", String.valueOf(rank))
                    .replace("{player}", nameOf(e.uuid))
                    .replace("{time}", HardcoreManager.fmtTime(e.aliveMillis()))));
            rank++;
        }
        return true;
    }

    private boolean deaths(CommandSender sender) {
        HardcoreManager mgr = plugin.getManager();
        List<HardcoreManager.Entry> list = mgr.topDeaths(10);
        sender.sendMessage(Messages.color(plugin.getConfig().getString(
                "messages.deaths-title", "&e===== 极限生存死亡榜 TOP{limit} =====")
                .replace("{limit}", String.valueOf(10))));
        if (list.isEmpty()) { sender.sendMessage(Messages.color("&7暂无死亡记录。")); return true; }
        int rank = 1;
        for (HardcoreManager.Entry e : list) {
            sender.sendMessage(Messages.color(plugin.getConfig().getString(
                    "messages.deaths-entry", "&e{rank}. &f{player} &7- 死于 &f{cause} &7({time})")
                    .replace("{rank}", String.valueOf(rank))
                    .replace("{player}", nameOf(e.uuid))
                    .replace("{cause}", e.deathCause)
                    .replace("{time}", java.time.Instant.ofEpochMilli(e.deathTime)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().substring(0, 16))));
            rank++;
        }
        return true;
    }

    private boolean revive(CommandSender sender, String targetName) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
        if (op == null || op.getUniqueId() == null) {
            sender.sendMessage(Messages.color(plugin.getConfig().getString("messages.player-not-found", "&c找不到玩家。")));
            return true;
        }
        if (!plugin.getManager().revive(op.getUniqueId())) {
            sender.sendMessage(Messages.color(plugin.getConfig().getString("messages.player-not-found", "&c找不到玩家。")));
            return true;
        }
        sender.sendMessage(Messages.color(plugin.getConfig().getString(
                "messages.revived", "&a已复活玩家 &e{player}&a，恢复一条命。").replace("{player}", targetName)));
        Player online = Bukkit.getPlayer(op.getUniqueId());
        if (online != null) {
            reviveOnline(online);
        }
        return true;
    }

    /** 复活在线玩家：切回生存 + 回出生点 + （配置可设）清背包。清背包默认关，保留玩家物品 */
    private void reviveOnline(Player online) {
        online.setGameMode(org.bukkit.GameMode.SURVIVAL);
        if (plugin.getConfig().getBoolean("hardcore.clear-inventory-on-revive", false)) {
            online.getInventory().clear();
            online.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
            online.getInventory().setExtraContents(new org.bukkit.inventory.ItemStack[1]);
        }
        online.teleport(online.getWorld().getSpawnLocation());
        online.sendMessage(Messages.color(plugin.getConfig().getString(
                "messages.revived-target", "&a你已被管理员复活，生存继续！")));
    }

    private boolean exempt(CommandSender sender, String[] args) {
        HardcoreManager mgr = plugin.getManager();
        if (args.length < 2) {
            sender.sendMessage(Messages.color("&c用法: /极限 豁免 添加|移除 <玩家> | /极限 豁免 列表"));
            return true;
        }
        if (args[1].equals("列表") || args[1].equals("list")) {
            sender.sendMessage(Messages.color(plugin.getConfig().getString(
                    "messages.exempt-list-title", "&e===== 豁免名单 =====")));
            List<UUID> list = mgr.getExempts();
            if (list.isEmpty()) { sender.sendMessage(Messages.color("&7豁免名单为空。")); return true; }
            for (UUID u : list) sender.sendMessage(Messages.color("&7- &e" + nameOf(u)));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Messages.color("&c用法: /极限 豁免 添加|移除 <玩家>"));
            return true;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(args[2]);
        UUID u = op.getUniqueId();
        if (args[1].equals("添加") || args[1].equals("add")) {
            mgr.addExempt(u);
            sender.sendMessage(Messages.color(plugin.getConfig().getString(
                    "messages.exempt-added", "&a已添加 &e{player} &a到豁免名单。").replace("{player}", args[2])));
        } else if (args[1].equals("移除") || args[1].equals("remove")) {
            mgr.removeExempt(u);
            sender.sendMessage(Messages.color(plugin.getConfig().getString(
                    "messages.exempt-removed", "&a已移除 &e{player} &a从豁免名单。").replace("{player}", args[2])));
        } else {
            sender.sendMessage(Messages.color("&c用法: /极限 豁免 添加|移除 <玩家>"));
        }
        return true;
    }

    private String nameOf(UUID u) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        return op.getName() != null ? op.getName() : u.toString().substring(0, 8);
    }
}