package org.yanbwe.raritycore.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.yanbwe.raritycore.RarityCore;

@EventBusSubscriber(modid = RarityCore.MODID)
public class RarityNetworkInitialization {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToClient(
                RaritySyncPayload.TYPE,
                RaritySyncPayload.STREAM_CODEC,
                (payload, context) -> payload.handle(context));

        registrar.playToClient(
                IncrementalSyncPayload.TYPE,
                IncrementalSyncPayload.STREAM_CODEC,
                (payload, context) -> payload.handle(context));



        registrar.playToClient(
                ItemDataSyncPayload.TYPE,
                ItemDataSyncPayload.STREAM_CODEC,
                (payload, context) -> payload.handle(context));

        registrar.playToServer(
                EditModeRequestPayload.TYPE,
                EditModeRequestPayload.STREAM_CODEC,
                (payload, context) -> payload.handle(context));
    }
}