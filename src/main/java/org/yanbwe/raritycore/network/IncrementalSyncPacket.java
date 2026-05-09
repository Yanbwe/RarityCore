package org.yanbwe.raritycore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class IncrementalSyncPacket {
    public static SimpleChannel INSTANCE;
    
    /** 单次增量同步包允许的最大操作数，防止恶意数据包导致 OOM */
    private static final int MAX_OPERATIONS = 10000;
    
    public static void initialize() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.INCREMENTAL_SYNC_CHANNEL),
                () -> NetworkConstants.PROTOCOL_VERSION,
                NetworkConstants.PROTOCOL_VERSION::equals,
                NetworkConstants.PROTOCOL_VERSION::equals
        );
        
        INSTANCE.messageBuilder(IncrementalSyncPacket.class, 0)
                .encoder(IncrementalSyncPacket::encode)
                .decoder(IncrementalSyncPacket::new)
                .consumerMainThread(IncrementalSyncPacket::handle)
                .add();
    }

    private List<ChangeOperation> changeOperations;

    public IncrementalSyncPacket(List<ChangeOperation> changeOperations) {
        this.changeOperations = new ArrayList<>(changeOperations);
    }

    public IncrementalSyncPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        
        // 安全上限检查：防止恶意/损坏的数据包导致 OOM
        if (size < 0 || size > MAX_OPERATIONS) {
            RarityCore.LOGGER.warn("IncrementalSyncPacket received with invalid size: {}, discarding packet", size);
            this.changeOperations = Collections.emptyList();
            return;
        }
        
        changeOperations = new ArrayList<>(size);
        ChangeOperation.OperationType[] allTypes = ChangeOperation.OperationType.values();
        
        for (int i = 0; i < size; i++) {
            int opType = buf.readInt();
            
            // 边界检查：防止 ArrayIndexOutOfBoundsException
            if (opType < 0 || opType >= allTypes.length) {
                RarityCore.LOGGER.warn("IncrementalSyncPacket received invalid operation type: {}, skipping entry", opType);
                // 跳过无效条目的剩余数据，保持缓冲区读取位置正确
                buf.readUtf();               // itemId
                if (buf.readBoolean()) {     // hasRarity
                    buf.readInt();           // rarity
                }
                continue;
            }
            
            String itemIdStr = buf.readUtf();
            ResourceLocation itemId = ResourceLocation.parse(itemIdStr);
            
            Integer rarity = null;
            boolean hasRarity = buf.readBoolean();
            if (hasRarity) {
                rarity = buf.readInt();
            }
            
            ChangeOperation.OperationType type = allTypes[opType];
            changeOperations.add(new ChangeOperation(type, itemId, rarity));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(changeOperations.size());
        for (ChangeOperation op : changeOperations) {
            buf.writeInt(op.getType().ordinal());
            buf.writeUtf(op.getItemId().toString());
            buf.writeBoolean(op.getRarity() != null);
            if (op.getRarity() != null) {
                buf.writeInt(op.getRarity());
            }
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 安全校验：确保仅在客户端处理
            if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
                RarityCore.LOGGER.warn("IncrementalSyncPacket received on wrong side, ignoring");
                ctx.get().setPacketHandled(true);
                return;
            }
            
            // 批量应用变更操作到客户端的注册表
            RarityCore.LOGGER.debug("Applying incremental sync packet with {} operations", changeOperations.size());
            
            int appliedCount = 0;
            int skippedCount = 0;
            
            for (ChangeOperation op : changeOperations) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(op.getItemId());
                
                switch (op.getType()) {
                    case ADD:
                    case UPDATE:
                        if (item != null && !op.getItemId().equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getDefaultKey())) {
                            // 直接操作底层映射,不触发变更记录,避免循环
                            RarityRegistry.ITEM_RARITY_MAP.put(op.getItemId(), op.getRarity());
                            appliedCount++;
                        } else {
                            skippedCount++;
                            RarityCore.LOGGER.debug("Skipped invalid item: {}", op.getItemId());
                        }
                        break;
                    case DELETE:
                        if (RarityRegistry.ITEM_RARITY_MAP.containsKey(op.getItemId())) {
                            RarityRegistry.ITEM_RARITY_MAP.remove(op.getItemId());
                            appliedCount++;
                        } else {
                            skippedCount++;
                        }
                        break;
                }
            }
            
            RarityCore.LOGGER.debug("Incremental sync applied: {} operations, {} skipped", 
                appliedCount, skippedCount);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }

    public List<ChangeOperation> getChangeOperations() {
        return changeOperations;
    }
}