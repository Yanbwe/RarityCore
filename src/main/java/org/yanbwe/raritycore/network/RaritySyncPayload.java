package org.yanbwe.raritycore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.client.CacheInvalidationListener;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.HashMap;
import java.util.Map;

public record RaritySyncPayload(Map<ResourceLocation, Integer> rarityData) implements CustomPacketPayload {
    /** 紧凑构造函数 — 在序列化前对 Map 大小进行边界检查，超限仅记录警告不阻止发送 */
    public RaritySyncPayload {
        int size = rarityData.size();
        if (size > NetworkConstants.MAX_RARITY_SYNC_ENTRIES) {
            RarityCore.LOGGER.warn("RaritySyncPayload: Map size {} exceeds recommended limit of {} entries. "
                + "This may cause network performance degradation or client buffer overflow.",
                size, NetworkConstants.MAX_RARITY_SYNC_ENTRIES);
        }
    }

    public static final CustomPacketPayload.Type<RaritySyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.RARITY_SYNC_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, RaritySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT),
            RaritySyncPayload::rarityData,
            RaritySyncPayload::new);

    @Override
    public Type<RaritySyncPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 信任服务端数据，直接覆盖客户端注册表
            // 不再用 BuiltInRegistries.ITEM.get() 过滤——服务端是权威方，
            // 避免在 JEI 等大型模组环境下因客户端注册表查询失败导致模组物品被静默丢弃
            RarityRegistry.ITEM_RARITY_MAP.clear();
            RarityRegistry.ITEM_RARITY_MAP.putAll(rarityData);
            CacheInvalidationListener.onNetworkSync();
        });
    }
}