package org.yanbwe.raritycore.event;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.network.RaritySyncPacket;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = RarityCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // 当玩家登录时，发送当前服务端的稀有度数据到客户端
        Map<net.minecraft.resources.ResourceLocation, Integer> serverRarityData = new HashMap<>(RarityRegistry.ITEM_RARITY_MAP);
        RaritySyncPacket packet = new RaritySyncPacket(serverRarityData);
        RaritySyncPacket.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (net.minecraft.server.level.ServerPlayer) event.getEntity()), packet);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // Load server-side config file when server starts
        org.yanbwe.raritycore.config.RarityConfigLoader.loadConfigRarityData();
        RarityCore.LOGGER.info("RarityCore server starting, loaded server-side rarity config");
    }
}