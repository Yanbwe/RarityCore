# RarityCoreAPI 1.20.1 命名统一与 API 补齐 实施计划

**目标：** 让 1.21.1 的 `RarityCoreAPI` 恢复 1.20.1 的公开 API 命名（旧名保留标 @Deprecated），并补齐 1.20.1 独有、1.21.1 缺失的全部公共 API（含 StylePatch/StyleSnapshot/批量机制重建）。

**架构：** 所有改动集中在 5 个文件：`RarityCoreAPI`（改名+补齐入口）、`RarityStyleConfigManager`（新增 setter/批量机制/值对象）、`RarityRegistry`（新增遍历族方法）、`SyncManager`（恢复配置版本计数）、`ConfigReloadService`（重载时递增版本）。补齐的 API 全部是薄委托，复用 1.21.1 现有实现（resolveBorder/resolveTooltip/resolveColor、ITEM_RARITY_MAP/AUTO_RARITY_MAP、ConfigReloadService.reloadAllConfigs）。

**技术栈：** Java 21、NeoForge 21.1.x、ModDevGradle。

## 全局约束

- 只改公共 API 面，不改变 1.21.1 内部业务逻辑与网络协议。
- 改名方向已确认：**统一为 1.20.1 名字作为正式 API**，1.21.1 现有名字标 `@Deprecated` 保留转发。
- 补齐范围已确认：**全部补齐**（含 StylePatch/StyleSnapshot/批量机制）。
- 行为差异以 1.21.1 现状为准（如 resolveColor 与 1.20.1 getColor 的 "inherit" 细节差异、DEFAULT_RGB_COLOR 值不同），不做行为对齐。
- `RarityCoreAPI` 仅被 KubeJS 双绑定（`raritycore`/`RarityCore`），工程内无 Java 调用点，改名无连锁影响；废弃方法保留转发保证 JS 脚本不破坏。
- 事件发布仅恢复 `registerRarities` 的 `RarityRegistryChangedEvent`（其语义一部分）；`RarityConfigReloadEvent`/`RarityStyleChangedEvent` 发布不恢复（1.21.1 有意移除，属行为变更非 API 缺失，不在本计划范围）。
- 编译验证代替自动化测试（Minecraft 模组无法单元测试）；人工测试指导见任务 7。

---

### 任务 1：RarityStyleConfigManager 基础设施扩展（批量机制、新 setter、值对象）

**文件：**
- 修改：`src/main/java/org/yanbwe/raritycore/config/RarityStyleConfigManager.java`

**接口：**
- 消费：无
- 产生：任务 5、6（RarityCoreAPI 样式查询/写入/批量族依赖本任务的公开方法）

- [ ] **步骤 1：批量机制。** 新增实例字段 `private int batchDepth = 0;`。新增私有方法 `private void persist() { if (batchDepth == 0) { saveToFile(); invalidateCaches(); } }`。将现有全部 setter 尾部的 `saveToFile(); invalidateCaches();` 替换为 `persist();`（共 11 处：setBorderEnabled/setTooltipEnabled/setTooltipColorEnabled/setColor/setBorderUseTexture/setBorderStyle/setBorderShow/setTooltipShow/setTooltipContent/setTooltipColored/setTooltipStarMode/setItemNameColorEnabled——按文件实际数量核对，行 732-790）。新增：
  ```java
  public void beginStyleBatch() { batchDepth++; }
  public void endStyleBatch() {
      if (batchDepth <= 0) return;
      batchDepth--;
      if (batchDepth == 0) { saveToFile(); invalidateCaches(); }
  }
  ```
- [ ] **步骤 2：新增全局 setter。** 在 `isNoRaritySkip()`/`getNoRarityDefaultRarity()`（行 740-741）旁新增：
  ```java
  public void setNoRaritySkip(boolean skip) { defaults.noRarity.skip = skip; persist(); }
  public void setNoRarityDefaultRarity(int rarity) { defaults.noRarity.defaultRarity = rarity; persist(); }
  ```
- [ ] **步骤 3：新增逐级 setter 与 getter。** 在逐级 setter 区（行 782 setTooltipStarMode 之后）新增：
  ```java
  public void setTooltipStarRepeatChar(int level, String repeatChar) {
      ensureTooltip(level).star.repeatChar = repeatChar;
      persist();
  }
  public String getBorderFallback() { return defaults.border.fallback; }
  ```
- [ ] **步骤 4：重建值对象 StarSegmentConfig / StylePatch / StyleSnapshot**（作为 `RarityStyleConfigManager` 的 public static 嵌套类，放在 PerRarityStyle 类之后）：
  ```java
  /** 1.20.1 兼容：逐级星星配置（1.21.1 无 Specified 标志概念，故省略） */
  public static class StarSegmentConfig {
      public boolean colored = true;
      public String mode = "repeat";
      public String repeatChar = "★";
      public String custom = "";
      public StarSegmentConfig() {}
      public StarSegmentConfig(boolean colored, String mode, String repeatChar, String custom) {
          this.colored = colored; this.mode = mode; this.repeatChar = repeatChar; this.custom = custom;
      }
  }

  /** 1.20.1 兼容：样式补丁（null 字段跳过） */
  public static class StylePatch {
      public final int rarity;
      public Boolean borderUseTexture;
      public Integer borderStyle;
      public String tooltipContent;
      public String starMode;
      public String starRepeatChar;
      public StylePatch(int rarity) { this.rarity = rarity; }
  }

  /** 1.20.1 兼容：样式快照（不可变） */
  public static class StyleSnapshot {
      public final int rarity;
      public final boolean borderUseTexture;
      public final int borderStyle;
      public final String borderFallback;
      public final boolean tooltipShow;
      public final String tooltipContent;
      public final boolean tooltipColored;
      public final String starMode;
      public final String starRepeatChar;
      public final String starCustom;
      public final boolean starColored;
      public StyleSnapshot(int rarity, boolean borderUseTexture, int borderStyle, String borderFallback,
                           boolean tooltipShow, String tooltipContent, boolean tooltipColored,
                           String starMode, String starRepeatChar, String starCustom, boolean starColored) {
          this.rarity = rarity; this.borderUseTexture = borderUseTexture; this.borderStyle = borderStyle;
          this.borderFallback = borderFallback; this.tooltipShow = tooltipShow; this.tooltipContent = tooltipContent;
          this.tooltipColored = tooltipColored; this.starMode = starMode; this.starRepeatChar = starRepeatChar;
          this.starCustom = starCustom; this.starColored = starColored;
      }
  }
  ```
- [ ] **步骤 5：新增 getStarConfig / setStyle / getStyleSnapshot 实例方法**（放在 resolveTooltip 族方法附近，行 694-702 之后）：
  ```java
  public StarSegmentConfig getStarConfig(int level) {
      TooltipStarConfig star = resolveTooltip(level).star;
      return new StarSegmentConfig(star.colored, star.mode, star.repeatChar, star.custom);
  }
  public void setStyle(StylePatch patch) {
      if (patch.borderUseTexture != null) setBorderUseTexture(patch.rarity, patch.borderUseTexture);
      if (patch.borderStyle != null) setBorderStyle(patch.rarity, patch.borderStyle);
      if (patch.tooltipContent != null) setTooltipContent(patch.rarity, patch.tooltipContent);
      if (patch.starMode != null) setTooltipStarMode(patch.rarity, patch.starMode);
      if (patch.starRepeatChar != null) setTooltipStarRepeatChar(patch.rarity, patch.starRepeatChar);
  }
  public StyleSnapshot getStyleSnapshot(int level) {
      BorderConfig border = resolveBorder(level);
      TooltipConfig tooltip = resolveTooltip(level);
      return new StyleSnapshot(level, border.useTexture, border.style, getBorderFallback(),
          tooltip.show, tooltip.content, tooltip.colored,
          tooltip.star.mode, tooltip.star.repeatChar, tooltip.star.custom, tooltip.star.colored);
  }
  ```
- [ ] **步骤 6：编译验证**：`./gradlew compileJava`
- [ ] **步骤 7：提交**：`git add -A && git commit -m "feat(style): 新增 1.20.1 兼容的样式 setter、批量机制与值对象"`

---

### 任务 2：RarityRegistry 遍历族 + SyncManager 版本计数 + ConfigReloadService 递增

**文件：**
- 修改：`src/main/java/org/yanbwe/raritycore/registry/RarityRegistry.java`
- 修改：`src/main/java/org/yanbwe/raritycore/network/SyncManager.java`
- 修改：`src/main/java/org/yanbwe/raritycore/service/ConfigReloadService.java`

**接口：**
- 消费：无
- 产生：任务 4（getConfigVersion）、任务 5（查询族）依赖

- [ ] **步骤 1：RarityRegistry 新增遍历族 public static 方法**（语义对齐 1.20.1：遍历 `BuiltInRegistries.ITEM` 全注册表，用 `getRarity(item)` 解析过滤；`getItemIdByRarity` 用 `BuiltInRegistries.ITEM.getKey`）：
  ```java
  public static java.util.List<Item> getItemsByRarity(int rarity) {
      java.util.List<Item> result = new java.util.ArrayList<>();
      for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
          if (getRarity(item) == rarity) result.add(item);
      }
      return java.util.Collections.unmodifiableList(result);
  }
  public static java.util.List<ResourceLocation> getItemIdsByRarity(int rarity) {
      java.util.List<ResourceLocation> result = new java.util.ArrayList<>();
      for (Item item : BuiltInRegistries.ITEM) {
          if (getRarity(item) == rarity) result.add(BuiltInRegistries.ITEM.getKey(item));
      }
      return Collections.unmodifiableList(result);
  }
  public static java.util.Set<Integer> getConfiguredRarities() {
      java.util.Set<Integer> set = new java.util.HashSet<>(ITEM_RARITY_MAP.values());
      set.addAll(AUTO_RARITY_MAP.values());
      set.add(org.yanbwe.raritycore.config.RarityStyleConfigManager.getInstance().getNoRarityDefaultRarity());
      return Collections.unmodifiableSet(set);
  }
  public static java.util.List<Item> getItemsByRarities(java.util.Set<Integer> rarities) {
      if (rarities == null || rarities.isEmpty()) return Collections.emptyList();
      java.util.List<Item> result = new java.util.ArrayList<>();
      for (Item item : BuiltInRegistries.ITEM) {
          if (rarities.contains(getRarity(item))) result.add(item);
      }
      return Collections.unmodifiableList(result);
  }
  public static int getRarityCount(int rarity) {
      int count = 0;
      for (Item item : BuiltInRegistries.ITEM) {
          if (getRarity(item) == rarity) count++;
      }
      return count;
  }
  public static java.util.Map<ResourceLocation, Integer> getAllRarityEntries() {
      java.util.Map<ResourceLocation, Integer> map = new java.util.HashMap<>(AUTO_RARITY_MAP);
      map.putAll(ITEM_RARITY_MAP); // ITEM 覆盖 AUTO，与 1.20.1 一致
      return Collections.unmodifiableMap(map);
  }
  ```
- [ ] **步骤 2：SyncManager 恢复配置版本计数。** 新增字段与方法（放文件顶部字段区）：
  ```java
  private static final java.util.concurrent.atomic.AtomicInteger CONFIG_VERSION = new java.util.concurrent.atomic.AtomicInteger(1);
  public static int getConfigVersion() { return CONFIG_VERSION.get(); }
  public static void bumpConfigVersion() { CONFIG_VERSION.incrementAndGet(); }
  ```
- [ ] **步骤 3：ConfigReloadService.reloadAllConfigs 递增版本。** 在 "批处理队列处理之后、SyncManager 同步之前" 插入 `SyncManager.bumpConfigVersion();`。
- [ ] **步骤 4：编译验证**：`./gradlew compileJava`
- [ ] **步骤 5：提交**：`git add -A && git commit -m "feat(api): RarityRegistry 新增遍历查询族，恢复配置版本计数"`

---

### 任务 3：RarityCoreAPI 改名组（统一 1.20.1 命名）

**文件：**
- 修改：`src/main/java/org/yanbwe/raritycore/api/RarityCoreAPI.java`

**接口：**
- 消费：无
- 产生：无（本任务独立；KubeJS 双绑定自动获得新方法）

- [ ] **步骤 1：getColor → getRarityColor。** 将现有 `getColor(int)`（行 234）改名为 `public static int getRarityColor(int level)`（实现不变），并新增 @Deprecated 转发 `getColor(int)`。
- [ ] **步骤 2：getTexture → getRarityTexture。** 同法。
- [ ] **步骤 3：isComponentRarityControlEnabled → isNbtRarityControlEnabled。** 正式名 `isNbtRarityControlEnabled()`（实现不变），旧名 @Deprecated 转发。
- [ ] **步骤 4：反转逐级开关废弃状态。** 去掉 `isLevelRendererEnabled(int)`/`isLevelTooltipEnabled(int)`/`isLevelNameColorEnabled(int)` 的 @Deprecated；给 `isBorderEnabled(int)`/`isTooltipEnabled(int)`/`isNameColorEnabled(int)` 加 @Deprecated。
- [ ] **步骤 5：补无参 `isNameColorEnabled()`。**
- [ ] **步骤 6：编译验证**，提交。

---

### 任务 4：RarityCoreAPI 常量与探测补齐组

**文件：**
- 修改：`src/main/java/org/yanbwe/raritycore/api/RarityCoreAPI.java`

**接口：**
- 消费：任务 2（getConfigVersion → SyncManager）
- 产生：无

- [ ] **步骤 1：补常量**：MIN_RARITY / MAX_RARITY / DEFAULT_RGB_COLOR / API_VERSION=1400。
- [ ] **步骤 2：补探测**：isAvailable() / getModVersion()（neoforged.fml.ModList）/ getConfigVersion()。
- [ ] **步骤 3：补简单委托**：validateRarity / reloadConfigs / getRarityRgbColor。
- [ ] **步骤 4：编译验证**，提交。

---

### 任务 5：RarityCoreAPI 样式查询/写入补齐组

**文件：**
- 修改：`src/main/java/org/yanbwe/raritycore/api/RarityCoreAPI.java`

**接口：**
- 消费：任务 1（getStarConfig/setTooltipStarRepeatChar/getBorderFallback 等）
- 产生：无

- [ ] **步骤 1：补样式查询族**（7 个）：getTooltipContent / getLevelTranslationKey / getLevelFallbackKey / getStarConfig / isBorderUseTexture / getBorderStyle / getBorderFallback。
- [ ] **步骤 2：补样式写入族**（10 个）：setBorderEnabled / setTooltipEnabled / setTooltipColorEnabled / setNoRaritySkip / setNoRarityDefaultRarity / setBorderUseTexture / setBorderStyle / setTooltipContent / setStarMode / setStarRepeatChar。
- [ ] **步骤 3：编译验证**，提交。

---

### 任务 6：RarityCoreAPI 批量族 + registerRarities

**文件：**
- 修改：`src/main/java/org/yanbwe/raritycore/api/RarityCoreAPI.java`

**接口：**
- 消费：任务 1（beginStyleBatch/endStyleBatch/setStyle/getStyleSnapshot）
- 产生：无

- [ ] **步骤 1：补批量族**：beginStyleBatch / endStyleBatch / setStyle(StylePatch) / getStyleSnapshot(int)。
- [ ] **步骤 2：补 registerRarities**（循环注册 + 统一同步 + 发布 RarityRegistryChangedEvent）。
- [ ] **步骤 3：编译验证**，提交。

---

### 任务 7：整体编译验证 + 人工测试指导

**文件：** 无（验证任务）

- [ ] **步骤 1：全量编译**：`./gradlew build`。
- [ ] **步骤 2：KubeJS 脚本冒烟测试**（人工）：新补 API 返回正确值；废弃名与正式名结果一致；批量族生效。
- [ ] **步骤 3：游戏内命令验证**（人工）：`/raritycore reload` 后 `getConfigVersion()` 递增。
- [ ] **步骤 4：提交**（若发现问题修复后提交）。
