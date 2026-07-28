# TLoot

Minecraft 寻宝插件 — 玩家发起寻宝活动，其他玩家参与争夺。支持 MySQL 持久化和 Redis 多端跨服同步。

## 功能特性

- **玩家寻宝**：玩家花费金币创建寻宝活动，放置宝箱，设置保底金币和参与费用
- **系统寻宝**：定时自动生成宝藏，按配置的世界范围随机放置，带战利品表
- **参与机制**：支付费用参与寻宝，获得指南针指针追踪宝藏位置
- **GUI 界面**：图形化界面发起寻宝、查看可参与的寻宝列表
- **ActionBar 距离显示**：实时显示与宝藏的距离
- **宝箱保护**：宝藏箱子无法被破坏（包括玩家挖掘、TNT/苦力怕爆炸等）
- **过期清理**：宝藏过期后自动移除箱子
- **Vault 经济**：通过 Vault 处理金币流转
- **MySQL 存储**：可选 MySQL 持久化，自动建库建表，HikariCP 连接池 + 异步写入
- **Redis 跨服同步**：多端子服务器实时同步宝藏创建、领取、过期、加入等事件及广播

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/treasure create <金额> <费用>` | 创建寻宝 | 玩家 |
| `/treasure join <ID>` | 参与寻宝 | 玩家 |
| `/treasure list` | 查看寻宝列表 | 玩家 |
| `/treasure info <ID>` | 查看寻宝详情 | 玩家 |
| `/treasure reload` | 重载配置 | `tloot.admin` |

别名: `/tloot`, `/t`

## 依赖

- **必需**: [Vault](https://www.spigotmc.org/resources/vault.34315/) + 任意经济插件（如 EssentialsX）
- **多端部署**: MySQL 5.7+ / 8.x + Redis 6+

## 兼容性

- Paper/Spigot 1.20+
- Java 21+

## 安装

1. 下载 `Liu-TLoot-1.0.jar`
2. 放入服务器 `plugins/` 目录
3. 确保已安装 Vault 和经济插件
4. 重启服务器
5. 编辑 `plugins/TLoot/config.yml` 自定义配置

## 配置说明

### 存储设置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `storage.type` | yaml | 存储类型：`yaml`（单服）或 `mysql`（多端） |

### 数据库设置（MySQL 模式）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `database.host` | localhost | 数据库地址 |
| `database.port` | 3306 | 数据库端口 |
| `database.database` | tloot | 数据库名（自动创建） |
| `database.username` | root | 用户名 |
| `database.password` | "" | 密码 |
| `database.table-prefix` | tloot_ | 表名前缀 |
| `database.pool-size` | 10 | 连接池大小 |

### Redis 跨服同步设置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `redis.enabled` | false | 是否启用跨服同步 |
| `redis.host` | localhost | Redis 地址 |
| `redis.port` | 6379 | Redis 端口 |
| `redis.password` | "" | Redis 密码 |
| `redis.channel` | tloot:sync | 同步频道名 |
| `redis.server-id` | "" | 服务器ID（留空自动生成，多端需各不同） |

### 多端部署示例

每个子服的 `config.yml`：

```yaml
storage:
  type: mysql

database:
  host: 192.168.1.100
  port: 3306
  database: tloot
  username: tloot
  password: "your-password"

redis:
  enabled: true
  host: 192.168.1.100
  port: 6379
  server-id: "survival-1"   # 每个子服设不同ID
```

### 基础设置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `settings.ticket-price` | 500 | 参与寻宝需要支付的门票金额 |
| `settings.claim-distance` | 5 | 领取宝藏的最大距离（格） |
| `settings.expire-time` | 360 | 宝藏过期时间（分钟） |
| `settings.min-guaranteed-coins` | 100000 | 保底金币最小值 |
| `settings.max-guaranteed-coins` | 1000000 | 保底金币最大值 |

### 自动寻宝设置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `auto-treasure.enabled` | true | 是否启用自动寻宝 |
| `auto-treasure.interval` | 30 | 定时生成间隔（分钟） |
| `auto-treasure.max-active` | 3 | 同时存在的系统宝藏最大数量 |
| `auto-treasure.ticket-price` | 300 | 系统宝藏参与费用 |
| `auto-treasure.expire-time` | 120 | 系统宝藏过期时间（分钟） |

### 世界配置

- **allowed-worlds**：控制允许创建宝藏的世界（为空则允许所有世界）
- **world-names**：显示给玩家的友好名称
- **auto-treasure.worlds**：按世界单独配置生成范围

### 战利品表

在 `auto-treasure.loot-table` 中配置物品和指令奖励：

```yaml
loot-table:
  - material: DIAMOND
    amount-min: 1
    amount-max: 5
    weight: 50
  - command: "give {player} minecraft:netherite_sword 1"
    weight: 3
```

## 项目结构

```
TLoot/
├── src/main/java/com/tloot/
│   ├── TLoot.java                       # 插件主类
│   ├── beacon/BeaconEffectManager.java  # 信标特效
│   ├── command/TreasureCommand.java     # 命令处理
│   ├── config/
│   │   ├── ConfigManager.java           # 配置管理
│   │   ├── LootEntry.java              # 战利品条目
│   │   └── MessageManager.java         # 消息管理
│   ├── data/
│   │   ├── Treasure.java               # 宝藏数据类
│   │   └── TreasureManager.java        # 宝藏管理器 + Redis 同步回调
│   ├── gui/GUIManager.java             # GUI 管理
│   ├── item/
│   │   ├── PointerItem.java            # 指针物品
│   │   └── TreasureSignItem.java       # 寻宝告示牌
│   ├── listener/
│   │   ├── PointerListener.java        # 指针追踪
│   │   ├── TreasureListener.java       # 宝箱交互
│   │   ├── TreasureSignListener.java   # 告示牌监听
│   │   └── gui/                        # GUI 监听器
│   ├── storage/
│   │   ├── StorageBackend.java         # 存储抽象接口
│   │   ├── YamlStorage.java            # YAML 文件存储
│   │   └── MySQLStorage.java           # MySQL 存储（HikariCP）
│   ├── sync/
│   │   └── RedisSyncManager.java       # Redis pub/sub 跨服同步
│   └── task/
│       ├── AutoTreasureTask.java       # 自动寻宝任务
│       └── TreasureExpireTask.java     # 过期清理任务
└── src/main/resources/
    ├── config.yml                       # 默认配置
    ├── messages.yml                     # 消息文本
    └── plugin.yml                       # 插件描述
```

## 开发

### 编译

```bash
mvn clean package
```

生成的 jar 文件位于 `target/Liu-TLoot-1.0.jar`

## 作者

liuxingyu2001
