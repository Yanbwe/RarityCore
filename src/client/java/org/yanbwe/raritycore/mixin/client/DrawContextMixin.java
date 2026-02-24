package org.yanbwe.raritycore.mixin.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.RaritycoreClient;

@Mixin(DrawContext.class)
public class DrawContextMixin {
    
    /**
     * 处理物品装饰渲染，在物品装饰渲染完成后添加稀有度边框
     * 这模仿了Forge版本GuiGraphicsMixin的实现
     */
    @Inject(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;II)V", 
            at = @At("TAIL"))
    private void drawItemInSlotWithRarityBorder(TextRenderer textRenderer, ItemStack stack, int x, int y, CallbackInfo ci) {
        if (!stack.isEmpty()) {
            // 在物品装饰渲染完成后，根据稀有度渲染边框
            DrawContext context = (DrawContext)(Object)this;
            RaritycoreClient.renderItemRarityBorder(context, stack, x, y);
        }
    }
}