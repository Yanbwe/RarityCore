package org.yanbwe.raritycore.event;

import net.neoforged.bus.api.Event;

/**
 * 视觉表现配置重载事件
 * RarityStyle 配置被文件改动并重新加载后触发（区别于 API 写入触发的 RarityStyleChangedEvent）
 */
public class RarityStyleReloadEvent extends Event {

    /** 受影响的可选稀有度等级，0 表示全局配置、负数表示全等级 */
    private final int rarity;
    /** 是否由文件外部改动触发（true）还是内部重载流程触发（false） */
    private final boolean external;

    public RarityStyleReloadEvent(int rarity, boolean external) {
        this.rarity = rarity;
        this.external = external;
    }

    /** 受影响的稀有度等级（0=全局，负数=全等级） */
    public int getRarity() {
        return rarity;
    }

    /** 是否由文件外部改动触发 */
    public boolean isExternal() {
        return external;
    }
}
