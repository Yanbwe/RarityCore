package org.yanbwe.raritycore.event;

import net.minecraftforge.eventbus.api.Event;

/**
 * 视觉表现配置变更事件
 * 通过 API 写入并持久化某项视觉表现配置后触发，便于监听方刷新缓存与渲染
 */
public class RarityStyleChangedEvent extends Event {

    /** 受影响的稀有度等级（0 表示全局主开关/无稀有度回退等非逐级项） */
    private final int rarity;
    /** 变更项标识 */
    private final ChangeTarget target;

    public RarityStyleChangedEvent(int rarity, ChangeTarget target) {
        this.rarity = rarity;
        this.target = target;
    }

    /** 受影响的稀有度等级 */
    public int getRarity() {
        return rarity;
    }

    /** 变更项标识 */
    public ChangeTarget getTarget() {
        return target;
    }

    /** 视觉表现配置变更项 */
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
}
