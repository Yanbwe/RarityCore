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
     * 当前正在处理的物品栈
     */
    private static ItemStack currentItemStack = ItemStack.EMPTY;
    
    /**
     * 在物品槽渲染前设置当前物品栈
     */
    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void setCurrentItemStack(DrawContext context, Slot slot, CallbackInfo ci) {
        currentItemStack = slot.getStack();
    }
    
    /**
     * 在物品槽渲染后添加稀有度边框
     */
    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void drawRarityBorder(DrawContext context, Slot slot, CallbackInfo ci) {
        ItemStack itemStack = slot.getStack();
        if (!itemStack.isEmpty()) {
            // 直接使用slot的坐标（相对于屏幕）
            // 在drawSlot方法中，坐标已经是屏幕坐标了
            int x = slot.x;
            int y = slot.y;
            
            // 渲染稀有度边框
            RaritycoreClient.renderItemRarityBorder(context, itemStack, x, y);
        }
        // 清空当前物品栈
        currentItemStack = ItemStack.EMPTY;
    }
    
    // 注意：由于drawSlot方法中可能没有直接调用drawText，
    // 物品名称颜色功能将在其他地方实现
}