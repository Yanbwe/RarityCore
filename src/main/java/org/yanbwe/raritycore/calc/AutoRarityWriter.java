package org.yanbwe.raritycore.calc;

import net.minecraft.world.item.Item;
import org.yanbwe.raritycore.RarityCore;

import java.util.Map;

/**
 * 自动稀有度写入器 — 将计算结果写入配置文件
 * 委托给 AutoRarityConfigManager 执行实际的文件写入操作
 */
public class AutoRarityWriter {

    /**
     * 写入自动计算的配置
     * @param finalResults 合并后的最终结果 (Item → Rarity)
     */
    static void writeAutoConfigs(Map<Item, Integer> finalResults) {
        try {
            RarityCore.LOGGER.info("Starting to write auto config: {} items", finalResults.size());

            if (finalResults.isEmpty()) {
                RarityCore.LOGGER.warn("No data to write, skipping auto config write");
                return;
            }

            // 清理旧的 auto_*.json(NBT 文件已废弃)
            AutoRarityConfigManager.cleanupAutoNbtFiles();

            // 写入 auto_rarity.json(仅 ID 匹配)
            AutoRarityConfigManager.writeAutoRarityJson(finalResults);

            RarityCore.LOGGER.info("Config write completed: {} items written to auto_rarity.json",
                finalResults.size());

        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to write auto rarity configs", e);
        }
    }
}
