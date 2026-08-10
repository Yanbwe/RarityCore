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
     * 
     * 注意：两个renderItem重载必须使用各自独立、参数完整的handler方法。
     * Mixin要求@Inject的handler参数与目标方法参数一一对应（尾部可仅保留CallbackInfo），
     * 若用同一个不完整参数的handler注入多个重载，签名不匹配的目标会被静默跳过
     * （见Mixin 0.8.5 InjectionInfo，多目标时checkDescriptor失败仅return），
     * 而精致存储网格物品（ItemGridStack.draw）实际调用的是7参数完整版，
     * 共用handler会导致7参数版注入失效、边框不渲染。
     * require=0保证RS未加载时不崩溃
     */
    @Inject(method = "renderItem(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/item/ItemStack;)V",
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

    /**
     * Hook带完整参数的renderItem方法
     * 精致存储网格物品（ItemGridStack.draw）通过此重载渲染，handler签名必须与目标完全一致
     */
    @Inject(method = "renderItem(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/item/ItemStack;ZLjava/lang/String;I)V",
            at = @At("TAIL"),
            remap = false,
            require = 0)
    private void onRenderItemFull(GuiGraphics graphics, int x, int y, ItemStack stack, boolean overlay, String text, int textColor, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty()) {
            try {
                ItemBorderRenderer.renderRarityBorder(graphics, stack, x, y);
            } catch (Exception e) {
                // 静默失败，不影响原版渲染
            }
        }
    }
}