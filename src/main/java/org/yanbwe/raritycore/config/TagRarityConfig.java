package org.yanbwe.raritycore.config;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tag 稀有度配置数据结构。
 *
 * <p>存储从 TagRarity.json 加载的 TagKey → 稀有度映射规则列表。
 * 规则按稀有度降序排列，运行时找到第一个匹配的 Tag 即可返回最高稀有度。</p>
 *
 * <p>线程安全：使用同步列表保护读写操作。</p>
 */
public class TagRarityConfig {

    /**
     * 单条 Tag 稀有度规则。
     *
     * @param tagKey 物品 Tag 键
     * @param rarity 稀有度等级 (1-7)
     */
    public record TagRarityEntry(TagKey<Item> tagKey, int rarity) {}

    /** 规则列表，按稀有度降序排列 */
    private static final List<TagRarityEntry> TAG_RULES =
            Collections.synchronizedList(new ArrayList<>());

    /**
     * 设置 Tag 稀有度规则列表。
     * 调用方应确保规则已按稀有度降序排列。
     *
     * @param rules 规则列表
     */
    public static void setRules(List<TagRarityEntry> rules) {
        synchronized (TAG_RULES) {
            TAG_RULES.clear();
            TAG_RULES.addAll(rules);
        }
    }

    /**
     * 获取 Tag 稀有度规则列表（只读快照）。
     *
     * @return 不可修改的规则列表
     */
    public static List<TagRarityEntry> getRules() {
        synchronized (TAG_RULES) {
            return List.copyOf(TAG_RULES);
        }
    }

    /**
     * 清除所有规则。
     */
    public static void clearRules() {
        synchronized (TAG_RULES) {
            TAG_RULES.clear();
        }
    }
}
