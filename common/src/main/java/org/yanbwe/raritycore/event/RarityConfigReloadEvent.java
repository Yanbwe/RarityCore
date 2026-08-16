package org.yanbwe.raritycore.event;

import net.neoforged.bus.api.Event;

/**
 * 配置重载事件（V14）。
 *
 * <p>配置整体重载完成后触发，分为客户端侧重载与服务端重载。
 * 通过 {@code NeoForge.EVENT_BUS} 发布。
 */
public abstract class RarityConfigReloadEvent extends Event {

    protected RarityConfigReloadEvent() {
    }

    /**
     * 客户端侧配置重载事件。
     */
    public static class Client extends RarityConfigReloadEvent {
        public Client() {
            super();
        }
    }

    /**
     * 服务端配置重载事件。
     */
    public static class Server extends RarityConfigReloadEvent {
        public Server() {
            super();
        }
    }
}
