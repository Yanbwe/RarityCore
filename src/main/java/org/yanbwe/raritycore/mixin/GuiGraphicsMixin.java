package org.yanbwe.raritycore.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.ItemBorderRenderer;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    @Inject(method = "renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"))
    private void renderFakeItemWithRarityBorder(ItemStack itemStack, int x, int y, CallbackInfo ci) {
        if (!itemStack.isEmpty()) {
            GuiGraphics guiGraphics = (GuiGraphics)(Object)this;
            ItemBorderRenderer.renderRarityBorder(guiGraphics, itemStack, x, y);
        }
    }
}