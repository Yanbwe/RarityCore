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
import org.yanbwe.raritycore.config.ClientConfigManager;

/**
 * FTB Library GuiHelper混合器（软依赖，@Pseudo）
 * Hook drawItem方法实现稀有度边框兼容性
 *
 * FTB系列界面（FTB Quests任务界面等）基于FTB Library的BaseScreen体系，
 * 不继承AbstractContainerScreen也不使用原版Slot，所有物品图标
 * （任务图标、奖励图标、奖励表图标、toast等）统一经GuiHelper.drawItem渲染，
 * 其中renderOverlay=false（drawStatic路径）完全不经过原版renderItemDecorations，
 * 现有GuiGraphicsMixin钩子无法覆盖
 *
 * drawItem被调用时pose已平移到物品中心（ItemIcon及FTB各界面统一约定），
 * 物品模型占据[-8,8]x[-8,8]区域，因此在(-8,-8)绘制16x16边框即可精确对齐，
 * 图标非16尺寸或缩放时边框随pose变换自动匹配；
 * stack由drawItem参数直接提供，无需访问实例（避免接口强转的ClassCastException）
 * require=0保证FTB Library未加载时不崩溃
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.ui.GuiHelper")
public class GuiHelperMixin {

    @Inject(method = "drawItem(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IZLjava/lang/String;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0)
    private static void rc$onDrawItem(GuiGraphics graphics, ItemStack stack, int hash, boolean renderOverlay, String text, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty()
                && !RarityExclusionManager.isRenderingTooltipItem()
                && ClientConfigManager.isEnableFtbLibraryAdapter()) {
            try {
                ItemBorderRenderer.renderRarityBorder(graphics, stack, -8, -8);
            } catch (Exception e) {
                // 静默失败，不影响原版渲染
            }
        }
    }
}
