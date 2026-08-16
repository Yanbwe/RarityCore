package org.yanbwe.raritycore.event;

import net.neoforged.bus.api.Event;

import java.util.Collections;
import java.util.Set;

/**
 * RarityStyle 视觉表现变更事件（V14）。
 *
 * <p>样式配置（颜色、边框、工具提示、名称颜色、无稀有度回退等）变更后触发。
 * 携带受影响的稀有度等级集合与变更目标枚举。
 * 通过 {@code NeoForge.EVENT_BUS} 发布。
 */
public class RarityStyleChangedEvent extends Event {

    /** 变更目标类型 */
    public enum StyleChangeTarget {
        COLOR,
        BORDER,
        TOOLTIP,
        NAME_COLOR,
        NO_RARITY,
        ALL
    }

    private final Set<Integer> affectedLevels;
    private final StyleChangeTarget target;

    public RarityStyleChangedEvent(Set<Integer> affectedLevels, StyleChangeTarget target) {
        this.affectedLevels = affectedLevels != null
            ? Collections.unmodifiableSet(affectedLevels)
            : Collections.emptySet();
        this.target = target;
    }

    /** 受影响的稀有度等级集合，只读视图 */
    public Set<Integer> getAffectedLevels() {
        return affectedLevels;
    }

    /** 变更目标类型 */
    public StyleChangeTarget getTarget() {
        return target;
    }
}
