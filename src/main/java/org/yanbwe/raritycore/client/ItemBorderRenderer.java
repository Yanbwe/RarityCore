package org.yanbwe.raritycore.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;
import org.yanbwe.raritycore.util.RarityValidator;

public class ItemBorderRenderer {

    public static void renderRarityBorder(GuiGraphicsExtractor guiGraphics, ItemStack itemStack, int x, int y) {
        if (!ClientConfigManager.isEnableItemBorderRendering()) {
            return;
        }

        if (itemStack.isEmpty()) {
            return;
        }

        Integer rarity;

        boolean isCacheEnabled = ClientConfigManager.isEnableCacheSystem();

        if (isCacheEnabled) {
            rarity = RenderCacheManager.getCachedRarity(itemStack);
            if (rarity == null) {
                rarity = RarityRegistry.getRarity(itemStack);
                if (rarity != null) {
                    RenderCacheManager.cacheItemStackRarity(itemStack, rarity);
                }
            }
        } else {
            rarity = RarityRegistry.getRarity(itemStack);
        }

        if (rarity == null) {
            rarity = RarityConstants.RARITY_COMMON;
        }

        Item item = itemStack.getItem();
        if (ClientConfigManager.isSkipUnconfiguredItems() && !hasConfiguredRarity(item)) {
            return;
        }

        rarity = RarityValidator.normalizeRarity(rarity);

        if (ClientConfigManager.isUseTextureBorder()) {
            renderTextureBorder(guiGraphics, rarity, x, y);
        } else {
            renderColorBorder(guiGraphics, rarity, x, y);
        }
    }

    private static void renderTextureBorder(GuiGraphicsExtractor guiGraphics, int rarity, int x, int y) {
        String textureName = "rarity_" + rarity;

        try {
            Identifier textureLocation = Identifier.parse(RarityConstants.BORDER_TEXTURE_PATH + textureName + RarityConstants.TEXTURE_SUFFIX);
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, textureLocation, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to render texture border for rarity {}, falling back to color border: {}", rarity, e.getMessage());
            renderColorBorder(guiGraphics, rarity, x, y);
        }
    }

    private static void renderColorBorder(GuiGraphicsExtractor guiGraphics, int rarity, int x, int y) {
        int borderColor = RarityColorUtil.getRarityArgbColor(rarity);

        if (ClientConfigManager.getItemBorderStyle() == 1) {
            int alphaMask = 0x80000000;
            int translucentColor = (borderColor & 0x00FFFFFF) | alphaMask;

            guiGraphics.fill(x, y, x + 16, y + 16, translucentColor);
        } else {
            guiGraphics.fill(x, y, x + 16, y + 1, borderColor);
            guiGraphics.fill(x, y + 15, x + 16, y + 16, borderColor);
            guiGraphics.fill(x, y, x + 1, y + 16, borderColor);
            guiGraphics.fill(x + 15, y, x + 16, y + 16, borderColor);
        }
    }

    private static boolean hasConfiguredRarity(Item item) {
        if (item == null) {
            return false;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return false;
        }

        if (RarityRegistry.ITEM_RARITY_MAP.containsKey(itemId)) {
            return true;
        }

        if (RarityRegistry.hasAutoRarity(itemId)) {
            return true;
        }

        return false;
    }

    public static void handleSkipConfigChange() {
        RenderCacheManager.clearAllCache();
    }
}