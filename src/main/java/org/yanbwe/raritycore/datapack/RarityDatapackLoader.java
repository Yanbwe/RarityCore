package org.yanbwe.raritycore.datapack;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.yanbwe.raritycore.Raritycore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public class RarityDatapackLoader implements SimpleSynchronousResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DATA_PATH = "data/raritycore/rarity/";
    
    @Override
    public Identifier getFabricId() {
        return new Identifier(Raritycore.MOD_ID, "rarity_datapacks");
    }
    
    @Override
    public void reload(ResourceManager manager) {
        Raritycore.LOGGER.info("Loading rarity datapacks...");
        
        // 清空现有数据
        RarityRegistry.clearDatapackData();
        
        // 加载所有稀有度配置文件
        Collection<Identifier> resources = manager.findResources("rarity", 
            id -> id.getPath().endsWith(".json"));
        
        int loadedCount = 0;
        for (Identifier resourceId : resources) {
            try {
                loadedCount += loadRarityFile(manager, resourceId);
            } catch (Exception e) {
                Raritycore.LOGGER.error("Failed to load rarity datapack file: {}", resourceId, e);
            }
        }
        
        Raritycore.LOGGER.info("Loaded {} rarity entries from {} datapack files", 
            RarityRegistry.getDatapackEntryCount(), loadedCount);
    }
    
    private int loadRarityFile(ResourceManager manager, Identifier fileId) throws IOException {
        try (InputStream inputStream = manager.getResource(fileId).get().getInputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            
            if (json == null) {
                Raritycore.LOGGER.warn("Empty or invalid JSON in datapack file: {}", fileId);
                return 0;
            }
            
            int entryCount = 0;
            for (String key : json.keySet()) {
                try {
                    int rarity = json.get(key).getAsInt();
                    if (rarity >= 1 && rarity <= 7) {
                        Identifier itemId = new Identifier(key);
                        RarityRegistry.registerDatapackRarity(itemId, rarity);
                        entryCount++;
                    } else {
                        Raritycore.LOGGER.warn("Invalid rarity value {} for item {} in file {}", 
                            rarity, key, fileId);
                    }
                } catch (JsonSyntaxException | NumberFormatException e) {
                    Raritycore.LOGGER.warn("Invalid rarity value for item {} in file {}: {}", 
                        key, fileId, e.getMessage());
                }
            }
            
            Raritycore.LOGGER.debug("Loaded {} entries from datapack file: {}", entryCount, fileId);
            return 1; // 返回文件数量
        }
    }
}