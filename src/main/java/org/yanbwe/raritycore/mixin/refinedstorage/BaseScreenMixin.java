package org.yanbwe.raritycore.mixin.refinedstorage;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.ItemBorderRenderer;

/**
 * 精致存储BaseScreen混合器
 * Hook renderItem方法实现稀有度边框兼容性
 * 使用@Pseudo注解实现软依赖
 */
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.screen.BaseScreen")
public class BaseScreenMixin {
    
    /**
     * 在物品渲染后添加稀有度边框
     * 精致存储直接调用graphics.renderItem，绕过了原版的renderItemDecorations
     * 同时hook两个renderItem重载（4参数简化版和7参数完整版），require=0保证RS未加载时不崩溃
     */
    @Inject(method = {"renderItem(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/item/ItemStack;)V", 
                      "renderItem(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/item/ItemStack;ZLjava/lang/String;I)V"},
            at = @At("TAIL"),
            remap = false,
            require = 0)
    private void onRenderItem(GuiGraphics graphics, int x, int y, ItemStack stack, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty()) {
            try {
                ItemBorderRenderer.renderRarityBorder(graphics, stack, x, y);
            } catch (Exception e) {
                // 静默失败，不影响原版渲染
            }
        }
    }
}