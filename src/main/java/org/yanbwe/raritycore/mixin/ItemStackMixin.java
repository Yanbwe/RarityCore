package org.yanbwe.raritycore.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityConstants;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true, require = 1)
    private void modifyHoverName(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        
        // 检查是否启用物品名称变色功能
        if (!org.yanbwe.raritycore.config.ClientConfigManager.isEnableItemNameColor()) {
            return;
        }
        
        // 获取物品的稀有度(支持 NBT 匹配,使用物品堆缓存)
        // RenderCacheManager.getCachedRarity() 在未命中时已自动计算并缓存
        Integer rarity = org.yanbwe.raritycore.cache.RenderCacheManager.getCachedRarity(stack);
                
        // 如果仍然没有获取到稀有度,使用默认值
        if (rarity == null || rarity < 1) {
            return; // 直接返回,不修改名称颜色
        }
        
        // 如果启用了跳过未配置物品且物品没有配置稀有度,则不修改名称颜色
        // 注意:需要检查物品是否真的没有配置,而不是默认的稀有度1
        // 使用包含神化NBT检查的增强版配置检测
        Item item = stack.getItem();
        if (org.yanbwe.raritycore.config.ClientConfigManager.isSkipUnconfiguredItems() && !RarityRegistry.hasConfiguredRarity(item, stack)) {
            return;
        }
        
        // 检查该等级的 nameColor 开关（全局主开关已通过）
        // 注意：使用原始稀有度值，RarityStyleConfigManager 内部会处理 >7 等级的回退
        if (!RarityStyleConfigManager.isLevelNameColorEnabled(rarity)) {
            return;
        }
        
        // 从 RarityStyle 获取该等级的 RGB 颜色
        int rgbColor = RarityStyleConfigManager.getColor(rarity);
        
        // 如果是普通稀有度(1),则使用白色,但不添加格式化代码(默认颜色)
        if (rarity == RarityConstants.RARITY_COMMON) {
            return;
        }
        Component originalName = cir.getReturnValue();
        
        // 使用 RGB 颜色设置名称颜色
        cir.setReturnValue(originalName.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgbColor))));
    }
    
}