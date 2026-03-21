package org.yanbwe.raritycore.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.ItemBorderRenderer;

@Mixin(Gui.class)
public class GuiMixin
{
	@Inject(method = "renderSlot",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V", shift = Shift.AFTER))
	private void renderSlot(GuiGraphics graphics, int x, int y, float time, Player player, ItemStack item, int something, CallbackInfo info)
	{
		// 渲染热键栏物品边框 - 使用根据稀有度变化的颜色
		ItemBorderRenderer.renderRarityBorder(graphics, item, x, y);
	}
}