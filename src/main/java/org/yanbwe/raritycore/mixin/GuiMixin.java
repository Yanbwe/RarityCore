package org.yanbwe.raritycore.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.raritycore.client.ItemBorderRenderer;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
            at = @At("HEAD"))
    private void renderSlotWithRarityBorder(GuiGraphics guiGraphics, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack itemStack, int slotIndex, CallbackInfo ci) {
        if (!itemStack.isEmpty()) {
            ItemBorderRenderer.renderRarityBorder(guiGraphics, itemStack, x, y);
        }
    }
}