package org.yanbwe.raritycore.mixin.ftblibrary;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.ItemBorderRenderer;
import org.yanbwe.raritycore.client.RarityExclusionManager;

/**
 * FTB Library GuiHelper混合器
 * Hook drawItem方法实现稀有度边框兼容性
 * FTB系列界面（FTB Quests等）通过GuiHelper.drawItem直渲染物品图标，绕过了原版的renderItemDecorations
 * 使用@Pseudo注解实现软依赖
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.ui.GuiHelper")
public class GuiHelperMixin {

    /**
     * 在物品渲染后添加稀有度边框
     * drawItem被调用时pose已平移到物品中心（ItemIcon及FTB各界面统一约定），
     * 物品模型占据[-8,8]x[-8,8]区域，因此在(-8,-8)绘制16x16边框即可精确对齐，
     * 图标非16尺寸或缩放时边框随pose变换自动匹配
     * require=0保证FTB Library未加载时不崩溃
     */
    @Inject(method = "drawItem(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IZLjava/lang/String;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0)
    private static void onDrawItem(GuiGraphics graphics, ItemStack stack, int hash, boolean renderOverlay, String text, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty() && !RarityExclusionManager.isRenderingTooltipItem()) {
            try {
                ItemBorderRenderer.renderRarityBorder(graphics, stack, -8, -8);
            } catch (Exception e) {
                // 静默失败，不影响原版渲染
            }
        }
    }
}
