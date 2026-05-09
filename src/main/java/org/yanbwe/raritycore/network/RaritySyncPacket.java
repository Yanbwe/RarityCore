package org.yanbwe.raritycore.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RaritySyncPacket {
    public static SimpleChannel INSTANCE;
    
    /**
     * 客户端记录的最近一次收到的配置版本号
     * 初始为0，与服务端版本号比对以跳过重复同步
     */
    private static int clientConfigVersion = 0;
    
    public static void initialize() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.RARITY_SYNC_CHANNEL),
                () -> NetworkConstants.PROTOCOL_VERSION,
                NetworkConstants.PROTOCOL_VERSION::equals,
                NetworkConstants.PROTOCOL_VERSION::equals
        );
        
        INSTANCE.messageBuilder(RaritySyncPacket.class, 0)
                .encoder(RaritySyncPacket::encode)
                .decoder(RaritySyncPacket::new)
                .consumerMainThread(RaritySyncPacket::handle)
                .add();
    }

    private final int configVersion;
    private Map<ResourceLocation, Integer> rarityData;

    /**
     * 构造包(服务端发送用)
     * @param configVersion 当前配置版本号
     * @param rarityData 物品稀有度映射
     */
    public RaritySyncPacket(int configVersion, Map<ResourceLocation, Integer> rarityData) {
        this.configVersion = configVersion;
        this.rarityData = rarityData;
    }

    /**
     * 解码包(客户端接收用)
     * 使用 NBT CompoundTag 编码以减少数据量:
     *   - 旧格式: writeUtf(key) + writeInt(value) 每条约30-50字节
     *   - NBT格式: CompoundTag二进制编码，每条约10-15字节
     */
    public RaritySyncPacket(FriendlyByteBuf buf) {
        this.configVersion = buf.readVarInt();
        
        // 版本匹配: 客户端已是最新，跳过数据解析
        if (this.configVersion == clientConfigVersion) {
            this.rarityData = null;
            return;
        }
        
        CompoundTag nbt = buf.readNbt();
        rarityData = new HashMap<>();
        if (nbt != null) {
            for (String key : nbt.getAllKeys()) {
                Tag tag = nbt.get(key);
                if (tag instanceof IntTag intTag) {
                    rarityData.put(ResourceLocation.parse(key), intTag.getAsInt());
                }
            }
        }
    }

    /**
     * 编码包(服务端发送用)
     * 格式: [varInt version] [NBT CompoundTag]
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(configVersion);
        
        CompoundTag nbt = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : rarityData.entrySet()) {
            nbt.putInt(entry.getKey().toString(), entry.getValue());
        }
        buf.writeNbt(nbt);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 安全校验：确保仅在客户端处理服务端发来的同步包
            if (ctx.get().getDirection() != NetworkEvent.Context.NetworkDirection.PLAY_TO_CLIENT) {
                RarityCore.LOGGER.warn("RaritySyncPacket received on wrong side, ignoring");
                ctx.get().setPacketHandled(true);
                return;
            }
            
            // 版本匹配——客户端数据已是最新，跳过全量覆盖
            if (rarityData == null) {
                RarityCore.LOGGER.debug("Rarity sync skipped: client version {} matches server", clientConfigVersion);
                ctx.get().setPacketHandled(true);
                return;
            }
            
            // 更新客户端版本号
            clientConfigVersion = this.configVersion;
            
            // 构建经过滤的新映射,然后用 putAll 一次性替换以缩小数据竞争窗口
            Map<ResourceLocation, Integer> filtered = new HashMap<>();
            for (Map.Entry<ResourceLocation, Integer> entry : rarityData.entrySet()) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(entry.getKey());
                if (item != null && !entry.getKey().equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getDefaultKey())) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
            RarityRegistry.ITEM_RARITY_MAP.clear();
            RarityRegistry.ITEM_RARITY_MAP.putAll(filtered);
            
            // 通知缓存系统网络同步已完成
            org.yanbwe.raritycore.client.CacheInvalidationListener.onNetworkSync();
        });
        ctx.get().setPacketHandled(true);
        return true;
    }


}