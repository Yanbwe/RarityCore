package org.yanbwe.raritycore.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;
import java.util.Map;

/**
 * 稀有度注册集合变更事件
 * 批量注册或整体重载后一次性发布本次发生的全部变更，避免逐条触发 RarityChangeEvent
 */
public class RarityRegistryChangedEvent extends Event {

    /** 本次变更条目：物品 ID → 旧等级（可能为 null）/ 新等级（可能为 null） */
    private final Map<ResourceLocation, RarityChange> changes;

    public RarityRegistryChangedEvent(Map<ResourceLocation, RarityChange> changes) {
        this.changes = changes;
    }

    /** 本次全部变更条目（不可变） */
    public Map<ResourceLocation, RarityChange> getChanges() {
        return changes;
    }

    /** 单条变更描述 */
    public static class RarityChange {
        /** 旧等级，移除时为空 */
        public final Integer oldRarity;
        /** 新等级，新增时为空 */
        public final Integer newRarity;

        public RarityChange(Integer oldRarity, Integer newRarity) {
            this.oldRarity = oldRarity;
            this.newRarity = newRarity;
        }
    }
}
