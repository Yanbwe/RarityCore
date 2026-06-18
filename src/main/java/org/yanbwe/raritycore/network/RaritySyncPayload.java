package org.yanbwe.raritycore.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.client.CacheInvalidationListener;
import org.yanbwe.raritycore.config.TagRarityConfig;
import org.yanbwe.raritycore.config.TagRarityConfigLoader;
import org.yanbwe.raritycore.registry.RarityRegistry;

/**
 * 鏈嶅姟绔悓姝ョ█鏈夊害鏁版嵁鐨勭綉缁滀紶杈撹浇浣擄紝鎼哄甫:
 * <ul>
 *   <li>鎵嬪姩閰嶇疆琛?(FinalRarity.json)</li>
 *   <li>鑷姩璁＄畻琛?(auto_rarity.json)</li>
 *   <li>Tag 绋€鏈夊害瑙勫垯琛?(TagRarity.json)</li>
 * </ul>
 */
public record RaritySyncPayload(
    Map<ResourceLocation, Integer> itemRarityData,
    Map<ResourceLocation, Integer> autoRarityData,
    List<TagRuleTransfer> tagRules
) implements CustomPacketPayload {

    // -- Tag 瑙勫垯浼犺緭鐢ㄨ浇浣?----------------------------------------------

    /** Tag 绋€鏈夊害瑙勫垯鐨勭綉缁滀紶杈撴牸寮忥紝閬垮厤鐩存帴搴忓垪鍖?TagKey<Item>銆?*/
    public record TagRuleTransfer(ResourceLocation tagLocation, int rarity) {
        public static final StreamCodec<FriendlyByteBuf, TagRuleTransfer> STREAM_CODEC =
            StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, TagRuleTransfer::tagLocation,
                ByteBufCodecs.VAR_INT,         TagRuleTransfer::rarity,
                TagRuleTransfer::new);

        /** 杩樺師涓?TagRarityConfig.TagRarityEntry */
        public TagRarityConfig.TagRarityEntry toTagRarityEntry() {
            return new TagRarityConfig.TagRarityEntry(
                net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, tagLocation),
                rarity);
        }
    }

    // -- CustomPacketPayload 鎺ュ彛 ---------------------------------------------

    public static final CustomPacketPayload.Type<RaritySyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.RARITY_SYNC_CHANNEL));

    public static final StreamCodec<FriendlyByteBuf, RaritySyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT),
                RaritySyncPayload::itemRarityData,
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT),
                RaritySyncPayload::autoRarityData,
            TagRuleTransfer.STREAM_CODEC.apply(ByteBufCodecs.list()),
                RaritySyncPayload::tagRules,
            RaritySyncPayload::new);

    @Override
    public Type<RaritySyncPayload> type() {
        return TYPE;
    }

    // -- 瀹㈡埛绔鐞?----------------------------------------------------------

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 1) 鎵嬪姩娉ㄥ唽琛?(FinalRarity.json)
            RarityRegistry.ITEM_RARITY_MAP.clear();
            RarityRegistry.ITEM_RARITY_MAP.putAll(itemRarityData);

            // 2) 鑷姩璁＄畻琛?(auto_rarity.json)
            RarityRegistry.applySyncedAutoRarity(autoRarityData);

            // 3) TagRarity 瑙勫垯
            TagRarityConfigLoader.applySyncedRules(tagRules);

            CacheInvalidationListener.onNetworkSync();
        });
    }
}
