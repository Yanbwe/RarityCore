package org.yanbwe.raritycore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.HashMap;
import java.util.Map;

public record RaritySyncPayload(Map<Identifier, Integer> rarityData) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RaritySyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.RARITY_SYNC_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, RaritySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT),
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
            org.yanbwe.raritycore.client.CacheInvalidationListener.onNetworkSync();
        });
    }
}