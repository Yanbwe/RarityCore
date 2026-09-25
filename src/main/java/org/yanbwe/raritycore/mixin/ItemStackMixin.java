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
import org.yanbwe.raritycore.client.IronSpellsDisplaySwitch;
import org.yanbwe.raritycore.config.ClientConfigManager;
import org.yanbwe.raritycore.config.RarityStyleConfigManager;
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

        // 铁魔法联动已在客户端关闭时，把法术等级派生的稀有度替换为回退值（仅影响显示）。
        // 名称颜色与边框/提示走同一条解析入口，必须同样处理，否则关闭开关后名字仍是法术稀有度的颜色
        rarity = IronSpellsDisplaySwitch.applyIfDisabled(stack, rarity, null);

        // 如果启用了跳过未配置物品且物品没有配置稀有度,则不修改名称颜色
        Item item = stack.getItem();
        if (ClientConfigManager.isSkipUnconfiguredItems() && !RarityRegistry.hasConfiguredRarity(item)) {
            return;
        }

        // 直接使用原始稀有度值查询 RarityStyleConfigManager
        // 如果是默认稀有度(1)，则使用白色，但不添加格式化代码(默认颜色)
        if (rarity == RarityConstants.MIN_RARITY) {
            return;
        }
        
        // 从 RarityStyleConfigManager 获取该等级的 RGB 颜色
        // 使用 TextColor.fromRgb() + Style.EMPTY.withColor() 替代 ChatFormatting
        RarityStyleConfigManager mgr = RarityStyleConfigManager.getInstance();
        
        // 检查逐级 nameColor 开关
        if (!mgr.resolveItemNameColor(rarity)) {
            return;
        }
        
        int rgbColor = mgr.resolveColor(rarity);
        Component originalName = cir.getReturnValue();
        
        // 使用 RGB 颜色设置物品名称
        Style coloredStyle = Style.EMPTY.withColor(TextColor.fromRgb(rgbColor));
        cir.setReturnValue(originalName.copy().withStyle(coloredStyle));
    }
    

}