package org.yanbwe.raritycore.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record IncrementalSyncPayload(List<ChangeOperationData> changeOperations) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<IncrementalSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.INCREMENTAL_SYNC_CHANNEL));
    @SuppressWarnings("unchecked")
    public static final StreamCodec<FriendlyByteBuf, IncrementalSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, (StreamCodec<FriendlyByteBuf, ChangeOperationData>) (StreamCodec<?, ChangeOperationData>) ChangeOperationData.STREAM_CODEC),
            IncrementalSyncPayload::changeOperations,
            IncrementalSyncPayload::new);

    @Override
    public Type<IncrementalSyncPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            RarityCore.LOGGER.debug("Applying incremental sync packet with {} operations", changeOperations.size());

            int appliedCount = 0;
            int skippedCount = 0;

            for (ChangeOperationData op : changeOperations) {
                var item = BuiltInRegistries.ITEM.get(op.itemId());

                switch (op.type()) {
                    case ADD, UPDATE -> {
                        if (item != null && !op.itemId().equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                            RarityRegistry.ITEM_RARITY_MAP.put(op.itemId(), op.rarity());
                            appliedCount++;
                        } else {
                            skippedCount++;
                            RarityCore.LOGGER.debug("Skipped invalid item: {}", op.itemId());
                        }
                    }
                    case DELETE -> {
                        if (RarityRegistry.ITEM_RARITY_MAP.containsKey(op.itemId())) {
                            RarityRegistry.ITEM_RARITY_MAP.remove(op.itemId());
                            appliedCount++;
                        } else {
                            skippedCount++;
                        }
                    }
                }
            }

            RarityCore.LOGGER.debug("Incremental sync completed: applied={}, skipped={}", appliedCount, skippedCount);
            org.yanbwe.raritycore.client.CacheInvalidationListener.onNetworkSync();
        });
    }

    public enum OperationType {
        ADD,
        UPDATE,
        DELETE
    }

    public static final class ChangeOperationData {
        @SuppressWarnings("unchecked")
        private static final StreamCodec<FriendlyByteBuf, OperationType> OPERATION_TYPE_CODEC =
                (StreamCodec<FriendlyByteBuf, OperationType>) (StreamCodec<?, OperationType>) ByteBufCodecs.VAR_INT.map(
                        ordinal -> OperationType.values()[ordinal],
                        OperationType::ordinal
                );

        @SuppressWarnings("unchecked")
        private static final StreamCodec<FriendlyByteBuf, Optional<Integer>> OPTIONAL_VAR_INT =
                (StreamCodec<FriendlyByteBuf, Optional<Integer>>) (StreamCodec<?, Optional<Integer>>) ByteBufCodecs.optional(ByteBufCodecs.VAR_INT);

        @SuppressWarnings("unchecked")
        private static final StreamCodec<FriendlyByteBuf, Identifier> IDENTIFIER_CODEC =
                (StreamCodec<FriendlyByteBuf, Identifier>) (StreamCodec<?, Identifier>) Identifier.STREAM_CODEC;

        @SuppressWarnings("unchecked")
        public static final StreamCodec<FriendlyByteBuf, ChangeOperationData> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public void encode(FriendlyByteBuf buf, @Nonnull ChangeOperationData value) {
                OPERATION_TYPE_CODEC.encode(buf, value.type());
                IDENTIFIER_CODEC.encode(buf, value.itemId());
                OPTIONAL_VAR_INT.encode(buf, Optional.ofNullable(value.rarity()));
            }

            @Override
            public ChangeOperationData decode(FriendlyByteBuf buf) {
                OperationType type = OPERATION_TYPE_CODEC.decode(buf);
                Identifier itemId = IDENTIFIER_CODEC.decode(buf);
                Optional<Integer> rarity = OPTIONAL_VAR_INT.decode(buf);
                return new ChangeOperationData(type, itemId, rarity.orElse(null));
            }
        };

        private final OperationType type;
        private final Identifier itemId;
        private final Integer rarity;

        public ChangeOperationData(OperationType type, Identifier itemId, Integer rarity) {
            this.type = type;
            this.itemId = itemId;
            this.rarity = rarity;
        }

        public OperationType type() {
            return type;
        }

        public Identifier itemId() {
            return itemId;
        }

        public Integer rarity() {
            return rarity;
        }
    }
}
