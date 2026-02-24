package org.yanbwe.raritycore.network.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.yanbwe.raritycore.Raritycore;
import org.yanbwe.raritycore.client.RarityClientData;
import org.yanbwe.raritycore.network.RaritySyncPacket;

import java.util.Map;

public class ClientRaritySyncHandler {
    
    public static void register() {
        // 客户端接收服务端数据包
        ClientPlayNetworking.registerGlobalReceiver(RaritySyncPacket.PACKET_ID, (client, handler, buf, responseSender) -> {
            Map<Identifier, Integer> rarityData = RaritySyncPacket.readRarityData(buf);
            client.execute(() -> {
                // 在客户端主线程更新稀有度数据
                RarityClientData.updateRarityData(rarityData);
                Raritycore.LOGGER.debug("Updated client rarity data with {} entries", rarityData.size());
            });
        });
        
        Raritycore.LOGGER.info("Registered ClientRaritySyncHandler");
    }
}