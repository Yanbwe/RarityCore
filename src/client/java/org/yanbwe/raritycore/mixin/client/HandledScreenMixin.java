package org.yanbwe.raritycore.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.RaritycoreClient;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    
    /**
     * 在物品槽渲染后添加稀有度边框
     */
    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void drawRarityBorder(DrawContext context, Slot slot, CallbackInfo ci) {
        ItemStack itemStack = slot.getStack();
        if (!itemStack.isEmpty()) {
            // 获取槽位在屏幕上的实际坐标
            HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
            int x = slot.x + screen.getX();
            int y = slot.y + screen.getY();
            
            // 渲染稀有度边框
            RaritycoreClient.renderItemRarityBorder(context, itemStack, x, y);
        }
    }
}