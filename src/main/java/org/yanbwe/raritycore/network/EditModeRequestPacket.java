package org.yanbwe.raritycore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.edit.EditModeManager;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.function.Supplier;

/**
 * 客户端编辑模式请求包
 * 携带编辑模式所有参数：物品ID、稀有度、是否删除、模式、自动重载、忽略标签
 */
public class EditModeRequestPacket {
    public static SimpleChannel INSTANCE;

    public static void initialize() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.EDIT_MODE_REQUEST_CHANNEL),
                () -> NetworkConstants.PROTOCOL_VERSION,
                NetworkConstants.PROTOCOL_VERSION::equals,
                NetworkConstants.PROTOCOL_VERSION::equals
        );

        INSTANCE.messageBuilder(EditModeRequestPacket.class, 0)
                .encoder(EditModeRequestPacket::encode)
                .decoder(EditModeRequestPacket::new)
                .consumerMainThread(EditModeRequestPacket::handle)
                .add();
    }

    private ResourceLocation itemId;
    private int rarity;
    private boolean deleteMode;
    private int modeOrdinal;
    private boolean autoReload;
    private String ignoreTags;

    public EditModeRequestPacket(ResourceLocation itemId, int rarity, boolean deleteMode,
                                  int modeOrdinal, boolean autoReload, String ignoreTags) {
        this.itemId = itemId;
        this.rarity = rarity;
        this.deleteMode = deleteMode;
        this.modeOrdinal = modeOrdinal;
        this.autoReload = autoReload;
        this.ignoreTags = ignoreTags != null ? ignoreTags : "";
    }

    public EditModeRequestPacket(FriendlyByteBuf buf) {
        this.itemId = ResourceLocation.parse(buf.readUtf());
        this.rarity = buf.readInt();
        this.deleteMode = buf.readBoolean();
        this.modeOrdinal = buf.readInt();
        this.autoReload = buf.readBoolean();
        this.ignoreTags = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(itemId.toString());
        buf.writeInt(rarity);
        buf.writeBoolean(deleteMode);
        buf.writeInt(modeOrdinal);
        buf.writeBoolean(autoReload);
        buf.writeUtf(ignoreTags);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                RarityCore.LOGGER.warn("Received edit mode request from null player");
                return;
            }

            if (!player.hasPermissions(2)) {
                RarityCore.LOGGER.warn("Player {} without sufficient permissions tried to use edit mode",
                    player.getName().getString());
                return;
            }

            RarityCore.LOGGER.info("Processing edit request from {}: item={}, rarity={}, mode={}, autoReload={}, ignore={}",
                player.getName().getString(), itemId, rarity, modeOrdinal, autoReload, ignoreTags);

            // 恢复服务端编辑状态
            EditModeManager.setRarity(rarity);
            EditModeManager.setAutoReload(autoReload);
            EditModeManager.setIgnoreTags(ignoreTags);

            try {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemId);
                if (item == null || itemId.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getDefaultKey())) {
                    RarityCore.LOGGER.warn("Invalid item in edit request: {}", itemId);
                    return;
                }

                EditModeManager.EditMode mode = EditModeManager.EditMode.values()[Math.min(modeOrdinal, 1)];
                EditModeManager.setMode(mode);

                if (mode == EditModeManager.EditMode.FULLMATCH) {
                    // 需要在服务端重建 ItemStack 以获取 NBT
                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                    if (player.getMainHandItem().getItem() == item) {
                        stack = player.getMainHandItem();
                    }
                    EditModeManager.handleFullMatchEditServer(itemId, stack, item);
                    // 触发 NBT 配置重载使新规则立即生效
                    org.yanbwe.raritycore.nbtmatching.NbtConfigLoader.loadAllConfigs();
                } else {
                    EditModeManager.handleNormalEditServer(itemId, item);
                }
            } catch (Exception e) {
                RarityCore.LOGGER.error("Error processing edit mode request", e);
            }
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
