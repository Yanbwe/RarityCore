package org.yanbwe.raritycore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager.BorderConfig;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityConstants;

public class ItemBorderRenderer {

    public static void renderRarityBorder(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y) {
        RarityStyleConfigManager styleMgr = RarityStyleConfigManager.getInstance();
        
        if (!styleMgr.isBorderEnabled()) return;
        if (itemStack.isEmpty()) return;

        Integer rarity;
        boolean isCacheEnabled = ClientConfigManager.isEnableCacheSystem();
        if (isCacheEnabled) {
            rarity = RenderCacheManager.getCachedRarity(itemStack);
            if (rarity == null) {
                rarity = RarityRegistry.getRarity(itemStack);
                if (rarity != null) RenderCacheManager.cacheItemStackRarity(itemStack, rarity);
            }
        } else {
            rarity = RarityRegistry.getRarity(itemStack);
        }
        if (rarity == null) rarity = RarityConstants.MIN_RARITY;

        Item item = itemStack.getItem();
        if (styleMgr.isNoRaritySkip() && !RarityRegistry.hasConfiguredRarity(item)) return;

        BorderConfig border = styleMgr.resolveBorder(rarity);
        if (!border.show) return;

        if (border.useTexture) {
            String texturePath = styleMgr.getBorderTexture(rarity);
            renderTextureBorder(guiGraphics, texturePath, x, y, rarity);
        } else {
            int rgbColor = styleMgr.resolveColor(rarity);
            renderColorBorder(guiGraphics, rgbColor, x, y, border.style);
        }
    }

    private static void renderTextureBorder(GuiGraphics guiGraphics, String texturePath, int x, int y, int fallbackRarity) {
        ResourceLocation textureLocation;
        try {
            textureLocation = ResourceLocation.parse(texturePath);
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to parse texture path '{}', falling back to color border", texturePath);
            RarityStyleConfigManager mgr = RarityStyleConfigManager.getInstance();
            renderColorBorder(guiGraphics, mgr.resolveColor(fallbackRarity), x, y, mgr.resolveBorder(fallbackRarity).style);
            return;
        }
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            guiGraphics.blit(textureLocation, x, y, 0, 0, 16, 16, 16, 16);
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to load texture '{}', falling back to color border", texturePath);
            RarityStyleConfigManager mgr = RarityStyleConfigManager.getInstance();
            renderColorBorder(guiGraphics, mgr.resolveColor(fallbackRarity), x, y, mgr.resolveBorder(fallbackRarity).style);
        }
    }

    private static void renderColorBorder(GuiGraphics guiGraphics, int rgbColor, int x, int y, int style) {
        int argbColor = 0xFF000000 | (rgbColor & 0x00FFFFFF);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (style == 1) {
            int alphaMask = 0x80000000;
            int translucentColor = (argbColor & 0x00FFFFFF) | alphaMask;
            guiGraphics.fill(x, y, x + 16, y + 16, translucentColor);
        } else {
            guiGraphics.fill(x, y, x + 16, y + 1, argbColor);
            guiGraphics.fill(x, y + 15, x + 16, y + 16, argbColor);
            guiGraphics.fill(x, y, x + 1, y + 16, argbColor);
            guiGraphics.fill(x + 15, y, x + 16, y + 16, argbColor);
        }
    }

    public static void handleSkipConfigChange() {
        RenderCacheManager.clearAllCache();
    }
}
