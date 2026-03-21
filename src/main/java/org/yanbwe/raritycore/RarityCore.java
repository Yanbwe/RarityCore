package org.yanbwe.raritycore;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;

@Mod(RarityCore.MODID)
public class RarityCore {
    public static final String MODID = "raritycore";
    private static final Logger LOGGER = LogUtils.getLogger();

    public RarityCore(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("{} mod loading...", MODID);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }
}