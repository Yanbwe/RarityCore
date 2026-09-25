# RarityCore 更新日志

## [1211.14.8]

### 修复
- 修复批量同步缓冲区在积压时**静默丢弃变更操作**的问题：`SyncBatchManager.addOperation` 在待处理数达到 `maxPendingOperations`（默认 1000，可由 `sync_batch.json` 覆盖）时直接 `return true` 而不入队，而唯一调用方 `ConfigLoaderUtils` 忽略了返回值——第 1001 条起的配置项既不注册也不下发、且无任何日志。该类批量加载路径（`FinalRarityConfigFolderLoader` / `RarityConfigLoader`，均以批处理模式逐文件加载）在加载期间不排空，因此真实可达
  - 现在始终入队，达阈值时通过返回值请求调用方立即排空并记录 WARN；字段名与 `sync_batch.json` 键名保持不变（配置兼容），仅澄清其语义是"排空请求阈值"而非丢弃上限
  - `ConfigLoaderUtils` 响应返回值，在整份文件加载完成后排空一次：先按 `ConfigReloadService` 同语义把操作应用到注册表，再一次性交给增量同步缓冲区按上限分包下发，避免逐条发包
- 修复铁魔法（Iron's Spells）开关导致**服务端与客户端算出不同稀有度**的问题：`RarityRegistry.checkIronSpellsRarity` 与 `RarityCacheCoordinator` 在解析/缓存阶段读取 `ClientConfigManager.isEnableIronSpellsAdapter()`，而该键位于各端独立的 `config/raritycore/client.json`。多人游戏下服务端与客户端会对同一物品得出不同结果，且用户无法对齐
  - 该开关现明确定义为**纯客户端显示开关**（与 1.20.1 的修复语义一致）：解析链两端一致地按法术等级解析，关闭仅表示本地不显示；新增 `client.IronSpellsDisplaySwitch` 承载该开关，边框、工具提示、名称颜色三条渲染链路均接入
  - 兼容适配器初始化不再受该开关控制（两端都要初始化）；`RarityCacheCoordinator` 的缓存判定改为仅取决于适配器是否加载，避免缓存路径在两端分叉
  - 对外 API（`RarityCoreAPI.getRarity()` 等）返回的仍是真实解析结果

## [1211.14.7]

### 修复
- 修复等值条件（`equals`）在数值/布尔匹配上的静默失效：NBT 没有布尔类型，原版与多数模组用 `ByteTag` 存储标志位（`getAsString()` 为 `1b`/`0b`），此前 `equals(1)`、`equals(true)` 会因字符串比较而无法命中
- 等值比较改为类型感知：数值条件仅与数值型标签比较（不再让数字型期望值误匹配 `StringTag("32")` 之类的文本），布尔条件同时兼容 `1b/0b` 与 `1/0`，并兼容 `Double(1.0)` 等数值形式
- 兼容旧同步包数据：期望值被历史版本字符串化（如 `"1"`、`"true"`）时仍能正确匹配，无需重连即可自愈


## [1211.14.6] - 2026-08-28

### 新增
- FTB Library 兼容（issue #22）：FTB Quests 任务界面中的任务图标、奖励图标、奖励表图标现在会渲染稀有度边框

### 配置
- `client.json` 新增 `enableFtbLibraryAdapter`（默认开启），可禁用 FTB Library 稀有度边框联动


## [1211.14.5] - 2026-08-10

### 修复
- 修复缓存"组件感知"问题：带组件稀有度控制（`raritycore.Level`）的物品堆改为按 NBT 哈希走组件缓存，不再被 ID 缓存互相污染（修复"所有枪械一起变"根因）

### 调整
- 组件稀有度控制改为"半开"：检测到 ModularRarity 后，仅对 ModularShoot 提供的枪械（`modularshoot:gun`）与插件（`modularshoot:plugin`）物品执行组件稀有度读取，其他物品 O(1) 短路跳过，消除 `copyTag` 全量 NBT 复制的热路径开销（绑定物品稀有度由 `registerRarity` 提供，不受影响）

### 构建与其他
- 更新模组版本至 1211.14.5

## [1211.14.4] - 2026-08-10

### 新增
- 支持 TacZ 物品的编辑模式，自动生成子物品匹配配置
- 编辑模式面板支持鼠标拖拽移动（位置不持久化，重启后复位）
- 新增 `enableIronSpellsAdapter` 配置开关，可禁用铁魔法（Iron Spells）稀有度联动（issue #14）

### 修复
- 修复面板外按下拖动时误触面板移动（增加面板内按下守卫）
- 渲染时钳制面板位置，面板外按下复位拖动状态

### 构建与其他
- 更新模组版本至 1211.14.4，修复相关配置
- 修改发布工作流以支持版本标签推送