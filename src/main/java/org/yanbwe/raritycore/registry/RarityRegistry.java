package org.yanbwe.raritycore.registry;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.network.ChangeOperation;
import org.yanbwe.raritycore.network.IncrementalSyncPacket;
import org.yanbwe.raritycore.network.RaritySyncPacket;

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
     * 获取物品的稀有度等级
     * @param item 要查稀有度的物品
     * @return 物品的稀有度等级（1-7）
     */
    @Nullable
    public static Integer getRarity(@Nullable Item item) {
        if (item != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                // 首先检查本模组的稀有度配置
                Integer configuredRarity = ITEM_RARITY_MAP.get(itemId);
                if (configuredRarity != null) {
                    return configuredRarity;
                }
                
                // 如果没有本模组的稀有度配置，使用原版稀有度
                net.minecraft.world.item.ItemStack tempStack = new net.minecraft.world.item.ItemStack(item);
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
        return 1; // 默认为普通
    }
}