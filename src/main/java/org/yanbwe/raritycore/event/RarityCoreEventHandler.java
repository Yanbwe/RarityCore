package org.yanbwe.raritycore.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.yanbwe.raritycore.service.ServiceFactory;

public class RarityCoreEventHandler {

    private static final Identifier RARITY_DATA_LOADER_KEY = Identifier.fromNamespaceAndPath("raritycore", "rarity");
    private static final Identifier ITEM_DATA_CONFIG_LOADER_KEY = Identifier.fromNamespaceAndPath("raritycore", "item_data_matches");

    @SubscribeEvent
    public void addReloadListeners(AddServerReloadListenersEvent event) {
        ServiceFactory factory = ServiceFactory.getInstance();
        event.addListener(RARITY_DATA_LOADER_KEY, factory.getRarityDataLoader());

        event.addListener(ITEM_DATA_CONFIG_LOADER_KEY, factory.createItemDataConfigLoader());

        try {
            Class<?> listenerClass = Class.forName("org.yanbwe.raritycore.client.CacheInvalidationListener");
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(listenerClass);
        } catch (ClassNotFoundException e) {
            org.yanbwe.raritycore.RarityCore.LOGGER.warn("CacheInvalidationListener class not found, skipping registration");
        }

        org.yanbwe.raritycore.itemdatamatching.ItemDataConfigLoader.loadAllConfigs();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        NeoForge.EVENT_BUS.register(new org.yanbwe.raritycore.tick.ServerTickListener());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ServiceFactory factory = ServiceFactory.getInstance();
        factory.getSchedulerService().stopScheduledTasks();

        org.yanbwe.raritycore.network.SyncManager.clearChangeBuffer();

        org.yanbwe.raritycore.network.DelayedSyncManager.shutdown();
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        org.yanbwe.raritycore.command.RarityCoreCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            org.yanbwe.raritycore.network.SyncManager.syncRarityToClients(org.yanbwe.raritycore.registry.RarityRegistry.getItemRarityMap());

            org.yanbwe.raritycore.network.ItemDataSyncManager.syncItemDataRulesToPlayer(serverPlayer);
        }
    }
}