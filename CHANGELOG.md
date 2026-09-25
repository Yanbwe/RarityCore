# RarityCore 更新日志

## [1201.14.4]

### 修复
- 修复 `/raritycore reload` 之后新数据从不推送给客户端的问题：`NetworkRetryManager` 用 `channel instanceof IncrementalSyncPacket` / `instanceof RaritySyncPacket` 分派发送，但形参实为 `IncrementalSyncPacket.INSTANCE`／`RaritySyncPacket.INSTANCE`，其声明类型是 `SimpleChannel`，与包类没有任何继承关系，两个判断恒为假、方法恒返回 `false`（发送从未发生）。而调用方丢弃了返回值、"发送成功"日志又只在重试时打印，因此失败完全静默。后果不只是用户手写配置不生效——**75 个内置数据包 JSON（含 74 个模组）全部经 `RarityRegistry.register(item, rarity)`（默认同步）注册**，同样受影响
  - 现在直接用通道形参发送，不再做无意义的类型判断；成功判定改为依据返回值而非"没有抛异常"，失败会真正重试并按玩家粒度只重试失败者，全失败时记录 error
- 修复 `RarityStyle.json` 写盘时丢失逐级 `tooltip` 段的问题：`buildCurrentConfigJson` 的 `rarities` 循环只写出 `color`、`itemNameColor`、`border` 三个键，**从不写 `tooltip`**，而 `parseLevelOverride` 会读取它。于是任何一次保存（含 `setTooltipContent` / `setStarMode` / `setStarRepeatChar` 及 KubeJS 同名绑定触发的写盘）都会把该段从文件里抹掉，setter 改动重启即失，用户手写的逐级 tooltip/star 配置也永久读不回来
  - 同一处 `border` 原本无条件写全 4 个字段，重载时 `parseBorder` 把它们全部标记为"显式指定"，导致**"未指定即向低等级继承"在首次保存后永久失效**；逐级 `border.fallback` 也从不被写出
  - 现在新增 `writeBorder` / `writeTooltip`，按 `*Specified` 标记只写显式指定的字段；`defaults` 段仍完整写出（它是全量默认值的唯一来源）
- 修复批量同步缓冲区在积压时**静默丢弃变更操作**的问题：`SyncBatchManager.addOperation` 在待处理数达到 1000 时直接 `return true` 而不入队，注释里的"立即发送"需要调用方响应返回值才会发生，而所有调用方都忽略了它——超阈值的操作被丢弃，只有下次登录或 reload 才会补上
  - 现在始终入队，阈值到达时通过返回值请求调用方立即排空；常量由 `MAX_PENDING_OPERATIONS` 更名为 `URGENT_FLUSH_THRESHOLD`（它从来不是丢弃上限）
  - `RarityRegistry` 的三处变更登记改为经统一的 `enqueueChange` 入队，收到排空请求时调用 `DelayedSyncManager.flushPendingOperations()`（该方法只向单线程执行器提交任务，不阻塞、也不会与批量锁重入）
- 修复铁魔法（Iron's Spells）开关导致**服务端与客户端算出不同稀有度**的问题：`RarityRegistry.checkIronSpellbooksRarity` 在稀有度解析阶段读取 `ClientConfigManager.isEnableIronSpellsAdapter()`，而该键位于各端独立的 `config/raritycore/client.json`。多人游戏下服务器管理员为排查 issue #14 关掉自己那份，就会使服务端跳过铁魔法分支、客户端仍走该分支，同一物品两边结果不一致，且用户没有任何手段对齐
  - 该开关现明确定义为**纯客户端显示开关**：解析链两端一致地按法术等级解析，关闭仅表示本地不显示；对外 API（`RarityCoreAPI.getRarity()` 等）返回的仍是真实解析结果，联动模组与 KubeJS 在任一端拿到的数据一致
  - 兼容适配器初始化不再受该开关控制（`CompatibilityManager`）——否则关掉开关的一侧根本不初始化适配器，反而解析不出铁魔法
  - 新增 `client.IronSpellsDisplaySwitch` 作为该开关的唯一归属地；开关开启（默认）时第一道判断即返回，渲染热路径零额外开销
- 修复神化与铁魔法物品在**边框渲染路径**上一律显示最低档的问题：这两类物品会被 `DualCacheManager` 主动判为未命中（以强制实时计算），而 `ItemBorderRenderer` 在未命中时直接退到 `defaults.noRarity.defaultRarity`。现在先按物品查 ID 缓存，取不到才用默认值

### 其他
- `ClientConfigManager` 类文档明确其"纯客户端显示配置"定位：其中的开关只能影响本地显示，不得参与稀有度解析

## [1201.14.3]

### 修复
- 修复 `client.json` 中的配置项被静默删除、导致手动添加的开关不生效的问题：V14 迁移逻辑 `handleLegacyFiles()` 会把该文件整体重写为"仅含 `enableCacheSystem`"，其余键（包括 1201.14.1 新增的 `enableIronSpellsAdapter`）每次启动都被清除，用户手动添加也会被吞掉，因此铁魔法的法术等级动态映射始终无法关闭
  - 现在改为定向剔除，只移除 `enableItemBorderRendering`、`enableTooltipInsert` 两个已无读取方且默认值一致的死键，文件中其余任何键（本模组开关与用户自定义键）一律原样保留
  - 加载顺序调整为「先清理旧键 → 加载 RarityStyle → 重新加载客户端配置」，补入的开关当次启动即生效
- 修复旧版本生成的 `client.json` 缺少新增配置项的问题：加载时会把缺失的已知键按默认值补入文件并记录日志（原文件其余内容保留），存量安装无需手动改配置即可看到开关；用户已显式设置的值不会被覆盖

## [1201.14.2]

### 修复
- 修复 NBT 匹配规则在重启客户端或重进存档后失效的问题：规则同步包把等值条件的数值/布尔值统一转成字符串，导致 `equals` 条件中的数值（如 `1`）与布尔（如 `true`）在客户端无法匹配原版 `ByteTag`（`getAsString()` 为 `1b`/`0b`）等标签；现在同步包保留原始类型，且旧格式数据包也能被容错匹配（无需重连即可自愈）
- 等值比较新增类型判定：数值条件仅与数值型标签比较（避免数字型期望值误匹配字符串标签内容），布尔条件兼容 `1b`/`0b` 与原版标志位存储形式

### 诊断
- `EqualsCondition` 新增比较过程 debug 日志（路径、实际标签类型与内容、期望值类型与值、匹配结果），便于排查规则为何不生效

### 网络
- 协议版本提升至 `1.2.0`（同步包条件值类型语义变更）。多人游戏需服务端与客户端均为 1201.14.2 及以上，版本不一致会被拒绝连接

## [1201.14.1] - 2026-08-10

### 新增
- 编辑模式面板支持鼠标拖拽移动（位置不持久化，重启后复位）
- 新增 FTB Library 物品边框渲染兼容（Mixin + 兼容管理器登记）
- 新增 `enableIronSpellsAdapter` 配置开关，可禁用铁魔法（Iron Spells）稀有度联动（issue #14）

### 修复
- 修复与精致存储（Refined Storage）的渲染联动失效
- 修复编辑模式折叠面板点击误触发模式切换，恢复右键物品槽编辑
- 修复面板外按下拖动时误触面板移动（增加面板内按下守卫）
- 渲染时钳制面板位置，避免面板拖出屏幕边界

### 构建与其他
- 更新构建和发布工作流，添加版本解析与发布功能
- 更新 .gitignore 以排除新增构建文件
