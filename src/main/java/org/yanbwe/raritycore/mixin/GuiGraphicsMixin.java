package org.yanbwe.raritycore.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.ItemBorderRenderer;
import org.yanbwe.raritycore.client.RarityExclusionManager;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    /**
     * 处理物品装饰渲染，为其添加基于稀有度的边框
     * 最关键的注入点，因为物品的装饰在此渲染
     * 同时也涵盖了大部分物品边框显示的需求
     */
    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At(value = "TAIL"), require = 1)
    private void renderItemDecorationsWithRarityBorder(Font font, ItemStack itemStack, int x, int y, CallbackInfo ci) {
        if (!itemStack.isEmpty() && !RarityExclusionManager.isRenderingTooltipItem()) {
            GuiGraphics guiGraphics = (GuiGraphics)(Object)this;
            ItemBorderRenderer.renderRarityBorder(guiGraphics, itemStack, x, y);
        }
    }
    
    /**
     * 处理虚拟物品渲染，为其添加基于稀有度的边框
     * 虚拟物品通常用于JEI等MOD的物品展示，也需要边框
     */
    @Inject(method = "renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At(value = "TAIL"), require = 1)
    private void renderFakeItemWithRarityBorder(ItemStack itemStack, int x, int y, CallbackInfo ci) {
        if (!itemStack.isEmpty() && !RarityExclusionManager.isRenderingTooltipItem()) {
            GuiGraphics guiGraphics = (GuiGraphics)(Object)this;
            ItemBorderRenderer.renderRarityBorder(guiGraphics, itemStack, x, y);
        }
    }
}