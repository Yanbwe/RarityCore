package org.yanbwe.raritycore.network;

import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.registry.RarityRegistry;

public record EditModeRequestPayload(Identifier itemId, int rarity, boolean deleteMode) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EditModeRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.EDIT_MODE_REQUEST_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, EditModeRequestPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC,
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

            if (!Commands.LEVEL_MODERATORS.check(serverPlayer.permissions())) {
                RarityCore.LOGGER.warn("Player {} without sufficient permissions tried to use edit mode",
                    serverPlayer.getName().getString());
                return;
            }

            RarityCore.LOGGER.info("Processing edit mode request from player {}: Item={}, Rarity={}, Delete={}",
                serverPlayer.getName().getString(), itemId, rarity, deleteMode);

            try {
                var itemHolder = BuiltInRegistries.ITEM.get(itemId);
                if (itemHolder.isPresent() && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                    var item = itemHolder.get().value();
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