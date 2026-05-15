package org.yanbwe.raritycore.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.util.JsonPerformanceOptimizer;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.service.ConfigReloadService;
import org.yanbwe.raritycore.util.RarityConstants;

import javax.annotation.Nonnull;
import java.util.Map;

public class RarityDataLoader extends SimpleJsonResourceReloadListener {
    /**
     * 物品稀有度数据加载器的单例实例
     */
    public static final RarityDataLoader INSTANCE = new RarityDataLoader();

    public RarityDataLoader() {
        super(JsonPerformanceOptimizer.getOptimizedGson(), "rarity");
    }

    /**
     * 从资源包加载JSON里的数据然后应用
     * 
     * @param jsons 包含物品稀有度配置的JSON元素映射,键为资源位置,值为JSON元素
     * @param resourceManager 资源管理器
     * @param profiler 性能统计器
     */
    @Override
    @SuppressWarnings("null")
    protected void apply(@Nonnull Map<ResourceLocation, JsonElement> jsons, @Nonnull ResourceManager resourceManager, @Nonnull ProfilerFiller profiler) {
        // 遍历所有加载的JSON配置文件
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation location = entry.getKey();
            JsonElement element = entry.getValue();

            if (element.isJsonObject()) {
                JsonObject jsonObject = element.getAsJsonObject();
                
                // 遍历JSON对象中的所有物品配置
                for (Map.Entry<String, JsonElement> itemEntry : jsonObject.entrySet()) {
                    String itemIdString = itemEntry.getKey();
                    JsonElement rarityElement = itemEntry.getValue();

                    if (rarityElement.isJsonPrimitive() && rarityElement.getAsJsonPrimitive().isNumber()) {
                        int rarity = rarityElement.getAsInt();
                        
                        ResourceLocation itemId = ResourceLocation.parse(itemIdString);
                        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemId);
                        
                        if (item == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                            RarityCore.LOGGER.warn("Unknown item '{}' in rarity data file '{}'", itemIdString, location);
                            continue;
                        }
                        
                        if (rarity < RarityConstants.MIN_RARITY || rarity > RarityConstants.MAX_RARITY) {
                            RarityCore.LOGGER.warn("Invalid rarity value {} for item '{}' in rarity data file '{}'", rarity, itemIdString, location);
                            continue;
                        }
                        
                        // 使用 syncToClients=false 禁止逐条增量同步，避免在启动/重载时发送大量小包
                        // 数据一致性由后续 ConfigReloadService.reloadOnStartup() 的批处理 + 玩家登录全量同步保证
                        RarityRegistry.register(item, rarity, false);
                    } else {
                        RarityCore.LOGGER.warn("Invalid rarity data format for item '{}' in rarity data file '{}'", itemEntry.getKey(), location);
                    }
                }
            } else {
                RarityCore.LOGGER.warn("Invalid format in rarity data file '{}', expected JSON object", location);
            }
        }
        
        // 使用统一的配置重载服务进行完整加载
        ConfigReloadService.reloadOnStartup();
    }
}