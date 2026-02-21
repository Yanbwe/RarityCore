# RarityCore API Documentation

**This documentation is written for RarityCore 1.20.1 Forge**

## Main API Classes

### 1. RarityRegistry (Core Registry Class)
**Package Path**: `org.yanbwe.raritycore.registry.RarityRegistry`

#### Public Fields
```java
// Item rarity mapping table (thread-safe)
public static final ConcurrentHashMap<ResourceLocation, Integer> ITEM_RARITY_MAP
```

#### Public Methods

##### Rarity Registration and Management
```java
// Register item rarity
public static void register(@Nullable Item item, int rarity, boolean syncToClients)

// Unregister item rarity
public static void unregister(@Nullable Item item, boolean syncToClients)

// Get item rarity level
public static @NotNull Integer getRarity(@Nullable Item item)

// Get normalized item rarity (following inclusivity principle)
public static @NotNull Integer getNormalizedRarity(@Nullable Item item)

// Get localized rarity tooltip string
public static @NotNull String getLocalizedRarityTooltip(@Nullable Item item)

// Check if item has rarity configuration
public static boolean hasRarity(@Nullable Item item)

// Get all registered item rarity mappings
public static Map<ResourceLocation, Integer> getAllRarities()
```

##### Network Synchronization
```java
// Synchronize rarity data to all clients
public static void syncRarityToClients()

// Synchronize rarity data to all clients with retry mechanism
public static void syncRarityToClientsWithRetry()

// Synchronize incremental changes to all clients
public static void syncIncrementalChangesToClients()

// Synchronize incremental changes to all clients with retry mechanism
public static void syncIncrementalChangesToClientsWithRetry()

// Get current pending change count
public static int getPendingChangeCount()

// Clear change buffer
public static void clearChangeBuffer()
```

### 2. RarityColorUtil (Color Utility Class)
**Package Path**: `org.yanbwe.raritycore.util.RarityColorUtil`

#### Public Methods
```java
// Get chat formatting color by rarity
public static ChatFormatting getRarityChatColor(int rarity)

// Get ARGB color value by rarity
public static int getRarityArgbColor(int rarity)
```

**Rarity Color Mapping**:
- 1 (Common): White (WHITE) - 0xFFA0A0A0
- 2 (Uncommon): Green (GREEN) - 0xFF00AA00
- 3 (Rare): Dark Aqua (DARK_AQUA) - 0xFF00AAAA
- 4 (Epic): Light Purple (LIGHT_PURPLE) - 0xFFC870FF
- 5 (Legendary): Gold (GOLD) - 0xFFFFAA00
- 6 (Mythical): Red (RED) - 0xFFFF5555
- 7 (Unique): Dark Red (DARK_RED) - 0xFFAA0000

### 3. RarityValidator (Validation Utility Class)
**Package Path**: `org.yanbwe.raritycore.util.RarityValidator`

#### Public Methods
```java
// Validate if rarity value is valid (1-7)
public static boolean isValidRarity(int rarity)

// Normalize rarity value (values < 1 become 1, values > 7 become 7)
public static int normalizeRarity(int rarity)

// Validate if item is valid
public static boolean isValidItem(Item item)

// Get item resource location identifier
public static ResourceLocation getItemId(Item item)

// Validate if border style is valid (0 or 1)
public static boolean isValidBorderStyle(int borderStyle)

// Validate and return valid border style
public static int validateBorderStyle(int borderStyle)
```

### 13. ConfigManager (Configuration Manager Class)
**Package Path**: `org.yanbwe.raritycore.config.ConfigManager`

#### Public Methods

##### Configuration Initialization and Loading
```java
// Initialize all configurations
public static void initializeConfigs()

// Load client configuration
public static void loadClientConfig()

// Save client configuration to file
public static void saveClientConfig()
```

##### Path Retrieval
```java
// Get client configuration path
public static Path getClientConfigPath()

// Get configuration directory path
public static Path getConfigDirPath()

// Get final rarity configuration path
public static Path getFinalRarityConfigPath()

// Get FinalRarityConfig folder path
public static Path getFinalRarityConfigFolderPath()
```

##### Client Rendering Configuration
```java
// Item border rendering
public static boolean isEnableItemBorderRendering()
public static void setEnableItemBorderRendering(boolean enable)

// Item border style (0: hollow, 1: solid)
public static int getItemBorderStyle()
public static void setItemBorderStyle(int style)

// Texture border
public static boolean isUseTextureBorder()
public static void setUseTextureBorder(boolean useTexture)

// Item name coloring
public static boolean isEnableItemNameColor()
public static void setEnableItemNameColor(boolean enable)

// Tooltip insertion
public static boolean isEnableTooltipInsert()
public static void setEnableTooltipInsert(boolean enable)

// Get whether tooltip insertion is enabled
public static boolean isEnableTooltipInsert()

// Set whether tooltip insertion is enabled
public static void setEnableTooltipInsert(boolean enable)

// Vanilla rarity check
public static boolean isCheckVanillaRarity()
public static void setCheckVanillaRarity(boolean check)

// Skip unconfigured items
public static boolean isSkipUnconfiguredItems()
public static void setSkipUnconfiguredItems(boolean skip)
```

##### Validation Methods
```java
// Validate if rarity value is valid
public static boolean isValidRarity(int rarity)
```

### 14. EditModeManager (Edit Mode Manager Class)
**Package Path**: `org.yanbwe.raritycore.edit.EditModeManager`

#### Public Methods
```java
// Edit mode control
public static boolean toggleEditMode()
public static void setEditMode(boolean enabled)
public static boolean isEditModeEnabled()

// Rarity level operations
public static void nextRarity()
public static void previousRarity()
public static void setRarity(int rarity)
public static int getCurrentRarity()
public static List<Integer> getAvailableRarities()

// Modify item rarity (client-side only)
@OnlyIn(Dist.CLIENT)
public static boolean modifyItemRarity(ItemStack itemStack)

// Reset edit mode status
public static void reset()
```

### 15. RarityCoreCommands (Command Utility Class)
**Package Path**: `org.yanbwe.raritycore.command.RarityCoreCommands`

#### Public Methods
```java
// Save rarity to configuration file (for external calls)
public static void saveRarityToConfigPublic(String itemId, int rarity)
```

### 16. SyncBatchManager (Synchronization Batch Manager)
**Package Path**: `org.yanbwe.raritycore.network.SyncBatchManager`

#### Public Enums
```java
// Synchronization priority enumeration
public enum SyncPriority {
    IMMEDIATE,    // Send immediately
    HIGH,         // High priority
    NORMAL,       // Normal priority
    LOW           // Low priority
}
```

#### Public Methods
```java
// Add change operation to batch queue (default normal priority)
public static boolean addOperation(ChangeOperation operation)

// Add change operation to batch queue (specified priority)
public static boolean addOperation(ChangeOperation operation, SyncPriority priority)

// Get and clear pending operations list (sorted by priority)
public static List<ChangeOperation> getAndClearPendingOperations()

// Get and clear pending operations list
public static List<ChangeOperation> getAndClearPendingOperations(boolean sortByPriority)

// Get current pending operation count
public static int getPendingOperationCount()

// Clear all pending operations
public static void clearAllOperations()

// Merge duplicate operations to reduce network transmission
public static List<ChangeOperation> optimizeOperations(List<ChangeOperation> operations)

// Get batch statistics
public static BatchStats getBatchStats()
```

### 17. NetworkRetryManager (Network Retry Manager)
**Package Path**: `org.yanbwe.raritycore.network.NetworkRetryManager`

#### Public Methods
```java
// Send incremental sync packet with retry mechanism
public static void sendIncrementalSyncWithRetry(IncrementalSyncPacket packet)

// Send full sync packet with retry mechanism
public static void sendFullSyncWithRetry(RaritySyncPacket packet)

// Send packet to specific player (with retry)
public static <T> void sendToPlayerWithRetry(Object channel, T packet, ServerPlayer player)
```

### 18. DelayedSyncManager (Delayed Synchronization Manager)
**Package Path**: `org.yanbwe.raritycore.network.DelayedSyncManager`

#### Public Methods
```java
// Schedule delayed synchronization
public static void scheduleDelayedSync()

// Force immediate execution of delayed sync
public static void forceImmediateSync()

// Shutdown synchronization manager
public static void shutdown()

// Check if there are pending synchronization operations
public static boolean hasPendingOperations()

// Get current synchronization status information
public static SyncStatus getStatus()
```

### 10. NbtRarityMatcher (NBT Matching Core Class)
**Package Path**: `org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher`

#### Public Methods
```java
// Get item's NBT matched rarity (with cache)
public static Integer getNbtMatchedRarity(ItemStack itemStack)

// Calculate rarity directly (without cache, for internal cache use)
public static Integer calculateWithoutCache(ItemStack itemStack)

// Register matching rule
public static void registerRule(NbtMatchRule rule)

// Clear all rules for specified item ID
public static void clearRulesForResource(ResourceLocation itemId)

// Reload all rules
public static void reloadRules()

// Get current cached rule statistics
public static Map<ResourceLocation, Integer> getRuleStatistics()

// Validate rule validity
public static boolean validateRule(NbtMatchRule rule)

// Get total rule count
public static int getRuleCount()
```

### 11. NbtMatchRule (NBT Matching Rule Class)
**Package Path**: `org.yanbwe.raritycore.nbtmatching.NbtMatchRule`

#### Public Methods
```java
// Get item ID
public ResourceLocation getItemId()

// Get rarity level
public int getRarity()

// Get priority
public int getPriority()

// Check if rule is enabled
public boolean isEnabled()

// Check if item matches this rule
public boolean matches(ItemStack itemStack)

// Get matching conditions list
public List<NbtCondition> getConditions()
```

### 19. ImprovedRenderCacheManager (Enhanced Render Cache Manager)
**Package Path**: `org.yanbwe.raritycore.client.ImprovedRenderCacheManager`

#### Public Methods
```java
// Smart cache preloading
public static void smartPreloadCache()

// Get cache statistics
public static CacheStats getCacheStats()

// Cache health check
public static void checkCacheHealth()

// Get cached item stack rarity
public static Integer getCachedItemStackRarity(ItemStack itemStack)

// Cache item stack rarity
public static void cacheItemStackRarity(ItemStack itemStack, Integer rarity)

// Smart cache cleanup
public static void smartCleanup()
```

#### Cache Statistics Class
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

### 20. ConfigFileUtils (Configuration File Utility Class)
**Package Path**: `org.yanbwe.raritycore.util.ConfigFileUtils`

#### Public Methods
```java
// Ensure directory exists
public static boolean ensureDirectoryExists(Path directoryPath, String operationName)

// Read JSON configuration file
public static JsonObject readJsonConfig(Path configFile, String operationName)

// Write JSON configuration file
public static boolean writeJsonConfig(Path configFile, JsonObject jsonObject, String operationName)

// Safely read and update JSON configuration file
public static boolean updateJsonConfig(Path configFile, JsonUpdater updater, String operationName)
```

#### Interface
```java
// JSON updater functional interface
@FunctionalInterface
public interface JsonUpdater {
    void update(JsonObject jsonObject) throws Exception;
}
```

## Usage Examples

### 6. NBT Matching System Usage
```java
import org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher;
import org.yanbwe.raritycore.nbtmatching.NbtMatchRule;
import net.minecraft.world.item.ItemStack;

// Get item's NBT matched rarity
ItemStack enchantedSword = player.getMainHandItem();
Integer nbtRarity = NbtRarityMatcher.getNbtMatchedRarity(enchantedSword);

// Create custom matching rule
NbtMatchRule customRule = new NbtMatchRule();
// Configure rule...
NbtRarityMatcher.registerRule(customRule);

// Reload all NBT rules
NbtRarityMatcher.reloadRules();
```

### 7. Cache System Usage
```java
import org.yanbwe.raritycore.client.ImprovedRenderCacheManager;

// Smart cache preloading
ImprovedRenderCacheManager.smartPreloadCache();

// Get cache statistics
ImprovedRenderCacheManager.CacheStats stats = ImprovedRenderCacheManager.getCacheStats();
System.out.println("Cache hit rate: " + stats.getHitRate() + "%");

// Manual cache cleanup
ImprovedRenderCacheManager.smartCleanup();
```

### 1. Basic Rarity Query
```java
import org.yanbwe.raritycore.registry.RarityRegistry;
import net.minecraft.world.item.Items;

// Get item rarity
Item diamond = Items.DIAMOND;
Integer rarity = RarityRegistry.getRarity(diamond);
if (rarity != null) {
    System.out.println("Diamond rarity: " + rarity); // Output: 4
}
```

### 2. Color Retrieval
```java
import org.yanbwe.raritycore.util.RarityColorUtil;

// Get color corresponding to rarity
int rarityLevel = 5; // Legendary level
ChatFormatting chatColor = RarityColorUtil.getRarityChatColor(rarityLevel);
int argbColor = RarityColorUtil.getRarityArgbColor(rarityLevel);
```

### 3. Configuration Management
```java
import org.yanbwe.raritycore.config.ConfigManager;

// Check if border rendering is enabled
if (ConfigManager.isEnableItemBorderRendering()) {
    // Border rendering is enabled
}

// Modify configuration
ConfigManager.setEnableItemNameColor(false);
ConfigManager.saveClientConfig(); // Save to file
```

### 4. Edit Mode Operations
```java
import org.yanbwe.raritycore.edit.EditModeManager;
import net.minecraft.world.item.ItemStack;

// Enable edit mode and set rarity
EditModeManager.setEditMode(true);
EditModeManager.setRarity(6); // Set to Mythical level

// Modify item rarity (client environment)
ItemStack itemStack = player.getMainHandItem();
boolean success = EditModeManager.modifyItemRarity(itemStack);
```

### 5. Configuration File Operations
```java
import org.yanbwe.raritycore.util.ConfigFileUtils;
import com.google.gson.JsonObject;

// Safely update configuration file
Path configFile = Paths.get("config/example.json");
ConfigFileUtils.updateJsonConfig(configFile, jsonObject -> {
    jsonObject.addProperty("custom_setting", "value");
}, "Example Operation");
```

## Important Notes

1. `EditModeManager.modifyItemRarity()` can only be called in client environment
2. Rarity changes are automatically synchronized to all clients
3. Configuration changes require calling `saveClientConfig()` or `saveServerConfig()` to persist to file
4. `getLocalizedRarityTooltip()` returns fully localized tooltip strings ready for display
5. Star display is now permanently enabled and cannot be configured. The default star emoji "⭐" will always be displayed
6. Configuration files are automatically versioned and upgraded when the mod version changes
7. Use `/raritycore config version` to check current configuration version information
8. Use `/raritycore config upgrade` to force configuration file upgrades
9. NBT matching system has highest priority and will override other rarity sources
10. Cache system is enabled by default and can be disabled through configuration files
11. NBT rules support complex condition matching including equals, contains, range conditions

## Dependencies

To use these APIs, add the following dependency in `mods.toml`:
```toml
[[dependencies.your_mod]]
    modId="raritycore"
    mandatory=true
    versionRange="[1.0,)"
    ordering="AFTER"
    side="BOTH"
```

#