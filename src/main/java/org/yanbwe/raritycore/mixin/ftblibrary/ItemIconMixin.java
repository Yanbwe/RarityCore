package org.yanbwe.raritycore.mixin.ftblibrary;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.ItemBorderRenderer;
import org.yanbwe.raritycore.client.RarityExclusionManager;
import org.yanbwe.raritycore.config.ClientConfigManager;

/**
 * FTB Library ItemIcon 混合器（软依赖，@Pseudo）
 *
 * FTB Quests 的任务界面（QuestScreen）基于 FTB Library 的 BaseScreen 体系，
 * 不继承 AbstractContainerScreen 也不使用原版 Slot，所有物品图标
 * （任务图标、奖励图标、奖励表图标）均通过 ItemIcon#draw / #drawStatic 绘制，
 * 其中 drawStatic 不经过 GuiGraphics#renderItemDecorations，现有 mixin 无法覆盖。
 *
 * 方案：在 ItemIcon#draw / #drawStatic 前后挂载钩子，
 * 渲染期间抑制 GuiGraphicsMixin 的内部钩子（避免重复绘制），
 * 渲染完成后按图标实际绘制尺寸（可能为非 16x16）绘制稀有度边框。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.icon.ItemIcon")
public class ItemIconMixin {

    @Inject(method = {"draw", "drawStatic"},
            at = @At("HEAD"),
            remap = false,
            require = 0)
    private void rc$beforeDrawIcon(GuiGraphics graphics, int x, int y, int w, int h, CallbackInfo ci) {
        if (ClientConfigManager.isEnableFtbLibraryAdapter()) {
            // 接管边框渲染，抑制 renderItemDecorations 内的边框绘制
            RarityExclusionManager.setSuppressBorderRender(true);
        }
    }

    @Inject(method = {"draw", "drawStatic"},
            at = @At("TAIL"),
            remap = false,
            require = 0)
    private void rc$afterDrawIcon(GuiGraphics graphics, int x, int y, int w, int h, CallbackInfo ci) {
        if (!ClientConfigManager.isEnableFtbLibraryAdapter()) {
            return;
        }

        try {
            RarityExclusionManager.setSuppressBorderRender(false);
            if (w <= 0 || h <= 0) {
                return;
            }
            // 鸭子类型强转：调用目标类自身的公开方法 getStack()
            ItemIconAccessor accessor = (ItemIconAccessor) (Object) this;
            ItemBorderRenderer.renderRarityBorderScaled(graphics, accessor.getStack(), x, y, w, h);
        } catch (Exception e) {
            // 静默失败，不影响原渲染
        }
    }
}
