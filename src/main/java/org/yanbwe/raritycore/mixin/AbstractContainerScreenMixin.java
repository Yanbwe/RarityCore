package org.yanbwe.raritycore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.ItemBorderRenderer;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    // 注入到 renderSlot 方法中，在物品和装饰渲染完成后添加边框
    @Inject(
        method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V", 
        at = @At(
            value = "INVOKE", 
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            shift = org.spongepowered.asm.mixin.injection.At.Shift.AFTER
        )
    )
    private void renderSlot(GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        // 在物品渲染后添加稀有度边框
        ItemStack itemStack = slot.getItem();
        int x = slot.x;
        int y = slot.y;
        ItemBorderRenderer.renderRarityBorder(guiGraphics, itemStack, x, y);
    }
}