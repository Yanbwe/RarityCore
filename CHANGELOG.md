# RarityCore 更新日志

## [260x.14.1]

### 修复
- 修复等值条件（`equals`）在数值/布尔匹配上的静默失效：物品数据里的标志位常以数值形式存储（旧式 NBT 标签经解析为 `Double(1.0/0.0)`，布尔组件为 `Boolean`），此前 `equals(true)`、`equals(1)` 会因为按字符串比较（`"1.0"` vs `"true"`）而无法命中
- 等值比较改为类型感知：数值期望值对 `Double/Integer/Long/Short` 等一律按数值比较（容差 0.001），布尔期望值同时兼容 `Boolean` 与数值 `0/1`
- 兼容旧同步包数据：期望值被历史版本字符串化（如 `"1"`、`"true"`）时仍能正确匹配
- 字符串与数字不再混同：`equals(3)` 不会误匹配字符串 `"3"` 的文本标签（`tag.xxx` 下数值仍按数值比较）


## [260x.14.0] - 2026-08-16

### 新增
- V14 配置整合：新增 `RarityStyle.json`，统一管理颜色、边框、Tooltip、星星与无稀有度回退；`client.json` 裁剪为仅保留缓存与精妙核心适配器开关；旧 `RarityClientConfig.json` 启动时自动删除
- 去除 7 级稀有度上限：8+ 级稀有度与 1-7 级统一走继承/回退路径，支持更高等级 Tooltip、边框与颜色
- 公共 API 升级至 V14：新增全局开关、逐级视觉表现读写、`RarityConfigReloadEvent` / `RarityStyleChangedEvent` / `RarityRegistryChangedEvent`、集合查询、批量注册、样式快照与版本感知
- 等级名支持通过翻译键显示文字（默认 `rarity.core.1`~`rarity.core.7`）

### 修复
- 修复 Tooltip 中等级名直接显示阿拉伯数字的问题：`@{level}` 现在会按 `RarityStyle.json` 的 `translationKey` 解析为本地化文字，翻译缺失时回退到 `fallback`

### 调整
- 删除旧 `RarityClientConfig`、`StarDisplayConfigManager` 与命名稀有度常量
- 命令/重载流程统一接入 `RarityStyleConfigManager` 与 `RarityConfigReloadEvent`

### 构建与其他
- 26.1 与 26.2 共享同一份源码，分别构建 `raritycore-2601.13.7.jar` 与 `raritycore-2602.13.6.jar`

## [2602.13.6] - 2026-08-16

### 新增
- V14 配置整合：新增 `RarityStyle.json`，统一管理颜色、边框、Tooltip、星星与无稀有度回退；`client.json` 裁剪为仅保留缓存与精妙核心适配器开关；旧 `RarityClientConfig.json` 启动时自动删除
- 去除 7 级稀有度上限：8+ 级稀有度与 1-7 级统一走继承/回退路径，支持更高等级 Tooltip、边框与颜色
- 公共 API 升级至 V14：新增全局开关、逐级视觉表现读写、`RarityConfigReloadEvent` / `RarityStyleChangedEvent` / `RarityRegistryChangedEvent`、集合查询、批量注册、样式快照与版本感知
- 等级名支持通过翻译键显示文字（默认 `rarity.core.1`~`rarity.core.7`）

### 修复
- 修复 Tooltip 中等级名直接显示阿拉伯数字的问题：`@{level}` 现在会按 `RarityStyle.json` 的 `translationKey` 解析为本地化文字，翻译缺失时回退到 `fallback`

### 调整
- 删除旧 `RarityClientConfig`、`StarDisplayConfigManager` 与命名稀有度常量
- 命令/重载流程统一接入 `RarityStyleConfigManager` 与 `RarityConfigReloadEvent`

### 构建与其他
- 26.1 与 26.2 共享同一份源码，分别构建 `raritycore-2601.13.7.jar` 与 `raritycore-2602.13.6.jar`
