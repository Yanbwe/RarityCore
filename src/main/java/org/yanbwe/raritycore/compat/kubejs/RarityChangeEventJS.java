package org.yanbwe.raritycore.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.event.RarityChangeEvent;

/**
 * KubeJS 稀有度变更事件
 */
public class RarityChangeEventJS extends EventJS {

    private final RarityChangeEvent forgeEvent;

    public RarityChangeEventJS(RarityChangeEvent forgeEvent) {
        this.forgeEvent = forgeEvent;
    }

    public String getItemId() {
        return ForgeRegistries.ITEMS.getKey(forgeEvent.getItem()).toString();
    }

    public int getOldRarity() {
        Integer old = forgeEvent.getOldRarity();
        return old != null ? old : 0;
    }

    public int getNewRarity() {
        Integer newVal = forgeEvent.getNewRarity();
        return newVal != null ? newVal : 0;
    }

    public String getChangeType() {
        return forgeEvent.getChangeType().name().toLowerCase();
    }
}
