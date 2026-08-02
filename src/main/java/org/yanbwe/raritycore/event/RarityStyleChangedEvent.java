package org.yanbwe.raritycore.event;

import net.neoforged.bus.api.Event;

/**
 * RarityStyle 视觉表现变更事件（V14）。
 *
 * <p>通过 API 写入并持久化视觉表现配置后触发。
 * 携带受影响的稀有度等级和变更目标枚举。
 * 通过 {@code NeoForge.EVENT_BUS} 发布。
 */
public class RarityStyleChangedEvent extends Event {

    /** 变更目标类型 */
    public enum Target {
        COLOR, BORDER, TOOLTIP, NAME_COLOR
    }

    private final int level;
    private final Target target;

    public RarityStyleChangedEvent(int level, Target target) {
        this.level = level;
        this.target = target;
    }

    /** @return 受影响的稀有度等级 */
    public int getLevel() { return level; }

    /** @return 变更目标类型 */
    public Target getTarget() { return target; }
}
