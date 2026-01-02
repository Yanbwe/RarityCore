package org.yanbwe.raritycore.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;
import org.yanbwe.raritycore.util.RarityConstants;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void getHoverName(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        
        // 获取物品的稀有度
        Item item = stack.getItem();
        Integer rarity = RarityRegistry.getRarity(item);
        
        // 如果没有注册稀有度，则不修改名称颜色
        if (rarity == null) {
            return;
        }

        // 如果是普通稀有度（1），则使用白色，但不添加格式化代码（默认颜色）
        if (rarity == RarityConstants.RARITY_COMMON) {
            return;
        }

        if (rarity < RarityConstants.MIN_RARITY || rarity > RarityConstants.MAX_RARITY) {
            return;
        }
        
        // 获取对应颜色
        ChatFormatting color = RarityColorUtil.getRarityChatColor(rarity);
        
        // 获取原始名称
        Component originalName = cir.getReturnValue();
        
        // 创建带有颜色格式的新组件
        MutableComponent coloredName = Component.literal(originalName.getString()).withStyle(color);
        
        // 取消原始返回值并设置新的带颜色的名称
        cir.cancel();
        cir.setReturnValue(coloredName);
    }
    
    /**
     * 根据稀有度等级获取对应颜色
     * @param rarity 稀有度等级 (2-7)
     * @return ChatFormatting颜色
     */
    private ChatFormatting getRarityColor(int rarity) {
        return RarityColorUtil.getRarityChatColor(rarity);
    }
}