package org.yanbwe.raritycore.config;

import org.yanbwe.raritycore.util.RarityConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-rarity 客户端视觉表现配置数据模型。
 *
 * <p>JSON key 即稀有度等级的字符串形式（"1"~"7"），无内部 level 字段。
 * 每个等级定义：
 * <ul>
 *   <li><b>color</b> — RGB 颜色，格式 #RRGGBB，存储为 int</li>
 *   <li><b>texture</b> — 纹理边框资源路径</li>
 *   <li><b>tooltips</b> — 是否显示工具提示</li>
 *   <li><b>renderer</b> — 是否渲染物品槽边框</li>
 *   <li><b>nameColor</b> — 是否修改物品名称颜色</li>
 * </ul>
 *
 * <p><b>等级 >7 回退规则</b>：当查询等级 >7 且该等级未被显式配置时，沿用等级 7 的值。
 */
public class RarityClientConfig {

    /** 单例实例 */
    private static volatile RarityClientConfig instance;

    /** 稀有度等级 -> 客户端配置的映射，key=等级(>=1) */
    private final Map<Integer, RarityEntry> configs;

    RarityClientConfig() {
        this.configs = new HashMap<>();
    }

    /**
     * 获取单例实例（懒加载，线程安全）。
     */
    public static RarityClientConfig getInstance() {
        if (instance == null) {
            synchronized (RarityClientConfig.class) {
                if (instance == null) {
                    instance = new RarityClientConfig();
                }
            }
        }
        return instance;
    }

    /**
     * 替换当前实例（用于 reload）。
     */
    public static void replaceInstance(RarityClientConfig newConfig) {
        synchronized (RarityClientConfig.class) {
            instance = newConfig;
        }
    }

    /**
     * 注册一个稀有度等级的客户端配置。
     *
     * @param level 稀有度等级 (≥1)
     * @param entry 该等级的配置项
     */
    public void putConfig(int level, RarityEntry entry) {
        configs.put(level, entry);
    }

    /**
     * 获取指定等级的客户端配置。
     *
     * <p><b>回退规则</b>：
     * <ul>
     *   <li>level &lt; 1 → 返回等级 1 的配置</li>
     *   <li>level 已配置 → 直接返回</li>
     *   <li>level &gt; 7 且未配置 → 沿用等级 7 的值</li>
     *   <li>等级 7 也未配置 → 返回等级 1 的配置作为兜底</li>
     * </ul>
     *
     * @param level 稀有度等级
     * @return 对应的配置项，保证不返回 null
     */
    public RarityEntry getConfig(int level) {
        // 边界：非正等级回退到 1
        if (level < 1) {
            return getOrFallback(1);
        }
        // 直接命中
        if (configs.containsKey(level)) {
            return configs.get(level);
        }
        // 等级 >7 未配置 → 沿用等级 7
        if (level > RarityConstants.MAX_RARITY) {
            return getOrFallback(RarityConstants.MAX_RARITY);
        }
        // 其他未配置情况 → 回退到 1
        return getOrFallback(1);
    }

    /**
     * 获取颜色 RGB int 值。便捷方法。
     */
    public int getColor(int level) {
        return getConfig(level).getColor();
    }

    /**
     * 获取纹理路径。便捷方法。
     */
    public String getTexture(int level) {
        return getConfig(level).getTexture();
    }

    /**
     * 是否显示 tooltip。便捷方法。
     */
    public boolean isTooltipsEnabled(int level) {
        return getConfig(level).isTooltips();
    }

    /**
     * 是否渲染边框。便捷方法。
     */
    public boolean isRendererEnabled(int level) {
        return getConfig(level).isRenderer();
    }

    /**
     * 是否修改名称颜色。便捷方法。
     */
    public boolean isNameColorEnabled(int level) {
        return getConfig(level).isNameColor();
    }

    /**
     * 已配置的等级数量。
     */
    public int size() {
        return configs.size();
    }

    /**
     * 检查是否已加载有效配置。
     */
    public boolean isEmpty() {
        return configs.isEmpty();
    }

    // ---------------------------------------------------------------
    //  内部方法
    // ---------------------------------------------------------------

    /**
     * 获取配置，若目标等级不存在则递归向下查找到等级 1。
     */
    private RarityEntry getOrFallback(int level) {
        for (int l = level; l >= 1; l--) {
            if (configs.containsKey(l)) {
                return configs.get(l);
            }
        }
        // 理论上不会到此处（等级 1 必定由默认配置填充），防御性返回
        return new RarityEntry(0xCCCCCC, "", true, true, true);
    }

    // ===============================================================
    //  RarityEntry — 单个稀有度等级的客户端配置项
    // ===============================================================

    /**
     * 单个稀有度等级的客户端视觉表现配置。
     */
    public static class RarityEntry {
        /** RGB 颜色值（int 格式：0xRRGGBB） */
        private final int color;
        /** 纹理资源路径（如 "raritycore:textures/border/rarity_1.png"） */
        private final String texture;
        /** 是否显示工具提示 */
        private final boolean tooltips;
        /** 是否渲染物品槽边框 */
        private final boolean renderer;
        /** 是否修改物品名称颜色 */
        private final boolean nameColor;

        public RarityEntry(int color, String texture, boolean tooltips, boolean renderer, boolean nameColor) {
            this.color = color;
            this.texture = texture;
            this.tooltips = tooltips;
            this.renderer = renderer;
            this.nameColor = nameColor;
        }

        public int getColor() {
            return color;
        }

        public String getTexture() {
            return texture;
        }

        public boolean isTooltips() {
            return tooltips;
        }

        public boolean isRenderer() {
            return renderer;
        }

        public boolean isNameColor() {
            return nameColor;
        }

        @Override
        public String toString() {
            return "RarityEntry{color=#" + Integer.toHexString(color).toUpperCase()
                    + ", texture='" + texture + '\''
                    + ", tooltips=" + tooltips
                    + ", renderer=" + renderer
                    + ", nameColor=" + nameColor + '}';
        }
    }
}
