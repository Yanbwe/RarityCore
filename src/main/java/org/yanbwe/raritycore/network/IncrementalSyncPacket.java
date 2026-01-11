package org.yanbwe.raritycore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.network.ChangeOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class IncrementalSyncPacket {
    public static final String PROTOCOL_VERSION = "1.0";
    public static SimpleChannel INSTANCE;
    
    public static void initialize() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(RarityCore.MODID, "incremental_sync"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );
        
        INSTANCE.messageBuilder(IncrementalSyncPacket.class, 0)
                .encoder(IncrementalSyncPacket::encode)
                .decoder(IncrementalSyncPacket::new)
                .consumerMainThread(IncrementalSyncPacket::handle)
                .add();
    }

    private List<ChangeOperation> changeOperations;

    public IncrementalSyncPacket(List<ChangeOperation> changeOperations) {
        this.changeOperations = changeOperations;
    }

    public IncrementalSyncPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        changeOperations = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int opType = buf.readInt();
            String itemIdStr = buf.readUtf();
            ResourceLocation itemId = new ResourceLocation(itemIdStr);
            
            Integer rarity = null;
            boolean hasRarity = buf.readBoolean();
            if (hasRarity) {
                rarity = buf.readInt();
            }
            
            ChangeOperation.OperationType type = ChangeOperation.OperationType.values()[opType];
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
            // 应用变更操作到客户端的注册表
            for (ChangeOperation op : changeOperations) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(op.getItemId());
                
                switch (op.getType()) {
                    case ADD:
                    case UPDATE:
                        if (item != null && !op.getItemId().equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getDefaultKey())) {
                            // 直接操作底层映射，不触发变更记录，避免循环
                            RarityRegistry.ITEM_RARITY_MAP.put(op.getItemId(), op.getRarity());
                        }
                        break;
                    case DELETE:
                        RarityRegistry.ITEM_RARITY_MAP.remove(op.getItemId()); // 从映射中删除
                        break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
        return true;
    }

    public List<ChangeOperation> getChangeOperations() {
        return changeOperations;
    }
}