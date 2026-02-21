# RarityCore API 文档

**本文为1.20.1forge的raritycore编写**

## 主要API类

### 1. RarityRegistry (核心注册类)
包路径: `org.yanbwe.raritycore.registry.RarityRegistry`

#### 公共字段
```java
// 物品稀有度映射表
public static final ConcurrentHashMap<ResourceLocation, Integer> ITEM_RARITY_MAP
```

#### 公共方法

##### 稀有度注册与管理
```java
// 注册物品稀有度
public static void register(@Nullable Item item, int rarity, boolean syncToClients)

// 删除物品稀有度注册
public static void unregister(@Nullable Item item, boolean syncToClients)

// 获取物品稀有度等级
public static @NotNull Integer getRarity(@Nullable Item item)

// 获取物品的完整稀有度工具提示字符串（支持本地化）
public static @NotNull String getLocalizedRarityTooltip(@Nullable Item item)

// 获取标准化的物品稀有度（遵循包容性原则）
public static @NotNull Integer getNormalizedRarity(@Nullable Item item)

// 检查物品是否有稀有度配置
public static boolean hasRarity(@Nullable Item item)

// 获取所有已注册的物品稀有度映射
public static Map<ResourceLocation, Integer> getAllRarities()
```

##### 网络同步
```java
// 同步稀有度数据到所有客户端（全量同步）
public static void syncRarityToClients()

// 使用重试机制同步稀有度数据到所有客户端
public static void syncRarityToClientsWithRetry()

// 同步增量变更到所有客户端
public static void syncIncrementalChangesToClients()

// 使用重试机制同步增量变更到所有客户端
public static void syncIncrementalChangesToClientsWithRetry()

// 获取当前变更缓冲区中的操作数量
public static int getPendingChangeCount()

// 清空变更缓冲区
public static void clearChangeBuffer()
```

### 2. RarityColorUtil (颜色工具类)
**包路径**: `org.yanbwe.raritycore.util.RarityColorUtil`

#### 公共方法
```java
// 根据稀有度获取聊天格式颜色
public static ChatFormatting getRarityChatColor(int rarity)

// 根据稀有度获取ARGB颜色值
public static int getRarityArgbColor(int rarity)
```

**稀有度对应颜色表**:
- 1 (普通): 白色 (WHITE) - 0xFFA0A0A0
- 2 (稀有): 绿色 (GREEN) - 0xFF00AA00
- 3 (罕见): 深青色 (DARK_AQUA) - 0xFF00AAAA
- 4 (史诗): 浅紫色 (LIGHT_PURPLE) - 0xFFC870FF
- 5 (传说): 金色 (GOLD) - 0xFFFFAA00
- 6 (神话): 红色 (RED) - 0xFFFF5555
- 7 (唯一): 深红色 (DARK_RED) - 0xFFAA0000

### 3. RarityValidator (验证工具类)
**包路径**: `org.yanbwe.raritycore.util.RarityValidator`

#### 公共方法
```java
// 验证稀有度值是否有效 (1-7)
public static boolean isValidRarity(int rarity)

// 标准化稀有度值 (小于1视为1，大于7视为7)
public static int normalizeRarity(int rarity)

// 验证物品是否有效
public static boolean isValidItem(Item item)

// 获取物品的资源位置标识符
public static ResourceLocation getItemId(Item item)

// 验证边框样式是否有效 (0或1)
public static boolean isValidBorderStyle(int borderStyle)

// 验证并返回有效的边框样式
public static int validateBorderStyle(int borderStyle)
```

### 4. ConfigManager (配置管理类)
**包路径**: `org.yanbwe.raritycore.config.ConfigManager`

#### 公共方法

##### 配置初始化与加载
```java
// 初始化所有配置
public static void initializeConfigs()

// 加载客户端配置
public static void loadClientConfig()

// 保存客户端配置到文件
public static void saveClientConfig()
```

##### 路径获取
```java
// 获取客户端配置路径
public static Path getClientConfigPath()

// 获取配置目录路径
public static Path getConfigDirPath()

// 获取最终稀有度配置路径
public static Path getFinalRarityConfigPath()

// 获取FinalRarityConfig文件夹路径
public static Path getFinalRarityConfigFolderPath()
```

##### 客户端渲染配置
```java
// 物品边框渲染
public static boolean isEnableItemBorderRendering()
public static void setEnableItemBorderRendering(boolean enable)

// 物品边框样式 (0:空心, 1:实心)
public static int getItemBorderStyle()
public static void setItemBorderStyle(int style)

// 纹理边框
public static boolean isUseTextureBorder()
public static void setUseTextureBorder(boolean useTexture)

// 物品名称变色
public static boolean isEnableItemNameColor()
public static void setEnableItemNameColor(boolean enable)

// 工具提示插入
public static boolean isEnableTooltipInsert()
public static void setEnableTooltipInsert(boolean enable)

// 获取是否启用工具提示插入
public static boolean isEnableTooltipInsert()

// 设置是否启用工具提示插入
public static void setEnableTooltipInsert(boolean enable)

// 原版稀有度检查
public static boolean isCheckVanillaRarity()
public static void setCheckVanillaRarity(boolean check)

// 跳过未配置物品
public static boolean isSkipUnconfiguredItems()
public static void setSkipUnconfiguredItems(boolean skip)
```

##### 验证方法
```java
// 验证稀有度值是否有效
public static boolean isValidRarity(int rarity)
```

### 5. EditModeManager (编辑模式管理类)
**包路径**: `org.yanbwe.raritycore.edit.EditModeManager`

#### 公共方法
```java
// 编辑模式控制
public static boolean toggleEditMode()
public static void setEditMode(boolean enabled)
public static boolean isEditModeEnabled()

// 稀有度等级操作
public static void nextRarity()
public static void previousRarity()
public static void setRarity(int rarity)
public static int getCurrentRarity()
public static List<Integer> getAvailableRarities()

// 物品稀有度修改 (仅客户端)
@OnlyIn(Dist.CLIENT)
public static boolean modifyItemRarity(ItemStack itemStack)

// 重置编辑模式状态
public static void reset()
```

### 6. RarityCoreCommands (命令工具类)
**包路径**: `org.yanbwe.raritycore.command.RarityCoreCommands`

#### 公共方法
```java
// 保存稀有度到配置文件 (供外部调用)
public static void saveRarityToConfigPublic(String itemId, int rarity)
```

### 7. SyncBatchManager (同步批处理管理器)
**包路径**: `org.yanbwe.raritycore.network.SyncBatchManager`

#### 公共枚举
```java
// 同步优先级枚举
public enum SyncPriority {
    IMMEDIATE,    // 立即发送
    HIGH,         // 高优先级
    NORMAL,       // 正常优先级
    LOW           // 低优先级
}
```

#### 公共方法
```java
// 添加变更操作到批处理队列（默认正常优先级）
public static boolean addOperation(ChangeOperation operation)

// 添加变更操作到批处理队列（指定优先级）
public static boolean addOperation(ChangeOperation operation, SyncPriority priority)

// 获取并清空待处理的操作列表（按优先级排序）
public static List<ChangeOperation> getAndClearPendingOperations()

// 获取并清空待处理的操作列表
public static List<ChangeOperation> getAndClearPendingOperations(boolean sortByPriority)

// 获取当前待处理操作数量
public static int getPendingOperationCount()

// 清空所有待处理操作
public static void clearAllOperations()

// 合并重复操作以减少网络传输
public static List<ChangeOperation> optimizeOperations(List<ChangeOperation> operations)

// 获取批处理统计信息
public static BatchStats getBatchStats()
```

### 8. NetworkRetryManager (网络重试管理器)
**包路径**: `org.yanbwe.raritycore.network.NetworkRetryManager`

#### 公共方法
```java
// 带重试机制的增量同步包发送
public static void sendIncrementalSyncWithRetry(IncrementalSyncPacket packet)

// 带重试机制的全量同步包发送
public static void sendFullSyncWithRetry(RaritySyncPacket packet)

// 发送包到特定玩家（带重试）
public static <T> void sendToPlayerWithRetry(Object channel, T packet, ServerPlayer player)
```

### 9. DelayedSyncManager (延迟同步管理器)
**包路径**: `org.yanbwe.raritycore.network.DelayedSyncManager`

#### 公共方法
```java
// 调度延迟同步
public static void scheduleDelayedSync()

// 立即执行延迟同步（强制执行）
public static void forceImmediateSync()

// 关闭同步管理器
public static void shutdown()

// 检查是否有待处理的同步操作
public static boolean hasPendingOperations()

// 获取当前同步状态信息
public static SyncStatus getStatus()
```

### 10. NbtRarityMatcher (NBT匹配核心类)
**包路径**: `org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher`

#### 公共方法
```java
// 获取物品的NBT匹配稀有度（带缓存）
public static Integer getNbtMatchedRarity(ItemStack itemStack)

// 直接计算稀有度（不使用缓存，供缓存内部调用）
public static Integer calculateWithoutCache(ItemStack itemStack)

// 注册匹配规则
public static void registerRule(NbtMatchRule rule)

// 为指定物品ID清除所有规则
public static void clearRulesForResource(ResourceLocation itemId)

// 重新加载所有规则
public static void reloadRules()

// 获取当前缓存的规则数量统计
public static Map<ResourceLocation, Integer> getRuleStatistics()

// 验证规则的有效性
public static boolean validateRule(NbtMatchRule rule)

// 获取规则总数
public static int getRuleCount()
```

### 11. NbtMatchRule (NBT匹配规则类)
**包路径**: `org.yanbwe.raritycore.nbtmatching.NbtMatchRule`

#### 公共方法
```java
// 获取物品ID
public ResourceLocation getItemId()

// 获取稀有度等级
public int getRarity()

// 获取优先级
public int getPriority()

// 检查规则是否启用
public boolean isEnabled()

// 检查物品是否匹配此规则
public boolean matches(ItemStack itemStack)

// 获取匹配条件列表
public List<NbtCondition> getConditions()
```

### 13. ConfigManager (配置管理类)
**包路径**: `org.yanbwe.raritycore.config.ConfigManager`
**包路径**: `org.yanbwe.raritycore.util.ConfigFileUtils`

#### 公共方法

##### 配置初始化与加载
```java
// 初始化所有配置
public static void initializeConfigs()

// 加载客户端配置
public static void loadClientConfig()

// 保存客户端配置到文件
public static void saveClientConfig()
```

##### 路径获取
```java
// 获取客户端配置路径
public static Path getClientConfigPath()

// 获取配置目录路径
public static Path getConfigDirPath()

// 获取最终稀有度配置路径
public static Path getFinalRarityConfigPath()

// 获取FinalRarityConfig文件夹路径
public static Path getFinalRarityConfigFolderPath()
```

##### 客户端渲染配置
```java
// 物品边框渲染
public static boolean isEnableItemBorderRendering()
public static void setEnableItemBorderRendering(boolean enable)

// 物品边框样式 (0:空心, 1:实心)
public static int getItemBorderStyle()
public static void setItemBorderStyle(int style)

// 纹理边框
public static boolean isUseTextureBorder()
public static void setUseTextureBorder(boolean useTexture)

// 物品名称变色
public static boolean isEnableItemNameColor()
public static void setEnableItemNameColor(boolean enable)

// 工具提示插入
public static boolean isEnableTooltipInsert()
public static void setEnableTooltipInsert(boolean enable)

// 原版稀有度检查
public static boolean isCheckVanillaRarity()
public static void setCheckVanillaRarity(boolean check)

// 跳过未配置物品
public static boolean isSkipUnconfiguredItems()
public static void setSkipUnconfiguredItems(boolean skip)

// 缓存系统
public static boolean isEnableCacheSystem()
public static void setEnableCacheSystem(boolean enable)
```

##### 验证方法
```java
// 验证稀有度值是否有效
public static boolean isValidRarity(int rarity)
```

### 14. EditModeManager (编辑模式管理类)
**包路径**: `org.yanbwe.raritycore.edit.EditModeManager`

#### 公共方法
```java
// 编辑模式控制
public static boolean toggleEditMode()
public static void setEditMode(boolean enabled)
public static boolean isEditModeEnabled()

// 稀有度等级操作
public static void nextRarity()
public static void previousRarity()
public static void setRarity(int rarity)
public static int getCurrentRarity()
public static List<Integer> getAvailableRarities()

// 物品稀有度修改 (仅客户端)
@OnlyIn(Dist.CLIENT)
public static boolean modifyItemRarity(ItemStack itemStack)

// 重置编辑模式状态
public static void reset()
```

### 15. RarityCoreCommands (命令工具类)
**包路径**: `org.yanbwe.raritycore.command.RarityCoreCommands`

#### 公共方法
```java
// 保存稀有度到配置文件 (供外部调用)
public static void saveRarityToConfigPublic(String itemId, int rarity)
```

### 16. SyncBatchManager (同步批处理管理器)
**包路径**: `org.yanbwe.raritycore.network.SyncBatchManager`

#### 公共枚举
```java
// 同步优先级枚举
public enum SyncPriority {
    IMMEDIATE,    // 立即发送
    HIGH,         // 高优先级
    NORMAL,       // 正常优先级
    LOW           // 低优先级
}
```

#### 公共方法
```java
// 添加变更操作到批处理队列（默认正常优先级）
public static boolean addOperation(ChangeOperation operation)

// 添加变更操作到批处理队列（指定优先级）
public static boolean addOperation(ChangeOperation operation, SyncPriority priority)

// 获取并清空待处理的操作列表（按优先级排序）
public static List<ChangeOperation> getAndClearPendingOperations()

// 获取并清空待处理的操作列表
public static List<ChangeOperation> getAndClearPendingOperations(boolean sortByPriority)

// 获取当前待处理操作数量
public static int getPendingOperationCount()

// 清空所有待处理操作
public static void clearAllOperations()

// 合并重复操作以减少网络传输
public static List<ChangeOperation> optimizeOperations(List<ChangeOperation> operations)

// 获取批处理统计信息
public static BatchStats getBatchStats()
```

### 17. NetworkRetryManager (网络重试管理器)
**包路径**: `org.yanbwe.raritycore.network.NetworkRetryManager`

#### 公共方法
```java
// 带重试机制的增量同步包发送
public static void sendIncrementalSyncWithRetry(IncrementalSyncPacket packet)

// 带重试机制的全量同步包发送
public static void sendFullSyncWithRetry(RaritySyncPacket packet)

// 发送包到特定玩家（带重试）
public static <T> void sendToPlayerWithRetry(Object channel, T packet, ServerPlayer player)
```

### 18. DelayedSyncManager (延迟同步管理器)
**包路径**: `org.yanbwe.raritycore.network.DelayedSyncManager`

#### 公共方法
```java
// 调度延迟同步
public static void scheduleDelayedSync()

// 立即执行延迟同步（强制执行）
public static void forceImmediateSync()

// 关闭同步管理器
public static void shutdown()

// 检查是否有待处理的同步操作
public static boolean hasPendingOperations()

// 获取当前同步状态信息
public static SyncStatus getStatus()
```

### 19. ImprovedRenderCacheManager (改进的渲染缓存管理器)
**包路径**: `org.yanbwe.raritycore.client.ImprovedRenderCacheManager`

#### 公共方法
```java
// 智能预加载缓存
public static void smartPreloadCache()

// 获取缓存统计信息
public static CacheStats getCacheStats()

// 缓存健康检查
public static void checkCacheHealth()

// 获取物品栈的缓存稀有度
public static Integer getCachedItemStackRarity(ItemStack itemStack)

// 缓存物品栈稀有度
public static void cacheItemStackRarity(ItemStack itemStack, Integer rarity)

// 智能清理缓存
public static void smartCleanup()
```

#### 缓存统计类
```java
public static class CacheStats {
    public long getHits()
    public long getMisses()
    public long getClears()
    public long getRarityCacheSize()
    public long getItemStackCacheSize()
    public double getHitRate()
}
```

### 20. ConfigFileUtils (配置文件工具类)
**包路径**: `org.yanbwe.raritycore.util.ConfigFileUtils`

#### 公共方法
```java
// 确保目录存在
public static boolean ensureDirectoryExists(Path directoryPath, String operationName)

// 读取JSON配置文件
public static JsonObject readJsonConfig(Path configFile, String operationName)

// 写入JSON配置文件
public static boolean writeJsonConfig(Path configFile, JsonObject jsonObject, String operationName)

// 安全地读取并更新JSON配置文件
public static boolean updateJsonConfig(Path configFile, JsonUpdater updater, String operationName)
```

#### 接口
```java
// JSON更新器函数式接口
@FunctionalInterface
public interface JsonUpdater {
    void update(JsonObject jsonObject) throws Exception;
}
```

## 使用示例

### 6. NBT匹配系统使用
```java
import org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher;
import org.yanbwe.raritycore.nbtmatching.NbtMatchRule;
import net.minecraft.world.item.ItemStack;

// 获取物品的NBT匹配稀有度
ItemStack enchantedSword = player.getMainHandItem();
Integer nbtRarity = NbtRarityMatcher.getNbtMatchedRarity(enchantedSword);

// 创建自定义匹配规则
NbtMatchRule customRule = new NbtMatchRule();
// 配置规则...
NbtRarityMatcher.registerRule(customRule);

// 重新加载所有NBT规则
NbtRarityMatcher.reloadRules();
```

### 7. 缓存系统使用
```java
import org.yanbwe.raritycore.client.ImprovedRenderCacheManager;

// 智能预加载缓存
ImprovedRenderCacheManager.smartPreloadCache();

// 获取缓存统计信息
ImprovedRenderCacheManager.CacheStats stats = ImprovedRenderCacheManager.getCacheStats();
System.out.println("缓存命中率: " + stats.getHitRate() + "%");

// 手动清理缓存
ImprovedRenderCacheManager.smartCleanup();
```

### 1. 基本稀有度查询
```java
import org.yanbwe.raritycore.registry.RarityRegistry;
import net.minecraft.world.item.Items;

// 获取物品稀有度
Item diamond = Items.DIAMOND;
Integer rarity = RarityRegistry.getRarity(diamond);
if (rarity != null) {
    System.out.println("Diamond rarity: " + rarity); // 输出: 4
}
```

### 2. 颜色获取
```java
import org.yanbwe.raritycore.util.RarityColorUtil;

// 获取稀有度对应的颜色
int rarityLevel = 5; // 传说级别
ChatFormatting chatColor = RarityColorUtil.getRarityChatColor(rarityLevel);
int argbColor = RarityColorUtil.getRarityArgbColor(rarityLevel);
```

### 3. 配置管理
```java
import org.yanbwe.raritycore.config.ConfigManager;

// 检查边框渲染是否启用
if (ConfigManager.isEnableItemBorderRendering()) {
    // 边框渲染已启用
}

// 修改配置
ConfigManager.setEnableItemNameColor(false);
ConfigManager.saveClientConfig(); // 保存到文件
```

### 4. 编辑模式操作
```java
import org.yanbwe.raritycore.edit.EditModeManager;
import net.minecraft.world.item.ItemStack;

// 启用编辑模式并设置稀有度
EditModeManager.setEditMode(true);
EditModeManager.setRarity(6); // 设置为神话级别

// 修改物品稀有度 (客户端环境)
ItemStack itemStack = player.getMainHandItem();
boolean success = EditModeManager.modifyItemRarity(itemStack);
```

### 5. 配置文件操作
```java
import org.yanbwe.raritycore.util.ConfigFileUtils;
import com.google.gson.JsonObject;

// 安全更新配置文件
Path configFile = Paths.get("config/example.json");
ConfigFileUtils.updateJsonConfig(configFile, jsonObject -> {
    jsonObject.addProperty("custom_setting", "value");
}, "Example Operation");
```

## 注意事项

1. `EditModeManager.modifyItemRarity()` 只能在客户端环境调用；
2. 稀有度变更会自动同步到所有客户端；
3. 配置更改后需要调用 `saveClientConfig()` 或 `saveServerConfig()` 才会保存到文件；
4. `getLocalizedRarityTooltip()` 方法返回已本地化的完整工具提示字符串，可直接用于显示；
5. 配置文件在模组版本变更时会自动进行版本控制和升级；
6. 使用 `/raritycore config version` 查看当前配置版本信息；
7. 使用 `/raritycore config upgrade` 强制升级配置文件；
8. NBT匹配系统具有最高优先级，会覆盖其他稀有度来源；
9. 缓存系统默认启用，可通过配置文件禁用；
10. NBT规则支持复杂的条件匹配，包括等于、包含、范围等条件。

## 依赖关系

要使用这些API，需要在 `mods.toml` 中添加依赖：
```toml
[[dependencies.your_mod]]
    modId="raritycore"
    mandatory=true
    versionRange="[1.0,)"
    ordering="AFTER"
    side="BOTH"
```
