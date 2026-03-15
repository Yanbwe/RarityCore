package org.yanbwe.raritycore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.function.Supplier;

/**
 * 客户端编辑模式请求包
 * 用于在多人游戏中，客户端向服务端发送编辑模式修改请求
 */
public class EditModeRequestPacket {
    public static final String PROTOCOL_VERSION = "1.0";
    public static SimpleChannel INSTANCE;
    
    public static void initialize() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(RarityCore.MODID, "edit_mode_request"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );
        
        // 服务端接收客户端的请求包
        INSTANCE.messageBuilder(EditModeRequestPacket.class, 0)
                .encoder(EditModeRequestPacket::encode)
                .decoder(EditModeRequestPacket::new)
                .consumerMainThread(EditModeRequestPacket::handle)
                .add();
    }

    private ResourceLocation itemId;
    private int rarity;
    private boolean deleteMode;

    public EditModeRequestPacket(ResourceLocation itemId, int rarity, boolean deleteMode) {
        this.itemId = itemId;
        this.rarity = rarity;
        this.deleteMode = deleteMode;
    }

    public EditModeRequestPacket(FriendlyByteBuf buf) {
        String itemIdStr = buf.readUtf();
        this.itemId = new ResourceLocation(itemIdStr);
        this.rarity = buf.readInt();
        this.deleteMode = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(itemId.toString());
        buf.writeInt(rarity);
        buf.writeBoolean(deleteMode);
    }

    /**
     * 在服务端处理编辑模式请求
     */
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                RarityCore.LOGGER.warn("Received edit mode request from null player");
                return;
            }
            
            // 检查玩家是否有权限（需要 OP 权限）
            if (!player.hasPermissions(2)) {
                RarityCore.LOGGER.warn("Player {} without sufficient permissions tried to use edit mode", 
                    player.getName().getString());
                return;
            }
            
            RarityCore.LOGGER.info("Processing edit mode request from player {}: Item={}, Rarity={}, Delete={}",
                player.getName().getString(), itemId, rarity, deleteMode);
            
            try {
                // 在服务端注册稀有度
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemId);
                if (item != null && !itemId.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getDefaultKey())) {
                    if (deleteMode) {
                        // 删除模式
                        RarityRegistry.unregister(item, false);
                        RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), 0);
                        RarityCore.LOGGER.info("Deleted rarity for item {} by player {}", 
                            itemId, player.getName().getString());
                    } else {
                        // 设置稀有度
                        RarityRegistry.register(item, rarity, false);
                        RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), rarity);
                        RarityCore.LOGGER.info("Set rarity {} for item {} by player {}", 
                            rarity, itemId, player.getName().getString());
                    }
                    
                    // 同步到所有客户端（包括请求者）
                    RarityRegistry.syncRarityToClientsWithRetry();
                } else {
                    RarityCore.LOGGER.warn("Invalid item received in edit mode request: {}", itemId);
                }
            } catch (Exception e) {
                RarityCore.LOGGER.error("Error processing edit mode request", e);
            }
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
