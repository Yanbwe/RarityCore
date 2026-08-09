package org.yanbwe.raritycore.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.yanbwe.raritycore.event.RarityChangeEvent;
import org.yanbwe.raritycore.event.RarityQueryEvent;
import org.yanbwe.raritycore.event.RarityTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * RarityCore KubeJS 事件系统。
 * <p>
 * 在 {@link RarityCoreKubeJSPlugin#registerEvents()} 中完成事件组注册，
 * 在 {@link RarityCoreKubeJSPlugin#init()} 中通过 {@link #init()} 手动注册
 * NeoForge 事件桥接监听器（不使用 @EventBusSubscriber 以避免
 * KubeJS 未安装时因缺失 EventJS 类导致类加载失败）。
 * </p>
 *
 * <h3>JS 使用示例</h3>
 * <pre>{@code
 * RarityCoreEvents.rarityChanged(event => {
 *     console.log(`${event.itemId}: ${event.oldRarity} -> ${event.newRarity}`)
 * })
 *
 * RarityCoreEvents.rarityQuery(event => {
 *     if (event.itemId === "minecraft:diamond") {
 *         event.rarity = 6  // 修改稀有度
 *     }
 * })
 *
 * RarityCoreEvents.rarityTooltip(event => {
 *     event.addText("自定义文本")
 * })
 * }</pre>
 */
public final class RarityCoreKubeJSEvents {

    private RarityCoreKubeJSEvents() {}

    /** 事件组名称，对应 JS 中 {@code RarityCoreEvents} */
    static final EventGroup GROUP = EventGroup.of("RarityCoreEvents");

    /** 稀有度变更事件：物品稀有度被注册/更新/删除时触发 */
    public static final EventHandler RARITY_CHANGED =
            GROUP.server("rarityChanged", () -> RarityChangedEventJS.class);

    /** 稀有度查询事件：查询物品稀有度时触发，可通过 {@code event.rarity} 修改结果 */
    public static final EventHandler RARITY_QUERY =
            GROUP.server("rarityQuery", () -> RarityQueryEventJS.class);

    /** 工具提示事件：物品工具提示构建时触发，可追加自定义文本（仅客户端） */
    public static final EventHandler RARITY_TOOLTIP =
            GROUP.client("rarityTooltip", () -> RarityTooltipEventJS.class);

    // ======================================================================
    //  初始化（由 RarityCoreKubeJSPlugin 调用）
    // ======================================================================

    /**
     * 初始化 NeoForge 事件桥接。
     * 仅当 KubeJS 已加载（Plugin 被激活）时调用。
     */
    public static void init() {
        NeoForge.EVENT_BUS.register(new NeoForgeBridge());
    }

    // ======================================================================
    //  NeoForge 事件 → KubeJS 事件桥接
    // ======================================================================

    /** 内部桥接类，通过 NeoForge.EVENT_BUS 监听模组事件并转发到 KubeJS */
    static final class NeoForgeBridge {

        @SubscribeEvent
        public void onRarityChange(RarityChangeEvent event) {
            if (!RARITY_CHANGED.hasListeners()) return;
            RARITY_CHANGED.post(new RarityChangedEventJS(event));
        }

        @SubscribeEvent
        public void onRarityQuery(RarityQueryEvent event) {
            if (!RARITY_QUERY.hasListeners()) return;
            RarityQueryEventJS jsEvent = new RarityQueryEventJS(event);
            RARITY_QUERY.post(jsEvent);
            // 将 JS 侧修改的稀有度回写到 NeoForge 事件
            if (jsEvent.getRarity() != event.getOriginalRarity()) {
                event.setOverriddenRarity(jsEvent.getRarity());
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onRarityTooltip(RarityTooltipEvent event) {
            if (!RARITY_TOOLTIP.hasListeners()) return;
            RarityTooltipEventJS jsEvent = new RarityTooltipEventJS(event);
            RARITY_TOOLTIP.post(jsEvent);
            // 将 JS 侧追加的文本合并到 NeoForge 事件的组件列表
            for (String text : jsEvent.getTexts()) {
                event.getTooltipComponents().add(Component.literal(text));
            }
        }
    }

    // ======================================================================
    //  KubeJS 事件类定义
    // ======================================================================

    /**
     * KubeJS 稀有度变更事件。
     * <p>物品稀有度被注册/更新/删除时触发。</p>
     *
     * <h4>JS 属性</h4>
     * <ul>
     *   <li><b>itemId</b> — 物品 ID 字符串（如 "minecraft:diamond_sword"）</li>
     *   <li><b>oldRarity</b> — 旧稀有度等级，注册时为 0</li>
     *   <li><b>newRarity</b> — 新稀有度等级，删除时为 0</li>
     *   <li><b>changeType</b> — 变更类型："register" | "update" | "remove"</li>
     * </ul>
     */
    public static class RarityChangedEventJS implements KubeEvent {
        private final String itemId;
        private final int oldRarity;
        private final int newRarity;
        private final String changeType;

        RarityChangedEventJS(RarityChangeEvent event) {
            this.itemId = BuiltInRegistries.ITEM.getKey(event.getItem()).toString();
            this.oldRarity = event.getOldRarity() != null ? event.getOldRarity() : 0;
            this.newRarity = event.getNewRarity() != null ? event.getNewRarity() : 0;
            this.changeType = event.getChangeType().name().toLowerCase();
        }

        public String getItemId()     { return itemId; }
        public int getOldRarity()    { return oldRarity; }
        public int getNewRarity()    { return newRarity; }
        public String getChangeType() { return changeType; }
    }

    /**
     * KubeJS 稀有度查询事件。
     * <p>查询物品稀有度时触发。可修改 {@code event.rarity} 来覆盖结果
     * （修改后会自动取消 NeoForge 侧的默认查询结果）。</p>
     *
     * <h4>JS 属性</h4>
     * <ul>
     *   <li><b>source</b> — 查询来源（"component" | "itemdata" | "apotheosis" | ...）</li>
     *   <li><b>itemId</b> — 物品 ID 字符串</li>
     *   <li><b>rarity</b> — 当前稀有度等级，可读写</li>
     * </ul>
     */
    public static class RarityQueryEventJS implements KubeEvent {
        private final String source;
        private final String itemId;
        private int rarity;

        RarityQueryEventJS(RarityQueryEvent event) {
            this.source = event.getSource();
            this.itemId = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString();
            this.rarity = event.getOriginalRarity();
        }

        public String getSource() { return source; }
        public String getItemId() { return itemId; }
        public int getRarity()    { return rarity; }
        public void setRarity(int rarity) { this.rarity = rarity; }
    }

    /**
     * KubeJS 工具提示事件（仅客户端）。
     * <p>物品工具提示构建时触发，可通过 {@code event.addText(text)} 追加自定义行。</p>
     *
     * <h4>JS 属性</h4>
     * <ul>
     *   <li><b>rarity</b> — 物品稀有度等级</li>
     *   <li><b>addText(text)</b> — 追加一行自定义文本</li>
     * </ul>
     */
    public static class RarityTooltipEventJS implements KubeEvent {
        private final int rarity;
        private final List<String> texts = new ArrayList<>();

        RarityTooltipEventJS(RarityTooltipEvent event) {
            this.rarity = event.getRarity();
        }

        public int getRarity() { return rarity; }

        /** 向工具提示追加一行自定义文本。 */
        public void addText(String text) {
            texts.add(text);
        }

        /** 获取 JS 侧追加的所有文本行（供桥接代码合并用）。 */
        List<String> getTexts() {
            return texts;
        }
    }
}
