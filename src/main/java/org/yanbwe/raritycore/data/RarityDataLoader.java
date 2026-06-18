package org.yanbwe.raritycore.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityConstants;

import java.util.Map;

public class RarityDataLoader extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final RarityDataLoader INSTANCE = new RarityDataLoader();

    private static final String FOLDER = "rarity";

    public RarityDataLoader() {
        super(ExtraCodecs.JSON, net.minecraft.resources.FileToIdConverter.json(FOLDER));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> resourceList, ResourceManager resourceManager, ProfilerFiller profiler) {
        DynamicOps<JsonElement> ops = this.makeConditionalOps();

        for (Map.Entry<Identifier, JsonElement> entry : resourceList.entrySet()) {
            Identifier location = entry.getKey();
            JsonElement element = entry.getValue();

            if (element.isJsonObject()) {
                JsonObject jsonObject = element.getAsJsonObject();

                for (java.util.Map.Entry<String, com.google.gson.JsonElement> itemEntry : jsonObject.entrySet()) {
                    String itemIdString = itemEntry.getKey();
                    JsonElement rarityElement = itemEntry.getValue();

                    if (rarityElement.isJsonPrimitive() && rarityElement.getAsJsonPrimitive().isNumber()) {
                        int rarity = rarityElement.getAsInt();

                        Identifier itemId = Identifier.parse(itemIdString);
                        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemId)
                                .map(holder -> holder.value())
                                .orElse(null);

                        if (item == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                            RarityCore.LOGGER.warn("Unknown item '{}' in rarity data file '{}'", itemIdString, location);
                            continue;
                        }

                        if (rarity < RarityConstants.MIN_RARITY) {
                            RarityCore.LOGGER.warn("Invalid rarity value {} for item '{}' in rarity data file '{}'", rarity, itemIdString, location);
                            continue;
                        }

                        RarityRegistry.register(item, rarity);
                    } else {
                        RarityCore.LOGGER.warn("Invalid rarity data format for item '{}' in rarity data file '{}'", itemEntry.getKey(), location);
                    }
                }
            } else {
                RarityCore.LOGGER.warn("Invalid format in rarity data file '{}', expected JSON object", location);
            }
        }

        org.yanbwe.raritycore.service.ConfigReloadService.reloadOnStartup();
    }
}