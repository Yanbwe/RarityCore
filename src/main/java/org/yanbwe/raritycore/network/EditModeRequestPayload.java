package org.yanbwe.raritycore.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityConstants;

public record EditModeRequestPayload(ResourceLocation itemId, int rarity, boolean deleteMode) implements CustomPacketPayload {
    /** 紧凑构造函数 — 对载荷字段进行边界验证，防止恶意/异常数据导致的问题，超限仅记录警告不阻止处理 */
    public EditModeRequestPayload {
        if (itemId == null) {
            RarityCore.LOGGER.warn("EditModeRequestPayload: itemId is null, this may cause NullPointerException downstream");
        }
        if (!deleteMode && (rarity < RarityConstants.MIN_RARITY || rarity > RarityConstants.MAX_RARITY)) {
            RarityCore.LOGGER.warn("EditModeRequestPayload: rarity {} is outside valid range [{}-{}] for non-delete operation",
                rarity, RarityConstants.MIN_RARITY, RarityConstants.MAX_RARITY);
        }
    }

    public static final CustomPacketPayload.Type<EditModeRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.EDIT_MODE_REQUEST_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, EditModeRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            EditModeRequestPayload::itemId,
            ByteBufCodecs.VAR_INT,
            EditModeRequestPayload::rarity,
            ByteBufCodecs.BOOL,
            EditModeRequestPayload::deleteMode,
            EditModeRequestPayload::new);

    @Override
    public Type<EditModeRequestPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                RarityCore.LOGGER.warn("EditModeRequestPayload received on client side or from null player, ignoring");
                return;
            }

            if (!serverPlayer.hasPermissions(2)) {
                RarityCore.LOGGER.warn("Player {} without sufficient permissions tried to use edit mode",
                    serverPlayer.getName().getString());
                return;
            }

            RarityCore.LOGGER.info("Processing edit mode request from player {}: Item={}, Rarity={}, Delete={}",
                serverPlayer.getName().getString(), itemId, rarity, deleteMode);

            try {
                var item = BuiltInRegistries.ITEM.get(itemId);
                if (item != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                    if (deleteMode) {
                        RarityRegistry.unregister(item, false);
                        RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), 0);
                        RarityCore.LOGGER.info("Deleted rarity for item {} by player {}",
                            itemId, serverPlayer.getName().getString());
                    } else {
                        RarityRegistry.register(item, rarity, false);
                        RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), rarity);
                        RarityCore.LOGGER.info("Set rarity {} for item {} by player {}",
                            rarity, itemId, serverPlayer.getName().getString());
                    }
                } else {
                    RarityCore.LOGGER.warn("Invalid item ID in edit mode request: {}", itemId);
                }
            } catch (Exception e) {
                RarityCore.LOGGER.error("Error processing edit mode request: {}", e.getMessage());
            }
        });
    }
}