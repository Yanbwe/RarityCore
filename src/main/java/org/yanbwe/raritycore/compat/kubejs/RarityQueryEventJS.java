package org.yanbwe.raritycore.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventJS;
import org.yanbwe.raritycore.event.RarityQueryEvent;

/**
 * KubeJS 稀有度查询事件 — 可修改返回值
 */
public class RarityQueryEventJS extends EventJS {

    private final RarityQueryEvent forgeEvent;

    public RarityQueryEventJS(RarityQueryEvent forgeEvent) {
        this.forgeEvent = forgeEvent;
    }

    public String getItemId() {
        if (!forgeEvent.getItemStack().isEmpty()) {
            var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(forgeEvent.getItemStack().getItem());
            return key != null ? key.toString() : "unknown";
        }
        return "unknown";
    }

    public int getRarity() {
        return forgeEvent.getRarity();
    }

    public void setRarity(int rarity) {
        forgeEvent.setRarity(rarity);
    }

    public String getSource() {
        return forgeEvent.getSource();
    }
}
