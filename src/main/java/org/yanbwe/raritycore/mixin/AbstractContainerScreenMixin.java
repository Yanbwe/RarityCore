package org.yanbwe.raritycore.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.ItemBorderRenderer;
import org.yanbwe.raritycore.client.RarityExclusionManager;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    @Inject(
        method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V",
        at = @At("HEAD")
    )
    private void renderSlot(GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        if (RarityExclusionManager.isRenderingTooltipItem()) {
            return;
        }

        ItemStack itemStack = slot.getItem();
        int x = slot.x;
        int y = slot.y;
        ItemBorderRenderer.renderRarityBorder(guiGraphics, itemStack, x, y);
    }
}