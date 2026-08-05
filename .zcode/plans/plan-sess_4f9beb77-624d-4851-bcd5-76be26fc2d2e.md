# RarityCoreAPI 1.20.1 命名统一与 API 补齐 实施计划

**目标**：1.21.1 的 `RarityCoreAPI` 统一为 1.20.1 命名（旧名标 @Deprecated 保留转发），并补齐 1.20.1 独有、1.21.1 缺失的全部公共 API（含 StylePatch/StyleSnapshot/批量机制重建）。

**已确认的决策**：① 统一为 1.20.1 名字作为正式 API；② 全部补齐（含批量族）。

**改动文件（5 个）**：
- `api/RarityCoreAPI.java`（改名 + 补齐入口）
- `config/RarityStyleConfigManager.java`（新 setter、批量机制、值对象）
- `registry/RarityRegistry.java`（遍历族方法）
- `network/SyncManager.java`（恢复配置版本计数）
- `service/ConfigReloadService.java`（重载时递增版本）

**全局约束**：只改公共 API 面，不动内部业务逻辑与网络协议；行为差异以 1.21.1 现状为准；`RarityCoreAPI` 仅被 KubeJS 双绑定、工程内零 Java 调用点，无连锁影响；事件发布仅恢复 registerRarities 的 RarityRegistryChangedEvent（RarityConfigReloadEvent/RarityStyleChangedEvent 发布不恢复——1.21.1 有意移除，属行为变更非 API 缺失）；编译验证代替自动化测试（MC 模组无法单元测试）。

---

### 任务 1：RarityStyleConfigManager 基础设施扩展
**文件**：修改 `config/RarityStyleConfigManager.java`
1. 批量机制：新增 `private int batchDepth = 0;` + 私有 `persist()`（batchDepth==0 时执行 saveToFile+invalidateCaches）；把现有 12 个 setter 尾部 `saveToFile(); invalidateCaches();` 统一改为 `persist();`（行 732-790）；新增 `beginStyleBatch()`（batchDepth++）/ `endStyleBatch()`（归零时执行一次保存+失效）。
2. 新增 setter：`setNoRaritySkip(boolean)`、`setNoRarityDefaultRarity(int)`（改 `defaults.noRarity` 字段 + persist()）、`setTooltipStarRepeatChar(int, String)`（`ensureTooltip(level).star.repeatChar = ...` + persist()）、getter `getBorderFallback()`（返回 `defaults.border.fallback`）。
3. 重建 3 个 public static 嵌套值对象（在 PerRarityStyle 后）：
   - `StarSegmentConfig`：字段 colored/mode/repeatChar/custom（1.21.1 无 Specified 标志概念，省略）
   - `StylePatch`：final rarity + 可空 borderUseTexture/borderStyle/tooltipContent/starMode/starRepeatChar
   - `StyleSnapshot`：11 个 final 字段（rarity/borderUseTexture/borderStyle/borderFallback/tooltipShow/tooltipContent/tooltipColored/starMode/starRepeatChar/starCustom/starColored）+ 全参构造器
4. 新增实例方法：`getStarConfig(int)`（从 resolveTooltip(level).star 拷贝）、`setStyle(StylePatch)`（按非 null 字段委托 setter）、`getStyleSnapshot(int)`（从 resolveBorder/resolveTooltip 现算）。
5. 编译验证 `./gradlew compileJava`，提交。

### 任务 2：RarityRegistry 遍历族 + SyncManager 版本计数
**文件**：修改 `registry/RarityRegistry.java`、`network/SyncManager.java`、`service/ConfigReloadService.java`
1. RarityRegistry 新增 6 个 public static 方法（语义对齐 1.20.1，遍历 `BuiltInRegistries.ITEM` 用 `getRarity(item)` 过滤）：
   - `getItemsByRarity(int)` / `getItemIdsByRarity(int)` → unmodifiableList
   - `getConfiguredRarities()` → ITEM_RARITY_MAP.values ∪ AUTO_RARITY_MAP.values ∪ {getNoRarityDefaultRarity()}，unmodifiableSet
   - `getItemsByRarities(Set)`（null/空→emptyList）/ `getRarityCount(int)` / `getAllRarityEntries()`（先 AUTO 后 ITEM 覆盖，unmodifiableMap）
2. SyncManager 恢复：`AtomicInteger CONFIG_VERSION = new AtomicInteger(1)` + `getConfigVersion()` + `bumpConfigVersion()`。
3. ConfigReloadService.reloadAllConfigs 中"批处理队列处理之后、SyncManager 同步之前"插入 `SyncManager.bumpConfigVersion()`（与 1.20.1 位置一致）。
4. 编译验证，提交。

### 任务 3：RarityCoreAPI 改名组（统一 1.20.1 命名）
**文件**：修改 `api/RarityCoreAPI.java`
1. `getColor(int)` → 正式 `getRarityColor(int)`（实现不变），`getColor` 改 @Deprecated 转发。
2. `getTexture(int)` → 正式 `getRarityTexture(int)`，`getTexture` 改 @Deprecated 转发。
3. `isComponentRarityControlEnabled()` → 正式 `isNbtRarityControlEnabled()`（javadoc 注明实际控制 DataComponent 稀有度读取），旧名改 @Deprecated 转发。
4. 反转逐级开关状态：`isLevelRendererEnabled(int)`/`isLevelTooltipEnabled(int)`/`isLevelNameColorEnabled(int)` 去掉 @Deprecated 恢复正式；`isBorderEnabled(int)`/`isTooltipEnabled(int)`/`isNameColorEnabled(int)` 加 @Deprecated。
5. 新增无参 `isNameColorEnabled()` → `getInstance().isItemNameColorEnabled(RarityConstants.MIN_RARITY)`（1.20.1 主开关语义）。
6. 编译验证，提交。

### 任务 4：RarityCoreAPI 常量与探测补齐组
**文件**：修改 `api/RarityCoreAPI.java`
1. 常量：`MIN_RARITY`/`MAX_RARITY`（=RarityConstants）、`DEFAULT_RGB_COLOR`（=RarityColorUtil.DEFAULT_RGB_COLOR，1.21.1 现值 0xA0A0A0）、`API_VERSION = 1400`（与 1.20.1 特性集对齐，javadoc 注明）。
2. 探测：`isAvailable()` → true；`getModVersion()` → `net.neoforged.fml.ModList` 查询 + try-catch 返回 "unknown"；`getConfigVersion()` → `SyncManager.getConfigVersion()`。
3. 简单委托：`validateRarity(int)` → normalizeRarity；`reloadConfigs()` → `ConfigReloadService.reloadAllConfigs(null, false)`；`getRarityRgbColor(int)` → `RarityColorUtil.getRarityRgbColor`。
4. 编译验证，提交。

### 任务 5：RarityCoreAPI 样式查询/写入补齐组
**文件**：修改 `api/RarityCoreAPI.java`
1. 查询族（转发 getInstance()）：`getTooltipContent(int)` → resolveTooltip(rarity).content；`getLevelTranslationKey(int)` → .level.translationKey；`getLevelFallbackKey(int)` → .level.fallback；`getStarConfig(int)` → manager.getStarConfig；`isBorderUseTexture(int)` → resolveBorder(rarity).useTexture；`getBorderStyle(int)` → resolveBorder(rarity).style；`getBorderFallback()` → manager.getBorderFallback()。
2. 写入族：`setBorderEnabled/setTooltipEnabled/setTooltipColorEnabled`（转发现有全局 setter）、`setNoRaritySkip/setNoRarityDefaultRarity/setBorderUseTexture/setBorderStyle/setTooltipContent`、`setStarMode(int,String)`（→ setTooltipStarMode）、`setStarRepeatChar(int,String)`（→ setTooltipStarRepeatChar，共 10 个）。
3. 编译验证，提交。

### 任务 6：RarityCoreAPI 批量族 + registerRarities
**文件**：修改 `api/RarityCoreAPI.java`
1. 批量族：`beginStyleBatch()` / `endStyleBatch()` / `setStyle(RarityStyleConfigManager.StylePatch)` / `getStyleSnapshot(int)`（转发 manager）。
2. `registerRarities(Map<Item,Integer>)`：循环 `RarityRegistry.register(item, rarity, false)` 收集 changed（`BuiltInRegistries.ITEM.getKey`），最后 `syncIncrementalChangesToClients()` + `NeoForge.EVENT_BUS.post(new RarityRegistryChangedEvent(changed, Collections.emptySet()))`。
3. 编译验证，提交。

### 任务 7：整体编译验证 + 人工测试指导
1. `./gradlew build` 全量编译。
2. 人工测试（runClient + KubeJS 脚本）：新补 API（getRarityRgbColor/getItemsByRarity/getStarConfig 等）返回正确值；废弃名（getColor）与正式名（getRarityColor）结果一致；`setStarRepeatChar` + `endStyleBatch` 后工具提示星星变化；`/raritycore reload` 后 `getConfigVersion()` 递增。
3. 修复问题后提交。

**执行方式**：批准后先保存计划到 `docs/superpowers/plans/2026-08-05-raritycore-api-1.20.1-compat.md`，再按任务 1→7 顺序执行（每任务独立提交，编译验证）。