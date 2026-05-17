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

        // CacheInvalidationListener 已通过 @EventBusSubscriber 注解自动注册
        // 此处不再手动注册，避免重复订阅导致每个事件触发多次
        // ItemDataConfigLoader.loadAllConfigs() 已移至 ConfigReloadService.reloadOnStartup() 统一管理
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ServiceFactory factory = ServiceFactory.getInstance();
        factory.getSchedulerService().startScheduledTasks();

        NeoForge.EVENT_BUS.register(new org.yanbwe.raritycore.tick.ServerTickListener());

        // 服务器启动/重载后重置网络调度器状态，避免旧残留
        org.yanbwe.raritycore.network.DelayedSyncManager.reset();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ServiceFactory factory = ServiceFactory.getInstance();
        factory.getSchedulerService().stopScheduledTasks();

        org.yanbwe.raritycore.network.SyncManager.clearChangeBuffer();

        org.yanbwe.raritycore.network.DelayedSyncManager.shutdown();
        org.yanbwe.raritycore.network.ItemDataSyncManager.shutdown();
        org.yanbwe.raritycore.network.NetworkRetryManager.shutdown();
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