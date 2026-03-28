package org.yanbwe.raritycore.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.ItemBorderRenderer;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At(value = "TAIL"))
    private void renderItemDecorationsWithRarityBorder(Font font, ItemStack itemStack, int x, int y, @Nullable String s, CallbackInfo ci) {
        if (!itemStack.isEmpty()) {
            GuiGraphics guiGraphics = (GuiGraphics)(Object)this;
            ItemBorderRenderer.renderRarityBorder(guiGraphics, itemStack, x, y);
        }
    }

    @Inject(method = "renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At(value = "TAIL"))
    private void renderFakeItemWithRarityBorder(ItemStack itemStack, int x, int y, CallbackInfo ci) {
        if (!itemStack.isEmpty()) {
            GuiGraphics guiGraphics = (GuiGraphics)(Object)this;
            ItemBorderRenderer.renderRarityBorder(guiGraphics, itemStack, x, y);
        }
    }
}
