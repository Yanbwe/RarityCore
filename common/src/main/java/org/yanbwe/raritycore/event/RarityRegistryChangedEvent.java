package org.yanbwe.raritycore.event;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;

import java.util.Collections;
import java.util.Map;

/**
 * 稀有度注册表变更事件（V14）。
 *
 * <p>批量注册或整体重载后一次性发布本次全部变更。
 * 包含所有发生变化的物品 ID → 稀有度等级映射。
 * 通过 {@code NeoForge.EVENT_BUS} 发布。
 */
public class RarityRegistryChangedEvent extends Event {

    private final Map<Identifier, Integer> changedEntries;

    public RarityRegistryChangedEvent(Map<Identifier, Integer> changedEntries) {
        this.changedEntries = changedEntries != null
            ? Collections.unmodifiableMap(changedEntries)
            : Collections.emptyMap();
    }

    /** @return 新增或修改的条目（物品 ID → 稀有度等级），只读视图 */
    public Map<Identifier, Integer> getChangedEntries() {
        return changedEntries;
    }
}
