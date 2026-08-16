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
    public enum ChangeTarget {
        BORDER_ENABLED,
        TOOLTIP_ENABLED,
        TOOLTIP_COLOR_ENABLED,
        NO_RARITY_SKIP,
        NO_RARITY_DEFAULT_RARITY,
        BORDER_USE_TEXTURE,
        BORDER_STYLE,
        TOOLTIP_CONTENT,
        STAR_MODE,
        STAR_REPEAT_CHAR
    }

    /** 变更目标类型（1.21.1 旧名，已废弃） */
    @Deprecated
    public enum Target {
        COLOR, BORDER, TOOLTIP, NAME_COLOR
    }

    private final int level;
    private final ChangeTarget target;

    public RarityStyleChangedEvent(int level, ChangeTarget target) {
        this.level = level;
        this.target = target;
    }

    /** 受影响的稀有度等级（0=全局主开关/无稀有度回退等非逐级项） */
    public int getRarity() {
        return level;
    }

    /** @deprecated 使用 {@link #getRarity()} */
    @Deprecated
    public int getLevel() {
        return level;
    }

    /** 变更项标识 */
    public ChangeTarget getTarget() {
        return target;
    }
}
