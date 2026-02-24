package org.yanbwe.raritycore.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.yanbwe.raritycore.Raritycore;

import java.util.HashMap;
import java.util.Map;

public class RaritySyncPacket {
    public static final Identifier PACKET_ID = new Identifier(Raritycore.MOD_ID, "rarity_sync");
    
    public static void register() {
        // 服务端接收客户端数据包
        ServerPlayNetworking.registerGlobalReceiver(PACKET_ID, (server, player, handler, buf, responseSender) -> {
            // 客户端发送的数据包处理逻辑（如果需要的话）
            Raritycore.LOGGER.debug("Received rarity sync packet from client");
        });
        
        // 客户端接收服务端数据包
        ClientPlayNetworking.registerGlobalReceiver(PACKET_ID, (client, handler, buf, responseSender) -> {
            Map<Identifier, Integer> rarityData = readRarityData(buf);
            client.execute(() -> {
                // 在客户端主线程更新稀有度数据
                org.yanbwe.raritycore.client.RarityClientData.updateRarityData(rarityData);
                Raritycore.LOGGER.debug("Updated client rarity data with {} entries", rarityData.size());
            });
        });
        
        Raritycore.LOGGER.info("Registered RaritySyncPacket");
    }
    
    /**
     * 发送稀有度数据到客户端
     * @param player 目标玩家
     * @param rarityData 稀有度数据
     */
    public static void send(ServerPlayerEntity player, Map<Identifier, Integer> rarityData) {
        PacketByteBuf buf = PacketByteBufs.create();
        writeRarityData(buf, rarityData);
        ServerPlayNetworking.send(player, PACKET_ID, buf);
    }
    
    /**
     * 将稀有度数据写入数据包
     * @param buf 数据包缓冲区
     * @param rarityData 稀有度数据
     */
    private static void writeRarityData(PacketByteBuf buf, Map<Identifier, Integer> rarityData) {
        buf.writeInt(rarityData.size());
        for (Map.Entry<Identifier, Integer> entry : rarityData.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }
    
    /**
     * 从数据包读取稀有度数据
     * @param buf 数据包缓冲区
     * @return 稀有度数据映射
     */
    private static Map<Identifier, Integer> readRarityData(PacketByteBuf buf) {
        Map<Identifier, Integer> rarityData = new HashMap<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            Identifier itemId = buf.readIdentifier();
            int rarity = buf.readInt();
            rarityData.put(itemId, rarity);
        }
        return rarityData;
    }
}