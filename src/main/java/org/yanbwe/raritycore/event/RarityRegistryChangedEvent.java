package org.yanbwe.raritycore.event;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 稀有度注册表变更事件（V14）。
 *
 * <p>批量注册或整体重载后一次性发布本次全部变更。
 * 包含所有发生变化的物品 ID → 稀有度等级映射。
 * 通过 {@code NeoForge.EVENT_BUS} 发布。
 */
public class RarityRegistryChangedEvent extends Event {

    private final Map<ResourceLocation, Integer> changedEntries;
    private final Set<ResourceLocation> removedEntries;

    public RarityRegistryChangedEvent(Map<ResourceLocation, Integer> changed, Set<ResourceLocation> removed) {
        this.changedEntries = changed != null ? Collections.unmodifiableMap(changed) : Collections.emptyMap();
        this.removedEntries = removed != null ? Collections.unmodifiableSet(removed) : Collections.emptySet();
    }

    /** @return 新增或修改的条目（物品 ID → 稀有度等级），只读视图 */
    public Map<ResourceLocation, Integer> getChangedEntries() { return changedEntries; }

    /** @return 被移除的条目（物品 ID 集合），只读视图 */
    public Set<ResourceLocation> getRemovedEntries() { return removedEntries; }
}
