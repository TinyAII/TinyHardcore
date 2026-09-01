# 极限生存 TinyHardcore

服务器模拟"一条命"极限生存插件。死亡即永久死亡，支持三种死亡处理、管理员复活/处决/重置/开新赛季，全服死亡公告+存活排行榜，UUID 防绕过。全中文界面，中英双语命令，零依赖。

## 功能特性

- **一条命**：首次进服自动登记，死亡即标记永久死亡（按 UUID 记录，改名/重进逃不掉）
- **三种死亡处理**（config `death-handling`）：
  - `kick`：死亡被踢出，重进被拦
  - `spectator`：观战（自由视角，可到处飞着看）
  - `spectator-lock`：观战（强制锁定最近存活玩家第一视角，鼠标左右键切换；无存活玩家则固定在死亡点）
- **观战锁定可选锁定管理员视角**（`spectator-lock-admin`，默认不允许）
- **管理员神权**（`/极限 管理`，仅 OP）：
  - 查看任意玩家状态
  - 复活（恢复一条命，可选清背包，回出生点）
  - 处决（直接永久死亡+踢出）
  - 重置（删档重来，下次进服当新号）
  - 豁免名单（死亡不标记）
  - 全服复活 / 重置全服开新赛季（二次确认）
  - 操作日志 `admin-log.yml`
- **全服死亡公告** + **存活排行榜** + **死亡榜**
- **进服提示**"你只有一条命"
- 可选按 IP 记录防换号

## 命令（中英双语）

| 功能 | 中文 | 英文 |
|---|---|---|
| 状态 | /极限 | /hardcore |
| 存活排行 | /极限 排行 | /hardcore top |
| 死亡榜 | /极限 死亡榜 | /hardcore deaths |
| 复活 | /极限 管理 复活 <玩家> | /hardcore admin revive <p> |
| 处决 | /极限 管理 击杀 <玩家> | /hardcore admin kill <p> |
| 重置 | /极限 管理 重置 <玩家> | /hardcore admin reset <p> |
| 豁免 | /极限 管理 豁免 添加/移除 <玩家> | /hardcore admin exempt ... |
| 全服复活 | /极限 管理 全服复活 | /hardcore admin reviveall |
| 新赛季 | /极限 管理 重置全服 确认 | /hardcore admin resetall confirm |
| 重载 | /极限 管理 重载 | /hardcore admin reload |

## 配置（plugins/TinyHardcore/config.yml）

- `death-handling`：kick / spectator / spectator-lock
- `spectator-lock-admin`：是否允许锁定管理员视角（默认 false）
- `clear-inventory-on-revive`：复活是否清背包（默认 false）
- `announce-death`：全服死亡公告（默认 true）
- `track-ip`：按 IP 防换号（默认 false）
- `reset-spawn-on-restart`：重开后回出生点（默认 false）

## 安装

1. 下载 jar 放入 `plugins/` 目录
2. 重启服务器（或 reload）
3. 启动日志显示 TinyAII 横幅 + 极限生存已启用

> 需要 Java 17+，支持 Paper/Spigot 1.16 ~ 26.2。零依赖。

## 兼容性

- Paper / Spigot 1.16 ~ 26.2，Java 17+
- 零依赖，无需任何前置

---

# TinyHardcore - Hardcore Survival Plugin

Simulate "one life" hardcore survival on your server. Death = permanent death. Three death-handling modes, admin revive/execute/reset/new season, death announcements + survival leaderboard, UUID anti-bypass. Full Chinese UI, bilingual commands, zero dependency.

## Features

- **One Life**: auto-register on first join, death marks permanent (UUID-based, rename/rejoin can't bypass)
- **3 Death Modes** (`death-handling`): kick / spectator (free) / spectator-lock (forced first-person lock to nearest alive player)
- **Optional Lock Admin View** (`spectator-lock-admin`, default off)
- **Admin God Commands** (`/hardcore admin`, OP only): info/revive/kill/reset/exempt/reviveall/resetall confirm/reload, operation log
- **Death Announcements** + **Survival Leaderboard** + **Death Leaderboard**
- Optional IP tracking anti-alt

## Commands

- `/hardcore` status, `/hardcore top`, `/hardcore deaths`
- `/hardcore admin info|revive|kill|reset|exempt|reviveall|resetall|reload`

## Install

1. Put jar into `plugins/`
2. Restart server (or reload)
3. Startup log shows TinyAII banner + hardcore enabled

> Java 17+, Paper/Spigot 1.16 ~ 26.2. Zero dependency.

## License

MIT License - free, open source. TinyAII brand banner preserved.
