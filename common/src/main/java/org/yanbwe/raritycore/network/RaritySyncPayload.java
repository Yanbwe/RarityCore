package org.yanbwe.raritycore.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.config.TagRarityLoader;
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
    Map<Identifier, Integer> itemRarityData,
    Map<Identifier, Integer> autoRarityData,
    List<TagRuleTransfer> tagRules
) implements CustomPacketPayload {

    /** Tag 绋€鏈夊害瑙勫垯鐨勭綉缁滀紶杈撴牸寮忥紝浣跨敤 (namespace, path, rarity) 涓夊厓缁勩€?*/
    public record TagRuleTransfer(String tagNamespace, String tagPath, int rarity) {
        public static final StreamCodec<FriendlyByteBuf, TagRuleTransfer> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TagRuleTransfer::tagNamespace,
                ByteBufCodecs.STRING_UTF8, TagRuleTransfer::tagPath,
                ByteBufCodecs.VAR_INT,     TagRuleTransfer::rarity,
                TagRuleTransfer::new);

        /** 杩樺師涓?TagRarityLoader.TagRarityEntry */
        public TagRarityLoader.TagRarityEntry toTagRarityEntry() {
            return new TagRarityLoader.TagRarityEntry(tagNamespace, tagPath, rarity);
        }
    }

    public static final CustomPacketPayload.Type<RaritySyncPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.RARITY_SYNC_CHANNEL));

    public static final StreamCodec<FriendlyByteBuf, RaritySyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT),
                RaritySyncPayload::itemRarityData,
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT),
                RaritySyncPayload::autoRarityData,
            TagRuleTransfer.STREAM_CODEC.apply(ByteBufCodecs.list()),
                RaritySyncPayload::tagRules,
            RaritySyncPayload::new);

    @Override
    public Type<RaritySyncPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 1) 鎵嬪姩娉ㄥ唽琛?(FinalRarity.json)
            RarityRegistry.ITEM_RARITY_MAP.clear();
            RarityRegistry.ITEM_RARITY_MAP.putAll(itemRarityData);

            // 2) 鑷姩璁＄畻琛?(auto_rarity.json)
            RarityRegistry.applySyncedAutoRarity(autoRarityData);

            // 3) TagRarity 瑙勫垯
            TagRarityLoader.applySyncedRules(tagRules);

            org.yanbwe.raritycore.client.CacheInvalidationListener.onNetworkSync();
        });
    }
}
