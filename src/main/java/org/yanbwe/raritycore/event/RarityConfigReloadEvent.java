package org.yanbwe.raritycore.event;

import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.Event;

/**
 * 配置重载事件
 * 配置整体重载完成后触发，分为客户端侧重载与服务端重载
 */
public abstract class RarityConfigReloadEvent extends Event {

    /** 是否为游戏启动时的自动重载 */
    private final boolean startup;
    /** 触发重载的命令源（启动或程序化触发时为空） */
    private final CommandSourceStack source;

    protected RarityConfigReloadEvent(boolean startup, CommandSourceStack source) {
        this.startup = startup;
        this.source = source;
    }

    /** 是否为游戏启动时的自动重载 */
    public boolean isStartup() {
        return startup;
    }

    /** 触发重载的命令源，可能为 null */
    public CommandSourceStack getSource() {
        return source;
    }

    /**
     * 客户端侧配置重载事件
     * 在 RarityStyle、客户端配置、星星与缓存刷新完成后触发
     */
    public static class Client extends RarityConfigReloadEvent {
        public Client(boolean startup, CommandSourceStack source) {
            super(startup, source);
        }
    }

    /**
     * 服务端配置重载事件
     * 在全部配置重载流程完成后触发
     */
    public static class Server extends RarityConfigReloadEvent {
        public Server(boolean startup, CommandSourceStack source) {
            super(startup, source);
        }
    }
}
