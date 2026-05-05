package org.yanbwe.raritycore;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import org.yanbwe.raritycore.compat.CompatibilityChecker;
import org.yanbwe.raritycore.compat.CompatibilityManager;
import org.yanbwe.raritycore.event.RarityCoreEventHandler;
import org.yanbwe.raritycore.service.ServiceFactory;

@Mod(RarityCore.MODID)
public class RarityCore {
    public static final String MODID = "raritycore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RarityCore(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(new RarityCoreEventHandler());

        initializeServices();
    }

    private void initializeServices() {
        ServiceFactory.getInstance().initializeAllServices();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(CompatibilityChecker::performCompatibilityCheck);

        event.enqueueWork(CompatibilityManager::initializeCompatibilityAdapters);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }
}