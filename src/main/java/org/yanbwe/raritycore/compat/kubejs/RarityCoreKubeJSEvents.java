package org.yanbwe.raritycore.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import org.yanbwe.raritycore.RarityCore;

/**
 * RarityCore KubeJS 事件注册
 * 将 RarityCore 的 Forge 事件暴露给 KubeJS 脚本
 */
public class RarityCoreKubeJSEvents {

    public static final EventGroup GROUP = EventGroup.of("RarityCoreEvents");

    /** 稀有度变更事件 - 物品稀有度被注册/更新/删除时触发 */
    public static final EventHandler RARITY_CHANGED = GROUP
        .server("rarityChanged", () -> RarityChangeEventJS.class);

    /** 稀有度查询事件 - 查询物品稀有度时触发，允许修改返回值 */
    public static final EventHandler RARITY_QUERY = GROUP
        .server("rarityQuery", () -> RarityQueryEventJS.class);

    /** 稀有度工具提示事件 - 工具提示构建时触发，允许添加自定义信息 */
    public static final EventHandler RARITY_TOOLTIP = GROUP
        .client("rarityTooltip", () -> RarityTooltipEventJS.class);

    /**
     * 注册事件组，在插件 registerEvents() 中调用
     */
    public static void register() {
        GROUP.register();
        RarityCore.LOGGER.debug("RarityCore KubeJS events registered");
    }
}
