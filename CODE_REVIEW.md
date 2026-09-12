# TLoot 代码审查报告（内存泄漏 / Bug / 优化）

审查范围：`src/main/java/com/tloot` 全部 27 个类 + 资源文件，对照提交 `d2d06a5` 逐文件核对。
验证方式：`mvn clean package` 全量编译通过（Paper API 26.2，Java 21），并用独立审查代理对修复后的代码做了两轮对抗式复核（其发现的问题已全部处理）。

---

## 一、内存泄漏

| # | 位置 | 问题 | 处置 |
|---|------|------|------|
| 1 | `gui/GUIManager` | `playerCreateCoins` / `playerTicketPrice` 只在「成功领取告示牌」时清理。玩家在发起界面调好金额后直接退出/跨服转移，条目永久残留；`playerOpenGUI`/页码表同样只在特定 GUI 的关闭事件里清理 | 新增 `handlePlayerQuit(uuid)` 清空该玩家全部会话状态（`listener/PlayerSessionListener` 在 `PlayerQuitEvent` 调用），插件关闭时 `clearAll()` 兜底 |
| 2 | `listener/PointerListener` | 三个以 UUID 为键的 Map（粒子冷却、指南针目标、描述刷新）按玩家持续写入，退出清理路径不完整 | 退出时统一 `clearPlayerState`；新增 `shutdown()` 取消任务并清空 |
| 3 | `data/TreasureManager` | `playerCreatingTreasure` 这个以 UUID 为键的 Map 只有 put/get，**全项目无任何调用**，也没有清理逻辑 → 纯粹的泄漏源 | 整块功能删除 |
| 4 | `storage/MySQLStorage` | `Executors.newSingleThreadExecutor()` 是无界队列：数据库卡顿时任务无限堆积，且每个任务持有 `Treasure` 强引用 | 改为 `1` 线程 + `ArrayBlockingQueue(2048)`，队列满丢弃最旧任务并打日志（避免主线程被迫执行 JDBC 写入） |
| 5 | `TLoot#onEnable` | `registerListeners()` 里 `new PointerListener(this)` 被实例化两次，其中一次仍会启动定时任务（重载时任务翻倍） | 只实例化一次并复用同一实例注册 |
| 6 | `TLoot#onDisable` | `autoTreasureTask` 重载时未取消旧实例，会重复起任务；`instance` 静态引用未置空 | 增加取消逻辑；`onDisable` 中 `instance = null` 并清空全部会话状态 |
| 7 | `data/TreasureManager` | `locationIndex` 只增不减（`removeTreasure` 移除内存对象后索引仍指向已删除 ID），已删除宝藏的指针也从不回收 | 引入 `unindex()` 统一「内存 + 位置索引」撤销；新增 `PointerItem.removePointers` 在领取/过期/移除时回收背包指针 |
| 8 | `beacon/BeaconEffectManager` | 任务句柄不可重复取消（`stop()` 后仍指向已取消任务，`start()` 可重复创建）；每秒 `getAllTreasures()` 复制整个宝藏集合 | `start()` 幂等 + `treasuresView()` 只读视图遍历 |

---

## 二、Bug

### 会造成玩家资产损失 / 刷取

| # | 位置 | 问题 | 处置 |
|---|------|------|------|
| 1 | `listener/TreasureListener#claimTreasure` | 先 `addItem(奖励)` 再清理指针 → 背包放不下时，多余的**指针被当作奖励掉在地上**，形成刷指针 | 先回收指针，再发放奖励 |
| 2 | 同上 | 指针清理用 `item.setAmount(0)`，槽位未被清空，客户端残留「幽灵物品」，服务端与客户端不同步 | 统一改为 `inventory.setItem(slot, null)`（`PointerItem.removePointers`），过期任务同样修复 |
| 3 | 同上 | 保底金币用 `depositPlayer` 但忽略返回值；且「钱先到账、宝藏还在」——购买失败时玩家已拿到钱 | 先移除箱子方块再结算；校验 `EconomyResponse.transactionSuccess()` 并告警 |
| 4 | `listener/TreasureListener` | `claim-distance` 配置项**完全没被使用**，可远程点击箱子领取 | 领取前校验世界与距离（`claim.too-far`） |
| 5 | `listener/TreasureSignListener` | 不检查 `event.isCancelled()`，也不校验扣款结果；`createTreasure` 抛异常时钱已扣走且箱子已被清空 | 校验 `isCancelled`、校验扣款返回值、创建失败自动退款、箱子改为创建成功后清空 |
| 6 | `command/TreasureCommand#handleJoin`、`gui/CompassGUIListener` | 未检查背包空间就扣费：付费后指针放不下直接掉地上（等于白花钱） | 参与前校验 `firstEmpty()`；放不下的物品改为掉落而非丢弃 |
| 7 | `command/TreasureCommand#handleJoin` | 忽略扣款失败仍把玩家加为参与者 | 校验 `transactionSuccess()`，失败即中止 |
| 8 | `listener/gui/MyTreasureGUIListener` | 只要背包里有**任意**指针就拒绝重新获取，参与多个寻宝的玩家无法取回其它指针 | 只比对指向同一宝藏的指针 |
| 9 | `listener/gui/*` | 4 个 GUI 监听器共用同一套「同优先级 + 取消事件 + `ignoreCancelled`」逻辑：Bukkit 中一个监听器取消事件后，同优先级的后续监听器会被跳过（已在 `RegisteredListener#callEvent` 字节码中确认），导致**创建界面的「确认获取告示牌」等按钮失效** | 新增 `AbstractGUIListener` 基类 + `GUIManager.isGUIOpen(player, type, view)`：先确认 GUI 归属再取消事件，各 GUI 互不吞事件 |
| 10 | `listener/gui/*` | 切换界面（指南针第 2 页 → 第 3 页）时 `openInventory` 内部会同步触发旧界面的 `InventoryCloseEvent`，页码在 `openInventory` 前写入 → 新页码被立刻清掉，**第 3 页永远到不了** | 关闭事件不再清理会话状态，统一在退出/关闭插件时清理 |
| 11 | `listener/TreasureListener` | 跨世界使用指针时 `Location#distance` 抛 `IllegalArgumentException` | 先判断世界一致性，跨世界时改为提示所在世界 |
| 12 | `data/AutoTreasureTask` | 世界范围配置反向（min > max）时 `Random.nextInt` 抛异常 → **重复任务被永久取消**（自动寻宝静默停止）；物品数量、保底金币区间同理 | 生成前归一化区间，数量下限钳制为 ≥1 |
| 13 | `data/AutoTreasureTask` | 随机位置可能与已有宝藏重合，导致 `locationIndex` 覆盖、旧宝藏变成无法领取的幽灵数据 | 选中位置先做占用检查，占用则跳过 |
| 14 | `data/AutoTreasureTask` | 先放箱子再建记录，创建失败会留下无法领取的「幽灵箱子」 | 先建记录，成功后再 `setType(CHEST)` |

### 会导致数据丢失 / 加载失败

| # | 位置 | 问题 | 处置 |
|---|------|------|------|
| 15 | `data/Treasure#isExpired` | `expireTime` 缺省为 0 时 `createTime + 0 < now` 恒成立，旧格式数据加载即被静默丢弃（连带删除存档行） | 显式处理 `expireTime <= 0`，并在存储层记录跳过条数 |
| 16 | `data/Treasure#deserialize` | 任何脏数据（uuid 非法、字段缺失）都会抛异常使**整份存档加载失败** | 加异常兜底返回 `null`，逐条跳过并告警；宽容处理 items/commands/participants 的多种旧格式 |
| 17 | `storage/MySQLStorage#loadAll` | 单行损坏会中断整个 `ResultSet` 遍历，服务器以「零宝藏」启动 | 逐行 try/catch，跳过损坏行 |
| 18 | `storage/MySQLStorage` | 物品序列化失败时写入 `items = NULL`，覆盖数据库中已有的奖励物品 | 序列化失败整条记录跳过保存并告警 |
| 19 | `storage/YamlStorage` | 单条数据序列化失败会使整份存档写入失败，异常还会中断定时落盘任务 | `buildConfig` 逐条 try/catch |
| 20 | `config/ConfigManager` | `getConfigurationSection(...)` 可能返回 `null`（配置项存在但为空时）→ NPE | 改为取回 section 判空，`allowedWorlds` 用不可变副本 |
| 21 | `sync/RedisSyncManager` + `data/TreasureManager` | 领取公告只放在 Redis 回调里，而 Redis 默认关闭 → 单服部署**完全没有领取公告**（回归） | 本服公告回归领取动作，Redis 回调负责其它子服 |
| 22 | `data/TreasureManager` | Redis 跨服事件只撤销内存索引，不清理本服残留的同位置箱子（其他子服领取/过期后，本服箱子仍在且可被打开） | 抽出 `util/TreasureBlocks`，跨服与本地事件统一清理箱子（仅在区块已加载时操作，不强制生成区块） |
| 23 | `data/TreasureManager#createTreasure` | 无同位置占用校验，可覆盖已有宝藏的索引 | 冲突时拒绝并抛异常，调用方已做退款兜底 |
| 24 | `command/TreasureCommand` | 控制台无法执行 `/treasure reload`（先被 `player-only` 拦截）；Tab 补全在 `args` 为空时可能越界；补全会泄漏无权限的子命令 | 管理员子命令允许控制台执行；补全加空数组保护并按权限过滤 |
| 25 | `listener/gui/*` | 通过**解析物品显示名称**还原宝藏 ID，改文案即失效（指南针面板按固定 8 位截断，我的寻宝面板按空格截断） | 宝藏物品改用 PersistentDataContainer 携带 ID，旧物品保留解析兜底 |
| 26 | `gui/GUIManager` | `gui.compass.size` 配置为 9/小于 18 时 `itemsPerPage` 变负，分页计算出错 | `normalizeGuiSize` 钳制到 [18, 54] 且为 9 的倍数 |
| 27 | `gui/GUIManager#fillCreateMenuItems` | `max == min` 时进度条除零得到 NaN | 显式处理区间为 0 |
| 28 | `gui/GUIManager#track` | 若界面被其它插件拦截（`InventoryOpenEvent` 被取消），旧界面仍在屏幕上却已登记新类型 → 点击被路由到错误监听器 | 确认 `view.getTopInventory() == inventory` 后再登记类型 |
| 29 | `listener/TreasureListener` | 重复广播（本服公告由监听器与 Redis 回调各发一次） | 本服只发一次，跨服由回调负责 |

---

## 三、优化

1. **`NamespacedKey` 缓存**：`PointerItem.getTreasureId` / `TreasureSignItem` / `GUIManager` 原先每次调用都 `new NamespacedKey(...)`，而指针任务每 20 tick 对每个在线玩家调用多次 → 改为懒加载缓存。
2. **`Treasure` 位置键预计算**：`getLocationKey()` 原实现每次查询都拼接字符串（GUI 与事件高频调用），改为构造时算好；新增静态 `locationKeyOf` 供占用检查。
3. **YAML 存储防抖**：原先每次 `save/delete` 都同步重写整份 data.yml（玩家参与即触发，O(n²) 且阻塞主线程）→ 改为脏标记 + 每 5 秒合并落盘，文件 IO 放异步线程，写入加锁串行化，关闭时同步落盘。
4. **MySQL 去重与快照**：`save` / `saveAll` 的 SQL 与参数绑定重复代码抽成常量 + `bind()`；异步任务不再持有可变对象，条目数据在提交前于主线程完成序列化；`saveAll` 增加事务回滚。
5. **`getParticipants().size()` → `getParticipantCount()`**，遍历改用只读视图 `participantsView()`（GUI 每页刷新、过期任务、Redis 发布都在用）。
6. **GUI 物品构建**：`Treasure` 序列化去掉冗余的 `index` 字段；GUI 边框物品只构建一次复用；`glassPane()` 去掉无用参数；移除未使用的 `isGuiItem`。
7. **指针描述刷新节流**：原每 5 秒无条件 `setItemMeta` 写回主手物品（客户端不同步 + 无谓开销）→ 10 秒一次且**仅在文本真正变化时**写回。
8. **指针任务降噪**：每玩家 tick 逻辑收敛为单一入口，宝藏不存在/过期时立即清理状态。
9. **过期清理不再强制加载区块**：原先为每个过期宝藏 `getChunkAt(...).load(true)`，改为仅在区块已加载时移除箱子。
10. **光柱特效可配置**：硬编码的「最大高度 80」与固定间隔改为 `pointer.beacon.*`（可整体关闭）。
11. **自动寻宝日志降噪**：仅在生成/异常时输出，避免每轮刷屏。
12. **移除死代码**：`ConfigManager.getTicketPrice()`（与 `Treasure#getTicketPrice` 易混淆且无引用）、`GUIManager.getOpenGUI/removePlayer`（重构后无引用）、`data/TreasureManager` 中无任何调用方的 `playerCreatingTreasure` 系列方法。

---

## 四、验证

- `mvn -o clean package` 通过，产物 `target/Liu-TLoot-1.0.jar`（57 个条目，32 个类）。
- 独立审查代理两轮对抗式复核：首轮 6 项发现（含 GUI 事件互相吞掉、页码不可达、公告回归、跨世界距离异常、MySQL 整表加载失败、YAML 关闭竞态）已全部修复；次轮确认 4 个 GUI 均只被唯一监听器接管、页码状态正确、无残留泄漏路径，并指出 4 项低危问题，也已一并处理。

**仍需在真实服务器上确认**：`isGUIOpen` 依赖 `view.getTopInventory()` 与 `createInventory` 返回同一实例（Bukkit 契约如此），建议在测试服点一遍四个 GUI 做一次冒烟验证。
