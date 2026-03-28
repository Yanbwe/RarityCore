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
     * 精致存储直接调用graphics.renderItem,绕过了原版的renderItemDecorations
     */
    @Inject(method = {"renderItem(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/item/ItemStack;)V", 
                      "renderItem(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/item/ItemStack;ZLjava/lang/String;I)V"},
            at = @At("TAIL"),
            remap = false,
            require = 0)
    private void onRenderItem(GuiGraphics graphics, int x, int y, ItemStack stack, CallbackInfo ci) {
        // 处理简单版本的renderItem调用
        handleRenderItem(graphics, x, y, stack);
    }
    

    
    /**
     * 统一处理物品渲染后的稀有度边框渲染
     */
    private void handleRenderItem(GuiGraphics graphics, int x, int y, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            try {
                // 调用我们的稀有度边框渲染逻辑
                ItemBorderRenderer.renderRarityBorder(graphics, stack, x, y);
            } catch (Exception e) {
                // 静默失败,不影响原版渲染
                // 根据调试日志管理规范,注释掉高频触发的调试信息
                // org.yanbwe.raritycore.RarityCore.LOGGER.debug("Failed to render rarity border for Refined Storage item: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Hook带完整参数的renderItem方法
     */
    @Inject(method = "renderItem(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/item/ItemStack;ZLjava/lang/String;I)V",
            at = @At("TAIL"),
            remap = false)
    private void onRenderItemFull(GuiGraphics graphics, int x, int y, ItemStack stack, boolean overlay, String text, int textColor, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty()) {
            try {
                // 调用我们的稀有度边框渲染逻辑
                ItemBorderRenderer.renderRarityBorder(graphics, stack, x, y);
            } catch (Exception e) {
                // 静默失败,不影响原版渲染
            }
        }
    }
}