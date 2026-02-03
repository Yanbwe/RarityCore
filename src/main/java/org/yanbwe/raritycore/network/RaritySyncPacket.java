package org.yanbwe.raritycore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RaritySyncPacket {
    public static final String PROTOCOL_VERSION = "1.0";
    public static SimpleChannel INSTANCE;
    
    public static void initialize() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(RarityCore.MODID, "rarity_sync"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );
        
        INSTANCE.messageBuilder(RaritySyncPacket.class, 0)
                .encoder(RaritySyncPacket::encode)
                .decoder(RaritySyncPacket::new)
                .consumerMainThread(RaritySyncPacket::handle)
                .add();
    }

    private Map<ResourceLocation, Integer> rarityData;

    public RaritySyncPacket(Map<ResourceLocation, Integer> rarityData) {
        this.rarityData = rarityData;
    }

    public RaritySyncPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        rarityData = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf();
            int value = buf.readInt();
            rarityData.put(new ResourceLocation(key), value);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(rarityData.size());
        for (Map.Entry<ResourceLocation, Integer> entry : rarityData.entrySet()) {
            buf.writeUtf(entry.getKey().toString());
            buf.writeInt(entry.getValue());
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 在客户端更新稀有度数据，但不修改配置文件，也不同步回服务端
            // 这个包用于全量同步，会替换客户端的所有数据
            RarityRegistry.ITEM_RARITY_MAP.clear(); // 先清空现有数据
            for (Map.Entry<ResourceLocation, Integer> entry : rarityData.entrySet()) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(entry.getKey());
                if (item != null && !entry.getKey().equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getDefaultKey())) {
                    RarityRegistry.ITEM_RARITY_MAP.put(entry.getKey(), entry.getValue()); // 直接放入映射，不记录变更
                }
            }
            
            // 通知缓存系统网络同步已完成
            org.yanbwe.raritycore.client.CacheInvalidationListener.onNetworkSync();
        });
        ctx.get().setPacketHandled(true);
        return true;
    }


}