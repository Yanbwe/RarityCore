package org.yanbwe.raritycore.event;

import net.neoforged.bus.api.Event;

/**
 * 配置重载事件（V14）。
 *
 * <p>配置整体重载完成后触发。通过 {@code NeoForge.EVENT_BUS} 发布。
 * 包含 {@link Client} 和 {@link Server} 两个子类以区分重载来源。
 */
public abstract class RarityConfigReloadEvent extends Event {

    /** 客户端配置重载事件。 */
    public static class Client extends RarityConfigReloadEvent {}

    /** 服务端配置重载事件。 */
    public static class Server extends RarityConfigReloadEvent {}
}
