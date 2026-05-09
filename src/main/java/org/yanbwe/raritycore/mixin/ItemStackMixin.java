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
import org.yanbwe.raritycore.cache.RenderCacheManager;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.RarityClientConfig;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityConstants;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void modifyHoverName(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        
        // 检查是否启用物品名称变色功能 (client.json 总闸)
        if (!ClientConfigManager.isEnableItemNameColor()) {
            return;
        }
        
        // 获取物品的稀有度(支持 NBT 匹配,使用物品堆缓存)
        Integer rarity = RenderCacheManager.getCachedRarity(stack);
                
        // 如果缓存未命中,则从注册表获取并缓存
        if (rarity == null) {
            rarity = RarityRegistry.getRarity(stack);
            if (rarity != null) {
                RenderCacheManager.cacheItemStackRarity(stack, rarity);
            }
        }
                
        // 如果仍然没有获取到稀有度,使用默认值
        if (rarity == null || rarity < 1) {
            return;
        }
        
        // 如果启用了跳过未配置物品且物品没有配置稀有度,则不修改名称颜色
        Item item = stack.getItem();
        if (ClientConfigManager.isSkipUnconfiguredItems() && !RarityRegistry.hasConfiguredRarity(item)) {
            return;
        }

        // 直接使用原始稀有度值查询 RarityClientConfig，其 getConfig() 内部处理：
        // level < 1 → 回退1 | level > 7 未配置 → 回退7 | level 已配置 → 直接命中
        // 如果是普通稀有度(1),则使用白色,但不添加格式化代码(默认颜色)
        if (rarity == RarityConstants.RARITY_COMMON) {
            return;
        }
        
        // 从 RarityClientConfig 获取该等级的 RGB 颜色
        // 使用 TextColor.fromRgb() + Style.EMPTY.withColor() 替代 ChatFormatting
        RarityClientConfig clientConfig = RarityClientConfig.getInstance();
        
        // 检查 RarityClientConfig 的 per-level nameColor 开关
        if (!clientConfig.isEmpty() && !clientConfig.isNameColorEnabled(rarity)) {
            return;
        }
        
        int rgbColor = clientConfig.getColor(rarity);
        Component originalName = cir.getReturnValue();
        
        // 使用 RGB 颜色设置物品名称
        Style coloredStyle = Style.EMPTY.withColor(TextColor.fromRgb(rgbColor));
        cir.setReturnValue(originalName.copy().withStyle(coloredStyle));
    }
    

}