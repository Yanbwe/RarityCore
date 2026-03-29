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

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void renderSlotWithRarityBorder(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ItemStack itemStack = slot.getItem();
        if (!itemStack.isEmpty()) {
            ItemBorderRenderer.renderRarityBorder(guiGraphics, itemStack, slot.x, slot.y);
        }
    }
}