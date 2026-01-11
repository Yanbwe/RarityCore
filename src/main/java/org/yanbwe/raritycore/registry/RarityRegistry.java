package org.yanbwe.raritycore.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.network.RaritySyncPacket;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

public class RarityRegistry {
    /**
     * 物品稀有度映射
     */
    public static final ConcurrentHashMap<ResourceLocation, Integer> ITEM_RARITY_MAP = new ConcurrentHashMap<>();

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
                ITEM_RARITY_MAP.put(itemId, rarity);
                
                // 如果需要同步到客户端且当前在服务端环境中
                if (syncToClients) {
                    syncRarityToClients();
                }
            }
        }
    }
    
    /**
     * 将所有稀有度数据同步到客户端
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
     * 获取物品的稀有度等级
     * @param item 要查稀有度的物品
     * @return 物品的稀有度等级（1-7）
     */
    @Nullable
    public static Integer getRarity(@Nullable Item item) {
        if (item != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null && !itemId.equals(ForgeRegistries.ITEMS.getDefaultKey())) {
                return ITEM_RARITY_MAP.get(itemId);
            }
        }
        return null;
    }
}