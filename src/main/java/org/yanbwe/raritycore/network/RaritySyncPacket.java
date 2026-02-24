package org.yanbwe.raritycore.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.yanbwe.raritycore.Raritycore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.Map;

public class RaritySyncPacket {
    public static final Identifier PACKET_ID = new Identifier(Raritycore.MOD_ID, "rarity_sync");
    
    public static void register() {
        // 服务端接收客户端数据包
        ServerPlayNetworking.registerGlobalReceiver(PACKET_ID, (server, player, handler, buf, responseSender) -> {
            Raritycore.LOGGER.debug("Received rarity sync packet from client");
        });
    }
    
    /**
     * 向客户端发送稀有度数据
     */
    public static void sendToClient(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        Map<Identifier, Integer> rarityData = RarityRegistry.getAllRarities();
        
        // 写入数据
        buf.writeInt(rarityData.size());
        for (Map.Entry<Identifier, Integer> entry : rarityData.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            buf.writeInt(entry.getValue());
        }
        
        // 发送数据包
        ServerPlayNetworking.send(player, PACKET_ID, buf);
        Raritycore.LOGGER.debug("Sent rarity data to player: {}", player.getEntityName());
    }
    
    /**
     * 从字节缓冲区读取稀有度数据
     */
    public static Map<Identifier, Integer> readRarityData(PacketByteBuf buf) {
        int size = buf.readInt();
        Map<Identifier, Integer> rarityData = new java.util.HashMap<>();
        
        for (int i = 0; i < size; i++) {
            Identifier id = buf.readIdentifier();
            int rarity = buf.readInt();
            rarityData.put(id, rarity);
        }
        
        return rarityData;
    }
}