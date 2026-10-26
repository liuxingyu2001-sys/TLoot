# TLoot

Minecraft 寻宝插件 — 玩家发起寻宝活动，其他玩家参与争夺。支持 MySQL 持久化和 Redis 多端跨服同步。

> 更新记录见 [CHANGELOG.md](CHANGELOG.md)，详细的代码审查与修复说明见 [CODE_REVIEW.md](CODE_REVIEW.md)。

## 功能特性

- **玩家寻宝**：玩家花费金币创建寻宝活动，放置宝箱，设置保底金币和参与费用
- **系统寻宝**：定时自动生成宝藏，按配置的世界范围随机放置，带战利品表
- **厄运宝藏**：可选依赖 MythicMobs，按概率生成守卫怪物，全部击杀后宝箱才出现，奖励更丰厚
- **参与机制**：支付费用参与寻宝，获得指南针指针追踪宝藏位置；门票收入归发起者（可配置）
- **GUI 界面**：图形化界面发起寻宝、查看可参与的寻宝列表、管理自己发起/参与的寻宝
- **取消寻宝**：发起者可在「我的寻宝」中 Shift+左键取消（需无人参与），管理员可强制移除
- **ActionBar 距离显示**：实时显示与宝藏的距离
- **宝箱保护**：宝藏箱子无法被破坏（包括玩家挖掘、TNT/苦力怕爆炸等）
- **过期清理**：宝藏过期后自动移除箱子
- **Vault 经济**：通过 Vault 处理金币流转
- **MySQL 存储**：可选 MySQL 持久化，自动建库建表，HikariCP 连接池 + 异步写入
- **指针自动回收**：宝藏被领取 / 过期 / 被移除时，自动清除所有在线玩家背包中对应的寻宝指针
- **Redis 跨服同步**：多端子服务器实时同步宝藏创建、领取、过期、加入等事件及广播
  （公告职责划分：本服公告由插件自己发、跨服公告由 Redis 发，每个子服各收到一次，不会重复）

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/treasure` | 打开寻宝主菜单 | 玩家 |
| `/treasure create` | 打开发起寻宝界面（设置保底金币/参与费用后领取告示牌） | 玩家 |
| `/treasure join` | 参与寻宝（在公告/列表中点击宝藏，无需记 ID） | 玩家 |
| `/treasure list` / `/treasure compass` | 打开可参与寻宝列表（指南针面板） | 玩家 |
| `/treasure my` | 查看我发起/参与的寻宝 | 玩家 |
| `/treasure info` | 查看我参与的寻宝详情（管理员可加 ID 查询指定宝藏） | 玩家 |
| `/treasure cancel <ID>` | 取消自己发起的寻宝（需无人参与，退还保底金币与箱内物品；也可在「我的寻宝」中 Shift+左键） | 玩家 |
| `/treasure remove <ID>` | 强制移除任意宝藏（含已有人参与） | `tloot.admin` |
| `/treasure help` | 查看帮助 | 玩家 |
| `/treasure reload` | 重载配置（玩家与控制台均可） | `tloot.admin` |

别名: `/tloot`, `/t`

## 依赖

- **必需**: [Vault](https://www.spigotmc.org/resources/vault.34315/) + 任意经济插件（如 EssentialsX）
- **必需**: **LiuLib 1.0.5+**（物品规格解析，`plugin.yml depend: [LiuLib]`；缺失时服务端拒绝加载本插件）
- **可选**: **liuitemlib**（软依赖，`lib:<物品库ID>` 物品库引用；已加入 `plugin.yml softdepend`）
- **可选**: **MythicMobs 5.x**（软依赖，用于「厄运宝藏」守卫怪物；未安装时自动降级为普通宝藏）
- **多端部署**: MySQL 5.7+ / 8.x + Redis 6+

## 兼容性

- Paper/Leaf 26.2+（`api-version: 26.2`，按 paper-api 26.2 编译）
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

> YAML 模式下，参与/领取等操作只会标记数据为待保存，插件每 5 秒合并落盘一次（关闭服务器时同步落盘），
> 避免高频全量重写文件阻塞主线程；MySQL 模式下写入在独立线程池中串行执行且有队列上限。

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
| `settings.min-ticket-price` | 500 | 参与费用最小值 |
| `settings.owner-income` | true | 参与者支付的门票是否结算给发起者（关闭后门票由系统回收） |

> `settings.ticket-price` 仅为旧版流程保留的默认票价；发起界面中的参与费用使用 `settings.min-ticket-price` 作为下限。
> `settings.claim-distance` 同时用于「领取距离校验」和「靠近宝藏时的粒子提示」。
> 经济流转：发起者创建时支付「保底金币」作为押金/奖励，领取者获得该保底金币与箱子内的战利品；
> 每位参与者支付的门票在参与时结算给发起者（`owner-income: true`），因此发起者通过参与人数获得收益。
> 系统宝藏没有发起者，门票由系统回收，用于抑制通胀。

### 寻宝指针设置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `pointer.enable-actionbar` | true | 是否在 ActionBar 显示与宝藏的实时距离 |
| `pointer.beacon.enabled` | true | 宝藏上方是否渲染光柱粒子（关闭可降低粒子开销） |
| `pointer.beacon.interval-ticks` | 20 | 光柱粒子刷新间隔（tick） |
| `pointer.beacon.max-height` | 80 | 光柱相对宝藏的最大高度（格） |

> 指针在主手或副手时都会自动指向宝藏：插件每秒重新下发一次指南针目标，因此玩家重生、切换世界、管理员修改出生点后都会自动恢复指向。指南针无法跨世界指向，此时 ActionBar 会提示宝藏所在的世界（文案见 `messages.yml` 的 `pointer.wrong-world` / `pointer.world-unavailable`）。

### 自动寻宝设置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `auto-treasure.enabled` | true | 是否启用自动寻宝 |
| `auto-treasure.interval` | 30 | 定时生成间隔（分钟） |
| `auto-treasure.max-active` | 3 | 同时存在的系统宝藏最大数量 |
| `auto-treasure.ticket-price` | 300 | 系统宝藏参与费用 |
| `auto-treasure.expire-time` | 120 | 系统宝藏过期时间（分钟） |
| `auto-treasure.min-guaranteed-coins` | 50000 | 系统宝藏保底金币最小值 |
| `auto-treasure.max-guaranteed-coins` | 500000 | 系统宝藏保底金币最大值 |

### 厄运宝藏设置（依赖 MythicMobs）

开启后，自动寻宝有概率生成「厄运宝藏」：插件会在宝藏附近刷出配置的 MythicMobs 怪物，
**全部击杀后宝箱才会出现**，同时保底金币、参与费用和战利品都更丰厚。

```yaml
doom-treasure:
  enabled: false        # 需要安装 MythicMobs 并配置 mobs
  chance: 0.25          # 每次自动寻宝转换为厄运宝藏的概率
  mobs:                 # MythicMobs 内部名 → 数量
    "SkeletalKnight": 2
    "WitherSkeleton": 1
  spawn-radius: 6       # 守卫出生半径（格）
  coin-multiplier: 3.0  # 保底金币倍率
  ticket-multiplier: 2.0
  extra-loot-rolls: 3   # 额外抽取的战利品次数
```

说明：

- `mobs` 里必须是 `/mm mobs list` 中存在的**内部名**（区分大小写），填错会在控制台提示。
- 未安装 MythicMobs、`mobs` 为空或怪物名全部无效时，厄运宝藏会自动降级为普通宝藏，不会卡住任务。
- 守卫与宝箱状态不额外落库，用「宝藏坐标上是否已有箱子」判断是否已解锁：重启后未解锁的厄运宝藏会在**所在区块加载时**补刷守卫，已解锁的不会重复刷（区块未加载时不会强制加载，避免常驻区块）。
- 守卫自然消失（MythicMobs `Despawn`）时，只有附近 64 格内有玩家才会补刷，避免无人区域每秒「消失→重生」空转。
- 守卫只在**加载了宝藏所在世界且启用 `doom-treasure`** 的服务器上维护。多子服如果共享同一份世界目录，请只在一台服务器启用该功能，否则多台服务器会各自刷一套守卫。
- 玩家界面（指南针菜单 / 我的寻宝）会显示剩余守卫数量、宝箱是否已现世；区块未加载时显示「守卫尚未刷新（靠近后自动刷新）」。

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

#### 物品规格（LiuLib）

`material` 由 **LiuLib 物品规格解析**（`LiuLib.items().buildOrNull`）构建，解析失败才回退原来的
`Material.getMaterial` 逻辑（老配置的 `DIAMOND` 等原版枚举名行为不变）。支持的写法：

| 写法 | 例子 | 说明 |
| --- | --- | --- |
| 原版材质 | `material: DIAMOND` / `minecraft:diamond` / `diamond` | 大小写、命名空间写法都兼容 |
| 原版材质 + 数量后缀 | `material: DIAMOND*3` / `DIAMOND:3` | 数量仍以 `amount-min`/`amount-max` 为准（随机数量会覆盖） |
| 完整物品 | `material: "base64:..."` / `"nbt:..."` / `"bytes:..."` / `"item:base64:..."` | Paper 二进制序列化全保真（数据组件 / PDC / 附魔 / CE 物品），用 `/liulib item main` 复制 |
| CE 物品 | `material: "ce:命名空间:id"` / `"craftengine:命名空间:id"` | 需安装 CraftEngine |
| 物品库引用 | `material: "lib:gift:spring_2026"` | 需安装 **liuitemlib** 并配置对应物品库 |

```yaml
loot-table:
  # 完整物品：数量由 amount-min / amount-max 随机，超过单堆叠上限自动拆成多堆
  - material: "base64:H4sI..."
    amount-min: 1
    amount-max: 1
    weight: 5
  # 物品库引用
  - material: "lib:gift:spring_2026"
    amount-min: 1
    amount-max: 1
    weight: 10
```

> 完整物品规格在配置解析期构建一次并缓存模板，发放时只做 clone；损坏的 Base64/未知物品库 ID
> 会回退到原版材质解析，仍失败则按原来的「无效材料」警告跳过该条目。

## 项目结构

```
TLoot/
├── CODE_REVIEW.md                       # 代码审查报告（内存泄漏/Bug/优化）
├── src/main/java/com/tloot/
│   ├── TLoot.java                       # 插件主类（生命周期、依赖装配、监听器注册）
│   ├── beacon/BeaconEffectManager.java  # 宝藏光柱粒子特效
│   ├── command/TreasureCommand.java     # 命令处理与 Tab 补全
│   ├── config/
│   │   ├── ConfigManager.java           # 配置读取（区间/空值/上限防护）
│   │   ├── LootEntry.java               # 战利品条目
│   │   └── MessageManager.java          # 消息文本（字符串 + 列表，含默认值兜底）
│   ├── data/
│   │   ├── Treasure.java                # 宝藏数据类（序列化/容错反序列化）
│   │   └── TreasureManager.java         # 宝藏管理器 + Redis 同步回调
│   ├── doom/DoomTreasureManager.java    # 厄运宝藏守卫（MythicMobs 刷怪/击杀解锁/重启恢复）
│   ├── gui/GUIManager.java              # GUI 构建、会话状态与归属判定
│   ├── integration/
│   │   └── MythicMobsBridge.java        # MythicMobs 刷怪桥（反射，可选）
│   ├── item/
│   │   ├── PointerItem.java             # 指针物品（创建/识别/刷新/回收）
│   │   └── TreasureSignItem.java        # 寻宝告示牌物品
│   ├── listener/
│   │   ├── PointerListener.java         # 指针追踪（指南针/ActionBar/粒子）
│   │   ├── PlayerSessionListener.java   # 玩家退出清理、进服回收失效指针
│   │   ├── TreasureChunkCleanupListener.java # 区块加载时回收残留宝箱
│   │   ├── TreasureListener.java        # 宝箱交互、领取与箱子保护
│   │   ├── TreasureSignListener.java    # 告示牌放置并发起寻宝
│   │   └── gui/
│   │       ├── AbstractGUIListener.java # GUI 事件基类（归属判定/防误取消）
│   │       └── ...                      # 主菜单/发起/指南针/我的寻宝 四个监听器
│   ├── storage/
│   │   ├── StorageBackend.java          # 存储抽象接口
│   │   ├── YamlStorage.java             # YAML 存储（脏标记 + 防抖异步落盘）
│   │   └── MySQLStorage.java            # MySQL 存储（HikariCP + 有界写队列）
│   ├── sync/
│   │   └── RedisSyncManager.java        # Redis pub/sub 跨服同步
│   ├── task/
│   │   ├── AutoTreasureTask.java        # 定时自动寻宝
│   │   └── TreasureExpireTask.java      # 过期清理
│   └── util/
│       ├── Announcer.java               # 本服公告（与 Redis 跨服公告分工，避免重复）
│       └── TreasureBlocks.java          # 宝藏箱子清理（本地与跨服共用）
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

生成的 jar 文件位于 `target/Liu-TLoot-1.0.jar`。
依赖（HikariCP / MySQL Connector / Jedis）通过 `plugin.yml` 的 `libraries` 由服务端在启动时下载，不打进插件包。

### 可靠性设计

- **GUI 事件归属**：4 个 GUI 监听器继承 `AbstractGUIListener`，通过 `GUIManager.isGUIOpen(玩家, 类型, 界面)`
  判定点击归属后才取消事件；Bukkit 在同一优先级下遇到已取消事件会跳过后续 `ignoreCancelled` 监听器，
  因此不能在确认归属前取消事件。
- **会话状态**：界面类型/页码/待创建金额等全部以 UUID 为键保存在 `GUIManager`，统一在玩家退出
  （`PlayerSessionListener`）与插件关闭时清理，避免按 UUID 累积。
- **指针回收**：宝藏被领取/过期/移除（含跨服事件）时，`TreasureManager` 会回收所有在线玩家背包中对应的指针。
- **跨服一致性**：Redis 事件到达后由主线程执行，并清理本服可能残留的同位置箱子（`util/TreasureBlocks`）。
- **残留宝箱兜底回收**：目标区块未加载时无法立即移除箱子，会登记为待清理，
  由 `TreasureChunkCleanupListener` 在区块加载（玩家靠近）时回收；启动时跳过的过期记录同样会登记。

## 作者

liuxingyu2001
