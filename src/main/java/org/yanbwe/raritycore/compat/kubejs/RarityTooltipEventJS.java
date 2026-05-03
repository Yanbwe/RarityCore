package org.yanbwe.raritycore.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;
import org.yanbwe.raritycore.event.RarityTooltipEvent;

/**
 * KubeJS 稀有度工具提示事件 — 可追加自定义文本
 */
public class RarityTooltipEventJS extends EventJS {

    private final RarityTooltipEvent forgeEvent;

    public RarityTooltipEventJS(RarityTooltipEvent forgeEvent) {
        this.forgeEvent = forgeEvent;
    }

    public String getItemId() {
        var key = ForgeRegistries.ITEMS.getKey(forgeEvent.getItemStack().getItem());
        return key != null ? key.toString() : "unknown";
    }

    public int getRarity() {
        return forgeEvent.getRarity();
    }

    public boolean isSpecialRarity() {
        return forgeEvent.isSpecialRarity();
    }

    /** 添加自定义文本到 tooltip */
    public void addText(String text) {
        forgeEvent.getTooltipList().add(Component.literal(text));
    }

    /** 添加自定义 Component 到 tooltip */
    public void addComponent(Component component) {
        forgeEvent.getTooltipList().add(component);
    }
}
