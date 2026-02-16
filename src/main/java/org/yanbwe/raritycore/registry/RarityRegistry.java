package org.yanbwe.raritycore.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.yanbwe.raritycore.RarityCore;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.network.ChangeOperation;
import org.yanbwe.raritycore.network.IncrementalSyncPacket;
import org.yanbwe.raritycore.network.RaritySyncPacket;
import org.yanbwe.raritycore.util.RarityConstants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class RarityRegistry {
    /**
     * 物品稀有度映射
     */
    public static final ConcurrentHashMap<ResourceLocation, Integer> ITEM_RARITY_MAP = new ConcurrentHashMap<>();
    
    /**
     * 变更操作缓冲区
     */
    private static final List<ChangeOperation> CHANGE_OPERATIONS_BUFFER = new ArrayList<>();

    /**
     * 注册物品的稀有度等级
     * 1普通，2稀有，3罕见，4史诗，5传说，6神话，7唯一
     * 不注册视为普通品质
     * @param item 要注册稀有度的物品
     * @param rarity 稀有度等级
     */
    public static void register(@Nullable Item item, int rarity) {
        register(item, rarity, true);
    }
    
    /**
     * 注册物品的稀有度等级
     * 1普通，2稀有，3罕见，4史诗，5传说，6神话，7唯一
     * 不注册视为普通品质
     * @param item 要注册稀有度的物品
     * @param rarity 稀有度等级
     * @param syncToClients 是否同步到客户端
     */
    public static void register(@Nullable Item item, int rarity, boolean syncToClients) {
        if (item != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                Integer oldRarity = ITEM_RARITY_MAP.put(itemId, rarity);
                
                // 发布稀有度变更事件
                RarityChangeEvent.ChangeType changeType = (oldRarity == null) ? 
                    RarityChangeEvent.ChangeType.REGISTER : RarityChangeEvent.ChangeType.UPDATE;
                MinecraftForge.EVENT_BUS.post(new RarityChangeEvent(item, oldRarity, rarity, changeType));
                
                // 如果需要同步到客户端且当前在服务端环境中，记录变更操作
                if (syncToClients) {
                    // 记录变更操作
                    if (oldRarity == null) {
                        // 新增操作
                        CHANGE_OPERATIONS_BUFFER.add(new ChangeOperation(ChangeOperation.OperationType.ADD, itemId, rarity));
                    } else {
                        // 更新操作
                        CHANGE_OPERATIONS_BUFFER.add(new ChangeOperation(ChangeOperation.OperationType.UPDATE, itemId, rarity));
                    }
                    
                    // 同步到客户端
                    syncRarityToClients();
                }
            }
        }
    }
    
    /**
     * 删除物品的稀有度注册
     * @param item 要删除稀有度注册的物品
     * @param syncToClients 是否同步到客户端
     */
    public static void unregister(@Nullable Item item, boolean syncToClients) {
        if (item != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                Integer removedRarity = ITEM_RARITY_MAP.remove(itemId);
                
                // 发布稀有度变更事件
                if (removedRarity != null) {
                    MinecraftForge.EVENT_BUS.post(new RarityChangeEvent(
                        item, removedRarity, null, RarityChangeEvent.ChangeType.REMOVE));
                }
                
                // 如果需要同步到客户端且当前在服务端环境中，记录删除操作
                if (syncToClients) {
                    // 记录删除操作
                    if (removedRarity != null) {
                        CHANGE_OPERATIONS_BUFFER.add(new ChangeOperation(ChangeOperation.OperationType.DELETE, itemId, null));
                    }
                    
                    // 同步到客户端
                    syncRarityToClients();
                }
            }
        }
    }
    
    /**
     * 将所有稀有度数据同步到客户端（全量同步）
     */
    public static void syncRarityToClients() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // 创建包含当前所有数据的映射
            java.util.Map<ResourceLocation, Integer> currentData = new java.util.HashMap<>(ITEM_RARITY_MAP);
            RaritySyncPacket packet = new RaritySyncPacket(currentData);
            
            // 发送到所有在线玩家
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                RaritySyncPacket.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }
    
    /**
     * 将增量变更同步到客户端
     */
    public static void syncIncrementalChangesToClients() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !CHANGE_OPERATIONS_BUFFER.isEmpty()) {
            // 创建包含变更操作的增量同步包
            IncrementalSyncPacket packet = new IncrementalSyncPacket(new ArrayList<>(CHANGE_OPERATIONS_BUFFER));
            
            // 清空缓冲区
            CHANGE_OPERATIONS_BUFFER.clear();
            
            // 发送到所有在线玩家
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                IncrementalSyncPacket.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }
    
    /**
     * 获取当前变更缓冲区中的操作数量
     */
    public static int getPendingChangeCount() {
        return CHANGE_OPERATIONS_BUFFER.size();
    }
    
    /**
     * 清空变更缓冲区
     */
    public static void clearChangeBuffer() {
        CHANGE_OPERATIONS_BUFFER.clear();
    }

    /**
     * 获取物品的稀有度等级（标准化版本）
     * 遵循模组的包容性原则：小于1的值视为1，大于7的值视为7
     * @param item 要查稀有度的物品
     * @return 标准化后的物品稀有度等级（1-7）
     */
    public static @NotNull Integer getNormalizedRarity(@Nullable Item item) {
        Integer rawRarity = getRarity(item);
        return org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rawRarity);
    }
    
    /**
     * 获取本地化文本
     * @param key 本地化键
     * @return 本地化文本
     */
    private static String getLocalizedText(String key) {
        // 直接使用和原版工具提示系统一样的方式
        return net.minecraft.client.resources.language.I18n.get(key);
    }
    
    /**
     * 获取物品的完整稀有度工具提示字符串（支持本地化）
     * 返回格式示例：
     * - 普通物品："[普通] ⭐" (中文) 或 "[Common] ⭐" (英文)
     * - 高级物品："[5级稀有度-⭐⭐⭐⭐⭐]"
     * @param item 要获取工具提示的物品
     * @return 本地化的稀有度工具提示字符串
     */
    public static @NotNull String getLocalizedRarityTooltip(@Nullable Item item) {
        if (item == null) {
            return "[普通]"; // 默认返回普通稀有度
        }
        
        // 获取物品稀有度
        Integer rarity = getRarity(item);
        if (rarity == null) {
            rarity = RarityConstants.RARITY_COMMON;
        }
        
        // 标准化稀有度值
        rarity = org.yanbwe.raritycore.util.RarityValidator.normalizeRarity(rarity);
        
        // 构建工具提示字符串
        if (rarity > RarityConstants.RARITY_UNIQUE) {
            // 特殊稀有度（大于7级）
            String stars = org.yanbwe.raritycore.util.ComponentBuilder.getStars(rarity);
            return "[" + rarity + "级稀有度-" + stars + "]";
        } else {
            // 标准稀有度（1-7级）
            String rarityKey;
            switch (rarity) {
                case RarityConstants.RARITY_COMMON:
                    rarityKey = "rarity.core.common";
                    break;
                case RarityConstants.RARITY_UNCOMMON:
                    rarityKey = "rarity.core.uncommon";
                    break;
                case RarityConstants.RARITY_RARE:
                    rarityKey = "rarity.core.rare";
                    break;
                case RarityConstants.RARITY_EPIC:
                    rarityKey = "rarity.core.epic";
                    break;
                case RarityConstants.RARITY_LEGENDARY:
                    rarityKey = "rarity.core.legendary";
                    break;
                case RarityConstants.RARITY_MYTHICAL:
                    rarityKey = "rarity.core.mythical";
                    break;
                case RarityConstants.RARITY_UNIQUE:
                    rarityKey = "rarity.core.unique";
                    break;
                default:
                    rarityKey = "rarity.core.common";
                    break;
            }
            
            // 获取本地化文本
            String localizedLabel = net.minecraft.client.resources.language.I18n.get(rarityKey);
            String stars = org.yanbwe.raritycore.util.ComponentBuilder.getStars(rarity);
            return localizedLabel + " " + stars;
        }
    }
    
    /**
     * 获取物品栈的稀有度等级（支持NBT数据）
     * 优先级顺序：神化模组稀有度 > 原版稀有度 > 本模组稀有度（配置和数据包）
     * @param itemStack 要查稀有度的物品栈
     * @return 物品的稀有度等级（1-7）
     */
    public static @NotNull Integer getRarity(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return 1;
        }
        
        Item item = itemStack.getItem();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null || itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
            return 1;
        }
        
        // 使用统一的稀有度获取逻辑
        return getRarityInternal(itemId, itemStack, item);
    }
    
    /**
     * 统一的稀有度获取逻辑
     * 优先级顺序：NBT匹配 > 神化模组稀有度 > 本模组稀有度（配置和数据包） > 原版稀有度映射
     * @param itemId 物品资源位置
     * @param itemStack 物品栈（用于检查NBT数据）
     * @param item 物品
     * @return 物品的稀有度等级（1-7）
     */
    private static @NotNull Integer getRarityInternal(ResourceLocation itemId, @Nullable ItemStack itemStack, Item item) {
        // 首先检查NBT匹配配置（最高优先级）
        if (itemStack != null && itemStack.hasTag()) {
            Integer nbtMatchedRarity = org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher.getNbtMatchedRarity(itemStack);
            if (nbtMatchedRarity != null) {
                // RarityCore.LOGGER.debug("物品 {} 使用NBT匹配稀有度: {}", itemId, nbtMatchedRarity);
                return nbtMatchedRarity;
            }
        }
        
        // 然后检查神化模组稀有度
        if (org.yanbwe.raritycore.config.ServerConfigManager.isCheckApotheosisRarity() && itemStack != null) {
            boolean hasApothRarity = org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter.hasApotheosisRarity(itemStack);
            // 减少神化稀有度检查的日志输出，只在必要时记录
            // RarityCore.LOGGER.debug("物品 {} 是否具有神化稀有度: {}", itemId, hasApothRarity);
            
            Integer apothRarity = org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter.getMappedRarity(itemStack);
            if (apothRarity != null) {
                // RarityCore.LOGGER.debug("物品 {} 使用神化稀有度: {}", itemId, apothRarity);
                return apothRarity;
            } else {
                // 只对特定物品记录映射失败（避免大量日志）
                if (itemId.toString().contains("dragon_egg") || itemId.toString().contains("slime_ball")) {
                    RarityCore.LOGGER.trace("物品 {} 神化稀有度映射失败", itemId);
                }
            }
        } else {
            RarityCore.LOGGER.debug("神化稀有度检查已禁用或物品栈为空");
        }
        
        // 然后检查本模组的稀有度配置（包括配置文件和数据包）
        Integer configuredRarity = ITEM_RARITY_MAP.get(itemId);
        if (configuredRarity != null) {
            // RarityCore.LOGGER.debug("物品 {} 使用本模组稀有度: {}", itemId, configuredRarity);
            return configuredRarity;
        }
        
        // 最后检查原版稀有度映射（最低优先级）
        if (org.yanbwe.raritycore.config.ServerConfigManager.isCheckVanillaRarity()) {
            net.minecraft.world.item.Rarity vanillaRarity = itemStack != null ? itemStack.getRarity() : item.getDefaultInstance().getRarity();
            Integer mappedVanilla = mapVanillaRarity(vanillaRarity);
            if (mappedVanilla > 1) { // 只有当原版稀有度不是普通时才使用
                // RarityCore.LOGGER.debug("物品 {} 使用原版稀有度映射: {} (原版: {})", 
                //     itemId, mappedVanilla, vanillaRarity);
                return mappedVanilla;
            }
        }
        
        // 默认返回普通稀有度
        // RarityCore.LOGGER.debug("物品 {} 使用默认稀有度: 1", itemId);
        return 1;
    }
    
    /**
     * 映射原版稀有度到本模组稀有度
     * @param vanillaRarity 原版稀有度
     * @return 映射后的稀有度等级（1-7）
     */
    private static Integer mapVanillaRarity(Rarity vanillaRarity) {
        if (vanillaRarity == Rarity.UNCOMMON) {
            return 3; // 罕见
        } else if (vanillaRarity == Rarity.RARE) {
            return 4; // 史诗
        } else if (vanillaRarity == Rarity.EPIC) {
            return 5; // 传说
        } else {
            return 1; // 普通
        }
    }
    
    /**
     * 获取物品的稀有度等级
     * 优先级顺序：神化模组映射 > 本模组稀有度（配置和数据包） > 原版映射 > 默认值
     * @param item 要查稀有度的物品
     * @return 物品的稀有度等级（1-7）
     */
    public static @NotNull Integer getRarity(@Nullable Item item) {
        if (item != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                net.minecraft.world.item.ItemStack tempStack = new net.minecraft.world.item.ItemStack(item);
                
                // 首先检查神化模组稀有度（最高优先级）
                if (org.yanbwe.raritycore.config.ServerConfigManager.isCheckApotheosisRarity()) {
                    Integer apotheosisRarity = org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter.getMappedRarity(tempStack);
                    if (apotheosisRarity != null) {
                        return apotheosisRarity; // 返回映射后的神化稀有度
                    }
                }
                
                // 然后检查本模组的稀有度配置（包括配置文件和数据包）
                Integer configuredRarity = ITEM_RARITY_MAP.get(itemId);
                if (configuredRarity != null) {
                    return configuredRarity;
                }
                
                // 最后检查原版稀有度映射（最低优先级）
                if (org.yanbwe.raritycore.config.ServerConfigManager.isCheckVanillaRarity()) {
                    net.minecraft.world.item.Rarity vanillaRarity = tempStack.getRarity();
                    if (vanillaRarity == net.minecraft.world.item.Rarity.UNCOMMON) {
                        return 3; // 罕见
                    } else if (vanillaRarity == net.minecraft.world.item.Rarity.RARE) {
                        return 4; // 史诗
                    } else if (vanillaRarity == net.minecraft.world.item.Rarity.EPIC) {
                        return 5; // 传说
                    }
                }
            }
        }
        return 1; // 默认为普通
    }
    
    /**
     * 使用重试机制将所有稀有度数据同步到客户端（全量同步）
     */
    public static void syncRarityToClientsWithRetry() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // 创建包含当前所有数据的映射
            java.util.Map<ResourceLocation, Integer> currentData = new java.util.HashMap<>(ITEM_RARITY_MAP);
            RaritySyncPacket packet = new RaritySyncPacket(currentData);
            
            // 使用重试管理器发送
            org.yanbwe.raritycore.network.NetworkRetryManager.sendFullSyncWithRetry(packet);
        }
    }
    
    /**
     * 使用重试机制将增量变更同步到客户端
     */
    public static void syncIncrementalChangesToClientsWithRetry() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !CHANGE_OPERATIONS_BUFFER.isEmpty()) {
            // 创建包含变更操作的增量同步包
            IncrementalSyncPacket packet = new IncrementalSyncPacket(new ArrayList<>(CHANGE_OPERATIONS_BUFFER));
            
            // 清空缓冲区
            CHANGE_OPERATIONS_BUFFER.clear();
            
            // 使用重试管理器发送
            org.yanbwe.raritycore.network.NetworkRetryManager.sendIncrementalSyncWithRetry(packet);
        }
    }
}