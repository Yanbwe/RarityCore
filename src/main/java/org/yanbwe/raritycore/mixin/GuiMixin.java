package org.yanbwe.raritycore.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.ItemBorderRenderer;
import org.yanbwe.raritycore.client.RarityExclusionManager;

@Mixin(Gui.class)
public class GuiMixin {

    /**
     * Per-frame: 每当渲染网格开始前清空 RenderCacheManager 的帧级缓存。
     * Gui#render(GuiGraphics, DeltaTracker) → 包含所有 slot 渲染 → 是帧级边界。
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void beforeRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        org.yanbwe.raritycore.cache.RenderCacheManager.clearFrameCache();
    }

    @Inject(method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
            at = @At("TAIL"))
    private void renderSlotWithRarityBorder(GuiGraphics guiGraphics, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack itemStack, int slotIndex, CallbackInfo ci) {
        if (!itemStack.isEmpty() && !RarityExclusionManager.isRenderingTooltipItem()) {
            ItemBorderRenderer.renderRarityBorder(guiGraphics, itemStack, x, y);
        }
    }
}
